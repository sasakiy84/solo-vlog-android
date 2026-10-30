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
    static final int BG=Color.rgb(17,20,16),CARD=Color.rgb(31,36,29),INK=Color.rgb(244,243,235),MUTED=Color.rgb(151,160,143),LIME=Color.rgb(216,246,155),LINE=Color.rgb(53,61,48);
    private Ui() {}
    static int dp(Context c,float v){return Math.round(v*c.getResources().getDisplayMetrics().density);}
    static GradientDrawable rounded(Context c,int color,int radius){GradientDrawable s=new GradientDrawable();s.setColor(color);s.setCornerRadius(dp(c,radius));return s;}
    static GradientDrawable outlined(Context c,int color,int stroke,int radius){GradientDrawable s=rounded(c,color,radius);s.setStroke(dp(c,1),stroke);return s;}
    static void touch(View v,int color,int radius){v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x20FFFFFF),rounded(v.getContext(),color,radius),rounded(v.getContext(),Color.WHITE,radius)));v.setFocusable(true);}
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
    static final class Icon extends View {
        private final String kind;private final int color;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Icon(Context c,String kind,int color,String label,Runnable click){super(c);this.kind=kind;this.color=color;if(click!=null){setContentDescription(label);touch(this,Color.TRANSPARENT,24);setOnClickListener(v->click.run());}else setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo i){super.onInitializeAccessibilityNodeInfo(i);if(isClickable())i.setClassName("android.widget.Button");}
        @Override protected void onDraw(Canvas c){c.save();float s=dp(getContext(),24);c.translate((getWidth()-s)/2,(getHeight()-s)/2);c.scale(s/24,s/24);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.7f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
            switch(kind){
                case "back"->{c.drawLine(15,5,8,12,p);c.drawLine(8,12,15,19,p);}
                case "gear"->{c.drawCircle(12,12,7,p);c.drawCircle(12,12,2.7f,p);for(int i=0;i<8;i++){c.save();c.rotate(i*45,12,12);c.drawLine(12,2,12,5,p);c.restore();}}
                case "library"->{c.drawRoundRect(6,3,21,19,3,3,p);c.drawLine(3,7,3,22,p);c.drawLine(3,22,17,22,p);Path a=new Path();a.moveTo(12,8);a.lineTo(17,11);a.lineTo(12,14);a.close();c.drawPath(a,p);}
                case "flip"->{c.drawRoundRect(3,6,21,20,3,3,p);c.drawLine(8,6,10,3,p);c.drawLine(10,3,15,3,p);c.drawCircle(12,13,4,p);c.drawLine(12,9,16,9,p);c.drawLine(16,9,16,13,p);}
                case "mic","muted"->{c.drawRoundRect(9,2,15,14,3,3,p);c.drawArc(6,7,18,18,0,180,false,p);c.drawLine(12,18,12,22,p);c.drawLine(8,22,16,22,p);if(kind.equals("muted"))c.drawLine(3,3,21,21,p);}
                case "check"->{c.drawLine(5,12,10,17,p);c.drawLine(10,17,20,6,p);}
                case "portrait"->c.drawRoundRect(6,2,18,22,2,2,p);
                case "landscape"->c.drawRoundRect(2,6,22,18,2,2,p);
                case "arrow"->{c.drawLine(5,19,19,5,p);c.drawLine(8,5,19,5,p);c.drawLine(19,5,19,16,p);}
                case "moon"->{Path a=new Path();a.moveTo(18,18);a.cubicTo(4,23,0,6,12,3);a.cubicTo(7,13,14,19,21,14);c.drawPath(a,p);}
                default->{c.drawCircle(12,12,8,p);c.drawLine(12,8,12,16,p);c.drawLine(8,12,16,12,p);}
            }c.restore();}
    }
    static final class Shutter extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private boolean recording;private float progress;
        Shutter(Context c){super(c);setFocusable(true);setContentDescription("3秒撮影");}
        void state(boolean active,float fraction,int seconds){recording=active;progress=fraction;setContentDescription(active?"撮影を停止":seconds+"秒撮影");invalidate();}
        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo i){super.onInitializeAccessibilityNodeInfo(i);i.setClassName("android.widget.Button");}
        @Override protected void onDraw(Canvas c){float x=getWidth()/2f,y=getHeight()/2f,r=Math.min(x,y)-dp(getContext(),6);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(getContext(),3));p.setColor(recording?LINE:LIME);c.drawCircle(x,y,r,p);if(recording){p.setColor(LIME);c.drawArc(new RectF(x-r,y-r,x+r,y+r),-90,360*progress,false,p);}p.setStyle(Paint.Style.FILL);p.setColor(recording?0xFFE78C77:LIME);if(recording){float q=dp(getContext(),14);c.drawRoundRect(x-q,y-q,x+q,y+q,dp(getContext(),5),dp(getContext(),5),p);}else c.drawCircle(x,y,r-dp(getContext(),7),p);}
    }
}
