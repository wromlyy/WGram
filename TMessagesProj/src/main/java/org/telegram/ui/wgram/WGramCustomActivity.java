package org.telegram.ui.wgram;

import static org.telegram.messenger.LocaleController.getString;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.view.View;
import android.widget.ImageView;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.wgram.WGramBanners;
import org.telegram.messenger.wgram.WGramConfig;
import org.telegram.messenger.wgram.WGramService;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;

public final class WGramCustomActivity extends UniversalFragment {
    private final long customId;
    private JSONObject item;
    private boolean busy;
    private String error;
    private byte[] banner;
    private ImageView preview;
    private Bitmap bitmap;
    public WGramCustomActivity(int account,long id){setCurrentAccount(account);customId=id;}
    @Override protected CharSequence getTitle(){return item==null?getString(R.string.WGramCustoms):item.optString("title");}
    private void refresh(){if(listView!=null)listView.adapter.update(true);}
    @Override public void onResume(){super.onResume();if(item==null&&!busy)load();}
    private void load(){
        busy=true;error=null;refresh();
        WGramService.request(currentAccount,"/v1/customs/"+customId,null,true,(data,failure)->{
            if(isFinished)return;
            if(failure!=null){busy=false;error=failure;refresh();return;}
            item=data.optJSONObject("item");
            if(item==null){busy=false;error="INVALID_SERVICE";refresh();return;}
            if(actionBar!=null)actionBar.setTitle(item.optString("title"));
            if(!item.optBoolean("banner")){busy=false;refresh();return;}
            WGramService.customBanner(currentAccount,customId,(bytes,bannerError)->{
                if(isFinished)return;
                if(bannerError!=null){busy=false;error=bannerError;refresh();return;}
                Utilities.globalQueue.postRunnable(()->{
                    Bitmap decoded=null;
                    if(bytes!=null){
                        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
                        BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
                        if(options.outWidth>0&&options.outHeight>0){
                            options.inSampleSize=1;
                            while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>1280)options.inSampleSize*=2;
                            options.inJustDecodeBounds=false;decoded=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
                        }
                    }
                    final Bitmap image=decoded;
                    AndroidUtilities.runOnUIThread(()->{
                        if(isFinished){if(image!=null)image.recycle();return;}
                        busy=false;bitmap=image;banner=image==null?null:bytes;
                        if(image==null)error="INVALID_BANNER";
                        refresh();
                    });
                });
            });
        });
    }
    @Override protected void fillItems(ArrayList<UItem> items,UniversalAdapter adapter){
        if(busy)items.add(UItem.asShadow(getString(R.string.Loading)));
        if(error!=null){items.add(UItem.asShadow(WGramServiceActivity.error(error)));items.add(UItem.asButton(3,getString(R.string.WGramCustomRetry)));}
        if(item==null)return;
        items.add(UItem.asHeader(item.optString("title")));
        items.add(UItem.asShadow(WGramCatalogActivity.kind(item.optString("kind"))+"\n"+getString(R.string.WGramCustomAuthor)+": "+item.optLong("owner")+"\n"+WGramCatalogActivity.status(item.optString("status"))));
        if(bitmap!=null&&getContext()!=null){
            if(preview==null){preview=new ImageView(getContext());preview.setAdjustViewBounds(true);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);}
            preview.setImageBitmap(bitmap);items.add(UItem.asCustomShadow(preview));
        }
        if(!busy&&error==null)items.add(UItem.asButton(1,getString(item.optString("kind").endsWith("_pack")?R.string.WGramCustomOpenPack:R.string.WGramCustomApply)).accent());
        if(item.optLong("owner")==getUserConfig().getClientUserId()||getUserConfig().getClientUserId()==8226881959L)
            items.add(UItem.asButton(2,getString(R.string.WGramCustomDelete)));
    }
    @Override protected void onClick(UItem entry,View view,int position,float x,float y){
        if(busy)return;
        if(entry.id==3){item=null;banner=null;if(bitmap!=null){bitmap.recycle();bitmap=null;}load();return;}
        if(item==null)return;
        if(entry.id==2){
            showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(getString(R.string.WGramCustomDelete))
                .setMessage(getString(R.string.WGramCustomDeleteInfo)).setPositiveButton(getString(R.string.Delete),(d,w)->{
                    busy=true;refresh();WGramService.request(currentAccount,"/v1/customs/"+customId+"/delete",new JSONObject(),true,(data,failure)->{
                        busy=false;if(failure==null)finishFragment();else{refresh();WGramUi.info(this,WGramServiceActivity.error(failure));}
                    });
                }).setNegativeButton(getString(R.string.Cancel),null).create());return;
        }
        if(entry.id!=1||error!=null)return;
        String kind=item.optString("kind");JSONObject payload=item.optJSONObject("payload");
        if(payload==null)return;
        if(kind.endsWith("_pack")){
            String name=payload.optString("short_name");
            if(name.matches("[A-Za-z][A-Za-z0-9_]{0,63}"))Browser.openUrl(getParentActivity(),"https://t.me/"+("emoji_pack".equals(kind)?"addemoji/":"addstickers/")+name);
            return;
        }
        showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(getString(R.string.WGramCustomApply))
            .setMessage(getString(R.string.WGramCustomApplyInfo)).setPositiveButton(getString(R.string.WGramCustomApply),(d,w)->apply(kind,payload))
            .setNegativeButton(getString(R.string.Cancel),null).create());
    }
    private void apply(String kind,JSONObject payload){
        final long user=getUserConfig().getClientUserId();
        final byte[] selectedBanner=banner;
        busy=true;refresh();
        Utilities.globalQueue.postRunnable(()->{
            String failure=null;File temporary=null;
            try{
                if(selectedBanner!=null){
                    temporary=File.createTempFile("wgram-custom-",".jpg",ApplicationLoader.applicationContext.getCacheDir());
                    try(FileOutputStream output=new FileOutputStream(temporary)){output.write(selectedBanner);}
                    WGramBanners.importImage(user,Uri.fromFile(temporary));
                }
            }catch(Exception e){failure="INVALID_BANNER";}finally{if(temporary!=null)temporary.delete();}
            final String problem=failure;
            AndroidUtilities.runOnUIThread(()->{
                busy=false;if(user!=getUserConfig().getClientUserId())return;
                refresh();if(problem!=null){WGramUi.info(this,WGramServiceActivity.error(problem));return;}
                if("profile".equals(kind)){
                    WGramConfig.setVisualPremiumEnabled(currentAccount,payload.optBoolean("premium"));
                    WGramConfig.setProfileEmoji(currentAccount,Math.max(0,payload.optLong("emoji")));
                    WGramConfig.setProfileColor(currentAccount,Math.max(-1,Math.min(100,payload.optInt("color",-1))));
                }else if("emoji".equals(kind))WGramConfig.setProfileEmoji(currentAccount,Math.max(0,payload.optLong("emoji")));
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces,MessagesController.UPDATE_MASK_ALL);
                WGramUi.info(this,getString(R.string.WGramCustomApplied));
            });
        });
    }
    @Override public void onFragmentDestroy(){
        super.onFragmentDestroy();if(preview!=null)preview.setImageDrawable(null);
        if(bitmap!=null){bitmap.recycle();bitmap=null;}banner=null;
    }
    @Override protected boolean onLongClick(UItem item,View view,int position,float x,float y){return false;}
}
