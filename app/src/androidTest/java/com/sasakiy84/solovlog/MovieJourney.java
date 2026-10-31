package com.sasakiy84.solovlog;

import static com.google.common.truth.Truth.assertThat;
import android.content.Context;
import android.database.Cursor;
import android.os.SystemClock;
import android.provider.MediaStore;
import androidx.media3.common.util.UnstableApi;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

@UnstableApi
final class MovieJourney {
    private MovieJourney(){}
    private static long count(Context context){try(Cursor cursor=context.getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,new String[]{"_id"},MediaStore.Video.Media.RELATIVE_PATH+"=?",new String[]{"Movies/Daylog/"},null)){assertThat(cursor).isNotNull();return cursor.getCount();}}
    private static UiObject2 find(UiDevice device,String text){UiObject2 object=device.wait(Until.findObject(By.text(text)),20000);assertThat(object).isNotNull();return object;}
    static void renderAndSave(Context context,UiDevice device,boolean backgroundAndAdjust,String label)throws Exception{
        long before=count(context);find(device,"レンダリング");
        DeviceImages.screenshot(context,device,"14-"+label+"-adjust");
        var config=androidx.test.uiautomator.Configurator.getInstance();long idle=config.getWaitForIdleTimeout();
        if(backgroundAndAdjust)config.setWaitForIdleTimeout(0);
        try{find(device,"レンダリング").click();if(backgroundAndAdjust){UiObject2 back=device.wait(Until.findObject(By.desc("一覧に戻る")),10000);assertThat(back).isNotNull();back.click();}}finally{config.setWaitForIdleTimeout(idle);}
        if(backgroundAndAdjust){
            // The user can leave while the foreground processing service owns the render.
            find(device,"日々の記録");
            File[] metadata=new File(context.getFilesDir(),"rendered").listFiles((dir,name)->name.endsWith(".json"));assertThat(metadata).isNotNull();
            File latest=Arrays.stream(metadata).max(Comparator.comparingLong(File::lastModified)).orElseThrow();String id=latest.getName().replace(".json","");
            long deadline=SystemClock.elapsedRealtime()+180000;MovieDraft draft=MovieDraft.load(context,id);
            while("rendering".equals(draft.status)&&SystemClock.elapsedRealtime()<deadline){SystemClock.sleep(250);draft=MovieDraft.load(context,id);}
            assertThat(draft.status).isEqualTo("ready");assertThat(draft.movie(context).isFile()).isTrue();assertThat(count(context)).isEqualTo(before);
            find(device,"レンダリング").click();
        }
        assertThat(device.wait(Until.hasObject(By.text("レンダリング完了")),180000)).isTrue();assertThat(count(context)).isEqualTo(before);
        DeviceImages.screenshot(context,device,"15-"+label+"-preview");
        if(backgroundAndAdjust){
            UiObject2 toggle=device.wait(Until.findObject(By.desc("撮影時刻を表示")),10000);assertThat(toggle).isNotNull();toggle.click();
            assertThat(device.wait(Until.gone(By.text("保存")),5000)).isTrue();toggle=device.wait(Until.findObject(By.desc("撮影時刻を表示")),5000);assertThat(toggle).isNotNull();toggle.click();find(device,"レンダリング").click();
            assertThat(device.wait(Until.hasObject(By.text("レンダリング完了")),180000)).isTrue();assertThat(count(context)).isEqualTo(before);
        }
        find(device,"保存").click();assertThat(device.wait(Until.hasObject(By.text("保存しました")),60000)).isTrue();assertThat(count(context)).isEqualTo(before+1);
        assertThat(find(device,"保存済み").isEnabled()).isFalse();DeviceImages.screenshot(context,device,"16-"+label+"-saved");
    }
}
