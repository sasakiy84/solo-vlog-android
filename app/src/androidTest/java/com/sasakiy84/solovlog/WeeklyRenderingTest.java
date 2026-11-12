package com.sasakiy84.solovlog;

import static org.junit.Assert.*;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.MediaStore;
import androidx.media3.common.util.UnstableApi;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Real weekly composition and gallery publication, including clips from different days. */
@UnstableApi
@RunWith(AndroidJUnit4.class)
public final class WeeklyRenderingTest {
    @Test(timeout=600000) public void weekRendersBothDaysAndPublishesOnePlayableMovie() throws Exception {
        var instrumentation=InstrumentationRegistry.getInstrumentation();
        Context context=instrumentation.getTargetContext();
        ClipRepository repository=ClipRepository.get(context);
        Clip monday=new Clip(UUID.randomUUID().toString(),Instant.parse("2026-12-28T12:00:00Z").toEpochMilli(),1000,2,false,true,"UTC","First");
        Clip tuesday=new Clip(UUID.randomUUID().toString(),Instant.parse("2026-12-29T12:00:00Z").toEpochMilli(),1000,2,false,true,"UTC","Second");
        List<Clip> clips=List.of(monday,tuesday);
        String previousMonday=repository.dayCaption("2026-12-28"),previousTuesday=repository.dayCaption("2026-12-29");
        VideoExporter exporter=new VideoExporter(context);
        AtomicReference<File> rendered=new AtomicReference<>();AtomicReference<Uri> saved=new AtomicReference<>();
        AtomicReference<String> error=new AtomicReference<>();
        try {
            for(int n=0;n<clips.size();n++){
                File fixture=TestVideos.copyToCache(n==0?"white.mp4":"indoor.mp4");
                try{Files.copy(fixture.toPath(),repository.file(clips.get(n).id()).toPath(),StandardCopyOption.REPLACE_EXISTING);}finally{fixture.delete();}
                repository.add(clips.get(n));
            }
            repository.dayCaption("2026-12-28","Monday");repository.dayCaption("2026-12-29","Tuesday");
            MovieDraft draft=MovieDraft.createWeek(context,"2026-12-28",clips.stream().map(Clip::id).toList(),false);
            draft.dayBoundary=240;
            assertEquals("2026-12-28_2027-01-03-portrait",draft.fileName);
            assertEquals("Monday",draft.captionFor(monday));assertEquals("Tuesday",draft.captionFor(tuesday));
            draft.showDate=true;draft.showTime=true;draft.audio=false;
            CountDownLatch complete=new CountDownLatch(1);
            instrumentation.runOnMainSync(()->exporter.render(draft,repository,new VideoExporter.RenderListener(){
                @Override public void rendered(File file){rendered.set(file);complete.countDown();}
                @Override public void error(String message){error.set(message);complete.countDown();}
            }));
            assertTrue("Weekly render timed out",complete.await(480,TimeUnit.SECONDS));assertNull(error.get(),error.get());assertNotNull(rendered.get());
            CountDownLatch published=new CountDownLatch(1);String filename="test-week-"+UUID.randomUUID();
            instrumentation.runOnMainSync(()->exporter.saveRendered(rendered.get(),filename,new VideoExporter.Listener(){
                @Override public void saved(Uri uri){saved.set(uri);published.countDown();}
                @Override public void error(String message){error.set(message);published.countDown();}
            }));
            assertTrue("Gallery save timed out",published.await(60,TimeUnit.SECONDS));assertNull(error.get(),error.get());assertNotNull(saved.get());
            try(var cursor=context.getContentResolver().query(saved.get(),new String[]{MediaStore.Video.Media.DISPLAY_NAME},null,null,null)){
                assertNotNull(cursor);assertTrue(cursor.moveToFirst());assertEquals(filename+".mp4",cursor.getString(0));
            }
            try(MediaMetadataRetriever media=new MediaMetadataRetriever()){
                media.setDataSource(context,saved.get());long duration=Long.parseLong(media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                assertTrue("Both days must survive concatenation: "+duration,duration>=1900&&duration<=2200);
                for(long time:new long[]{500000,1500000}){
                    var frame=media.getFrameAtTime(time,MediaMetadataRetriever.OPTION_CLOSEST);assertNotNull("Decoded frame at "+time,frame);frame.recycle();
                }
            }
        } finally {
            instrumentation.runOnMainSync(exporter::close);
            if(saved.get()!=null)context.getContentResolver().delete(saved.get(),null,null);
            if(rendered.get()!=null)rendered.get().delete();
            for(Clip clip:clips)repository.delete(clip.id());
            repository.dayCaption("2026-12-28",previousMonday);repository.dayCaption("2026-12-29",previousTuesday);
        }
    }
}
