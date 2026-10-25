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
        runOnMainSync(() -> { try { action.run(); } catch (Throwable t) { failure = t; } });
        if (failure != null) throw failure;
    }
    private void open(String name, BaseFragment fragment) throws Throwable {
        main(() -> {
            fragment.setCurrentAccount(0);
            if (!activity.presentFragment(fragment, false, true)) throw new AssertionError("Cannot open " + name);
        });
        SystemClock.sleep(1200);
        waitForIdleSync();
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
            open("settings", new SettingsActivity());
            open("WGram settings", new WGramSettingsActivity(0));
            open("glass settings", new WGramSettingsActivity(0,101));
            open("history settings", new WGramSettingsActivity(0,102));
            open("ghost settings", new WGramSettingsActivity(0,103));
            open("extra settings", new WGramSettingsActivity(0,104));
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
