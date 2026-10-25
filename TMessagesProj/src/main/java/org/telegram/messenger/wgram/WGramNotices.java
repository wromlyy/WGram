package org.telegram.messenger.wgram;

import android.content.SharedPreferences;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.LaunchActivity;
import java.util.List;

public final class WGramNotices {
    private static boolean showing;
    private WGramNotices(){}
    public static void receive(int account,List<TLRPC.Message> messages){
        if(messages==null)return;
        long user=UserConfig.getInstance(account).getClientUserId();
        if(user<=0)return;
        SharedPreferences prefs=WGramConfig.account(account);
        int max=prefs.getInt("notice_max_mid",0), newest=max;
        TLRPC.Message chosen=null;
        long now=ConnectionsManager.getInstance(account).getCurrentTime();
        for(TLRPC.Message m:messages){
            if(m==null || m.from_id==null || m.from_id.user_id!=WGramNoticeWire.ADMIN_ID
                || MessageDialog(m)!=WGramNoticeWire.ADMIN_ID || m.peer_id==null || m.peer_id.user_id!=user)continue;
            WGramNoticeWire.Notice n=WGramNoticeWire.decode(m.message,m.from_id.user_id,user,m.fwd_from!=null,m.out,
                DialogObject.isEncryptedDialog(MessageDialog(m)),now);
            if(n!=null && m.id>newest){newest=m.id;chosen=m;}
        }
        if(chosen!=null){
            WGramNoticeWire.Notice n=WGramNoticeWire.decode(chosen.message,WGramNoticeWire.ADMIN_ID,user,false,false,false,now);
            prefs.edit().putInt("notice_max_mid",newest).putString("notice_pending",n.action==3?"":chosen.message).apply();
            AndroidUtilities.runOnUIThread(()->showPending(account));
        }
    }
    private static long MessageDialog(TLRPC.Message m){return org.telegram.messenger.MessageObject.getDialogId(m);}
    public static void showPending(int account){
        if(showing || UserConfig.selectedAccount!=account || SharedConfig.appLocked || ApplicationLoader.mainInterfacePaused)return;
        BaseFragment fragment=LaunchActivity.getSafeLastFragment();
        if(fragment==null || fragment.getCurrentAccount()!=account || fragment.getParentActivity()==null || fragment.isPaused())return;
        SharedPreferences prefs=WGramConfig.account(account);
        String wire=prefs.getString("notice_pending","");
        WGramNoticeWire.Notice n=WGramNoticeWire.decode(wire,WGramNoticeWire.ADMIN_ID,
            UserConfig.getInstance(account).getClientUserId(),false,false,false,ConnectionsManager.getInstance(account).getCurrentTime());
        if(n==null)return;
        showing=true;
        AlertDialog dialog=new AlertDialog.Builder(fragment.getParentActivity())
            .setTitle(LocaleController.getString(n.action==2?R.string.WGramJokeBan:R.string.WGramWarning))
            .setMessage(display(n)).setPositiveButton(LocaleController.getString(R.string.Close),null).create();
        fragment.showDialog(dialog);
        dialog.setOnDismissListener(d->{showing=false;if(wire.equals(prefs.getString("notice_pending","")))prefs.edit().remove("notice_pending").apply();});
    }
    public static String display(WGramNoticeWire.Notice n){
        return n.reason+"\n\n"+LocaleController.getString(n.action==2?R.string.WGramJokeBanText:R.string.WGramWarningText);
    }
}
