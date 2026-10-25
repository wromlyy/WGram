package org.telegram.messenger.wgram;

import android.util.LruCache;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import java.util.ArrayList;
import java.util.HashSet;

/** Public, opt-in Telegram catalog. Each record is bound to its actual MTProto sender. */
public final class WGramSharedProfiles {
    public static final String CATALOG = "wgram_profiles_8226881959";
    public interface Result { void done(long channel, String error); }
    public static final class Profile {
        public boolean premium;
        public long emoji, badgeEmoji;
        public int color=-1;
        public String badgeTitle="", badgeDescription="";
        public TLRPC.Photo banner;
        long fetched;
    }
    private static final WGramSharedProfiles[] instances=new WGramSharedProfiles[UserConfig.MAX_ACCOUNT_COUNT];
    public static WGramSharedProfiles get(int account){
        if(instances[account]==null)instances[account]=new WGramSharedProfiles(account);
        return instances[account];
    }
    private final int account;
    private final LruCache<Long,Profile> cache=new LruCache<>(128);
    private final HashSet<Long> loading=new HashSet<>();
    private final ArrayList<Result> resolving=new ArrayList<>();
    private long channel;
    private long resolveFailedAt;
    private boolean resolveRunning;
    private WGramSharedProfiles(int account){this.account=account;}
    private MessagesController controller(){return MessagesController.getInstance(account);}
    public Profile peek(long user){return cache.get(user);}
    public static boolean hasPremium(int account,TLRPC.User user){Profile p=user==null?null:get(account).peek(user.id);return p!=null&&p.premium;}
    public static long status(int account,TLRPC.User user){Profile p=user==null?null:get(account).peek(user.id);return p==null?0:p.badgeEmoji>0?p.badgeEmoji:p.emoji;}
    public static int color(int account,TLRPC.User user){Profile p=user==null?null:get(account).peek(user.id);return p==null?-1:p.color;}
    public void clear(){cache.evictAll();loading.clear();channel=0;}
    public void invalidate(long user){cache.remove(user);}
    public void resolve(Result result){
        if(channel!=0){result.done(channel,null);return;}
        if(System.currentTimeMillis()-resolveFailedAt<30000){result.done(0,"WGRAM_CATALOG_NOT_READY");return;}
        resolving.add(result);if(resolveRunning)return;resolveRunning=true;
        TLRPC.TL_contacts_resolveUsername req=new TLRPC.TL_contacts_resolveUsername();req.username=CATALOG;
        ConnectionsManager.getInstance(account).sendRequest(req,(response,error)->AndroidUtilities.runOnUIThread(()->{
            if(response instanceof TLRPC.TL_contacts_resolvedPeer){
                TLRPC.TL_contacts_resolvedPeer peer=(TLRPC.TL_contacts_resolvedPeer)response;
                controller().putUsers(peer.users,false);controller().putChats(peer.chats,false);
                if(peer.peer!=null)channel=peer.peer.channel_id;
            }
            if(channel==0)resolveFailedAt=System.currentTimeMillis();
            resolveRunning=false;ArrayList<Result> callbacks=new ArrayList<>(resolving);resolving.clear();
            for(Result callback:callbacks)callback.done(channel,channel==0?"WGRAM_CATALOG_NOT_READY":null);
        }));
    }
    public void create(Result callback){
        if(!WGramNoticeWire.isAdmin(UserConfig.getInstance(account).getClientUserId())){callback.done(0,"WGRAM_OWNER_ONLY");return;}
        long pending=WGramConfig.account(account).getLong("catalog_pending",0);
        if(pending!=0){setUsername(pending,callback);return;}
        resolveFailedAt=0;
        resolve((existing,error)->{
            if(existing!=0){
                TLRPC.Chat chat=controller().getChat(existing);
                callback.done(existing,chat!=null&&chat.creator?null:"WGRAM_CATALOG_NAME_TAKEN");return;
            }
            TLRPC.TL_channels_createChannel req=new TLRPC.TL_channels_createChannel();req.megagroup=true;
            req.title="WGram · профили и значки";req.about="Публичный каталог оформления WGram. Публикуйте только свои баннеры. Администратор: 8226881959.";
            ConnectionsManager.getInstance(account).sendRequest(req,(res,err)->AndroidUtilities.runOnUIThread(()->{
                if(!(res instanceof TLRPC.Updates)){callback.done(0,err==null?"WGRAM_CREATE_FAILED":err.text);return;}
                TLRPC.Updates updates=(TLRPC.Updates)res;
                controller().processUpdates(updates,false);
                controller().putUsers(updates.users,false);controller().putChats(updates.chats,false);
                if(updates.chats.isEmpty()){callback.done(0,"WGRAM_CREATE_FAILED");return;}
                long id=updates.chats.get(0).id;WGramConfig.account(account).edit().putLong("catalog_pending",id).apply();
                setUsername(id,callback);
            }));
        });
    }
    private void setUsername(long id,Result callback){
        TLRPC.TL_channels_updateUsername req=new TLRPC.TL_channels_updateUsername();req.channel=controller().getInputChannel(id);req.username=CATALOG;
        ConnectionsManager.getInstance(account).sendRequest(req,(res,error)->AndroidUtilities.runOnUIThread(()->{
            if(error!=null){callback.done(0,error.text);return;}
            channel=id;resolveFailedAt=0;WGramConfig.account(account).edit().remove("catalog_pending").apply();callback.done(id,null);
        }));
    }
    public void join(Result callback){
        resolve((id,error)->{
            if(id==0){callback.done(0,error);return;}
            TLRPC.TL_channels_joinChannel req=new TLRPC.TL_channels_joinChannel();req.channel=controller().getInputChannel(id);
            ConnectionsManager.getInstance(account).sendRequest(req,(res,err)->AndroidUtilities.runOnUIThread(()->{
                if(err!=null && !"USER_ALREADY_PARTICIPANT".equals(err.text)){callback.done(0,err.text);return;}
                if(res instanceof TLRPC.TL_chatInviteJoinResultOk)controller().processUpdates(((TLRPC.TL_chatInviteJoinResultOk)res).updates,false);
                callback.done(id,null);
            }));
        });
    }
    public void load(long user,Runnable changed){
        if(user<=0 || loading.contains(user))return;
        Profile old=cache.get(user);
        if(old!=null && System.currentTimeMillis()-old.fetched<5*60*1000)return;
        loading.add(user);final long session=UserConfig.getInstance(account).getClientUserId();
        resolve((id,error)->{
            if(id==0 || session!=UserConfig.getInstance(account).getClientUserId()){loading.remove(user);return;}
            Profile data=new Profile();data.fetched=System.currentTimeMillis();cache.put(user,data);
            final int[] pending={2};
            Runnable complete=()->{if(--pending[0]==0)loading.remove(user);if(session==UserConfig.getInstance(account).getClientUserId()){
                NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.updateInterfaces,MessagesController.UPDATE_MASK_ALL);changed.run();
            }};
            search(id,user,"#wgram_profile_"+user,messages->{
                int latest=0;
                for(TLRPC.Message m:messages){
                    JSONObject json=record(m,user,"#wgram_profile_"+user);
                    if(json==null || m.id<=latest || json.optLong("uid")!=user)continue;
                    latest=m.id;data.premium=json.optBoolean("premium");data.emoji=Math.max(0,json.optLong("emoji"));
                    data.color=Math.max(-1,Math.min(100,json.optInt("color",-1)));
                    data.banner=m.media==null?null:m.media.photo;
                }
                complete.run();
            });
            search(id,WGramNoticeWire.ADMIN_ID,"#wgram_badge_"+user,messages->{
                int latest=0;
                for(TLRPC.Message m:messages){
                    JSONObject json=record(m,WGramNoticeWire.ADMIN_ID,"#wgram_badge_"+user);
                    if(json==null || m.id<=latest || json.optLong("uid")!=user)continue;
                    latest=m.id;boolean active=json.optBoolean("active");
                    data.badgeEmoji=active?Math.max(0,json.optLong("emoji")):0;
                    data.badgeTitle=active?clip(json.optString("title"),40):"";
                    data.badgeDescription=active?clip(json.optString("description"),300):"";
                }
                complete.run();
            });
        });
    }
    private interface Messages {void done(ArrayList<TLRPC.Message> messages);}
    private void search(long id,long sender,String query,Messages callback){
        TLRPC.TL_messages_search req=new TLRPC.TL_messages_search();req.peer=controller().getInputPeer(-id);
        req.from_id=controller().getUser(sender)==null?null:controller().getInputPeer(sender);req.q=query;req.filter=new TLRPC.TL_inputMessagesFilterEmpty();req.limit=100;
        ConnectionsManager.getInstance(account).sendRequest(req,(res,error)->AndroidUtilities.runOnUIThread(()->{
            if(res instanceof TLRPC.messages_Messages){
                TLRPC.messages_Messages data=(TLRPC.messages_Messages)res;controller().putUsers(data.users,false);controller().putChats(data.chats,false);callback.done(data.messages);
            }else callback.done(new ArrayList<>());
        }));
    }
    private static JSONObject record(TLRPC.Message m,long sender,String tag){
        if(m.from_id==null || m.from_id.user_id!=sender || m.fwd_from!=null || m.message==null
            || m.message.length()>2048 || !m.message.startsWith(tag+"\n") || m.ttl_period!=0)return null;
        try{JSONObject j=new JSONObject(m.message.substring(tag.length()+1));return j.optInt("v")==1?j:null;}catch(Exception e){return null;}
    }
    private static String clip(String s,int max){return s.length()>max?s.substring(0,max):s;}
    public String profileRecord(){
        long user=UserConfig.getInstance(account).getClientUserId();
        try{return "#wgram_profile_"+user+"\n"+new JSONObject().put("v",1).put("uid",user)
            .put("premium",WGramConfig.isVisualPremiumEnabled(account)).put("emoji",WGramConfig.getProfileEmoji(account)).put("color",WGramConfig.getProfileColor(account));
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    public static String badgeRecord(long target,boolean active,long emoji,String title,String description){
        try{return "#wgram_badge_"+target+"\n"+new JSONObject().put("v",1).put("uid",target).put("active",active)
            .put("emoji",emoji).put("title",clip(title,40)).put("description",clip(description,300));
        }catch(Exception e){throw new IllegalStateException(e);}
    }
}
