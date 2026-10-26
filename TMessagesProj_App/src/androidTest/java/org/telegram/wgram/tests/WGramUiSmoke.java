package org.telegram.wgram.tests;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.graphics.Bitmap;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import org.telegram.messenger.wgram.WGramConfig;
import org.telegram.messenger.wgram.WGramHistory;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.SettingsActivity;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.wgram.WGramAdminActivity;
import org.telegram.ui.wgram.WGramProfileEditorActivity;
import org.telegram.ui.wgram.WGramSettingsActivity;
import org.telegram.ui.wgram.WGramServiceActivity;

/** Runs only on an offline, empty emulator; no Telegram login or network credentials. */
public final class WGramUiSmoke extends Instrumentation {
    private LaunchActivity activity;
    private Throwable failure;
    private int checks;

    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    private void main(Runnable action) throws Throwable {
        failure = null;
        java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(1);
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            try { action.run(); } catch (Throwable t) { failure = t; } finally { ready.countDown(); }
        });
        if (!ready.await(20, java.util.concurrent.TimeUnit.SECONDS)) {
            Throwable blocked = new Throwable("Main UI thread did not respond within 20 seconds");
            blocked.setStackTrace(android.os.Looper.getMainLooper().getThread().getStackTrace());
            throw blocked;
        }
        if (failure != null) throw failure;
    }
    private void open(String name, BaseFragment fragment) throws Throwable {
        Bundle opening = new Bundle(); opening.putString("stream", "OPEN: " + name + "\n"); sendStatus(0, opening);
        main(() -> {
            fragment.setCurrentAccount(0);
            if (!activity.presentFragment(fragment, false, true)) throw new AssertionError("Cannot open " + name);
        });
        SystemClock.sleep(1200);
        // Animated profiles keep posting frames; verify via a bounded main-thread callback.
        main(() -> {
            if (fragment.getFragmentView() == null) throw new AssertionError("No view: " + name);
        });
        Bitmap screen=getUiAutomation().takeScreenshot();
        if(screen!=null){
            File dir=new File(getTargetContext().getExternalFilesDir(null),"wgram-ui-results");dir.mkdirs();
            try(FileOutputStream out=new FileOutputStream(new File(dir,name.replace(' ','-')+".png"))){screen.compress(Bitmap.CompressFormat.PNG,100,out);}
            finally{screen.recycle();}
        }
        checks++;
        Bundle status = new Bundle(); status.putString("stream", "PASS: " + name + "\n"); sendStatus(0, status);
        main(() -> fragment.finishFragment(false));
        SystemClock.sleep(250);
    }
    private void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
    private void historyChecks() throws Throwable {
        java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);
        final Throwable[] error={null};
        org.telegram.messenger.MessagesStorage.getInstance(0).getStorageQueue().postRunnable(()->{
            try{
                long self=UserConfig.getInstance(0).getClientUserId();
                WGramHistory.clear(self);WGramConfig.setHistoryEnabled(0,true);
                TLRPC.TL_message m=new TLRPC.TL_message();m.id=10;m.date=(int)(System.currentTimeMillis()/1000)-60;
                m.dialog_id=9000000001L;m.peer_id=new TLRPC.TL_peerUser();m.peer_id.user_id=m.dialog_id;
                m.from_id=new TLRPC.TL_peerUser();m.from_id.user_id=m.dialog_id;m.message="original text";
                WGramHistory.observe(0,Collections.singletonList(m));
                m.message="edited text";WGramHistory.observe(0,Collections.singletonList(m));
                check(WGramHistory.list(0,m.dialog_id,"",2).get(0).text.equals("original text"),"edit snapshot");
                WGramHistory.deleted(0,m.dialog_id,Collections.singletonList(10),0);
                check(WGramHistory.list(0,m.dialog_id,"",1).get(0).text.equals("edited text"),"deleted snapshot");
                ArrayList<MessageObject> page=new ArrayList<>();WGramHistory.mergeInto(0,m.dialog_id,page,0,0,true);
                check(page.size()==1 && page.get(0).wgramDeleted,"deleted message restored into empty chat");
                check(page.get(0).messageOwner.date==m.date && page.get(0).getId()==10,"date and ID preserved");
                check(page.get(0).messageOwner.from_id.user_id==m.dialog_id,"sender preserved");
                ArrayList<MessageObject> bounds=new ArrayList<>();
                WGramHistory.mergeInto(0,m.dialog_id,bounds,5,MessagesController.LOAD_BACKWARD,true);check(bounds.isEmpty(),"older page cannot include newer tombstone");
                WGramHistory.mergeInto(0,m.dialog_id,bounds,15,MessagesController.LOAD_FORWARD,true);check(bounds.isEmpty(),"newer page cannot include older tombstone");
                WGramHistory.mergeInto(0,m.dialog_id,bounds,15,MessagesController.LOAD_BACKWARD,true);check(bounds.size()==1,"older empty page restores the right tombstone");
                WGramHistory.mergeInto(0,m.dialog_id,page,0,0,true);check(page.size()==1,"page merge has no duplicate IDs");
                WGramHistory.observe(0,Collections.singletonList(m));WGramHistory.deleted(0,m.dialog_id,Collections.singletonList(10),0);
                check(WGramHistory.list(0,m.dialog_id,"",1).size()==1,"server replay does not duplicate archive");
                page.clear();WGramHistory.mergeInto(0,m.dialog_id+1,page,0,0,true);check(page.isEmpty(),"other chat isolated");
                m.ttl_period=10;WGramHistory.observe(0,Collections.singletonList(m));
                check(WGramHistory.list(0,m.dialog_id,"",0).isEmpty(),"expiring edit removes old snapshots");
                page.clear();WGramHistory.mergeInto(0,m.dialog_id,page,0,0,true);check(page.isEmpty(),"expiring text removed from inline archive");
                WGramConfig.setHistoryEnabled(0,false);WGramHistory.clear(self);
            }catch(Throwable t){error[0]=t;}finally{done.countDown();}
        });
        if(!done.await(20,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("history checks timed out");
        if(error[0]!=null)throw error[0];
    }
    private Object field(Object object,String name) throws Exception {
        Class<?> type=object.getClass();
        while(type!=null){try{java.lang.reflect.Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(NoSuchFieldException e){type=type.getSuperclass();}}
        throw new NoSuchFieldException(name);
    }
    @SuppressWarnings("unchecked")
    private void seedAwards(long uid) throws Exception {
        org.telegram.messenger.wgram.WGramSharedProfiles service=org.telegram.messenger.wgram.WGramSharedProfiles.get(0);
        service.peek(uid);
        org.telegram.messenger.wgram.WGramSharedProfiles.Profile profile=new org.telegram.messenger.wgram.WGramSharedProfiles.Profile();
        java.lang.reflect.Constructor<org.telegram.messenger.wgram.WGramSharedProfiles.Badge> constructor=
            org.telegram.messenger.wgram.WGramSharedProfiles.Badge.class.getDeclaredConstructor(org.json.JSONObject.class);
        constructor.setAccessible(true);
        for(int i=0;i<3;i++)profile.badges.add(constructor.newInstance(new org.json.JSONObject().put("id","fixture"+i)
            .put("emoji",5368324170671202286L+i).put("title","Award "+i).put("description","Offline layout fixture")));
        profile.badgeEmoji=profile.badges.get(0).emoji;profile.badgeTitle=profile.badges.get(0).title;
        ((android.util.LruCache<Long,org.telegram.messenger.wgram.WGramSharedProfiles.Profile>)field(service,"cache")).put(uid,profile);
    }
    private void emojiChecks() {
        String text="😀😀";
        ArrayList<TLRPC.MessageEntity> entities=new ArrayList<>();
        for(int i=0;i<2;i++){
            TLRPC.TL_messageEntityTextUrl e=new TLRPC.TL_messageEntityTextUrl();e.offset=i*2;e.length=2;
            e.url=org.telegram.messenger.wgram.WGramEmojiWire.encode(5368324170671202286L+i);entities.add(e);
        }
        android.graphics.Paint paint=new android.graphics.Paint();paint.setTextSize(org.telegram.messenger.AndroidUtilities.dp(16));
        android.text.Spannable rendered=MessageObject.replaceAnimatedEmoji(text,entities,paint.getFontMetricsInt());
        org.telegram.ui.Components.AnimatedEmojiSpan[] spans=rendered.getSpans(0,rendered.length(),org.telegram.ui.Components.AnimatedEmojiSpan.class);
        check(spans.length==2,"adjacent received emojis render as two spans");
        check(rendered.getSpanEnd(spans[0])<=rendered.getSpanStart(spans[1]),"emoji spans do not overlap");
        CharSequence editing=org.telegram.ui.Components.ChatActivityEnterView.applyMessageEntities(entities,text,paint.getFontMetricsInt());
        check(editing instanceof android.text.Spanned&&((android.text.Spanned)editing).getSpans(0,editing.length(),org.telegram.ui.Components.AnimatedEmojiSpan.class).length==2,"editing restores both emojis");
        TLRPC.TL_messageEntityCustomEmoji broken=new TLRPC.TL_messageEntityCustomEmoji();broken.offset=-1;broken.length=Integer.MAX_VALUE;broken.document_id=42;
        entities.add(broken);check(MessageObject.replaceAnimatedEmoji(text,entities,paint.getFontMetricsInt())!=null,"corrupt emoji cannot crash Android renderer");
    }
    private BaseFragment dialogFixture() {
        return new BaseFragment(){@Override public android.view.View createView(android.content.Context context){
            android.widget.LinearLayout root=new android.widget.LinearLayout(context);root.setOrientation(android.widget.LinearLayout.VERTICAL);
            root.setBackgroundColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhite));
            actionBar.setTitle("WGram badge layout");
            for(boolean rtl:new boolean[]{false,true}){
                boolean saved=org.telegram.messenger.LocaleController.isRTL;
                org.telegram.messenger.LocaleController.isRTL=rtl;
                try{
                    org.telegram.ui.Cells.DialogCell cell=new org.telegram.ui.Cells.DialogCell(null,context,false,false,0,null);
                    cell.setDialog(9000000001L,null,(int)(System.currentTimeMillis()/1000),false,false);
                    int width=org.telegram.messenger.AndroidUtilities.dp(320);
                    cell.measure(android.view.View.MeasureSpec.makeMeasureSpec(width,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(0,android.view.View.MeasureSpec.UNSPECIFIED));
                    cell.layout(0,0,width,cell.getMeasuredHeight());cell.buildLayout();
                    org.telegram.ui.wgram.WGramBadgeDrawable strip=(org.telegram.ui.wgram.WGramBadgeDrawable)field(cell,"emojiStatus");
                    android.view.View view=(android.view.View)field(cell,"emojiStatusView");
                    check((Boolean)field(cell,"drawPremium"),"non-premium award visible "+rtl);
                    check(strip.getIntrinsicWidth()==org.telegram.messenger.AndroidUtilities.dp(22)*3+org.telegram.messenger.AndroidUtilities.dp(2)*2,"three awards measured "+rtl);
                    check(view.getWidth()==strip.getIntrinsicWidth(),"badge view reserves complete strip "+rtl);
                    android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(width,cell.getMeasuredHeight(),android.graphics.Bitmap.Config.ARGB_8888);
                    cell.draw(new android.graphics.Canvas(bitmap));
                    final int[] redraws={0};
                    android.graphics.drawable.Drawable.Callback previous=strip.getCallback();
                    strip.setCallback(new android.graphics.drawable.Drawable.Callback(){
                        @Override public void invalidateDrawable(android.graphics.drawable.Drawable d){redraws[0]++;}
                        @Override public void scheduleDrawable(android.graphics.drawable.Drawable d,Runnable r,long when){}
                        @Override public void unscheduleDrawable(android.graphics.drawable.Drawable d,Runnable r){}
                    });
                    android.graphics.Rect originalBounds=new android.graphics.Rect(strip.getBounds());
                    strip.draw(new android.graphics.Canvas(bitmap));strip.setCallback(previous);bitmap.recycle();
                    check(redraws[0]==0,"drawing stable awards does not start an endless redraw "+rtl);
                    check(originalBounds.equals(strip.getBounds()),"drawing preserves measured strip bounds "+rtl);
                    int badgeLeft=(Integer)field(cell,"nameMuteLeft")-org.telegram.messenger.AndroidUtilities.dp(2);
                    check(badgeLeft>=0&&badgeLeft+strip.getIntrinsicWidth()<=width,"awards stay in cell "+rtl);
                    root.addView(cell,new android.widget.LinearLayout.LayoutParams(width,cell.getMeasuredHeight()));
                }catch(Exception e){throw new RuntimeException(e);}finally{org.telegram.messenger.LocaleController.isRTL=saved;}
            }
            return fragmentView=root;
        }};
    }
    private String pluginCall(String operation, Object... args) throws Exception {
        java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(1);
        String[] result = new String[2];
        org.telegram.messenger.wgram.WGramPlugins.execute(operation, (json,error) -> {
            result[0]=json;result[1]=error;ready.countDown();
        }, args);
        if(!ready.await(30,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("Plugin operation timed out: "+operation);
        if(result[1]!=null)throw new AssertionError(result[1]);
        return result[0];
    }
    private void pluginMaster(boolean enabled) throws Exception {
        java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(1);
        String[] error = new String[1];
        org.telegram.messenger.wgram.WGramPlugins.setEnabled(enabled,(json,failure)->{error[0]=failure;ready.countDown();});
        if(!ready.await(30,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("Plugin engine timed out");
        if(error[0]!=null)throw new AssertionError(error[0]);
    }
    private void pluginChecks() throws Exception {
        File source=File.createTempFile("plugin-smoke-", ".py",getTargetContext().getCacheDir());
        String code="from base_plugin import BasePlugin, HookResult, HookStrategy\n"
            +"__id__ = 'wgram_smoke'\n__name__ = 'Smoke plugin'\n"
            +"class SmokePlugin(BasePlugin):\n"
            +"    def on_plugin_load(self):\n        self.add_on_send_message_hook()\n"
            +"    def on_send_message_hook(self, account, params):\n"
            +"        params.message += ' #' + str(account) + ':' + str(params.peer)\n"
            +"        return HookResult(HookStrategy.MODIFY, params)\n";
        try(FileOutputStream output=new FileOutputStream(source)){output.write(code.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        try{
            check(!org.telegram.messenger.wgram.WGramPlugins.enabled(),"Python engine starts disabled");
            org.json.JSONObject metadata=new org.json.JSONObject(pluginCall("inspect",source.getAbsolutePath()));
            check(metadata.getString("id").equals("wgram_smoke"),"Python runtime parses metadata on Android");
            pluginCall("install",source.getAbsolutePath(),metadata.getString("sha256"));
            check(!new org.json.JSONArray(pluginCall("list_plugins")).getJSONObject(0).getBoolean("enabled"),"Android plugin install does not execute code");
            pluginMaster(true);
            pluginCall("enable","wgram_smoke",true);
            check(new org.json.JSONArray(pluginCall("list_plugins")).getJSONObject(0).getBoolean("running"),"Python plugin lifecycle runs on Android");
            org.telegram.messenger.SendMessagesHelper.SendMessageParams params=new org.telegram.messenger.SendMessagesHelper.SendMessageParams();
            params.message="Hello";params.peer=9000000001L;
            params.entities=new ArrayList<>();params.entities.add(new TLRPC.TL_messageEntityBold());
            check(org.telegram.messenger.wgram.WGramPlugins.beforeSend(0,params),"Android send hook allows modified message");
            check(params.message.equals("Hello #0:9000000001"),"Java Python bridge preserves 64-bit peer and account");
            check(params.entities==null,"changed text drops stale formatting offsets");
            params.message="secret";params.peer=org.telegram.messenger.DialogObject.makeEncryptedDialogId(12);
            check(org.telegram.messenger.wgram.WGramPlugins.beforeSend(0,params)&&params.message.equals("secret"),"secret chat bypasses send hooks");
            pluginCall("enable","wgram_smoke",false);
            check(!new org.json.JSONArray(pluginCall("list_plugins")).getJSONObject(0).getBoolean("running"),"Android plugin unloads");
            pluginCall("remove","wgram_smoke");
            check(new org.json.JSONArray(pluginCall("list_plugins")).length()==0,"Android plugin removal cleans list");
        }finally{source.delete();pluginMaster(false);}
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            main(() -> {
                ApplicationLoader.postInitApplication();
                UserConfig.selectedAccount = 0;
                TLRPC.TL_user owner = new TLRPC.TL_user();
                owner.id = 8226881959L; owner.self = true; owner.first_name = "WGram Smoke";
                UserConfig.getInstance(0).setCurrentUser(owner);
                MessagesController.getInstance(0).putUser(owner, false);
                TLRPC.TL_user other = new TLRPC.TL_user();
                other.id = 9000000001L; other.first_name = "Offline profile";
                MessagesController.getInstance(0).putUser(other, false);
            });
            Intent intent = new Intent(getTargetContext(), LaunchActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity = (LaunchActivity) startActivitySync(intent);
            SystemClock.sleep(1500);
            Bundle own = new Bundle(); own.putLong("user_id", 8226881959L);
            open("own profile", new ProfileActivity(own));
            Bundle other = new Bundle(); other.putLong("user_id", 9000000001L);
            open("other profile", new ProfileActivity(other));
            open("private chat", new ChatActivity(other));
            main(()->WGramConfig.setProfileEmoji(0,5368324170671202286L));
            open("profile with local status", new ProfileActivity(own));
            main(()->WGramConfig.setProfileEmoji(0,0));
            main(()->{try{seedAwards(8226881959L);seedAwards(9000000001L);}catch(Exception e){throw new RuntimeException(e);}});
            open("profile with three badges",new ProfileActivity(own));
            open("other profile with three badges",new ProfileActivity(other));
            open("chat with three badges",new ChatActivity(other));
            main(()->MessagesController.getInstance(0).getUser(9000000001L).first_name="Very long WGram profile name for badge layout");
            open("dialog badges LTR RTL",dialogFixture());
            main(this::emojiChecks);
            open("emoji picker",new org.telegram.ui.wgram.WGramEmojiPickerActivity(0));
            open("settings", new SettingsActivity());
            open("WGram settings", new WGramSettingsActivity(0));
            open("glass settings", new WGramSettingsActivity(0,101));
            open("history settings", new WGramSettingsActivity(0,102));
            open("ghost settings", new WGramSettingsActivity(0,103));
            open("extra settings", new WGramSettingsActivity(0,104));
            open("plugins", new org.telegram.ui.wgram.WGramPluginsActivity(0));
            pluginChecks();
            open("admin", new WGramAdminActivity(0));
            for(int section=1;section<=5;section++)open("admin section "+section,new WGramAdminActivity(0,9000000001L,section));
            open("profile editor", new WGramProfileEditorActivity(0));
            open("bot connection", new WGramServiceActivity(0));
            historyChecks();
            result.putString("stream", "WGRAM_SMOKE_OK: " + checks + " screens and behavior checks passed\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", "WGRAM_SMOKE_FAILED\n" + Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
