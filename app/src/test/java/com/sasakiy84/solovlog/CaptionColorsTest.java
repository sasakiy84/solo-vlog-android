package com.sasakiy84.solovlog;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CaptionColorsTest {
    @Test public void brightYellowAndDarkBlueUseDifferentInkDespiteBothBeingSaturated(){
        assertTrue(CaptionColors.darkInk(CaptionColors.luminance(0xFFFFFF00)));
        assertFalse(CaptionColors.darkInk(CaptionColors.luminance(0xFF0000FF)));
    }
}
