package com.sasakiy84.solovlog;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.TextView;

final class Ui {
    static final int BG=Color.WHITE,CARD=0xFFF4F4F4,INK=0xFF202124,MUTED=0xFF70757A,ACCENT=INK,LINE=0xFFE4E5E7;
    private Ui() {}
    static int dp(Context c,float v){return Math.round(v*c.getResources().getDisplayMetrics().density);}
    static GradientDrawable rounded(Context c,int color,int radius){GradientDrawable s=new GradientDrawable();s.setColor(color);s.setCornerRadius(dp(c,radius));return s;}
    static GradientDrawable outlined(Context c,int color,int stroke,int radius){GradientDrawable s=rounded(c,color,radius);s.setStroke(dp(c,1),stroke);return s;}
    static void touch(View v,int color,int radius){v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x18000000),rounded(v.getContext(),color,radius),rounded(v.getContext(),Color.WHITE,radius)));v.setFocusable(true);}
    static TextView text(Context c,String value,int size,int color){TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setIncludeFontPadding(false);t.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));t.setLineSpacing(dp(c,3),1);return t;}

    static final class AspectFrame extends FrameLayout {
        private final float ratio;
        AspectFrame(Context c,boolean horizontal){super(c);ratio=horizontal?16f/9:9f/16;}
        @Override protected void onMeasure(int width,int height){int w=MeasureSpec.getSize(width),h=MeasureSpec.getSize(height);w=Math.min(w,Math.round(h*ratio));h=Math.round(w/ratio);super.onMeasure(MeasureSpec.makeMeasureSpec(w,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(h,MeasureSpec.EXACTLY));}
    }
    static final class Grid extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        Grid(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas canvas){paint.setColor(0x32FFFFFF);paint.setStrokeWidth(dp(getContext(),.6f));for(int i=1;i<3;i++){canvas.drawLine(getWidth()*i/3f,0,getWidth()*i/3f,getHeight(),paint);canvas.drawLine(0,getHeight()*i/3f,getWidth(),getHeight()*i/3f,paint);}paint.setColor(0x88FFFFFF);float x=getWidth()/2f,y=getHeight()/2f,r=dp(getContext(),4);canvas.drawLine(x-r,y,x+r,y,paint);canvas.drawLine(x,y-r,x,y+r,paint);}
    }
    // Material icons use vector assets; the touch target stays larger than the glyph.
    static final class Icon extends android.widget.ImageView {
        Icon(Context c,String kind,int color,String label,Runnable click){
            super(c);
            android.graphics.drawable.Drawable drawable=Ui.drawable(c,kind,color);
            setImageDrawable(drawable);setScaleType(ScaleType.CENTER_INSIDE);
            int padding=dp(c,12);setPadding(padding,padding,padding,padding);
            if(click!=null){setContentDescription(label);touch(this,Color.TRANSPARENT,24);setOnClickListener(v->click.run());}
            else setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo i){super.onInitializeAccessibilityNodeInfo(i);if(isClickable())i.setClassName("android.widget.Button");}
    }
    static android.graphics.drawable.Drawable drawable(Context c,String kind,int color){
        int resource=switch(kind){
            case "back"->R.drawable.ic_arrow_back;case "gear"->R.drawable.ic_settings;
            case "library"->R.drawable.ic_video_library;case "flip"->R.drawable.ic_cameraswitch;
            case "mic"->R.drawable.ic_mic;case "muted"->R.drawable.ic_mic_off;
            case "check"->R.drawable.ic_check_circle;case "unchecked"->R.drawable.ic_circle;
            case "pin"->R.drawable.ic_location_on;case "pin_off"->R.drawable.ic_location_off;case "edit"->R.drawable.ic_edit;case "download"->R.drawable.ic_download;case "camera"->R.drawable.ic_videocam;
            default->R.drawable.ic_chevron_right;
        };
        android.graphics.drawable.Drawable d=c.getDrawable(resource).mutate();
        if(!kind.equals("check")&&!kind.equals("unchecked"))d.setTint(color);
        d.setBounds(0,0,dp(c,20),dp(c,20));return d;
    }
    static final class Shutter extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private boolean recording,white;private float progress;
        Shutter(Context c){super(c);setFocusable(true);setContentDescription("2秒撮影");}
        void white(boolean enabled){white=enabled;invalidate();}
        void state(boolean active,float fraction,int seconds){recording=active;progress=fraction;setContentDescription(active?"撮影を停止":seconds+"秒撮影");invalidate();}
        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo i){super.onInitializeAccessibilityNodeInfo(i);i.setClassName("android.widget.Button");}
        @Override protected void onDraw(Canvas c){float x=getWidth()/2f,y=getHeight()/2f,r=Math.min(x,y)-dp(getContext(),6);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(getContext(),3));p.setColor(recording?LINE:white?Color.WHITE:ACCENT);c.drawCircle(x,y,r,p);if(recording){p.setColor(ACCENT);c.drawArc(new RectF(x-r,y-r,x+r,y+r),-90,360*progress,false,p);}p.setStyle(Paint.Style.FILL);p.setColor(recording?0xFFD93025:white?Color.WHITE:ACCENT);if(recording){float q=dp(getContext(),14);c.drawRoundRect(x-q,y-q,x+q,y+q,dp(getContext(),5),dp(getContext(),5),p);}else c.drawCircle(x,y,r-dp(getContext(),7),p);}
    }
}
