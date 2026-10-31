package com.sasakiy84.solovlog;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.InsetDrawable;
import android.view.View;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.camera.core.ZoomState;
import com.google.android.material.slider.LabelFormatter;
import com.google.android.material.slider.Slider;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Presets and a graduated slider share the actual camera range. */
final class ZoomControls extends LinearLayout {
    private final float[] presets={.6f,1,2};
    private final List<TextView> buttons=new ArrayList<>();
    private final Slider slider;
    private final TextView value;
    private final Consumer<Float> changed;
    private float minimum=1,maximum=1,ratio=1;
    private boolean available,dragging;
    ZoomControls(Context context,boolean leftHanded,Consumer<Float> changed){
        super(context);this.changed=changed;setOrientation(HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        LinearLayout choices=new LinearLayout(context);choices.setOrientation(VERTICAL);choices.setGravity(Gravity.CENTER);
        for(int i=0;i<presets.length;i++){
            int index=i;TextView button=Ui.text(context,format(presets[i]),15,Color.WHITE);button.setGravity(Gravity.CENTER);button.setSingleLine(true);button.setOnClickListener(v->choose(index));buttons.add(button);
            choices.addView(button,new LayoutParams(dp(56),dp(48)));
        }
        LinearLayout track=new LinearLayout(context);track.setOrientation(VERTICAL);track.setGravity(Gravity.CENTER_VERTICAL);track.setPadding(0,0,0,dp(24));
        slider=new RulerSlider(new ContextThemeWrapper(context,com.google.android.material.R.style.Theme_MaterialComponents_Light_NoActionBar));slider.setValueFrom(0);slider.setValueTo(100);slider.setValue(0);slider.setLabelBehavior(LabelFormatter.LABEL_GONE);slider.setTickVisible(false);
        slider.setTrackHeight(dp(2));slider.setTrackActiveTintList(ColorStateList.valueOf(Color.WHITE));slider.setTrackInactiveTintList(ColorStateList.valueOf(0x66FFFFFF));slider.setThumbTintList(ColorStateList.valueOf(Color.WHITE));slider.setThumbRadius(dp(6));slider.setHaloTintList(ColorStateList.valueOf(0x33FFFFFF));slider.setHaloRadius(dp(16));slider.setBackgroundColor(Color.TRANSPARENT);slider.setContentDescription("倍率");slider.setLabelFormatter(position->displayRatio(fromPosition(position)));
        value=Ui.text(context,"1×",14,Color.WHITE);value.setSingleLine(true);value.setGravity(Gravity.CENTER);track.addView(value,new LayoutParams(-1,dp(24)));track.addView(slider,new LayoutParams(-1,dp(48)));
        LayoutParams choiceParams=new LayoutParams(dp(56),dp(144));choiceParams.setMargins(leftHanded?0:dp(8),0,leftHanded?dp(8):0,0);
        if(leftHanded)addView(choices,choiceParams);addView(track,new LayoutParams(0,-1,1));if(!leftHanded)addView(choices,choiceParams);
        slider.addOnChangeListener((view,position,fromUser)->{if(fromUser&&available){ratio=fromPosition(position);refresh(false);changed.accept(ratio);}});
        slider.addOnSliderTouchListener(new Slider.OnSliderTouchListener(){
            @Override public void onStartTrackingTouch(Slider view){dragging=true;}
            @Override public void onStopTrackingTouch(Slider view){dragging=false;if(available)changed.accept(ratio);}
        });refresh(true);
    }
    void update(ZoomState state){minimum=state.getMinZoomRatio();maximum=state.getMaxZoomRatio();if(!dragging){ratio=state.getZoomRatio();refresh(true);}}
    void setAvailable(boolean ready){available=ready;refresh(!dragging);}
    // A wide preset names the lens approximately; selecting it must not digitally crop that lens.
    private float preset(int index){return index==0&&minimum<.85f?minimum:presets[index];}
    private String displayRatio(float zoom){return format(minimum<.85f&&Math.abs(zoom-minimum)<.0001f?Math.round(zoom*10)/10f:zoom);}
    private void choose(int index){float zoom=preset(index);if(!available||zoom<minimum-.001f||zoom>maximum+.001f)return;ratio=Math.max(minimum,Math.min(maximum,zoom));refresh(true);changed.accept(ratio);}
    private void refresh(boolean moveSlider){
        for(int i=0;i<buttons.size();i++){
            float preset=preset(i);TextView button=buttons.get(i);button.setText(displayRatio(preset));boolean supported=preset>=minimum-.001f&&preset<=maximum+.001f,selected=Math.abs(preset-ratio)<.015f;
            button.setEnabled(available&&supported);button.setAlpha(available&&supported?1:.35f);if(button.getBackground()==null||button.isSelected()!=selected){button.setSelected(selected);button.setTextColor(selected?Ui.INK:Color.WHITE);Ui.touch(button,selected?Color.WHITE:0x55000000,22);button.setBackground(new InsetDrawable(button.getBackground(),dp(2)));}
            button.setContentDescription((i==0&&minimum<.85f?"広角、最小倍率":preset==1?"標準 1倍":displayRatio(preset).replace("×","倍"))+(!supported?"、このカメラでは使用できません":""));
        }
        value.setText(displayRatio(ratio));value.setContentDescription("現在の倍率 "+displayRatio(ratio).replace("×","倍"));slider.setStateDescription(displayRatio(ratio).replace("×","倍"));slider.setEnabled(available&&maximum>minimum);slider.setAlpha(available&&maximum>minimum?1:.35f);
        if(moveSlider)slider.setValue(maximum>minimum?Math.max(0,Math.min(100,(float)(Math.log(ratio/minimum)/Math.log(maximum/minimum)*100))):0);
    }
    // Spread low magnifications out so framing near 1× stays easy to adjust.
    private float fromPosition(float position){return maximum>minimum?(float)(minimum*Math.pow(maximum/minimum,position/100f)):minimum;}
    private static String format(float zoom){return String.format(Locale.ROOT,"%.2f",zoom).replaceAll("0+$","").replaceAll("\\.$","")+"×";}
    private int dp(int n){return Ui.dp(getContext(),n);}
    private static final class RulerSlider extends Slider {
        private final Paint marks=new Paint(Paint.ANTI_ALIAS_FLAG);
        RulerSlider(Context context){super(context);}
        @Override protected void onDraw(Canvas canvas){
            marks.setColor(0xAAFFFFFF);marks.setStrokeWidth(Ui.dp(getContext(),1));float start=getTrackSidePadding(),width=getTrackWidth(),y=getHeight()/2f+Ui.dp(getContext(),6);
            for(int i=0;i<=20;i++){float x=start+width*i/20f;canvas.drawLine(x,y,x,y+Ui.dp(getContext(),i%5==0?8:4),marks);}super.onDraw(canvas);
        }
    }
}
