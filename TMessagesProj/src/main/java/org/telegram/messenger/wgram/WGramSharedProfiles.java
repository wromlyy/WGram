package org.telegram.messenger.wgram;

import android.util.Base64;
import android.util.LruCache;
import org.json.JSONObject;
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
    public static final class Profile {
        public boolean premium;
        public long emoji,badgeEmoji;
        public int color=-1;
        public String badgeTitle="",badgeDescription="";
        public File banner;
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
    public void clear(){generation++;cache.evictAll();loading.clear();callbacks.clear();}
    public void invalidate(long user){cache.remove(user);}
    public void load(long user,Runnable changed){
        accountCheck();
        if(user<=0||!WGramService.connected(account)){changed.run();return;}
        Profile old=cache.get(user);if(old!=null&&System.currentTimeMillis()-old.fetched<120000){changed.run();return;}
        ArrayList<Runnable> waiting=callbacks.get(user);
        if(waiting==null){waiting=new ArrayList<>();callbacks.put(user,waiting);}waiting.add(changed);
        if(loading.contains(user))return;
        loading.add(user);final int version=generation;
        WGramService.request(account,"/v1/profiles/"+user,null,true,(json,error)->{
            if(version!=generation)return;
            if(error!=null){notifyLoaded(user);return;}
            Profile data=new Profile();data.fetched=System.currentTimeMillis();data.premium=json.optBoolean("premium");
            data.emoji=Math.max(0,json.optLong("emoji"));data.color=Math.max(-1,Math.min(100,json.optInt("color",-1)));
            JSONObject badge=json.optJSONObject("badge");
            if(badge!=null&&badge.optBoolean("active")){
                data.badgeEmoji=Math.max(0,badge.optLong("emoji"));data.badgeTitle=clip(badge.optString("title"),40);data.badgeDescription=clip(badge.optString("description"),300);
            }
            if(json.optBoolean("banner"))WGramService.banner(account,user,(bytes,failure)->{
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
                        data.banner=saved;complete(user,data,changed);
                    });
                });
            });
            else complete(user,data,changed);
        });
    }
    private void complete(long user,Profile data,Runnable changed){
        cache.put(user,data);
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.updateInterfaces,MessagesController.UPDATE_MASK_EMOJI_STATUS);
        notifyLoaded(user);
    }
    private void notifyLoaded(long user){
        loading.remove(user);ArrayList<Runnable> waiting=callbacks.remove(user);
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
        try{WGramService.request(account,"/v1/admin/badge",new JSONObject().put("uid",target).put("active",active).put("emoji",emoji)
            .put("title",clip(title,40)).put("description",clip(description,300)),true,(result,error)->{if(error==null)invalidate(target);callback.done(result,error);});}
        catch(Exception e){callback.done(null,"INVALID_BADGE");}
    }
}
