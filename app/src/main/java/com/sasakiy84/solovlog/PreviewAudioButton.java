package com.sasakiy84.solovlog;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import java.util.function.Consumer;

/** Preview sound is opt-in per screen and never changes the exported audio track. */
// Android 16 platform theme and explicit drawable tinting; no AppCompat theme or XML inflation.
@android.annotation.SuppressLint("AppCompatCustomView")
final class PreviewAudioButton extends ImageView {
    private boolean audible;
    PreviewAudioButton(Context context,Consumer<Boolean> changed){
        super(context);setScaleType(ScaleType.CENTER_INSIDE);int padding=Ui.dp(context,14);setPadding(padding,padding,padding,padding);setFocusable(true);
        setBackground(new RippleDrawable(ColorStateList.valueOf(0x44FFFFFF),new InsetDrawable(Ui.rounded(context,0x66000000,16),Ui.dp(context,8)),Ui.rounded(context,Color.WHITE,24)));
        setOnClickListener(v->{audible=!audible;updateIcon();changed.accept(audible);});updateIcon();
    }
    boolean isAudible(){return audible;}
    void attachTo(FrameLayout frame){FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(Ui.dp(getContext(),48),Ui.dp(getContext(),48),Gravity.TOP|Gravity.END);params.setMargins(Ui.dp(getContext(),4),Ui.dp(getContext(),4),Ui.dp(getContext(),4),Ui.dp(getContext(),4));frame.addView(this,params);}
    private void updateIcon(){setImageDrawable(Ui.drawable(getContext(),audible?"volume_on":"volume_off",Color.WHITE));setContentDescription(audible?"音声をオフにする":"音声をオンにする");setSelected(audible);}
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName("android.widget.ToggleButton");info.setCheckable(true);info.setChecked(audible);}
}
