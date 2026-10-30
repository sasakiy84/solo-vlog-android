package com.sasakiy84.solovlog;

import static com.google.common.truth.Truth.assertThat;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.MediaStore;
import androidx.test.uiautomator.UiDevice;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;

final class DeviceImages {
    private DeviceImages(){}
    static void screenshot(Context context,UiDevice device,String name) throws Exception{
        device.waitForIdle();SystemClock.sleep(1500);
        File file=File.createTempFile("daylog-screen-",".png",context.getCacheDir());
        try{assertThat(device.takeScreenshot(file)).isTrue();publishFile(context,file,name);}finally{file.delete();}
    }
    static void publishFile(Context context,File file,String name) throws Exception{
        Uri image=create(context,name);
        try(FileInputStream source=new FileInputStream(file);OutputStream target=context.getContentResolver().openOutputStream(image)){assertThat(target).isNotNull();source.transferTo(target);}
        finish(context,image);
    }
    static void bitmap(Context context,Bitmap bitmap,String name) throws Exception{
        Uri image=create(context,name);
        try(OutputStream target=context.getContentResolver().openOutputStream(image)){assertThat(target).isNotNull();assertThat(bitmap.compress(Bitmap.CompressFormat.PNG,100,target)).isTrue();}
        finish(context,image);
    }
    private static Uri create(Context context,String name){
        ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,name+".png");values.put(MediaStore.Images.Media.MIME_TYPE,"image/png");values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/DaylogTestScreenshots");values.put(MediaStore.Images.Media.IS_PENDING,1);
        Uri image=context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);assertThat(image).isNotNull();return image;
    }
    private static void finish(Context context,Uri image){ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.IS_PENDING,0);context.getContentResolver().update(image,values,null,null);}
}
