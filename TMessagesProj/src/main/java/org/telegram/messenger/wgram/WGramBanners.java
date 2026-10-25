package org.telegram.messenger.wgram;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import androidx.exifinterface.media.ExifInterface;
import android.net.Uri;
import org.telegram.messenger.ApplicationLoader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

public final class WGramBanners {
    private WGramBanners(){}
    public static File file(long user){return new File(ApplicationLoader.applicationContext.getNoBackupFilesDir(),"wgram_banner_"+user+".jpg");}
    public static void importImage(long user,Uri uri)throws Exception{
        File temp=File.createTempFile("wgram-banner-",".tmp",ApplicationLoader.applicationContext.getCacheDir());
        Bitmap image=null,rotated=null,scaled=null,cropped=null;
        try{
            try(InputStream input=ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri);FileOutputStream output=new FileOutputStream(temp)){
                if(input==null)throw new IllegalArgumentException();
                byte[] buffer=new byte[8192];int count,total=0;
                while((count=input.read(buffer))!=-1){total+=count;if(total>20*1024*1024)throw new IllegalArgumentException();output.write(buffer,0,count);}
            }
            BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(temp.getPath(),options);
            if(options.outWidth<=0 || options.outHeight<=0)throw new IllegalArgumentException();
            options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>2048)options.inSampleSize*=2;
            options.inJustDecodeBounds=false;image=BitmapFactory.decodeFile(temp.getPath(),options);if(image==null)throw new IllegalArgumentException();
            int orientation=new ExifInterface(temp.getPath()).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);
            Matrix matrix=new Matrix();
            if(orientation==ExifInterface.ORIENTATION_ROTATE_90)matrix.postRotate(90);
            else if(orientation==ExifInterface.ORIENTATION_ROTATE_180)matrix.postRotate(180);
            else if(orientation==ExifInterface.ORIENTATION_ROTATE_270)matrix.postRotate(270);
            rotated=Bitmap.createBitmap(image,0,0,image.getWidth(),image.getHeight(),matrix,true);
            int width=rotated.getWidth(),height=rotated.getHeight();
            int cropWidth=width,cropHeight=height;
            if((long)width*9>(long)height*16)cropWidth=Math.max(1,height*16/9);else cropHeight=Math.max(1,width*9/16);
            cropped=Bitmap.createBitmap(rotated,(width-cropWidth)/2,(height-cropHeight)/2,cropWidth,cropHeight);
            scaled=Bitmap.createScaledBitmap(cropped,1280,720,true);
            try(FileOutputStream output=new FileOutputStream(file(user))){if(!scaled.compress(Bitmap.CompressFormat.JPEG,88,output))throw new IllegalStateException();}
        }finally{
            temp.delete();for(Bitmap b:new Bitmap[]{cropped,scaled,rotated,image})if(b!=null&&!b.isRecycled())b.recycle();
        }
    }
}
