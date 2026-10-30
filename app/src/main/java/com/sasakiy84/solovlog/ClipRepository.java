package com.sasakiy84.solovlog;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** SQLite commits metadata atomically; the original clips are never automatically expired. */
public final class ClipRepository extends SQLiteOpenHelper {
    private static ClipRepository instance;
    private final File directory;

    public static synchronized ClipRepository get(Context context) {
        if (instance == null) instance = new ClipRepository(context.getApplicationContext());
        return instance;
    }

    private ClipRepository(Context context) {
        super(context, "daylog.db", null, 1);
        directory = new File(context.getFilesDir(), "clips");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("クリップの保存先を作れませんでした");
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE clips (id TEXT PRIMARY KEY, captured_at INTEGER NOT NULL, duration_ms INTEGER NOT NULL, seconds INTEGER NOT NULL, landscape INTEGER NOT NULL, included INTEGER NOT NULL, zone_id TEXT NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("Unsupported database upgrade");
    }
    public File file(String id) {
        if (!id.matches("[a-f0-9-]{36}")) throw new IllegalArgumentException("Invalid clip identifier");
        return new File(directory, id + ".mp4");
    }
    public void add(Clip clip) {
        ContentValues values = new ContentValues();
        values.put("id", clip.id()); values.put("captured_at", clip.capturedAt());
        values.put("duration_ms", clip.durationMs()); values.put("seconds", clip.plannedSeconds());
        values.put("landscape", clip.landscape() ? 1 : 0); values.put("included", clip.included() ? 1 : 0);
        values.put("zone_id", clip.zoneId());
        getWritableDatabase().insertOrThrow("clips", null, values);
    }
    public List<Clip> all() {
        List<Clip> clips = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("clips", null, null, null, null, null, "captured_at ASC")) {
            while (c.moveToNext()) clips.add(new Clip(c.getString(0), c.getLong(1), c.getLong(2), c.getInt(3), c.getInt(4) != 0, c.getInt(5) != 0, c.getString(6)));
        }
        return clips;
    }
    public void include(String id, boolean included) {
        ContentValues values = new ContentValues(); values.put("included", included ? 1 : 0);
        getWritableDatabase().update("clips", values, "id=?", new String[]{id});
    }
    public void delete(String id) {
        File target = file(id);
        if (target.exists() && !target.delete()) throw new IllegalStateException("動画を削除できませんでした");
        getWritableDatabase().delete("clips", "id=?", new String[]{id});
    }
    public long bytes() { return all().stream().mapToLong(c -> file(c.id()).length()).sum(); }
}
