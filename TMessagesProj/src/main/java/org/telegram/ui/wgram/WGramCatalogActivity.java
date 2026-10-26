package org.telegram.ui.wgram;

import static org.telegram.messenger.LocaleController.getString;
import android.view.View;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.wgram.WGramService;
import org.telegram.messenger.wgram.WGramSharedProfiles;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import java.util.ArrayList;

public final class WGramCatalogActivity extends UniversalFragment {
    private final ArrayList<JSONObject> entries = new ArrayList<>();
    private boolean mine, busy;
    private long next;
    private int generation;
    private String error;
    public WGramCatalogActivity(int account) { setCurrentAccount(account); }
    @Override protected CharSequence getTitle() { return getString(R.string.WGramCustoms); }
    private void refresh() { if (listView != null) listView.adapter.update(true); }
    @Override public void onResume() { super.onResume(); load(false); }
    private void load(boolean more) {
        if (!WGramService.connected(currentAccount)) { entries.clear(); busy=false; refresh(); return; }
        final int version = ++generation;
        if (!more) { entries.clear(); next=0; }
        busy=true;error=null;refresh();
        String path="/v1/customs?mine="+(mine?"1":"0")+(more&&next>0?"&before="+next:"");
        WGramService.request(currentAccount,path,null,true,(json,failure)->{
            if(version!=generation)return;
            busy=false;error=failure;
            if(failure==null){
                JSONArray items=json.optJSONArray("items");
                if(items!=null)for(int i=0;i<items.length();i++){
                    JSONObject item=items.optJSONObject(i);
                    if(item!=null&&item.optLong("id")>0)entries.add(item);
                }
                next=json.optLong("next");
            }
            refresh();
        });
    }
    @Override protected void fillItems(ArrayList<UItem> items,UniversalAdapter adapter){
        items.add(UItem.asShadow(getString(R.string.WGramCustomsInfo)));
        if(!WGramService.connected(currentAccount)){
            items.add(UItem.asButton(5,getString(R.string.WGramService)));
            items.add(UItem.asShadow(getString(R.string.WGramCustomConnect)));
            return;
        }
        items.add(UItem.asCheck(1,getString(R.string.WGramCustomMine)).setChecked(mine));
        items.add(UItem.asButton(2,R.drawable.msg_add,getString(R.string.WGramCustomSubmitProfile)));
        items.add(UItem.asButton(3,R.drawable.msg_add,getString(R.string.WGramCustomSendBot)));
        items.add(UItem.asShadow(getString(R.string.WGramCustomBotInfo)));
        items.add(UItem.asHeader(getString(mine?R.string.WGramCustomMine:R.string.WGramCustomPublished)));
        if(busy)items.add(UItem.asShadow(getString(R.string.Loading)));
        if(error!=null)items.add(UItem.asButton(6,getString(R.string.WGramCustomRetry)));
        if(!busy&&error==null&&entries.isEmpty())items.add(UItem.asShadow(getString(R.string.WGramCustomEmpty)));
        for(int i=0;i<entries.size();i++){
            JSONObject entry=entries.get(i);
            items.add(UItem.asButton(100+i,entry.optString("title"),kind(entry.optString("kind"))));
            if(mine)items.add(UItem.asShadow(status(entry.optString("status"))));
        }
        if(next>0&&!busy)items.add(UItem.asButton(4,getString(R.string.WGramCustomMore)));
    }
    static String kind(String value){
        switch(value){
            case "banner":return getString(R.string.WGramBanner);
            case "emoji":return getString(R.string.WGramProfileEmoji);
            case "emoji_pack":return getString(R.string.WGramCustomEmojiPack);
            case "sticker_pack":return getString(R.string.WGramCustomStickerPack);
            default:return getString(R.string.WGramProfileHeader);
        }
    }
    static String status(String value){
        return getString("published".equals(value)?R.string.WGramCustomApproved:"rejected".equals(value)?R.string.WGramCustomRejected:R.string.WGramCustomPending);
    }
    @Override protected void onClick(UItem item,View view,int position,float x,float y){
        if(busy)return;
        if(item.id>=100&&item.id-100<entries.size()){
            presentFragment(new WGramCustomActivity(currentAccount,entries.get(item.id-100).optLong("id")));return;
        }
        switch(item.id){
            case 1:mine=!mine;load(false);return;
            case 2:
                WGramUi.input(this,getString(R.string.WGramCustomTitle),"",false,60,title->{
                    if(title.isEmpty())return;
                    showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(getString(R.string.WGramCustomSubmitProfile))
                        .setMessage(getString(R.string.WGramCustomSubmitInfo)).setPositiveButton(getString(R.string.WGramPublish),(d,w)->submit(title))
                        .setNegativeButton(getString(R.string.Cancel),null).create());
                });return;
            case 3:
                String bot=WGramService.bot(currentAccount);
                if(bot.matches("[A-Za-z0-9_]{5,32}"))Browser.openUrl(getParentActivity(),"https://t.me/"+bot+"?start=customs");
                else presentFragment(new WGramServiceActivity(currentAccount));
                return;
            case 4:load(true);return;
            case 5:presentFragment(new WGramServiceActivity(currentAccount));return;
            case 6:load(false);return;
        }
    }
    private void submit(String title){
        busy=true;refresh();
        WGramSharedProfiles.get(currentAccount).publish((saved,failure)->{
            if(failure!=null){busy=false;refresh();WGramUi.info(this,WGramServiceActivity.error(failure));return;}
            try{
                WGramService.request(currentAccount,"/v1/customs",new JSONObject().put("title",title).put("kind","profile"),true,(result,error)->{
                    busy=false;
                    if(error!=null){refresh();WGramUi.info(this,getString(R.string.WGramCustomSubmitFailed)+"\n"+WGramServiceActivity.error(error));return;}
                    mine=true;load(false);WGramUi.info(this,getString(R.string.WGramCustomSubmitted));
                });
            }catch(Exception error){busy=false;refresh();WGramUi.info(this,getString(R.string.WGramCustomSubmitFailed));}
        });
    }
    @Override protected boolean onLongClick(UItem item,View view,int position,float x,float y){return false;}
}
