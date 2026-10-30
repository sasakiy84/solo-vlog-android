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
        super(context, "daylog.db", null, 3);
        directory = new File(context.getFilesDir(), "clips");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("クリップの保存先を作れませんでした");
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE clips (id TEXT PRIMARY KEY, captured_at INTEGER NOT NULL, duration_ms INTEGER NOT NULL, seconds INTEGER NOT NULL, landscape INTEGER NOT NULL, included INTEGER NOT NULL, zone_id TEXT NOT NULL, caption TEXT NOT NULL DEFAULT '', latitude REAL, longitude REAL, accuracy REAL, place TEXT NOT NULL DEFAULT '', location_names TEXT NOT NULL DEFAULT '')");
        createDayCaptions(db);
        createPlaceHistory(db);
    }
    private void createDayCaptions(SQLiteDatabase db){db.execSQL("CREATE TABLE day_captions (day TEXT PRIMARY KEY, caption TEXT NOT NULL)");}
    private void createPlaceHistory(SQLiteDatabase db){db.execSQL("CREATE TABLE place_history (name TEXT NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, used_at INTEGER NOT NULL, PRIMARY KEY(name,latitude,longitude))");}
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if(oldVersion<1||newVersion!=3)throw new IllegalStateException("Unsupported database upgrade");
        if(oldVersion<2){db.execSQL("ALTER TABLE clips ADD COLUMN caption TEXT NOT NULL DEFAULT ''");createDayCaptions(db);}
        if(oldVersion<3){
            db.execSQL("ALTER TABLE clips ADD COLUMN latitude REAL");db.execSQL("ALTER TABLE clips ADD COLUMN longitude REAL");db.execSQL("ALTER TABLE clips ADD COLUMN accuracy REAL");
            db.execSQL("ALTER TABLE clips ADD COLUMN place TEXT NOT NULL DEFAULT ''");db.execSQL("ALTER TABLE clips ADD COLUMN location_names TEXT NOT NULL DEFAULT ''");
            createPlaceHistory(db);
        }
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
        values.put("zone_id", clip.zoneId());values.put("caption",clip.caption());values.put("place",clip.place());
        if(clip.location()!=null){values.put("latitude",clip.location().latitude());values.put("longitude",clip.location().longitude());values.put("accuracy",clip.location().accuracyMeters());}
        getWritableDatabase().insertOrThrow("clips", null, values);
    }
    public List<Clip> all() {
        List<Clip> clips = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("clips", null, null, null, null, null, "captured_at ASC")) {
            while (c.moveToNext()) clips.add(new Clip(c.getString(0), c.getLong(1), c.getLong(2), c.getInt(3), c.getInt(4) != 0, c.getInt(5) != 0, c.getString(6), c.getString(7),c.isNull(8)||c.isNull(9)?null:new GeoPoint(c.getDouble(8),c.getDouble(9),c.isNull(10)?0:c.getFloat(10)),c.getString(11)));
        }
        return clips;
    }
    public void caption(String id,String caption){
        ContentValues values=new ContentValues();values.put("caption",caption.strip());
        getWritableDatabase().update("clips",values,"id=?",new String[]{id});
    }
    public void place(String id,String place){
        ContentValues values=new ContentValues();values.put("place",place.strip());getWritableDatabase().update("clips",values,"id=?",new String[]{id});
    }
    public synchronized void location(String id,GeoPoint point) throws Exception{
        try(Cursor c=getReadableDatabase().query("clips",new String[]{"id"},"id=?",new String[]{id},null,null,null)){if(!c.moveToFirst())return;}
        ClipGeotagWriter.write(file(id),point);
        ContentValues values=new ContentValues();values.put("latitude",point.latitude());values.put("longitude",point.longitude());values.put("accuracy",point.accuracyMeters());
        getWritableDatabase().update("clips",values,"id=?",new String[]{id});
    }
    public java.util.List<String> placeCandidates(String id){
        try(Cursor c=getReadableDatabase().query("clips",new String[]{"location_names"},"id=?",new String[]{id},null,null,null)){
            if(!c.moveToFirst()||c.getString(0).isEmpty())return java.util.List.of();
            org.json.JSONArray data=new org.json.JSONArray(c.getString(0));java.util.List<String> names=new java.util.ArrayList<>();
            for(int i=0;i<data.length();i++)names.add(data.getString(i));return names;
        }catch(org.json.JSONException e){android.util.Log.w("DaylogPlaces","Invalid cached place names",e);return java.util.List.of();}
    }
    public void placeCandidates(String id,java.util.List<String> names){
        ContentValues values=new ContentValues();values.put("location_names",new org.json.JSONArray(names).toString());getWritableDatabase().update("clips",values,"id=?",new String[]{id});
    }
    public void rememberPlace(GeoPoint point,String name){
        if(point==null||name.isBlank()||name.equals(point.label()))return;
        ContentValues values=new ContentValues();values.put("name",name);values.put("latitude",point.latitude());values.put("longitude",point.longitude());values.put("used_at",System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("place_history",null,values,SQLiteDatabase.CONFLICT_REPLACE);
    }
    public java.util.List<String> nearbyPlaces(GeoPoint point){
        if(point==null)return java.util.List.of();double radius=Math.max(300,Math.min(5000,point.accuracyMeters()*2));java.util.LinkedHashSet<String> names=new java.util.LinkedHashSet<>();
        try(Cursor c=getReadableDatabase().query("place_history",new String[]{"name","latitude","longitude"},null,null,null,null,"used_at DESC","50")){
            while(c.moveToNext()&&names.size()<5)if(point.distanceMeters(new GeoPoint(c.getDouble(1),c.getDouble(2),0))<=radius)names.add(c.getString(0));
        }
        return java.util.List.copyOf(names);
    }
    public String dayCaption(String day){
        try(Cursor c=getReadableDatabase().query("day_captions",new String[]{"caption"},"day=?",new String[]{day},null,null,null)){
            return c.moveToFirst()?c.getString(0):"";
        }
    }
    public void dayCaption(String day,String caption){
        ContentValues values=new ContentValues();values.put("day",day);values.put("caption",caption.strip());
        getWritableDatabase().insertWithOnConflict("day_captions",null,values,SQLiteDatabase.CONFLICT_REPLACE);
    }
    public void include(String id, boolean included) {
        ContentValues values = new ContentValues(); values.put("included", included ? 1 : 0);
        getWritableDatabase().update("clips", values, "id=?", new String[]{id});
    }
    public synchronized void delete(String id) {
        File target = file(id);
        if (target.exists() && !target.delete()) throw new IllegalStateException("動画を削除できませんでした");
        getWritableDatabase().delete("clips", "id=?", new String[]{id});
    }
    public long bytes() { return all().stream().mapToLong(c -> file(c.id()).length()).sum(); }
}
