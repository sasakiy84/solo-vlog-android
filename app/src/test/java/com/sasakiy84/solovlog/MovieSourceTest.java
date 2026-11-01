package com.sasakiy84.solovlog;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

import java.util.List;
import org.junit.Test;

public final class MovieSourceTest {
    private final Clip clip=new Clip("00000000-0000-0000-0000-000000000001",1000,2100,2,false,true,"UTC");
    @Test public void sourceEditsAndDisplayOptionsInvalidateCachedRender(){
        String original=MovieDraft.signature(List.of(clip),"2026-10-31","旅行",true,false);
        assertNotEquals(MovieDraft.signature(List.of(clip),"2026-10-30","旅行",true,false),original);
        assertNotEquals(MovieDraft.signature(List.of(clip.withCaption("朝食")),"2026-10-31","旅行",true,false),original);
        assertNotEquals(MovieDraft.signature(List.of(clip.withTrim(100,1500)),"2026-10-31","旅行",true,false),original);
        assertNotEquals(MovieDraft.signature(List.of(clip),"2026-10-31","夕方",true,false),original);
        assertNotEquals(MovieDraft.signature(List.of(clip),"2026-10-31","旅行",false,false),original);
        assertNotEquals(MovieDraft.signature(List.of(clip),"2026-10-31","旅行",true,true),original);
    }
    @Test public void unsafeRenderIdentifiersCannotReachFilesystem(){
        assertThrows(IllegalArgumentException.class,()->new MovieDraft("../../clip"));
    }
}
