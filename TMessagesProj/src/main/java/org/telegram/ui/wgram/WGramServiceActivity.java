package org.telegram.ui.wgram;

import static org.telegram.messenger.LocaleController.getString;
import android.view.View;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.wgram.WGramService;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import java.util.ArrayList;

/** Pair through the bot's private chat, without asking for Telegram login codes. */
public final class WGramServiceActivity extends UniversalFragment {
    private boolean busy;
    public WGramServiceActivity(int account){setCurrentAccount(account);}
    @Override protected CharSequence getTitle(){return getString(R.string.WGramService);}
    private void refresh(){if(listView!=null)listView.adapter.update(true);}
    @Override protected void fillItems(ArrayList<UItem> items,UniversalAdapter adapter){
        items.add(UItem.asShadow(getString(R.string.WGramServiceInfo)));
        items.add(UItem.asButton(1,getString(R.string.WGramServiceAddress),WGramService.address(currentAccount)));
        items.add(UItem.asShadow(getString(busy?R.string.Loading:WGramService.connected(currentAccount)?R.string.WGramServiceConnected:R.string.WGramServiceDisconnected)));
        if(WGramService.connected(currentAccount)){
            items.add(UItem.asButton(4,getString(R.string.WGramOpenBot),"@"+WGramService.bot(currentAccount)));
            items.add(UItem.asButton(5,getString(R.string.WGramServiceDisconnect)));
        }else{
            items.add(UItem.asButton(2,getString(R.string.WGramServiceConnect)).accent());
            if(WGramService.pending(currentAccount)){
                items.add(UItem.asButton(4,getString(R.string.WGramOpenBot),"@"+WGramService.bot(currentAccount)));
                items.add(UItem.asButton(3,getString(R.string.WGramServiceCheck)).accent());
                items.add(UItem.asShadow(getString(R.string.WGramPairInstructions)));
            }
        }
    }
    public static String error(String error){
        return getString("BADGE_LIMIT".equals(error)?R.string.WGramBadgeLimit:"CONNECT_BOT_FIRST".equals(error)?R.string.WGramServiceDisconnected:
            "PAIR_EXPIRED".equals(error)?R.string.WGramPairExpired:"TRY_LATER".equals(error)?R.string.WGramServiceTryLater:R.string.WGramServiceFailed);
    }
    private void openBot(){
        String link=WGramService.pending(currentAccount)?WGramService.pairLink(currentAccount):"https://t.me/"+WGramService.bot(currentAccount);
        if(!link.isEmpty()&&getParentActivity()!=null)Browser.openUrl(getParentActivity(),link);
    }
    private void check(){
        if(busy)return;busy=true;refresh();
        WGramService.check(currentAccount,(data,error)->{
            busy=false;refresh();if(error!=null)WGramUi.info(this,error(error));
            else WGramUi.info(this,getString(data.optBoolean("pending")?R.string.WGramPairInstructions:R.string.WGramServiceConnected));
        });
    }
    @Override protected void onClick(UItem item,View view,int p,float x,float y){
        if(busy)return;
        switch(item.id){
            case 1:WGramUi.input(this,getString(R.string.WGramServiceAddress),WGramService.address(currentAccount),false,200,text->{
                try{WGramService.setAddress(currentAccount,text);refresh();}
                catch(Exception e){WGramUi.info(this,getString(R.string.WGramServiceHttps));}
            });return;
            case 2:
                if(WGramService.address(currentAccount).isEmpty()){WGramUi.info(this,getString(R.string.WGramServiceHttps));return;}
                busy=true;refresh();WGramService.start(currentAccount,(data,error)->{
                    busy=false;refresh();if(error!=null)WGramUi.info(this,error(error));else openBot();
                });return;
            case 3:check();return;
            case 4:openBot();return;
            case 5:showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(getString(R.string.WGramServiceDisconnect))
                .setMessage(getString(R.string.WGramServiceDisconnectInfo)).setPositiveButton(getString(R.string.OK),(d,w)->{WGramService.disconnect(currentAccount);refresh();})
                .setNegativeButton(getString(R.string.Cancel),null).create());return;
        }
    }
    @Override public void onResume(){super.onResume();refresh();}
    @Override protected boolean onLongClick(UItem i,View v,int p,float x,float y){return false;}
}
