package com.sasakiy84.solovlog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class MovieDraftTest {
    @Test public void adjustmentsAndReadyResultPersistWithoutPublishingToGallery() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        MovieDraft draft = MovieDraft.create(context, "2026-10-31",
            List.of(UUID.randomUUID().toString(), UUID.randomUUID().toString()), "旅行", false, true);
        File directory = Files.createTempDirectory(context.getCacheDir().toPath(), "draft-").toFile();
        MovieDraftStore store = new MovieDraftStore(directory);
        try {
            draft.compact = true;
            draft.embedLocation = false;
            draft.showPlace = false;
            draft.showText = false;
            draft.audio = false;
            draft.status = "ready";
            draft.sourceSignature = "source";
            store.save(draft);
            MovieDraft restored = store.load(draft.id);
            assertEquals(draft.clipIds,restored.clipIds);
            assertEquals("旅行",restored.caption);
            assertTrue(restored.showDate);
            assertFalse(restored.showTime);
            assertFalse(restored.showPlace);
            assertFalse(restored.showText);
            assertFalse(restored.audio);
            assertEquals("ready",restored.status);
            assertTrue(restored.savedUri.isEmpty());
            assertTrue(restored.compact);
            assertFalse(restored.embedLocation);
            assertEquals("source",restored.sourceSignature);
        } finally {
            for (File file : directory.listFiles()) file.delete();
            directory.delete();
        }
    }
    @Test public void periodKeepsExclusiveCaptionModesAndItsLastDayDuringCleanup() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        File directory=Files.createTempDirectory(context.getCacheDir().toPath(),"period-").toFile();
        MovieDraftStore store=new MovieDraftStore(directory);
        MovieDraft draft=new MovieDraft(UUID.randomUUID().toString());
        draft.day="2026-12-28";draft.days=10;draft.combined=true;draft.dayBoundary=240;draft.caption="Winter";
        draft.dayCaptions.put("2026-12-28","Tokyo");draft.dayCaptions.put("2026-12-29","Kyoto");
        Clip monday=new Clip(UUID.randomUUID().toString(),java.time.Instant.parse("2026-12-28T12:00:00Z").toEpochMilli(),2000,2,false,true,"UTC");
        Clip earlyTuesday=new Clip(UUID.randomUUID().toString(),java.time.Instant.parse("2026-12-29T02:00:00Z").toEpochMilli(),2000,2,false,true,"UTC");
        Clip tuesday=new Clip(UUID.randomUUID().toString(),java.time.Instant.parse("2026-12-29T12:00:00Z").toEpochMilli(),2000,2,false,true,"UTC");
        try {
            store.save(draft);Files.write(store.movie(draft).toPath(),new byte[]{1});
            MovieDraft restored=store.load(draft.id);
            assertEquals("Tokyo",restored.captionFor(monday));
            assertEquals("Tokyo",restored.captionFor(earlyTuesday));
            assertEquals("Kyoto",restored.captionFor(tuesday));
            restored.useDayCaptions=false;
            assertEquals("Winter",restored.captionFor(monday));assertEquals("Winter",restored.captionFor(tuesday));
            store.save(restored);assertFalse(store.load(draft.id).useDayCaptions);
            restored.useDayCaptions=true;assertEquals("Kyoto",restored.captionFor(tuesday));
            store.deleteBefore(java.time.LocalDate.of(2027,1,6));
            assertTrue(store.movie(draft).exists());
            store.deleteBefore(java.time.LocalDate.of(2027,1,7));
            assertFalse(store.movie(draft).exists());
        } finally {
            for(File file:directory.listFiles())file.delete();directory.delete();
        }
    }

}
