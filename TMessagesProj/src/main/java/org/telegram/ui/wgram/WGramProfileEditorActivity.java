package org.telegram.ui.wgram;

import static org.telegram.messenger.LocaleController.getString;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.wgram.WGramService;
import org.json.JSONObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.wgram.WGramBanners;
import org.telegram.messenger.wgram.WGramConfig;
import org.telegram.messenger.wgram.WGramSharedProfiles;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.ui.ProfileActivity;
import java.util.ArrayList;

public final class WGramProfileEditorActivity extends UniversalFragment {
    private boolean importing,publishing;
    public WGramProfileEditorActivity(int account){setCurrentAccount(account);}
    @Override protected CharSequence getTitle(){return getString(R.string.WGramCustomizeProfile);}
    private void refresh(){if(listView!=null)listView.adapter.update(true);}
    @Override protected void fillItems(ArrayList<UItem> items,UniversalAdapter adapter){
        items.add(UItem.asHeader(getString(R.string.WGramProfileHeader)));
        items.add(UItem.asCheck(1,getString(R.string.WGramVisualPremium)).setChecked(WGramConfig.isVisualPremiumEnabled(currentAccount)));
        items.add(UItem.asButton(2,getString(R.string.WGramProfileEmoji),WGramConfig.getProfileEmoji(currentAccount)==0?getString(R.string.WGramProfileEmojiEmpty):getString(R.string.WGramProfileEmojiChosen)));
        items.add(UItem.asButton(3,getString(R.string.WGramRemoveEmoji)));
        items.add(UItem.asButton(4,getString(R.string.WGramProfileColor)));
        items.add(UItem.asShadow(getString(R.string.WGramProfileVisibility)));
        items.add(UItem.asHeader(getString(R.string.WGramBanner)));
        items.add(UItem.asButton(10,getString(R.string.WGramChooseBanner),importing?getString(R.string.Loading):getString(WGramBanners.file(getUserConfig().getClientUserId()).exists()?R.string.WGramProfileEmojiChosen:R.string.WGramNotChosen)));
        items.add(UItem.asButton(11,getString(R.string.WGramRemoveBanner)));
        items.add(UItem.asButton(12,getString(R.string.WGramProfilePreview)));
        items.add(UItem.asShadow(getString(R.string.WGramBannerInfo)));
        items.add(UItem.asButton(23,getString(R.string.WGramCustoms)));
        items.add(UItem.asHeader(getString(R.string.WGramSharedProfile)));
        items.add(UItem.asButton(20,getString(R.string.WGramPublishProfile),publishing?getString(R.string.Loading):"").accent());
        items.add(UItem.asButton(21,getString(R.string.WGramService),getString(WGramService.connected(currentAccount)?R.string.WGramServiceConnected:R.string.WGramServiceDisconnected)));
        items.add(UItem.asButton(22,getString(R.string.WGramUnpublishProfile)));
        items.add(UItem.asShadow(getString(R.string.WGramProfilePublishInfo)));
    }
    @Override protected void onClick(UItem item,View view,int p,float x,float y){
        switch(item.id){
            case 1:WGramConfig.setVisualPremiumEnabled(currentAccount,!WGramConfig.isVisualPremiumEnabled(currentAccount));break;
            case 2:presentFragment(new WGramEmojiPickerActivity(currentAccount));return;
            case 3:WGramConfig.setProfileEmoji(currentAccount,0);break;
            case 4:
                ArrayList<CharSequence> labels=new ArrayList<>();ArrayList<Integer> ids=new ArrayList<>();labels.add(getString(R.string.WGramDefault));ids.add(-1);
                MessagesController.PeerColors palette=getMessagesController().profilePeerColors;
                if(palette!=null)for(MessagesController.PeerColor color:palette.colors){labels.add("●  "+color.id);ids.add(color.id);}
                showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(getString(R.string.WGramProfileColor)).setItems(labels.toArray(new CharSequence[0]),(d,w)->{WGramConfig.setProfileColor(currentAccount,ids.get(w));refresh();}).create());return;
            case 10:
                if(importing)return;
                Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("image/*");
                try{startActivityForResult(intent,2401);}catch(Exception e){WGramUi.info(this,getString(R.string.WGramBannerFailed));}return;
            case 11:
                WGramBanners.file(getUserConfig().getClientUserId()).delete();
                if(WGramService.connected(currentAccount)){
                    try{WGramService.request(currentAccount,"/v1/me/profile",new JSONObject().put("premium",WGramConfig.isVisualPremiumEnabled(currentAccount))
                        .put("emoji",WGramConfig.getProfileEmoji(currentAccount)).put("color",WGramConfig.getProfileColor(currentAccount)).put("banner",""),true,(d,e)->{
                            if(e!=null)WGramUi.info(this,WGramServiceActivity.error(e));
                            else WGramSharedProfiles.get(currentAccount).invalidate(getUserConfig().getClientUserId());
                        });}catch(Exception ignored){}
                }
                break;
            case 12:Bundle args=new Bundle();args.putLong("user_id",getUserConfig().getClientUserId());ProfileActivity profile=new ProfileActivity(args);profile.setCurrentAccount(currentAccount);presentFragment(profile);return;
            case 23:presentFragment(new WGramCatalogActivity(currentAccount));return;
            case 20:
                if(publishing||importing)return;
                showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(getString(R.string.WGramPublishProfile)).setMessage(getString(R.string.WGramProfilePublishInfo))
                    .setPositiveButton(getString(R.string.WGramPublish),(d,w)->publish()).setNegativeButton(getString(R.string.Cancel),null).create());return;
            case 21:presentFragment(new WGramServiceActivity(currentAccount));return;
            case 22:showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(getString(R.string.WGramUnpublishProfile))
                .setMessage(getString(R.string.WGramUnpublishInfo)).setPositiveButton(getString(R.string.Delete),(d,w)->{
                    WGramService.request(currentAccount,"/v1/me/delete",new JSONObject(),true,(result,error)->{
                        if(error!=null)WGramUi.info(this,WGramServiceActivity.error(error));
                        else{WGramSharedProfiles.get(currentAccount).invalidate(getUserConfig().getClientUserId());WGramUi.info(this,getString(R.string.WGramUnpublished));}
                    });
                }).setNegativeButton(getString(R.string.Cancel),null).create());return;
        }
        refresh();
    }
    private void publish(){
        publishing=true;refresh();final long user=getUserConfig().getClientUserId();
        WGramSharedProfiles.get(currentAccount).publish((result,error)->{
            publishing=false;refresh();if(user!=getUserConfig().getClientUserId())return;
            WGramUi.info(this,error==null?getString(R.string.WGramProfileQueued):WGramServiceActivity.error(error));
        });
    }

    @Override public void onActivityResultFragment(int requestCode,int resultCode,Intent data){
        if(requestCode!=2401 || resultCode!=Activity.RESULT_OK || data==null || data.getData()==null)return;
        final Uri uri=data.getData();final long user=getUserConfig().getClientUserId();importing=true;refresh();
        Utilities.globalQueue.postRunnable(()->{
            boolean success=true;try{WGramBanners.importImage(user,uri);}catch(Exception e){success=false;}
            final boolean ok=success;
            AndroidUtilities.runOnUIThread(()->{importing=false;if(user==getUserConfig().getClientUserId()){refresh();if(!ok)WGramUi.info(this,getString(R.string.WGramBannerFailed));}});
        });
    }
    @Override public void onResume(){super.onResume();refresh();}
    @Override protected boolean onLongClick(UItem i,View v,int p,float x,float y){return false;}
}
