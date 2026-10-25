package org.telegram.ui.wgram;

import static org.telegram.messenger.LocaleController.getString;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.wgram.WGramBanners;
import org.telegram.messenger.wgram.WGramConfig;
import org.telegram.messenger.wgram.WGramSharedProfiles;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;

/** Copies visible profile fields only after the user reviews changes to their Telegram account. */
public final class WGramCopyProfile {
    private static final boolean[] busy=new boolean[UserConfig.MAX_ACCOUNT_COUNT];
    private WGramCopyProfile(){}
    public static void show(BaseFragment fragment,TLRPC.User source){
        final int account=fragment.getCurrentAccount();
        if(source==null||source.deleted||source.bot||source.id==UserConfig.getInstance(account).getClientUserId()||busy[account])return;
        fragment.showDialog(new AlertDialog.Builder(fragment.getParentActivity()).setTitle(getString(R.string.WGramCopyProfile))
            .setMessage(UserObject.getUserName(source)+"\n\n"+getString(R.string.WGramCopyProfileConfirm))
            .setPositiveButton(getString(R.string.WGramCopyApply),(d,w)->new Operation(fragment,source).start())
            .setNegativeButton(getString(R.string.Cancel),null).create());
    }
    private static final class Operation implements NotificationCenter.NotificationCenterDelegate {
        final BaseFragment fragment;
        final TLRPC.User source;
        final int account;
        final long session;
        final MessagesController controller;
        final NotificationCenter notifications;
        final Runnable timeout=()->finish(false);
        AlertDialog progress;
        String download,upload;
        File photoFile;
        boolean finished,changed,bioClipped,appearanceFailed;
        Operation(BaseFragment fragment,TLRPC.User source){
            this.fragment=fragment;this.source=source;account=fragment.getCurrentAccount();
            session=UserConfig.getInstance(account).getClientUserId();controller=MessagesController.getInstance(account);notifications=NotificationCenter.getInstance(account);
        }
        boolean valid(){return !finished&&session!=0&&session==UserConfig.getInstance(account).getClientUserId();}
        void start(){
            if(busy[account])return;busy[account]=true;
            progress=new AlertDialog(fragment.getParentActivity(),AlertDialog.ALERT_TYPE_SPINNER);
            progress.setCanCancel(false);fragment.showDialog(progress);AndroidUtilities.runOnUIThread(timeout,90000);
            TLRPC.TL_users_getFullUser req=new TLRPC.TL_users_getFullUser();req.id=controller.getInputUser(source);
            ConnectionsManager.getInstance(account).sendRequest(req,(res,error)->AndroidUtilities.runOnUIThread(()->{
                if(!valid()){finish(false);return;}
                if(!(res instanceof TLRPC.TL_users_userFull)){finish(false);return;}
                TLRPC.TL_users_userFull full=(TLRPC.TL_users_userFull)res;
                String about=full.full_user.about==null?"":full.full_user.about;
                int limit=controller.getAboutLimit();bioClipped=about.codePointCount(0,about.length())>limit;
                if(bioClipped)about=about.substring(0,about.offsetByCodePoints(0,limit));
                update(about);
            }));
        }
        void update(String about){
            TL_account.updateProfile req=new TL_account.updateProfile();req.flags=7;
            req.first_name=source.first_name==null?"":source.first_name;req.last_name=source.last_name==null?"":source.last_name;req.about=about;
            ConnectionsManager.getInstance(account).sendRequest(req,(res,error)->AndroidUtilities.runOnUIThread(()->{
                if(!valid()){finish(false);return;}
                if(error!=null||!(res instanceof TLRPC.User)){finish(false);return;}
                TLRPC.User self=(TLRPC.User)res;controller.putUser(self,false);UserConfig.getInstance(account).setCurrentUser(self);changed=true;
                ArrayList<TLRPC.User> users=new ArrayList<>();users.add(self);MessagesStorage.getInstance(account).putUsersAndChats(users,null,false,true);
                TLRPC.UserFull ownFull=controller.getUserFull(session);
                if(ownFull!=null){ownFull.about=about;ownFull.flags=about.isEmpty()?ownFull.flags&~2:ownFull.flags|2;MessagesStorage.getInstance(account).updateUserInfo(ownFull,false);}
                WGramSharedProfiles.get(account).load(source.id,()->{if(valid())copyAppearance();else finish(false);});
            }));
        }
        void copyAppearance(){
            WGramSharedProfiles.Profile profile=WGramSharedProfiles.get(account).peek(source.id);
            WGramConfig.setVisualPremiumEnabled(account,profile!=null?profile.premium:source.premium);
            Long emoji=UserObject.getEmojiStatusDocumentId(source);
            WGramConfig.setProfileEmoji(account,profile!=null?profile.emoji:emoji==null?0:emoji);
            WGramConfig.setProfileColor(account,profile!=null?profile.color:UserObject.getProfileColorId(source));
            // Awards belong to their actual recipient and are never copied.
            File banner=profile==null?null:profile.banner;
            if(banner==null){WGramBanners.file(session).delete();copyAvatar();return;}
            Utilities.globalQueue.postRunnable(()->{
                File temp=null;
                boolean saved=false;
                try{
                    if(banner.length()>1500000)throw new IllegalArgumentException();
                    temp=File.createTempFile("wgram-copy-",".jpg",WGramBanners.file(session).getParentFile());
                    try(FileInputStream in=new FileInputStream(banner);FileOutputStream out=new FileOutputStream(temp)){
                        byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1)out.write(buffer,0,count);
                    }
                    if(session==UserConfig.getInstance(account).getClientUserId())saved=temp.renameTo(WGramBanners.file(session));
                }catch(Exception ignored){}finally{if(temp!=null)temp.delete();}
                final boolean ok=saved;
                AndroidUtilities.runOnUIThread(()->{if(valid()){appearanceFailed=!ok;copyAvatar();}else finish(false);});
            });
        }
        void copyAvatar(){
            if(source.photo==null||source.photo instanceof TLRPC.TL_userProfilePhotoEmpty){
                TLRPC.TL_photos_updateProfilePhoto req=new TLRPC.TL_photos_updateProfilePhoto();req.id=new TLRPC.TL_inputPhotoEmpty();
                ConnectionsManager.getInstance(account).sendRequest(req,(res,e)->AndroidUtilities.runOnUIThread(()->{
                    if(valid()&&e==null&&res instanceof TLRPC.TL_photos_photo){applyPhoto(((TLRPC.TL_photos_photo)res).photo);finish(true);}else finish(false);
                }));return;
            }
            TLRPC.FileLocation location=source.photo.photo_big;
            ImageLocation image=ImageLocation.getForUser(account,source,ImageLocation.TYPE_BIG);
            if(location==null||image==null){finish(false);return;}
            photoFile=FileLoader.getInstance(account).getPathToAttach(location,"jpg",true);
            download=FileLoader.getAttachFileName(location,"jpg");
            if(photoFile.exists()&&photoFile.length()>0){upload();return;}
            notifications.addObserver(this,NotificationCenter.fileLoaded);notifications.addObserver(this,NotificationCenter.fileLoadFailed);
            FileLoader.getInstance(account).loadFile(image,source,"jpg",FileLoader.PRIORITY_HIGH,1);
        }
        void upload(){
            download=null;
            if(!valid()||photoFile.length()>10000000){finish(false);return;}
            upload=photoFile.getAbsolutePath();
            notifications.addObserver(this,NotificationCenter.fileUploaded);notifications.addObserver(this,NotificationCenter.fileUploadFailed);
            FileLoader.getInstance(account).uploadFile(upload,false,true,ConnectionsManager.FileTypePhoto);
        }
        @Override public void didReceivedNotification(int id,int ignored,Object... args){
            if(!valid()){finish(false);return;}
            if((id==NotificationCenter.fileLoaded||id==NotificationCenter.fileLoadFailed)&&download!=null&&download.equals(args[0])){
                if(id==NotificationCenter.fileLoaded)upload();else finish(false);
            }else if((id==NotificationCenter.fileUploaded||id==NotificationCenter.fileUploadFailed)&&upload!=null&&upload.equals(args[0])){
                if(id==NotificationCenter.fileUploadFailed){finish(false);return;}
                upload=null;
                TLRPC.TL_photos_uploadProfilePhoto req=new TLRPC.TL_photos_uploadProfilePhoto();req.file=(TLRPC.InputFile)args[1];req.flags=1;
                ConnectionsManager.getInstance(account).sendRequest(req,(res,error)->AndroidUtilities.runOnUIThread(()->{
                    if(valid()&&error==null&&res instanceof TLRPC.TL_photos_photo){applyPhoto(((TLRPC.TL_photos_photo)res).photo);finish(true);}else finish(false);
                }));
            }
        }
        void applyPhoto(TLRPC.Photo photo){
            TLRPC.User self=UserConfig.getInstance(account).getCurrentUser();if(self==null)return;
            if(photo instanceof TLRPC.TL_photo){
                TLRPC.TL_userProfilePhoto avatar=new TLRPC.TL_userProfilePhoto();avatar.photo_id=photo.id;avatar.dc_id=photo.dc_id;
                TLRPC.PhotoSize small=FileLoader.getClosestPhotoSizeWithSize(photo.sizes,150),big=FileLoader.getClosestPhotoSizeWithSize(photo.sizes,800);
                if(small!=null)avatar.photo_small=small.location;if(big!=null)avatar.photo_big=big.location;self.photo=avatar;
            }else self.photo=new TLRPC.TL_userProfilePhotoEmpty();
            TLRPC.UserFull full=controller.getUserFull(session);if(full!=null){full.profile_photo=photo;MessagesStorage.getInstance(account).updateUserInfo(full,false);}
            controller.putUser(self,false);ArrayList<TLRPC.User> users=new ArrayList<>();users.add(self);MessagesStorage.getInstance(account).putUsersAndChats(users,null,false,true);
        }
        void finish(boolean success){
            if(finished)return;success=success&&!appearanceFailed;finished=true;busy[account]=false;AndroidUtilities.cancelRunOnUIThread(timeout);
            for(int id:new int[]{NotificationCenter.fileLoaded,NotificationCenter.fileLoadFailed,NotificationCenter.fileUploaded,NotificationCenter.fileUploadFailed})notifications.removeObserver(this,id);
            if(progress!=null)progress.dismiss();
            if(session!=UserConfig.getInstance(account).getClientUserId())return;
            if(changed){UserConfig.getInstance(account).saveConfig(true);notifications.postNotificationName(NotificationCenter.mainUserInfoChanged);notifications.postNotificationName(NotificationCenter.updateInterfaces,MessagesController.UPDATE_MASK_ALL);}
            WGramUi.info(fragment,getString(success?R.string.WGramCopyDone:changed?R.string.WGramCopyPartial:R.string.WGramCopyFailed)+(bioClipped?"\n"+getString(R.string.WGramCopyBioClipped):""));
        }
    }
}
