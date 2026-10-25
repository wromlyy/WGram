package org.telegram.wgram.tests;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.SettingsActivity;
import org.telegram.ui.wgram.WGramAdminActivity;
import org.telegram.ui.wgram.WGramProfileEditorActivity;
import org.telegram.ui.wgram.WGramSettingsActivity;

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
        checks++;
        Bundle status = new Bundle(); status.putString("stream", "PASS: " + name + "\n"); sendStatus(0, status);
        main(() -> fragment.finishFragment(false));
        SystemClock.sleep(250);
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
            open("settings", new SettingsActivity());
            open("WGram settings", new WGramSettingsActivity(0));
            open("admin", new WGramAdminActivity(0));
            open("profile editor", new WGramProfileEditorActivity(0));
            result.putString("stream", "WGRAM_SMOKE_OK: " + checks + " screens opened\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", "WGRAM_SMOKE_FAILED\n" + Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
