package com.sasakiy84.solovlog;

import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Each test owns a private copy, including tests that atomically replace the input MP4. */
final class TestVideos {
    private TestVideos(){}
    static File copyToCache(String asset)throws Exception{
        var instrumentation=InstrumentationRegistry.getInstrumentation();
        File copy=File.createTempFile("fixture-",".mp4",instrumentation.getTargetContext().getCacheDir());
        try(InputStream input=instrumentation.getContext().getAssets().open(asset)){
            Files.copy(input,copy.toPath(),StandardCopyOption.REPLACE_EXISTING);
        }catch(Exception e){copy.delete();throw e;}
        return copy;
    }
}
