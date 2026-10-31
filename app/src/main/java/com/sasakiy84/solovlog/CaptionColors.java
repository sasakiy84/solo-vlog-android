package com.sasakiy84.solovlog;

/** Prefer white on footage; reserve black for clearly bright backgrounds. */
final class CaptionColors {
    // Small, regular video captions need white through midtones, including indoor walls.
    // Black is used only above 0.40 linear luminance (at least 9:1 against black).
    static final float BLACK_INK_THRESHOLD=.40f;
    private CaptionColors(){}
    static boolean darkInk(double luminance){return luminance>BLACK_INK_THRESHOLD;}
    static double luminance(int color){return .2126*linear((color>>16)&255)+.7152*linear((color>>8)&255)+.0722*linear(color&255);}
    // Match the encoded BT.709 / SMPTE 170m RGB used by the video effect and decoded previews.
    private static double linear(int component){double c=component/255d;return c<.081?c/4.5:Math.pow((c+.099)/1.099,1/.45);}
}
