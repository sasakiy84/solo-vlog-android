package com.sasakiy84.solovlog;

import static org.junit.Assert.*;
import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.core.util.Pair;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.material.datepicker.MaterialDatePicker;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class DateRangeTest {
    @Test(timeout=600000) public void combineStartsAtChosenDayAndConfirmationReturnsTheInclusiveRange() throws Exception {
        var instrumentation=InstrumentationRegistry.getInstrumentation();
        var context=instrumentation.getTargetContext();
        MainActivity main=(MainActivity)instrumentation.startActivitySync(new Intent(context,MainActivity.class)
            .putExtra("review_day","2026-11-04").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        var monitor=instrumentation.addMonitor(DateRangeActivity.class.getName(),null,false);
        Activity calendar=null;
        try {
            instrumentation.runOnMainSync(()->assertTrue(find(main.getWindow().getDecorView(),"まとめて").performClick()));
            calendar=monitor.waitForActivityWithTimeout(120000);assertNotNull("Range calendar did not open",calendar);
            DateRangeActivity opened=(DateRangeActivity)calendar;
            AtomicReference<MaterialDatePicker<Pair<Long,Long>>> shown=new AtomicReference<>();
            instrumentation.runOnMainSync(()->{
                opened.getSupportFragmentManager().executePendingTransactions();
                @SuppressWarnings("unchecked") MaterialDatePicker<Pair<Long,Long>> picker=(MaterialDatePicker<Pair<Long,Long>>)opened.getSupportFragmentManager().findFragmentByTag("period");
                assertNotNull(picker);
                assertEquals(Long.valueOf(Instant.parse("2026-11-04T00:00:00Z").toEpochMilli()),picker.getSelection().first);
                assertEquals(Long.valueOf(Instant.parse("2026-11-10T00:00:00Z").toEpochMilli()),picker.getSelection().second);
                shown.set(picker);
            });
            AtomicReference<android.graphics.Rect[]> header=new AtomicReference<>();
            long layoutDeadline=android.os.SystemClock.uptimeMillis()+120000;
            while(header.get()==null&&android.os.SystemClock.uptimeMillis()<layoutDeadline){
                instrumentation.runOnMainSync(()->{
                    View root=shown.get().requireView();
                    android.graphics.Rect title=new android.graphics.Rect(),selection=new android.graphics.Rect();
                    TextView titleLabel=root.findViewById(com.google.android.material.R.id.mtrl_picker_title_text);
                    TextView selectionLabel=root.findViewById(com.google.android.material.R.id.mtrl_picker_header_selection_text);
                    if(titleLabel.getGlobalVisibleRect(title)&&selectionLabel.getGlobalVisibleRect(selection)){
                        title.top+=titleLabel.getPaddingTop();title.bottom-=titleLabel.getPaddingBottom();
                        selection.top+=selectionLabel.getPaddingTop();selection.bottom-=selectionLabel.getPaddingBottom();
                        header.set(new android.graphics.Rect[]{title,selection});
                    }
                });
                if(header.get()==null)Thread.sleep(100);
            }
            assertNotNull("Calendar header was not laid out",header.get());
            assertFalse("Calendar title overlaps the selected dates",android.graphics.Rect.intersects(header.get()[0],header.get()[1]));
            instrumentation.runOnMainSync(()->assertTrue(shown.get().requireView().findViewById(com.google.android.material.R.id.confirm_button).performClick()));
            long deadline=android.os.SystemClock.uptimeMillis()+120000;
            AtomicReference<TextView> range=new AtomicReference<>();
            while(range.get()==null&&android.os.SystemClock.uptimeMillis()<deadline){
                instrumentation.runOnMainSync(()->range.set(find(main.getWindow().getDecorView(),"2026.11.04 – 2026.11.10")));
                if(range.get()==null)Thread.sleep(100);
            }
            assertNotNull("Confirmed calendar period did not reach the clip list",range.get());
        } finally {
            Activity finalCalendar=calendar;
            instrumentation.runOnMainSync(()->{if(finalCalendar!=null&&!finalCalendar.isFinishing())finalCalendar.finish();main.finish();});
            instrumentation.removeMonitor(monitor);
        }
    }
    private static TextView find(View view,String text){
        if(view instanceof TextView label&&label.getText().toString().equals(text))return label;
        if(view instanceof ViewGroup group)for(int n=0;n<group.getChildCount();n++){
            TextView result=find(group.getChildAt(n),text);if(result!=null)return result;
        }
        return null;
    }
}
