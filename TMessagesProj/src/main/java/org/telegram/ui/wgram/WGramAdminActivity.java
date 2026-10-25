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

/** Owner controls authenticated by the logged-in Telegram account. WGram restrictions do not modify a Telegram account. */
public final class WGramAdminActivity extends UniversalFragment {
    private long target;
    private int section;
    private int action=4,days=7;
    private String reason;
    private boolean logView, busy;
    private long badgeEmoji;
    private String badgeTitle="",badgeDescription="";
    public WGramAdminActivity(int account){this(account,0);}
    public WGramAdminActivity(int account,long target){this(account,target,0);}
    public WGramAdminActivity(int account,long target,int section){setCurrentAccount(account);this.target=target;this.section=section;this.logView=section==5;}
    private boolean owner(){return WGramNoticeWire.isAdmin(getUserConfig().getClientUserId());}
    @Override protected CharSequence getTitle(){return getString(R.string.WGramAdmin);}
    private String reason(){return reason==null?getString(R.string.WGramReasonRules):reason;}
    private String actionName(){return getString(action==4?R.string.WGramBlocked:action==1?R.string.WGramWarning:action==2?R.string.WGramJokeBan:R.string.WGramRevoke);}
    @Override protected void fillItems(ArrayList<UItem> items,UniversalAdapter adapter){
        if(!owner()){
            items.add(UItem.asShadow(getString(R.string.WGramAdminDenied)));return;
        }
        {
            items.add(UItem.asHeader(getString(R.string.WGramAdmin)));
            items.add(UItem.asShadow(getString(R.string.WGramAdminHomeInfo)));
            items.add(UItem.asButton(101,R.drawable.msg_secret,getString(R.string.WGramAdminNotice)));
            items.add(UItem.asShadow(getString(R.string.WGramAdminNoticeHelp)));
            items.add(UItem.asButton(102,R.drawable.wgram_logo,getString(R.string.WGramBadgeEditor)));
            items.add(UItem.asShadow(getString(R.string.WGramAdminBadgeHelp)));
            items.add(UItem.asButton(103,R.drawable.msg_settings,getString(R.string.WGramService)));
            items.add(UItem.asButton(105,R.drawable.msg_recent,getString(R.string.WGramAdminLog)));
            items.add(UItem.asButton(104,R.drawable.msg_settings,getString(R.string.WGramAdminTools)));
            items.add(UItem.asShadow(getString(R.string.WGramAdminOwner)));
        }
        if(logView){
            items.add(UItem.asShadow(getString(R.string.WGramAdminLogInfo)));
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
        if(section==1 || section==2){
        items.add(UItem.asHeader(getString(R.string.WGramAdminRecipient)));
        TLRPC.User user=getMessagesController().getUser(target);
        items.add(UItem.asButton(10,getString(R.string.WGramTarget),target==0?getString(R.string.WGramNotChosen):""+target));
        if(user!=null)items.add(UItem.asButton(11,getString(R.string.WGramOpenChat),UserObject.getUserName(user)));
        items.add(UItem.asShadow(getString(R.string.WGramAdminRecipientInfo)));
        items.add(UItem.asButton(9,getString(R.string.WGramChooseContact)));
        }
        if(section==1){
        items.add(UItem.asHeader(getString(R.string.WGramAdminNotice)));
        items.add(UItem.asButton(12,getString(R.string.WGramNoticeType),actionName()));
        items.add(UItem.asButton(13,getString(R.string.WGramBanText),reason()));
        items.add(UItem.asButton(14,getString(R.string.WGramReasonPresets)));
        if(action!=4)items.add(UItem.asButton(15,getString(R.string.WGramNoticeDuration),days+" "+getString(R.string.WGramDays)));
        else items.add(UItem.asShadow(getString(R.string.WGramUntilRevoked)));
        items.add(UItem.asButton(16,getString(R.string.WGramNoticePreview)));
        items.add(UItem.asButton(17,getString(R.string.WGramNoticeSend)).accent());
        items.add(UItem.asButton(18,getString(R.string.WGramRevoke)));
        items.add(UItem.asShadow(getString(R.string.WGramAdminNoticeInfo)));
        }
        if(section==2){
        items.add(UItem.asHeader(getString(R.string.WGramBadgeEditor)));
        items.add(UItem.asButton(30,getString(R.string.WGramBadgeTitle),badgeTitle));
        items.add(UItem.asButton(31,getString(R.string.WGramBadgeDescription),badgeDescription));
        items.add(UItem.asButton(32,getString(R.string.WGramBadgeEmoji),getString(badgeEmoji==0?R.string.WGramNotChosen:R.string.WGramProfileEmojiChosen)));
        items.add(UItem.asButton(33,getString(R.string.WGramBadgeGrant)).accent());
        items.add(UItem.asButton(34,getString(R.string.WGramBadgeRevoke)));
        items.add(UItem.asButton(35,getString(R.string.WGramBadgeSaveTemplate)));
        items.add(UItem.asButton(36,getString(R.string.WGramBadgeTemplates)));
        items.add(UItem.asShadow(getString(R.string.WGramBadgeInfo)));
        }
        if(section==3){
        items.add(UItem.asHeader(getString(R.string.WGramService)));
        items.add(UItem.asButton(40,getString(R.string.WGramServiceConnect),busy?getString(R.string.Loading):""));
        items.add(UItem.asButton(41,getString(R.string.WGramOpenBot)));
        items.add(UItem.asShadow(getString(R.string.WGramServiceInfo)));
        }
        if(section==4){
        items.add(UItem.asButton(1,getString(R.string.WGramMyId),""+getUserConfig().getClientUserId()));
        items.add(UItem.asButton(2,getString(R.string.WGramOwnerId),""+WGramNoticeWire.ADMIN_ID));
        items.add(UItem.asHeader(getString(R.string.WGramAdminTools)));
        items.add(UItem.asButton(20,getString(R.string.WGramAdminLog)));
        items.add(UItem.asButton(21,getString(R.string.WGramAdminClearLog)));
        items.add(UItem.asButton(22,getString(R.string.WGramCopyTarget)));
        items.add(UItem.asButton(23,getString(R.string.WGramAdminReset)));
        items.add(UItem.asButton(24,getString(R.string.WGramDiagnostics)));
        items.add(UItem.asShadow(getString(R.string.WGramAdminLimits)));
        }
    }
    private void refresh(){if(listView!=null && listView.adapter!=null)listView.adapter.update(true);}
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
        final String label=getString(type==4?R.string.WGramBlocked:type==1?R.string.WGramWarning:type==2?R.string.WGramJokeBan:R.string.WGramRevoke);
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
            case 101:case 102:case 103:case 104:case 105:
                section=section==item.id-100?0:item.id-100;logView=section==5;refresh();return;
            case 9:
                Bundle pickerArgs=new Bundle();pickerArgs.putBoolean("onlyUsers",true);pickerArgs.putBoolean("returnAsResult",true);pickerArgs.putBoolean("destroyAfterSelect",true);
                org.telegram.ui.ContactsActivity picker=new org.telegram.ui.ContactsActivity(pickerArgs);picker.setCurrentAccount(currentAccount);
                picker.setDelegate((user,param,activity)->{target=user.id;refresh();});presentFragment(picker);return;
            case 3:logView=false;break;
            case 10:WGramUi.input(this,getString(R.string.WGramTarget),target==0?"":""+target,true,19,t->{try{target=Long.parseLong(t);if(target<1)target=0;}catch(Exception e){target=0;}refresh();});return;
            case 11:Bundle args=new Bundle();args.putLong("user_id",target);ChatActivity chat=new ChatActivity(args);chat.setCurrentAccount(currentAccount);presentFragment(chat);return;
            case 12:showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(getString(R.string.WGramNoticeType))
                .setItems(new CharSequence[]{getString(R.string.WGramBlocked),getString(R.string.WGramWarning),getString(R.string.WGramJokeBan)},(d,w)->{action=w==0?4:w==1?1:2;refresh();}).create());return;
            case 13:WGramUi.input(this,getString(R.string.WGramBanText),reason(),false,1000,t->{reason=t;refresh();});return;
            case 14:final int[] presets={R.string.WGramReasonRules,R.string.WGramReasonSpam,R.string.WGramReasonAbuse,R.string.WGramReasonJoke};
                CharSequence[] names=new CharSequence[presets.length];for(int i=0;i<names.length;i++)names[i]=getString(presets[i]);
                showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(getString(R.string.WGramReasonPresets)).setItems(names,(d,w)->{reason=getString(presets[w]);refresh();}).create());return;
            case 15:showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(getString(R.string.WGramNoticeDuration))
                .setItems(new CharSequence[]{"1 "+getString(R.string.WGramDays),"7 "+getString(R.string.WGramDays),"30 "+getString(R.string.WGramDays)},(d,w)->{days=w==0?1:w==1?7:30;refresh();}).create());return;
            case 16:preview();return;
            case 17:send(action);return;
            case 18:send(3);return;
            case 20:section=5;logView=true;refresh();return;
            case 21:showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(getString(R.string.WGramAdminClearLog))
                .setPositiveButton(getString(R.string.Delete),(d,w)->{WGramConfig.account(currentAccount).edit().remove("admin_log").apply();refresh();})
                .setNegativeButton(getString(R.string.Cancel),null).create());return;
            case 22:if(target>0)AndroidUtilities.addToClipboard(""+target);return;
            case 23:target=0;action=4;days=7;reason=null;break;
            case 30:WGramUi.input(this,getString(R.string.WGramBadgeTitle),badgeTitle,false,40,t->{badgeTitle=t;refresh();});return;
            case 31:WGramUi.input(this,getString(R.string.WGramBadgeDescription),badgeDescription,false,300,t->{badgeDescription=t;refresh();});return;
            case 32:presentFragment(new WGramEmojiPickerActivity(currentAccount,id->{badgeEmoji=id;refresh();}));return;
            case 33:badge(true);return;
            case 34:badge(false);return;
            case 35:saveTemplate();return;
            case 36:templates();return;
            case 40:presentFragment(new WGramServiceActivity(currentAccount));return;
            case 41:
                String bot=org.telegram.messenger.wgram.WGramService.bot(currentAccount);
                if(bot.isEmpty())presentFragment(new WGramServiceActivity(currentAccount));
                else org.telegram.messenger.browser.Browser.openUrl(getParentActivity(),"https://t.me/"+bot);return;
            case 24:WGramUi.info(this,"WGram 0.3\nAndroid API "+android.os.Build.VERSION.SDK_INT+"\nID "+getUserConfig().getClientUserId()
                +"\nLiquid Glass: "+WGramConfig.isGlassEnabled()+"\n"+getString(R.string.WGramHistory)+": "+WGramConfig.isHistoryEnabled(currentAccount));return;
        }
        refresh();
    }
    private void badge(boolean active){
        if(!owner()||busy)return;
        if(target<=0 || (active&&(badgeTitle.trim().isEmpty()||badgeEmoji<=0))){WGramUi.info(this,getString(R.string.WGramBadgeMissing));return;}
        final long recipient=target;

        showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(getString(active?R.string.WGramBadgeGrant:R.string.WGramBadgeRevoke))
            .setMessage("ID "+recipient+"\n\n"+badgeTitle+"\n"+badgeDescription+"\n\n"+getString(R.string.WGramBadgeInfo))
            .setPositiveButton(getString(R.string.WGramPublish),(d,w)->{
                if(!owner())return;busy=true;refresh();
                org.telegram.messenger.wgram.WGramSharedProfiles.get(currentAccount).badge(recipient,active,badgeEmoji,badgeTitle,badgeDescription,(result,error)->{
                    busy=false;refresh();if(!owner())return;
                    WGramUi.info(this,error==null?getString(R.string.WGramBadgeSaved):WGramServiceActivity.error(error));
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
