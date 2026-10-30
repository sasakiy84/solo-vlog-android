package com.sasakiy84.solovlog;

import static com.google.common.truth.Truth.assertThat;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class RepositoryMigrationTest {
    @Test public void locationUpgradePreservesExistingClipAndDayCaptions(){
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();String name="migration-"+UUID.randomUUID()+".db";
        try{
            try(SQLiteOpenHelper legacy=new SQLiteOpenHelper(context,name,null,2){
                @Override public void onCreate(SQLiteDatabase db){
                    db.execSQL("CREATE TABLE clips (id TEXT PRIMARY KEY, captured_at INTEGER NOT NULL, duration_ms INTEGER NOT NULL, seconds INTEGER NOT NULL, landscape INTEGER NOT NULL, included INTEGER NOT NULL, zone_id TEXT NOT NULL, caption TEXT NOT NULL DEFAULT '')");
                    db.execSQL("CREATE TABLE day_captions (day TEXT PRIMARY KEY, caption TEXT NOT NULL)");
                }
                @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){throw new AssertionError();}
            }){SQLiteDatabase db=legacy.getWritableDatabase();db.execSQL("INSERT INTO clips VALUES ('original',123456789,3000,3,1,0,'Asia/Tokyo','朝ごはん')");db.execSQL("INSERT INTO day_captions VALUES ('2026-10-30','京都旅行')");}
            try(SQLiteOpenHelper upgraded=new SQLiteOpenHelper(context,name,null,3){
                @Override public void onCreate(SQLiteDatabase db){throw new AssertionError();}
                @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){ClipRepository.get(context).onUpgrade(db,oldVersion,newVersion);}
            }){
                SQLiteDatabase db=upgraded.getReadableDatabase();
                try(Cursor row=db.rawQuery("SELECT caption,latitude,longitude,place FROM clips",null)){
                    assertThat(row.moveToFirst()).isTrue();assertThat(row.getString(0)).isEqualTo("朝ごはん");assertThat(row.isNull(1)).isTrue();assertThat(row.isNull(2)).isTrue();assertThat(row.getString(3)).isEmpty();
                }
                try(Cursor row=db.rawQuery("SELECT caption FROM day_captions",null)){assertThat(row.moveToFirst()).isTrue();assertThat(row.getString(0)).isEqualTo("京都旅行");}
                try(Cursor row=db.rawQuery("SELECT name FROM place_history",null)){assertThat(row.getCount()).isEqualTo(0);}
            }
        }finally{context.deleteDatabase(name);}
    }
    @Test public void captionUpgradePreservesLegacyMetadata() {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name="migration-"+UUID.randomUUID()+".db";
        try{
            try(SQLiteOpenHelper legacy=new SQLiteOpenHelper(context,name,null,1){
                @Override public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE clips (id TEXT PRIMARY KEY, captured_at INTEGER NOT NULL, duration_ms INTEGER NOT NULL, seconds INTEGER NOT NULL, landscape INTEGER NOT NULL, included INTEGER NOT NULL, zone_id TEXT NOT NULL)");}
                @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){throw new AssertionError();}
            }){legacy.getWritableDatabase().execSQL("INSERT INTO clips VALUES ('original',123456789,3000,3,1,0,'Asia/Tokyo')");}
            try(SQLiteOpenHelper upgraded=new SQLiteOpenHelper(context,name,null,3){
                @Override public void onCreate(SQLiteDatabase db){throw new AssertionError();}
                @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){ClipRepository.get(context).onUpgrade(db,oldVersion,newVersion);}
            }){
                SQLiteDatabase db=upgraded.getReadableDatabase();
                try(Cursor row=db.rawQuery("SELECT id,captured_at,duration_ms,landscape,included,zone_id,caption,latitude,longitude,place FROM clips",null)){
                    assertThat(row.getCount()).isEqualTo(1);assertThat(row.moveToFirst()).isTrue();
                    assertThat(row.getString(0)).isEqualTo("original");assertThat(row.getLong(1)).isEqualTo(123456789L);
                    assertThat(row.getLong(2)).isEqualTo(3000);assertThat(row.getInt(3)).isEqualTo(1);assertThat(row.getInt(4)).isEqualTo(0);
                    assertThat(row.getString(5)).isEqualTo("Asia/Tokyo");assertThat(row.getString(6)).isEmpty();assertThat(row.isNull(7)).isTrue();assertThat(row.isNull(8)).isTrue();assertThat(row.getString(9)).isEmpty();
                }
                try(Cursor days=db.rawQuery("SELECT caption FROM day_captions",null)){assertThat(days.getCount()).isEqualTo(0);}
            }
        }finally{context.deleteDatabase(name);}
    }
}
