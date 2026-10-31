package com.sasakiy84.solovlog;

import static com.google.common.truth.Truth.assertThat;
import java.util.List;
import org.junit.Test;

public final class MovieSourceTest {
    private final Clip clip=new Clip("00000000-0000-0000-0000-000000000001",1000,2100,2,false,true,"UTC");
    @Test public void changingLogicalDayDoesNotReuseAnotherDaysRender(){
        assertThat(MovieDraft.signature(List.of(clip),"2026-10-31","",true)).isNotEqualTo(MovieDraft.signature(List.of(clip),"2026-10-30","",true));
    }
    @Test public void changingSourceTextOrTimePreferenceRequiresAnotherRender(){
        String original=MovieDraft.signature(List.of(clip),"2026-10-31","旅行",true);
        assertThat(original).isNotEqualTo(MovieDraft.signature(List.of(clip.withCaption("朝食")),"2026-10-31","旅行",true));
        assertThat(original).isNotEqualTo(MovieDraft.signature(List.of(clip),"2026-10-31","旅行",false));
        assertThat(original).isNotEqualTo(MovieDraft.signature(List.of(clip),"2026-10-31","夕方",true));
    }
}
