package com.sasakiy84.solovlog;

import android.content.Intent;
import android.os.Bundle;
import androidx.core.util.Pair;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.MaterialDatePicker;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/** Material's range calendar stores dates at UTC midnight, independent of capture timezone. */
public final class DateRangeActivity extends AppCompatActivity {
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        @SuppressWarnings("unchecked")
        MaterialDatePicker<Pair<Long,Long>> picker=(MaterialDatePicker<Pair<Long,Long>>)getSupportFragmentManager().findFragmentByTag("period");
        if(picker==null){
            LocalDate start=LocalDate.parse(getIntent().getStringExtra("start"));
            LocalDate end=LocalDate.parse(getIntent().getStringExtra("end"));
            long first=start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
            long last=end.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
            picker=MaterialDatePicker.Builder.dateRangePicker().setTitleText("期間を選択")
                .setSelection(Pair.create(first,last)).setPositiveButtonText("選択").setNegativeButtonText("キャンセル")
                .setCalendarConstraints(new CalendarConstraints.Builder().setOpenAt(first).build())
                .setTheme(R.style.ThemeOverlay_Flint_Calendar).build();
            picker.show(getSupportFragmentManager(),"period");
        }
        picker.addOnPositiveButtonClickListener(range->{
            String start=Instant.ofEpochMilli(range.first).atZone(ZoneOffset.UTC).toLocalDate().toString();
            String end=Instant.ofEpochMilli(range.second).atZone(ZoneOffset.UTC).toLocalDate().toString();
            setResult(RESULT_OK,new Intent().putExtra("start",start).putExtra("end",end));finish();
        });
        picker.addOnNegativeButtonClickListener(view->finish());
        picker.addOnCancelListener(dialog->finish());
    }
}
