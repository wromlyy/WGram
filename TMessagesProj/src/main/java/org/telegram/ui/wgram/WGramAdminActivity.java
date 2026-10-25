package org.telegram.ui.wgram;

import static org.telegram.messenger.LocaleController.getString;
import android.os.Bundle;
import android.view.View;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.wgram.WGramConfig;
import org.telegram.messenger.wgram.WGramNoticeWire;
import org.telegram.messenger.wgram.WGramNotices;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;

/** Owner controls authenticated by the logged-in Telegram account. No remote app lock. */
public final class WGramAdminActivity extends UniversalFragment {
    private long target;
    private int action=2,days=7;
    private String reason;
    private boolean logView, busy;
    private long badgeEmoji;
    private String badgeTitle="",badgeDescription="";
    public WGramAdminActivity(int account){this(account,0);}
    public WGramAdminActivity(int account,long target){setCurrentAccount(account);this.target=target;}
    private boolean owner(){return WGramNoticeWire.isAdmin(getUserConfig().getClientUserId());}
    @Override protected CharSequence getTitle(){return getString(R.string.WGramAdmin);}
    private String reason(){return reason==null?getString(R.string.WGramReasonRules):reason;}
    private String actionName(){return getString(action==1?R.string.WGramWarning:action==2?R.string.WGramJokeBan:R.string.WGramRevoke);}
    @Override protected void fillItems(ArrayList<UItem> items,UniversalAdapter adapter){
        items.add(UItem.asHeader(getString(R.string.WGramAdminAccess)));
        items.add(UItem.asButton(1,getString(R.string.WGramMyId),""+getUserConfig().getClientUserId()));
        items.add(UItem.asButton(2,getString(R.string.WGramOwnerId),""+WGramNoticeWire.ADMIN_ID));
        items.add(UItem.asShadow(getString(owner()?R.string.WGramAdminOwner:R.string.WGramAdminDenied)));
        if(!owner())return;
        if(logView){
            items.add(UItem.asButton(3,getString(R.string.WGramAdminCompose)));
            try{
                JSONArray logs=new JSONArray(WGramConfig.account(currentAccount).getString("admin_log","[]"));
                if(logs.length()==0)items.add(UItem.asShadow(getString(R.string.WGramAdminLogEmpty)));
                for(int i=logs.length()-1;i>=0;i--){
                    JSONObject e=logs.getJSONObject(i);
                    items.add(UItem.asShadow("ID "+e.optLong("target")+" · "+e.optString("action")+"\n"
                        +DateFormat.getDateTimeInstance().format(new Date(e.optLong("time")))+"\n"+e.optString("reason")));
                }
            }catch(Exception ignored){}
            return;
        }
        items.add(UItem.asHeader(getString(R.string.WGramAdminRecipient)));
        TLRPC.User user=getMessagesController().getUser(target);
        items.add(UItem.asButton(10,getString(R.string.WGramTarget),target==0?getString(R.string.WGramNotChosen):""+target));
        if(user!=null)items.add(UItem.asButton(11,getString(R.string.WGramOpenChat),UserObject.getUserName(user)));
        items.add(UItem.asShadow(getString(R.string.WGramAdminRecipientInfo)));
        items.add(UItem.asHeader(getString(R.string.WGramAdminNotice)));
        items.add(UItem.asButton(12,getString(R.string.WGramNoticeType),actionName()));
        items.add(UItem.asButton(13,getString(R.string.WGramReason),reason()));
        items.add(UItem.asButton(14,getString(R.string.WGramReasonPresets)));
        items.add(UItem.asButton(15,getString(R.string.WGramNoticeDuration),days+" "+getString(R.string.WGramDays)));
        items.add(UItem.asButton(16,getString(R.string.WGramNoticePreview)));
        items.add(UItem.asButton(17,getString(R.string.WGramNoticeSend)).accent());
        items.add(UItem.asButton(18,getString(R.string.WGramRevoke)));
        items.add(UItem.asShadow(getString(R.string.WGramAdminNoticeInfo)));
        items.add(UItem.asHeader(getString(R.string.WGramBadgeEditor)));
        items.add(UItem.asButton(30,getString(R.string.WGramBadgeTitle),badgeTitle));
        items.add(UItem.asButton(31,getString(R.string.WGramBadgeDescription),badgeDescription));
        items.add(UItem.asButton(32,getString(R.string.WGramBadgeEmoji),getString(badgeEmoji==0?R.string.WGramNotChosen:R.string.WGramProfileEmojiChosen)));
        items.add(UItem.asButton(33,getString(R.string.WGramBadgeGrant)).accent());
        items.add(UItem.asButton(34,getString(R.string.WGramBadgeRevoke)));
        items.add(UItem.asButton(35,getString(R.string.WGramBadgeSaveTemplate)));
        items.add(UItem.asButton(36,getString(R.string.WGramBadgeTemplates)));
        items.add(UItem.asShadow(getString(R.string.WGramBadgeInfo)));
        items.add(UItem.asHeader(getString(R.string.WGramCatalog)));
        items.add(UItem.asButton(40,getString(R.string.WGramCreateCatalog),busy?getString(R.string.Loading):""));
        items.add(UItem.asButton(41,getString(R.string.WGramOpenCatalog)));
        items.add(UItem.asShadow(getString(R.string.WGramCatalogInfo)));
        items.add(UItem.asHeader(getString(R.string.WGramAdminTools)));
        items.add(UItem.asButton(20,getString(R.string.WGramAdminLog)));
        items.add(UItem.asButton(21,getString(R.string.WGramAdminClearLog)));
        items.add(UItem.asButton(22,getString(R.string.WGramCopyTarget)));
        items.add(UItem.asButton(23,getString(R.string.WGramAdminReset)));
        items.add(UItem.asButton(24,getString(R.string.WGramDiagnostics)));
        items.add(UItem.asShadow(getString(R.string.WGramAdminLimits)));
    }
    private void refresh(){if(listView!=null)listView.adapter.update(true);}
    private void preview(){
        WGramNoticeWire.Notice n=new WGramNoticeWire.Notice();n.action=action;n.reason=reason();
        showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(actionName()).setMessage(WGramNotices.display(n))
            .setPositiveButton(getString(R.string.Close),null).create());
    }
    private void send(int type){
        if(!owner())return;
        TLRPC.User user=getMessagesController().getUser(target);
        if(target<=0 || user==null || user.deleted || user.bot || target==getUserConfig().getClientUserId()){
            WGramUi.info(this,getString(R.string.WGramTargetMissing));return;
        }
        final long recipient=target;
        final String text=WGramNoticeWire.encode(recipient,type,getConnectionsManager().getCurrentTime(),days,reason());
        final String why=reason();
        final String label=getString(type==1?R.string.WGramWarning:type==2?R.string.WGramJokeBan:R.string.WGramRevoke);
        showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(getString(R.string.WGramNoticeSend))
            .setMessage(UserObject.getUserName(user)+" · ID "+recipient+"\n\n"+label+"\n"+why)
            .setPositiveButton(getString(R.string.Send),(d,w)->{
                if(!owner())return;
                getSendMessagesHelper().sendMessage(SendMessagesHelper.SendMessageParams.of(text,recipient));
                try{
                    JSONArray old=new JSONArray(WGramConfig.account(currentAccount).getString("admin_log","[]"));
                    JSONArray logs=new JSONArray();
                    for(int i=Math.max(0,old.length()-99);i<old.length();i++)logs.put(old.get(i));
                    JSONObject event=new JSONObject();event.put("target",recipient);event.put("action",label);event.put("reason",why);event.put("time",System.currentTimeMillis());
                    logs.put(event);WGramConfig.account(currentAccount).edit().putString("admin_log",logs.toString()).apply();
                }catch(Exception ignored){}
                WGramUi.info(this,getString(R.string.WGramNoticeQueued));
            }).setNegativeButton(getString(R.string.Cancel),null).create());
    }
    @Override protected void onClick(UItem item,View view,int position,float x,float y){
        if(item.id==1){AndroidUtilities.addToClipboard(""+getUserConfig().getClientUserId());return;}
        if(item.id==2){AndroidUtilities.addToClipboard(""+WGramNoticeWire.ADMIN_ID);return;}
        if(!owner())return;
        switch(item.id){
            case 3:logView=false;break;
            case 10:WGramUi.input(this,getString(R.string.WGramTarget),target==0?"":""+target,true,19,t->{try{target=Long.parseLong(t);if(target<1)target=0;}catch(Exception e){target=0;}refresh();});return;
            case 11:Bundle args=new Bundle();args.putLong("user_id",target);ChatActivity chat=new ChatActivity(args);chat.setCurrentAccount(currentAccount);presentFragment(chat);return;
            case 12:showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(getString(R.string.WGramNoticeType))
                .setItems(new CharSequence[]{getString(R.string.WGramWarning),getString(R.string.WGramJokeBan),getString(R.string.WGramRevoke)},(d,w)->{action=w+1;refresh();}).create());return;
            case 13:WGramUi.input(this,getString(R.string.WGramReason),reason(),false,300,t->{reason=t;refresh();});return;
            case 14:final int[] presets={R.string.WGramReasonRules,R.string.WGramReasonSpam,R.string.WGramReasonAbuse,R.string.WGramReasonJoke};
                CharSequence[] names=new CharSequence[presets.length];for(int i=0;i<names.length;i++)names[i]=getString(presets[i]);
                showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(getString(R.string.WGramReasonPresets)).setItems(names,(d,w)->{reason=getString(presets[w]);refresh();}).create());return;
            case 15:showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(getString(R.string.WGramNoticeDuration))
                .setItems(new CharSequence[]{"1 "+getString(R.string.WGramDays),"7 "+getString(R.string.WGramDays),"30 "+getString(R.string.WGramDays)},(d,w)->{days=w==0?1:w==1?7:30;refresh();}).create());return;
            case 16:preview();return;
            case 17:send(action);return;
            case 18:send(3);return;
            case 20:logView=true;break;
            case 21:showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(getString(R.string.WGramAdminClearLog))
                .setPositiveButton(getString(R.string.Delete),(d,w)->{WGramConfig.account(currentAccount).edit().remove("admin_log").apply();refresh();})
                .setNegativeButton(getString(R.string.Cancel),null).create());return;
            case 22:if(target>0)AndroidUtilities.addToClipboard(""+target);return;
            case 23:target=0;action=2;days=7;reason=null;break;
            case 30:WGramUi.input(this,getString(R.string.WGramBadgeTitle),badgeTitle,false,40,t->{badgeTitle=t;refresh();});return;
            case 31:WGramUi.input(this,getString(R.string.WGramBadgeDescription),badgeDescription,false,300,t->{badgeDescription=t;refresh();});return;
            case 32:presentFragment(new WGramEmojiPickerActivity(currentAccount,id->{badgeEmoji=id;refresh();}));return;
            case 33:badge(true);return;
            case 34:badge(false);return;
            case 35:saveTemplate();return;
            case 36:templates();return;
            case 40:
                if(busy)return;
                showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(getString(R.string.WGramCreateCatalog))
                    .setMessage(getString(R.string.WGramCatalogInfo)).setPositiveButton(getString(R.string.Create),(d,w)->{
                        busy=true;refresh();org.telegram.messenger.wgram.WGramSharedProfiles.get(currentAccount).create((id,error)->{
                            busy=false;refresh();WGramUi.info(this,error==null?getString(R.string.WGramCatalogReady):getString(R.string.WGramCatalogNotReady)+"\n"+error);
                        });
                    }).setNegativeButton(getString(R.string.Cancel),null).create());return;
            case 41:org.telegram.messenger.browser.Browser.openUrl(getParentActivity(),"https://t.me/"+org.telegram.messenger.wgram.WGramSharedProfiles.CATALOG);return;
            case 24:WGramUi.info(this,"WGram 0.2\nAndroid API "+android.os.Build.VERSION.SDK_INT+"\nID "+getUserConfig().getClientUserId()
                +"\nLiquid Glass: "+WGramConfig.isGlassEnabled()+"\n"+getString(R.string.WGramHistory)+": "+WGramConfig.isHistoryEnabled(currentAccount));return;
        }
        refresh();
    }
    private void badge(boolean active){
        if(!owner()||busy)return;
        if(target<=0 || (active&&(badgeTitle.trim().isEmpty()||badgeEmoji<=0))){WGramUi.info(this,getString(R.string.WGramBadgeMissing));return;}
        final long recipient=target;
        final String payload=org.telegram.messenger.wgram.WGramSharedProfiles.badgeRecord(recipient,active,badgeEmoji,badgeTitle,badgeDescription);
        showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(getString(active?R.string.WGramBadgeGrant:R.string.WGramBadgeRevoke))
            .setMessage("ID "+recipient+"\n\n"+badgeTitle+"\n"+badgeDescription+"\n\n"+getString(R.string.WGramBadgeInfo))
            .setPositiveButton(getString(R.string.WGramPublish),(d,w)->{
                if(!owner())return;busy=true;refresh();
                org.telegram.messenger.wgram.WGramSharedProfiles.get(currentAccount).join((id,error)->{
                    busy=false;refresh();if(!owner())return;
                    if(id==0){WGramUi.info(this,getString(R.string.WGramCatalogNotReady)+"\n"+error);return;}
                    getSendMessagesHelper().sendMessage(SendMessagesHelper.SendMessageParams.of(payload,-id));
                    org.telegram.messenger.wgram.WGramSharedProfiles.get(currentAccount).invalidate(recipient);
                    WGramUi.info(this,getString(R.string.WGramNoticeQueued));
                });
            }).setNegativeButton(getString(R.string.Cancel),null).create());
    }
    private void saveTemplate(){
        if(badgeTitle.trim().isEmpty()||badgeEmoji<=0){WGramUi.info(this,getString(R.string.WGramBadgeMissing));return;}
        try{
            JSONArray old=new JSONArray(WGramConfig.account(currentAccount).getString("badge_templates","[]")),list=new JSONArray();
            for(int i=Math.max(0,old.length()-29);i<old.length();i++)list.put(old.get(i));
            list.put(new JSONObject().put("title",badgeTitle).put("description",badgeDescription).put("emoji",badgeEmoji));
            WGramConfig.account(currentAccount).edit().putString("badge_templates",list.toString()).apply();
            WGramUi.info(this,getString(R.string.WGramBadgeTemplateSaved));
        }catch(Exception ignored){}
    }
    private void templates(){
        try{
            JSONArray list=new JSONArray(WGramConfig.account(currentAccount).getString("badge_templates","[]"));
            CharSequence[] names=new CharSequence[list.length()];for(int i=0;i<names.length;i++)names[i]=list.getJSONObject(i).optString("title");
            if(names.length==0){WGramUi.info(this,getString(R.string.WGramAdminLogEmpty));return;}
            showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(getString(R.string.WGramBadgeTemplates)).setItems(names,(d,w)->{
                JSONObject item=list.optJSONObject(w);if(item!=null){badgeTitle=item.optString("title");badgeDescription=item.optString("description");badgeEmoji=item.optLong("emoji");refresh();}
            }).create());
        }catch(Exception ignored){}
    }
    @Override public void onResume(){super.onResume();refresh();}
    @Override protected boolean onLongClick(UItem i,View v,int p,float x,float y){return false;}
}
