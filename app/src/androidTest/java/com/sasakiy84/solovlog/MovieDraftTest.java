package com.sasakiy84.solovlog;

import static com.google.common.truth.Truth.assertThat;
import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class MovieDraftTest {
    @Test public void adjustmentsAndReadyResultPersistWithoutPublishingToGallery()throws Exception{
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();MovieDraft draft=MovieDraft.create("2026-10-31",List.of(UUID.randomUUID().toString(),UUID.randomUUID().toString()),"旅行",false,false);
        try{
            draft.showPlace=false;draft.showText=false;draft.audio=false;draft.status="ready";draft.sourceSignature="source";draft.save(context);
            MovieDraft restored=MovieDraft.load(context,draft.id);assertThat(restored.clipIds).containsExactlyElementsIn(draft.clipIds).inOrder();assertThat(restored.caption).isEqualTo("旅行");assertThat(restored.showTime||restored.showPlace||restored.showText||restored.audio).isFalse();assertThat(restored.status).isEqualTo("ready");assertThat(restored.savedUri).isEmpty();assertThat(restored.sourceSignature).isEqualTo("source");
        }finally{new File(context.getFilesDir(),"rendered/"+draft.id+".json").delete();}
    }
    @Test public void unsafeRenderIdentifiersCannotReachFilesystem(){
        for(String id:List.of("../../clip","","a/b")){try{new MovieDraft(id);throw new AssertionError("Invalid identifier accepted");}catch(IllegalArgumentException expected){assertThat(expected.getMessage()).contains("Invalid");}}
    }
}
