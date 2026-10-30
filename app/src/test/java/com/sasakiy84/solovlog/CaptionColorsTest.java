package com.sasakiy84.solovlog;

import static com.google.common.truth.Truth.assertThat;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CaptionColorsTest {
    @Test public void whiteBackgroundUsesBlackInkAndBlackBackgroundUsesWhiteInk(){
        assertThat(CaptionColors.darkInk(CaptionColors.luminance(0xFFFFFFFF))).isTrue();
        assertThat(CaptionColors.darkInk(CaptionColors.luminance(0xFF000000))).isFalse();
    }
    @Test public void graySelectionMaximizesContrastInLinearLight(){
        assertThat(CaptionColors.darkInk(CaptionColors.luminance(0xFF808080))).isTrue();
        assertThat(CaptionColors.darkInk(CaptionColors.luminance(0xFF646464))).isFalse();
    }
    @Test public void brightYellowAndDarkBlueUseDifferentInkDespiteBothBeingSaturated(){
        assertThat(CaptionColors.darkInk(CaptionColors.luminance(0xFFFFFF00))).isTrue();
        assertThat(CaptionColors.darkInk(CaptionColors.luminance(0xFF0000FF))).isFalse();
    }
}
