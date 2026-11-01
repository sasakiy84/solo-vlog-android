package com.sasakiy84.solovlog;

import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Keep all primary choices visible without horizontal scrolling or moving a pressed chip. */
final class PlaceChoicesView extends LinearLayout {
    private Runnable deferred;
    private boolean touching;
    PlaceChoicesView(Context context){super(context);setOrientation(VERTICAL);}
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN)touching=true;
        boolean handled=super.dispatchTouchEvent(event);
        if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL){touching=false;if(deferred!=null){Runnable update=deferred;deferred=null;post(update);}}return handled;
    }
    void show(PlaceSuggestions suggestions,String selected,Consumer<String> choose,Runnable other){
        if(touching){deferred=()->show(suggestions,selected,choose,other);return;}
        removeAllViews();List<String> names=new ArrayList<>(suggestions.primary());names.add("その他");
        for(int i=0;i<names.size();i+=2){LinearLayout row=new LinearLayout(getContext());
            for(int j=i;j<Math.min(i+2,names.size());j++){
                String name=names.get(j);boolean isOther=j==names.size()-1,checked=isOther?!selected.isBlank()&&!suggestions.primary().contains(selected):selected.equals(name);
                TextView chip=Ui.text(getContext(),name,15,checked?Ui.ACCENT_DARK:Ui.INK);chip.setGravity(Gravity.CENTER_VERTICAL);chip.setSingleLine(true);chip.setEllipsize(android.text.TextUtils.TruncateAt.END);chip.setPadding(dp(10),0,dp(10),0);Ui.touch(chip,checked?Ui.ACCENT_SOFT:Ui.CARD,10);
                chip.setCompoundDrawablesRelative(Ui.drawable(getContext(),isOther?"edit":"pin",checked?Ui.ACCENT_DARK:Ui.INK),null,null,null);chip.setCompoundDrawablePadding(dp(6));chip.setSelected(checked);chip.setContentDescription(isOther?checked?"その他の場所、選択中 "+selected:"その他の場所":(checked?"場所の表示を外す ":"場所を表示 ")+name);chip.setOnClickListener(v->{if(isOther)other.run();else choose.accept(name);});
                LayoutParams params=new LayoutParams(0,dp(48),1);if(j>i)params.leftMargin=dp(8);row.addView(chip,params);
            }
            LayoutParams params=new LayoutParams(-1,-2);if(i>0)params.topMargin=dp(8);addView(row,params);
        }
    }
    private int dp(int n){return Ui.dp(getContext(),n);}
}
