package com.sasakiy84.solovlog;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import android.content.Context;
import android.content.ContentValues;
import android.provider.MediaStore;
import java.io.OutputStream;
import androidx.media3.effect.DefaultVideoFrameProcessor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.EditedMediaItemSequence;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.Transformer;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Exercises the real GL export on bright and dark footage, then inspects encoded pixels. */
@UnstableApi
@RunWith(AndroidJUnit4.class)
public final class CaptionRenderingTest {
    @Test public void encodedTimeAndBothCaptionsKeepInkForEachClip() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        List<File> files=new ArrayList<>();Transformer[] running=new Transformer[1];
        try{
            List<EditedMediaItem> items=new ArrayList<>();
            Clip clip=new Clip("fixture",Instant.parse("2026-10-30T12:34:00Z").toEpochMilli(),2000,2,false,true,"UTC","Breakfast").withPlace("Tokyo");
            int[] backgrounds={Color.WHITE,Color.BLACK,Color.rgb(100,100,100),Color.rgb(152,127,96),Color.rgb(128,128,128),Color.rgb(180,180,180)};
            for(int color:backgrounds){
                File image=File.createTempFile("contrast-",".png",context.getCacheDir());files.add(image);
                Bitmap bitmap=Bitmap.createBitmap(720,1280,Bitmap.Config.ARGB_8888);bitmap.eraseColor(color);
                try(FileOutputStream stream=new FileOutputStream(image)){assertThat(bitmap.compress(Bitmap.CompressFormat.PNG,100,stream)).isTrue();}bitmap.recycle();
                MediaItem source=new MediaItem.Builder().setUri(Uri.fromFile(image)).setMimeType(MimeTypes.IMAGE_PNG).setImageDurationMs(1000).build();
                File footage=File.createTempFile("contrast-source-",".mp4",context.getCacheDir());files.add(footage);assertThat(footage.delete()).isTrue();
                CountDownLatch prepared=new CountDownLatch(1);AtomicReference<Exception> preparationError=new AtomicReference<>();
                InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                    running[0]=new Transformer.Builder(context).setVideoMimeType(MimeTypes.VIDEO_H264).addListener(new Transformer.Listener(){
                        @Override public void onCompleted(Composition result,ExportResult exported){prepared.countDown();}
                        @Override public void onError(Composition result,ExportResult exported,ExportException failure){preparationError.set(failure);prepared.countDown();}
                    }).build();running[0].start(new EditedMediaItem.Builder(source).setFrameRate(15).build(),footage.getAbsolutePath());
                });
                assertThat(prepared.await(180,TimeUnit.SECONDS)).isTrue();if(preparationError.get()!=null)throw new AssertionError(preparationError.get());
                items.add(new EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(footage)))
                    .setEffects(new Effects(List.of(),List.of(new CaptionOverlay(footage,clip,"Kyoto",true)))).build());
            }
            Composition composition=new Composition.Builder(new EditedMediaItemSequence.Builder(items).build()).build();
            File output=File.createTempFile("contrast-",".mp4",context.getCacheDir());files.add(output);assertThat(output.delete()).isTrue();
            CountDownLatch done=new CountDownLatch(1);AtomicReference<Exception> error=new AtomicReference<>();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                running[0]=new Transformer.Builder(context).setVideoFrameProcessorFactory(new DefaultVideoFrameProcessor.Factory.Builder().setSdrWorkingColorSpace(DefaultVideoFrameProcessor.WORKING_COLOR_SPACE_DEFAULT).build()).setVideoMimeType(MimeTypes.VIDEO_H264).addListener(new Transformer.Listener(){
                    @Override public void onCompleted(Composition result,ExportResult exported){done.countDown();}
                    @Override public void onError(Composition result,ExportResult exported,ExportException failure){error.set(failure);done.countDown();}
                }).build();running[0].start(composition,output.getAbsolutePath());
            });
            assertThat(done.await(180,TimeUnit.SECONDS)).isTrue();if(error.get()!=null)throw new AssertionError("Caption export failed",error.get());
            try(MediaMetadataRetriever media=new MediaMetadataRetriever()){
                media.setDataSource(output.getAbsolutePath());
                for(int n=0;n<backgrounds.length;n++){
                    Bitmap frame=media.getFrameAtTime(500000+n*1000000L,MediaMetadataRetriever.OPTION_CLOSEST);
                    assertThat(frame).isNotNull();boolean bright=n==0;
                    ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,"08-contrast-"+n+".png");values.put(MediaStore.Images.Media.MIME_TYPE,"image/png");values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/DaylogTestScreenshots");values.put(MediaStore.Images.Media.IS_PENDING,1);
                    Uri image=context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);assertThat(image).isNotNull();
                    try(OutputStream stream=context.getContentResolver().openOutputStream(image)){assertThat(stream).isNotNull();assertThat(frame.compress(Bitmap.CompressFormat.PNG,100,stream)).isTrue();}
                    values.clear();values.put(MediaStore.Images.Media.IS_PENDING,0);context.getContentResolver().update(image,values,null,null);
                    assertThat(CaptionColors.darkInk(CaptionColors.luminance(frame.getPixel(0,0)))).isEqualTo(bright);
                    assertInk(frame,bright,.03f,.03f,.8f,.2f,"background "+n+", day text"); // day text
                    assertInk(frame,bright,.03f,.92f,.7f,.98f,"background "+n+", clip text");
                    assertInk(frame,bright,.03f,.86f,.7f,.92f,"background "+n+", place"); // clip text
                    assertInk(frame,bright,.7f,.8f,.98f,.98f,"background "+n+", clock"); // bottom-right clock
                    frame.recycle();
                }
            }
        }finally{
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{if(running[0]!=null)running[0].cancel();});
            for(File file:files)file.delete();
        }
    }
    private static void assertInk(Bitmap frame,boolean bright,float left,float top,float right,float bottom,String label){
        int count=0;
        for(int y=(int)(frame.getHeight()*top);y<frame.getHeight()*bottom;y++)for(int x=(int)(frame.getWidth()*left);x<frame.getWidth()*right;x++){
            int pixel=frame.getPixel(x,y);int r=(pixel>>16)&255,g=(pixel>>8)&255,b=pixel&255;
            if(bright?r<50&&g<50&&b<50:r>210&&g>210&&b>210)count++;
        }
        assertWithMessage(label).that(count).isGreaterThan(40);
    }
}
