package org.telegram.messenger.wgram;

import android.content.SharedPreferences;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.LaunchActivity;
import java.util.List;

public final class WGramNotices {
    private static AlertDialog activeDialog;
    private static int showingAccount=-1;
    private WGramNotices(){}
    public static void receive(int account,List<TLRPC.Message> messages){
        if(messages==null)return;
        long user=UserConfig.getInstance(account).getClientUserId();
        if(user<=0 || WGramNoticeWire.isAdmin(user))return;
        SharedPreferences prefs=WGramConfig.account(account);
        int newest=prefs.getInt("notice_max_mid",0);
        TLRPC.Message chosen=null;
        long now=ConnectionsManager.getInstance(account).getCurrentTime();
        for(TLRPC.Message m:messages){
            if(m==null || m.from_id==null || m.peer_id==null || m.ttl_period!=0
                || (m.media!=null && m.media.ttl_seconds!=0) || !WGramNoticeWire.isDirectDelivery(
                m.from_id.user_id,org.telegram.messenger.MessageObject.getDialogId(m),m.peer_id.user_id,m.peer_id.chat_id,m.peer_id.channel_id,user))continue;
            WGramNoticeWire.Notice n=WGramNoticeWire.decode(m.message,m.from_id.user_id,user,m.fwd_from!=null,m.out,
                DialogObject.isEncryptedDialog(org.telegram.messenger.MessageObject.getDialogId(m)),now);
            if(n!=null && m.id>newest){newest=m.id;chosen=m;}
        }
        if(chosen==null)return;
        WGramNoticeWire.Notice n=WGramNoticeWire.decode(chosen.message,WGramNoticeWire.ADMIN_ID,user,false,false,false,now);
        SharedPreferences.Editor edit=prefs.edit().putInt("notice_max_mid",newest);
        if(n.action==4)edit.putString("notice_lock",chosen.message).remove("notice_pending");
        else if(n.action==3)edit.remove("notice_lock").remove("notice_pending");
        else edit.putString("notice_pending",chosen.message);
        edit.apply();
        AndroidUtilities.runOnUIThread(()->{
            if(showingAccount==account && activeDialog!=null){activeDialog.dismiss();activeDialog=null;showingAccount=-1;}
            showPending(account);
        });
    }
    public static void showPending(int account){
        if(UserConfig.selectedAccount!=account || SharedConfig.appLocked || ApplicationLoader.mainInterfacePaused)return;
        if(activeDialog!=null && activeDialog.isShowing() && showingAccount==account)return;
        BaseFragment fragment=LaunchActivity.getSafeLastFragment();
        if(fragment==null || fragment.getCurrentAccount()!=account || fragment.getParentActivity()==null || fragment.isPaused())return;
        SharedPreferences prefs=WGramConfig.account(account);
        String lock=prefs.getString("notice_lock","");
        final boolean restricted=!lock.isEmpty();
        String wire=restricted?lock:prefs.getString("notice_pending","");
        WGramNoticeWire.Notice n=WGramNoticeWire.decode(wire,WGramNoticeWire.ADMIN_ID,
            UserConfig.getInstance(account).getClientUserId(),false,false,false,ConnectionsManager.getInstance(account).getCurrentTime());
        if(n==null || WGramNoticeWire.isAdmin(UserConfig.getInstance(account).getClientUserId())){
            if(!wire.isEmpty())prefs.edit().remove(restricted?"notice_lock":"notice_pending").apply();return;
        }
        if(activeDialog!=null)activeDialog.dismiss();
        AlertDialog.Builder builder=new AlertDialog.Builder(fragment.getParentActivity())
            .setTitle(LocaleController.getString(restricted?R.string.WGramBlocked:n.action==2?R.string.WGramJokeBan:R.string.WGramWarning))
            .setMessage(display(n));
        if(restricted){
            builder.setPositiveButton(LocaleController.getString(R.string.WGramDownloadTelegram),null)
                .setNeutralButton(LocaleController.getString(R.string.WGramAppeal),null);
        }else builder.setPositiveButton(LocaleController.getString(R.string.Close),null);
        final AlertDialog dialog=builder.create();
        activeDialog=dialog;showingAccount=account;
        if(restricted)dialog.show();else fragment.showDialog(dialog);
        if(restricted){
            dialog.setCancelable(false);dialog.setCanceledOnTouchOutside(false);
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener(v->
                org.telegram.messenger.browser.Browser.openUrl(fragment.getParentActivity(),"https://telegram.org/android"));
            dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL).setOnClickListener(v->{
                org.telegram.ui.wgram.WGramAppeal.show(fragment,account);
            });
        }
        dialog.setOnDismissListener(d->{
            if(activeDialog==dialog){activeDialog=null;showingAccount=-1;}
            if(!restricted && wire.equals(prefs.getString("notice_pending","")))prefs.edit().remove("notice_pending").apply();
            if(restricted && !prefs.getString("notice_lock","").isEmpty())AndroidUtilities.runOnUIThread(()->showPending(account),300);
        });
    }
    public static String display(WGramNoticeWire.Notice n){
        return n.reason+"\n\n"+LocaleController.getString(n.action==4?R.string.WGramBlockedInfo:n.action==2?R.string.WGramJokeBanText:R.string.WGramWarningText);
    }
}
