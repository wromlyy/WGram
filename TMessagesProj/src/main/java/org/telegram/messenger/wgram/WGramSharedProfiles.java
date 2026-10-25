package org.telegram.messenger.wgram;

import android.util.Base64;
import android.util.LruCache;
import org.json.JSONObject;
import org.json.JSONArray;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.tgnet.TLRPC;
import java.io.File;
import java.io.FileOutputStream;
import java.util.HashSet;
import java.util.HashMap;
import java.util.ArrayList;

/** Opt-in profile service. No catalog groups are created, searched, joined, or messaged. */
public final class WGramSharedProfiles {
    public static final int MAX_BADGES = 3;
    public static final class Badge {
        public final String id, title, description;
        public final long emoji;
        private Badge(JSONObject value) {
            id = value.optString("id");
            title = clip(value.optString("title"), 40);
            description = clip(value.optString("description"), 300);
            emoji = Math.max(0, value.optLong("emoji"));
        }
    }
    public static final class Profile {
        public boolean premium;
        public long emoji,badgeEmoji;
        public int color=-1;
        public String badgeTitle="",badgeDescription="";
        public final ArrayList<Badge> badges = new ArrayList<>();
        public File banner;
        boolean bannerLoaded;
        long fetched;
    }
    private static boolean cleaned;
    private static final WGramSharedProfiles[] instances=new WGramSharedProfiles[UserConfig.MAX_ACCOUNT_COUNT];
    public static WGramSharedProfiles get(int account){
        if(instances[account]==null)instances[account]=new WGramSharedProfiles(account);
        return instances[account];
    }
    private final int account;
    private long session;
    private int generation;
    private final LruCache<Long,Profile> cache=new LruCache<Long,Profile>(64){
        @Override protected void entryRemoved(boolean evicted,Long key,Profile old,Profile next){
            if(old.banner!=null&&(next==null||!old.banner.equals(next.banner)))old.banner.delete();
        }
    };
    private final HashSet<Long> loading=new HashSet<>();
    private final HashSet<Long> wantBanner=new HashSet<>();
    private final HashMap<Long,Long> attempted=new HashMap<>();
    private final HashMap<Long,ArrayList<Runnable>> callbacks=new HashMap<>();
    private WGramSharedProfiles(int account){
        this.account=account;
        if(!cleaned){cleaned=true;Utilities.globalQueue.postRunnable(()->{
            File[] files=ApplicationLoader.applicationContext.getCacheDir().listFiles((dir,name)->name.startsWith("wgram-remote-"));
            if(files!=null)for(File f:files)f.delete();
        });}
    }
    private void accountCheck(){long user=UserConfig.getInstance(account).getClientUserId();if(session!=user){clear();session=user;}}
    public Profile peek(long user){accountCheck();return cache.get(user);}
    public static boolean hasPremium(int account,TLRPC.User user){Profile p=user==null?null:get(account).peek(user.id);return p!=null&&p.premium;}
    public static long status(int account,TLRPC.User user){Profile p=user==null?null:get(account).peek(user.id);return p==null?0:p.badgeEmoji>0?p.badgeEmoji:p.emoji;}
    public static int color(int account,TLRPC.User user){Profile p=user==null?null:get(account).peek(user.id);return p==null?-1:p.color;}
    public void clear(){generation++;cache.evictAll();loading.clear();callbacks.clear();attempted.clear();wantBanner.clear();}
    public void invalidate(long user){cache.remove(user);attempted.remove(user);}
    /** Only visible users: coalesced requests, including backoff after network failures. */
    public void ensure(long user) {
        accountCheck();
        Long last = attempted.get(user);
        if (user <= 0 || !WGramService.connected(account) || loading.contains(user)
            || last != null && System.currentTimeMillis() - last < 120000) return;
        if (attempted.size() >= 256) attempted.clear();
        attempted.put(user, System.currentTimeMillis());
        load(user, false, () -> {});
    }
    public void load(long user,Runnable changed){load(user,true,changed);}
    private void load(long user,boolean withBanner,Runnable changed){
        accountCheck();
        if(user<=0||!WGramService.connected(account)){changed.run();return;}
        Profile old=cache.get(user);if(old!=null&&System.currentTimeMillis()-old.fetched<120000&&(!withBanner||old.bannerLoaded)){changed.run();return;}
        if(withBanner)wantBanner.add(user);
        ArrayList<Runnable> waiting=callbacks.get(user);
        if(waiting==null){waiting=new ArrayList<>();callbacks.put(user,waiting);}waiting.add(changed);
        if(loading.contains(user))return;
        loading.add(user);final int version=generation;
        WGramService.request(account,"/v1/profiles/"+user,null,true,(json,error)->{
            if(version!=generation)return;
            if(error!=null){notifyLoaded(user);return;}
            Profile data=new Profile();data.fetched=System.currentTimeMillis();data.premium=json.optBoolean("premium");
            data.emoji=Math.max(0,json.optLong("emoji"));data.color=Math.max(-1,Math.min(100,json.optInt("color",-1)));
            JSONArray badges=json.optJSONArray("badges");
            if(badges==null){badges=new JSONArray();JSONObject legacy=json.optJSONObject("badge");if(legacy!=null)badges.put(legacy);}
            for(int i=0;i<Math.min(MAX_BADGES,badges.length());i++){
                JSONObject value=badges.optJSONObject(i);
                if(value==null||!value.optBoolean("active"))continue;
                Badge badge=new Badge(value);
                if(badge.emoji>0&&!badge.title.isEmpty())data.badges.add(badge);
            }
            if(!data.badges.isEmpty()){
                Badge first=data.badges.get(0);data.badgeEmoji=first.emoji;data.badgeTitle=first.title;data.badgeDescription=first.description;
            }
            data.bannerLoaded=!json.optBoolean("banner");
            if(json.optBoolean("banner")&&wantBanner.contains(user))WGramService.banner(account,user,(bytes,failure)->{
                if(version!=generation)return;
                Utilities.globalQueue.postRunnable(()->{
                    File file=null;
                    if(bytes!=null&&bytes.length>=5&&(bytes[0]&255)==255&&(bytes[1]&255)==216){
                        try{
                            file=File.createTempFile("wgram-remote-",".jpg",ApplicationLoader.applicationContext.getCacheDir());
                            try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes);}
                        }catch(Exception e){if(file!=null)file.delete();file=null;}
                    }
                    final File saved=file;
                    AndroidUtilities.runOnUIThread(()->{
                        if(version!=generation){if(saved!=null)saved.delete();return;}
                        data.banner=saved!=null?saved:old==null?null:old.banner;data.bannerLoaded=saved!=null;complete(user,data,changed);
                    });
                });
            });
            else {if(json.optBoolean("banner")&&old!=null)data.banner=old.banner;complete(user,data,changed);}
        });
    }
    private void complete(long user,Profile data,Runnable changed){
        cache.put(user,data);
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.updateInterfaces,MessagesController.UPDATE_MASK_EMOJI_STATUS);
        notifyLoaded(user);
    }
    private void notifyLoaded(long user){
        loading.remove(user);wantBanner.remove(user);ArrayList<Runnable> waiting=callbacks.remove(user);
        if(waiting!=null)for(Runnable callback:waiting)callback.run();
    }
    private static String clip(String s,int max){return s.length()>max?s.substring(0,max):s;}
    public void publish(WGramService.Result callback){
        final long owner=UserConfig.getInstance(account).getClientUserId();
        final String endpoint=WGramService.address(account);
        final boolean premium=WGramConfig.isVisualPremiumEnabled(account);
        final long emoji=WGramConfig.getProfileEmoji(account);final int color=WGramConfig.getProfileColor(account);
        Utilities.globalQueue.postRunnable(()->{
            JSONObject data=new JSONObject();String error=null;
            try{
                data.put("premium",premium).put("emoji",emoji).put("color",color);
                File banner=WGramBanners.file(owner);
                if(banner.exists()){
                    if(banner.length()>1500000)throw new IllegalArgumentException();
                    java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream();
                    try(java.io.FileInputStream input=new java.io.FileInputStream(banner)){
                        byte[] b=new byte[8192];int count;while((count=input.read(b))!=-1){output.write(b,0,count);if(output.size()>1500000)throw new IllegalArgumentException();}
                    }
                    data.put("banner",Base64.encodeToString(output.toByteArray(),Base64.NO_WRAP));
                }
                // No local banner: preserve a photo uploaded directly to the bot.
            }catch(Exception e){error="INVALID_BANNER";}
            final String failure=error;
            AndroidUtilities.runOnUIThread(()->{
                if(owner!=UserConfig.getInstance(account).getClientUserId()||!endpoint.equals(WGramService.address(account)))return;
                if(failure!=null){callback.done(null,failure);return;}
                WGramService.request(account,"/v1/me/profile",data,true,(result,err)->{if(err==null)invalidate(owner);callback.done(result,err);});
            });
        });
    }
    public void badge(long target,boolean active,long emoji,String title,String description,WGramService.Result callback){
        badge(target,active,emoji,title,description,"",callback);
    }
    public void badge(long target,boolean active,long emoji,String title,String description,String id,WGramService.Result callback){
        try{WGramService.request(account,"/v1/admin/badge",new JSONObject().put("uid",target).put("active",active).put("emoji",emoji)
            .put("id",id).put("title",clip(title,40)).put("description",clip(description,300)),true,(result,error)->{if(error==null){invalidate(target);ensure(target);}callback.done(result,error);});}
        catch(Exception e){callback.done(null,"INVALID_BADGE");}
    }
}
