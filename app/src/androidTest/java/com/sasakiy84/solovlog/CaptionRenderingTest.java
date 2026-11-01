package com.sasakiy84.solovlog;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.media3.effect.DefaultVideoFrameProcessor;
import android.graphics.Bitmap;
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
    @Test(timeout=240000) public void encodedTimeAndBothCaptionsKeepInkForEachClip() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        List<File> files=new ArrayList<>();Transformer[] running=new Transformer[1];
        try{
            List<EditedMediaItem> items=new ArrayList<>();
            Clip clip=new Clip("fixture",Instant.parse("2026-10-30T12:34:00Z").toEpochMilli(),1000,2,false,true,"UTC","Breakfast").withPlace("Tokyo");
            String[] backgrounds={"white.mp4","indoor.mp4"};
            for(String asset:backgrounds){
                File footage=TestVideos.copyToCache(asset);files.add(footage);
                items.add(new EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(footage)))
                    .setEffects(new Effects(List.of(),List.of(CaptionOverlay.prepare(context,footage,clip,"Kyoto",true,false)))).build());
            }
            Composition composition=new Composition.Builder(new EditedMediaItemSequence.Builder(items).build()).build();
            File output=File.createTempFile("contrast-",".mp4",context.getCacheDir());files.add(output);output.delete();
            CountDownLatch done=new CountDownLatch(1);AtomicReference<Exception> error=new AtomicReference<>();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                try {
                running[0]=new Transformer.Builder(context).setVideoFrameProcessorFactory(new DefaultVideoFrameProcessor.Factory.Builder().setSdrWorkingColorSpace(DefaultVideoFrameProcessor.WORKING_COLOR_SPACE_DEFAULT).build()).setVideoMimeType(MimeTypes.VIDEO_H264).addListener(new Transformer.Listener(){
                    @Override public void onCompleted(Composition result,ExportResult exported){done.countDown();}
                    @Override public void onError(Composition result,ExportResult exported,ExportException failure){error.set(failure);done.countDown();}
                }).build();running[0].start(composition,output.getAbsolutePath());
                } catch (RuntimeException failure) { error.set(failure);done.countDown(); }
            });
            assertTrue("Caption export did not finish within 180 seconds",done.await(180,TimeUnit.SECONDS));if(error.get()!=null)throw new AssertionError("Caption export failed",error.get());
            try(MediaMetadataRetriever media=new MediaMetadataRetriever()){
                media.setDataSource(output.getAbsolutePath());
                for(int n=0;n<backgrounds.length;n++){
                    Bitmap frame=media.getFrameAtTime(500000+n*1000000L,MediaMetadataRetriever.OPTION_CLOSEST);
                    assertNotNull(frame);
                    try {
                    boolean bright=n==0;
                    assertInk(frame,bright,.03f,.03f,.8f,.2f,"background "+n+", day text"); // day text
                    assertInk(frame,bright,.03f,.92f,.7f,.98f,"background "+n+", clip text");
                    assertInk(frame,bright,.03f,.86f,.7f,.92f,"background "+n+", place"); // clip text
                    assertInk(frame,bright,.7f,.8f,.98f,.98f,"background "+n+", clock"); // bottom-right clock
                    } finally { frame.recycle(); }
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
        assertTrue(label,count>40);
    }
}
