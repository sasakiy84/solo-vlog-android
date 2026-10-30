package com.sasakiy84.solovlog;

/** Black/white contrast crossover on a linear-light background, ignoring source alpha. */
final class CaptionColors {
    static final float CROSSOVER=(float)(Math.sqrt(.05*1.05)-.05);
    private CaptionColors(){}
    static boolean darkInk(double luminance){return luminance>CROSSOVER;}
    static double luminance(int color){return .2126*linear((color>>16)&255)+.7152*linear((color>>8)&255)+.0722*linear(color&255);}
    // Match the encoded BT.709 / SMPTE 170m RGB used by the video effect and decoded previews.
    private static double linear(int component){double c=component/255d;return c<.081?c/4.5:Math.pow((c+.099)/1.099,1/.45);}
}
