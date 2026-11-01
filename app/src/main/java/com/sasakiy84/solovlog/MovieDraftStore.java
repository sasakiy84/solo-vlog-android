package com.sasakiy84.solovlog;

import android.content.Context;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.json.JSONArray;
import org.json.JSONObject;

/** Private draft files and their atomic metadata; never publishes to the shared gallery. */
final class MovieDraftStore {
    private final File directory;

    MovieDraftStore(Context context) {
        this(new File(context.getFilesDir(), "rendered"));
    }

    MovieDraftStore(File directory) {
        this.directory = directory;
    }

    private File directory() {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IllegalStateException("Cannot create render directory");
        }
        return directory;
    }

    File movie(MovieDraft draft) {
        return new File(directory(), draft.id + ".mp4");
    }

    private AtomicFile metadata(MovieDraft draft) {
        return new AtomicFile(new File(directory(), draft.id + ".json"));
    }

    MovieDraft load(String id) throws Exception {
        MovieDraft draft = new MovieDraft(id);
        JSONObject data = new JSONObject(new String(metadata(draft).readFully(), StandardCharsets.UTF_8));
        draft.outputResolution = data.getString("outputResolution");
        draft.fileName = data.getString("fileName");
        draft.sourceSignature = data.optString("sourceSignature");
        draft.day = data.getString("day");
        draft.caption = data.optString("caption");
        draft.status = data.optString("status", "editing");
        draft.error = data.optString("error");
        draft.savedUri = data.optString("savedUri");
        draft.showDate = data.getBoolean("showDate");
        draft.showTime = data.optBoolean("showTime", true);
        draft.showText = data.optBoolean("showText", true);
        draft.showPlace = data.optBoolean("showPlace", true);
        draft.audio = data.optBoolean("audio", true);
        draft.compact = data.optBoolean("compact", false);
        draft.embedLocation = data.optBoolean("embedLocation", true);
        JSONArray ids = data.getJSONArray("clips");
        for (int n = 0; n < ids.length(); n++) {
            String clip = ids.getString(n);
            if (!clip.matches("[a-f0-9-]{36}")) throw new IllegalArgumentException("Invalid clip identifier");
            draft.clipIds.add(clip);
        }
        return draft;
    }

    void save(MovieDraft draft) throws Exception {
        JSONObject data = new JSONObject()
            .put("outputResolution", draft.outputResolution)
            .put("fileName", draft.fileName)
            .put("sourceSignature", draft.sourceSignature)
            .put("day", draft.day)
            .put("caption", draft.caption)
            .put("status", draft.status)
            .put("error", draft.error)
            .put("savedUri", draft.savedUri)
            .put("showDate", draft.showDate)
            .put("showTime", draft.showTime)
            .put("showText", draft.showText)
            .put("showPlace", draft.showPlace)
            .put("audio", draft.audio)
            .put("clips", new JSONArray(draft.clipIds))
            .put("compact", draft.compact)
            .put("embedLocation", draft.embedLocation);
        AtomicFile target = metadata(draft);
        FileOutputStream stream = null;
        try {
            stream = target.startWrite();
            stream.write(data.toString().getBytes(StandardCharsets.UTF_8));
            target.finishWrite(stream);
        } catch (Exception e) {
            if (stream != null) target.failWrite(stream);
            throw e;
        }
    }

    long bytes() {
        File[] files = directory().listFiles();
        if (files == null) return 0;
        long total = 0;
        for (File file : files) if (file.isFile()) total += file.length();
        return total;
    }

    void deleteBefore(LocalDate cutoff) {
        File[] files = directory().listFiles((dir, name) -> name.matches("[a-f0-9-]{36}\\.json"));
        if (files == null) throw new IllegalStateException("レンダリング結果を確認できませんでした");
        for (File file : files) {
            LocalDate day;
            try {
                JSONObject data = new JSONObject(new String(new AtomicFile(file).readFully(), StandardCharsets.UTF_8));
                day = LocalDate.parse(data.getString("day"));
            } catch (Exception e) {
                android.util.Log.w("DaylogDraft", "Cannot read old render date", e);
                continue;
            }
            if (!day.isBefore(cutoff)) continue;
            File movie = new File(file.getParentFile(), file.getName().replace(".json", ".mp4"));
            if (movie.exists() && !movie.delete()) throw new IllegalStateException("レンダリング結果を削除できませんでした");
            new AtomicFile(file).delete();
            if (file.exists()) throw new IllegalStateException("レンダリング設定を削除できませんでした");
        }
    }
}
