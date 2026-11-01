package com.sasakiy84.solovlog;

import static com.google.common.truth.Truth.assertThat;
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
            assertThat(restored.clipIds).containsExactlyElementsIn(draft.clipIds).inOrder();
            assertThat(restored.caption).isEqualTo("旅行");
            assertThat(restored.showDate).isTrue();
            assertThat(restored.showTime).isFalse();
            assertThat(restored.showPlace).isFalse();
            assertThat(restored.showText).isFalse();
            assertThat(restored.audio).isFalse();
            assertThat(restored.status).isEqualTo("ready");
            assertThat(restored.savedUri).isEmpty();
            assertThat(restored.compact).isTrue();
            assertThat(restored.embedLocation).isFalse();
            assertThat(restored.sourceSignature).isEqualTo("source");
        } finally {
            for (File file : directory.listFiles()) file.delete();
            directory.delete();
        }
    }
}
