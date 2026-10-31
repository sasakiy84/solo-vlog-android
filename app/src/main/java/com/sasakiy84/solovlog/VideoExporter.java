package com.sasakiy84.solovlog;

import android.content.ContentValues;
import android.media.MediaMetadataRetriever;
import android.media.metrics.LogSessionId;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Effect;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Clock;
import androidx.media3.effect.Presentation;
import androidx.media3.transformer.DefaultAssetLoaderFactory;
import androidx.media3.transformer.DefaultDecoderFactory;
import androidx.media3.transformer.DefaultEncoderFactory;
import androidx.media3.transformer.VideoEncoderSettings;
import androidx.media3.transformer.AudioEncoderSettings;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.EditedMediaItemSequence;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.Effects;
import androidx.media3.effect.DefaultVideoFrameProcessor;
import androidx.media3.transformer.InAppMp4Muxer;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.Transformer;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@UnstableApi
public final class VideoExporter {
    public interface Listener { void saved(Uri uri); void error(String message); }
    public interface RenderListener { void rendered(File file); void error(String message); }
    private final Context context;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private Transformer transformer;
    private boolean preparing;
    private volatile int renderGeneration;
    private File currentOutput;
    private static final Object GALLERY_SAVE_LOCK=new Object();
    private static final java.util.Set<String> SAVING=java.util.concurrent.ConcurrentHashMap.newKeySet();
    static boolean isSaving(File file){return SAVING.contains(file.getAbsolutePath());}
    public VideoExporter(Context context) { this.context = context.getApplicationContext(); }

    public void render(MovieDraft draft,ClipRepository repository,RenderListener listener){
        List<Clip> clips=draft.clips(repository),metadataClips=draft.sourceClips(repository);
        String dayCaption=draft.caption;boolean showTime=draft.showTime,showDate=draft.showDate,includeAudio=draft.audio;
        if(clips.isEmpty()){listener.error("保存するクリップを選んでください。");return;}
        if(transformer!=null||preparing){listener.error("現在の処理が終わるまでお待ちください。");return;}
        preparing=true;int generation=++renderGeneration;
        // Decode color samples before Transformer reserves its decoder and GL context.
        io.execute(()->{
            try{
                List<EditedMediaItem> items=new ArrayList<>();boolean highResolution=false;
                for(Clip clip:clips){
                    if(generation!=renderGeneration)return;
                    File source=repository.file(clip.id());if(!source.isFile())throw new java.io.FileNotFoundException("Missing source clip");
                    int width,height;
                    try(MediaMetadataRetriever media=new MediaMetadataRetriever()){
                        media.setDataSource(source.getAbsolutePath());width=Integer.parseInt(media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));height=Integer.parseInt(media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));highResolution|=Math.max(width,height)>1920;
                    }
                    MediaItem item=new MediaItem.Builder().setUri(Uri.fromFile(source))
                        .setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder().setStartPositionMs(clip.trimStartMs()).setEndPositionMs(clip.trimEndMs()).build()).build();
                    EditedMediaItem.Builder edited=new EditedMediaItem.Builder(item).setRemoveAudio(!includeAudio||!clip.renderAudio());
                    List<Effect> effects=new ArrayList<>();
                    if(draft.compact){
                        // Never enlarge 720p originals just to produce a nominal 1080p file.
                        if(Math.max(width,height)>1920)effects.add(Presentation.createForWidthAndHeight(clip.landscape()?1920:1080,clip.landscape()?1080:1920,Presentation.LAYOUT_SCALE_TO_FIT));
                        edited.setFrameRate(30);
                    }
                    if(!dayCaption.isBlank()||showTime||showDate||!clip.caption().isBlank()||!clip.place().isBlank())effects.add(CaptionOverlay.prepare(context,source,clip,dayCaption,showTime,showDate));
                    // Explicit encoder bitrate settings force encoding even without visible effects.
                    edited.setEffects(new Effects(List.of(),effects));
                    items.add(edited.build());
                }
                boolean large=highResolution;main.post(()->{if(generation!=renderGeneration)return;preparing=false;startRender(items,metadataClips,draft,large,draft.compact, draft.compact&&hasHevcEncoder(),listener);});
            }catch(Exception e){Log.e("DaylogExport","Could not prepare clips",e);main.post(()->{if(generation!=renderGeneration)return;preparing=false;listener.error("元の動画を読み込めませんでした。元動画は残っています。");});}
        });
    }
    private static boolean hasHevcEncoder(){
        try{for(var codec:new android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS).getCodecInfos())if(codec.isEncoder())for(String type:codec.getSupportedTypes())if(type.equalsIgnoreCase(MimeTypes.VIDEO_H265))return true;}
        catch(RuntimeException e){Log.w("DaylogExport","HEVC capabilities unavailable",e);}
        return false;
    }
    private void startRender(List<EditedMediaItem> items,List<Clip> clips,MovieDraft draft,boolean highResolution,boolean reducedResolution,boolean hevc,RenderListener listener){
        EditedMediaItemSequence sequence = new EditedMediaItemSequence.Builder(items).experimentalSetForceAudioTrack(true).build();
        Composition composition = new Composition.Builder(sequence).build();
        File output;
        try { output = File.createTempFile("daylog-export-", ".mp4", context.getCacheDir()); output.delete(); }
        catch (Exception e) { listener.error("書き出し用の空き容量を確保できませんでした。"); return; }
        currentOutput=output;
        DefaultEncoderFactory.Builder encoder=new DefaultEncoderFactory.Builder(context).setEnableFallback(true);
        if(draft.compact)encoder.setRequestedVideoEncoderSettings(new VideoEncoderSettings.Builder().setBitrate(hevc?4_000_000:6_000_000).setBitrateMode(android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR).build())
            .setRequestedAudioEncoderSettings(new AudioEncoderSettings.Builder().setBitrate(96_000).build());
        transformer = new Transformer.Builder(context).setVideoMimeType(hevc?MimeTypes.VIDEO_H265:MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(encoder.build())
            .setAssetLoaderFactory(new DefaultAssetLoaderFactory(
                context,new DefaultDecoderFactory.Builder(context).setEnableDecoderFallback(true).build(),
                Clock.DEFAULT,LogSessionId.LOG_SESSION_ID_NONE))
            .setVideoFrameProcessorFactory(new DefaultVideoFrameProcessor.Factory.Builder().setSdrWorkingColorSpace(DefaultVideoFrameProcessor.WORKING_COLOR_SPACE_DEFAULT).build())
            .setMuxerFactory(new InAppMp4Muxer.Factory(entries->LocationMetadata.apply(entries,clips,draft.caption,draft.embedLocation)))
            .addListener(new Transformer.Listener() {
                @Override public void onCompleted(Composition result, ExportResult exportResult) {
                    transformer = null;
                    currentOutput=null;listener.rendered(output);
                }
                @Override public void onError(Composition result, ExportResult exportResult, ExportException exception) {
                    Log.e("DaylogExport", "Video export failed: "+exception.getErrorCodeName(), exception);
                    transformer = null;currentOutput=null;output.delete();
                    int code=exception.errorCode;
                    boolean videoFailure=switch(code){
                        case ExportException.ERROR_CODE_ENCODER_INIT_FAILED,
                             ExportException.ERROR_CODE_ENCODING_FAILED,
                             ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED,
                             ExportException.ERROR_CODE_DECODER_INIT_FAILED,
                             ExportException.ERROR_CODE_DECODING_FAILED,
                             ExportException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED,
                             ExportException.ERROR_CODE_MUXING_TIMEOUT -> true;
                        default -> false;
                    };
                    if(hevc&&videoFailure){int generation=renderGeneration;main.post(()->{if(generation==renderGeneration)startRender(items,clips,draft,highResolution,reducedResolution,false,listener);});return;}
                    if(highResolution&&!reducedResolution&&videoFailure){
                        List<EditedMediaItem> resized=new ArrayList<>();
                        for(int i=0;i<items.size();i++){
                            EditedMediaItem item=items.get(i);boolean wide=clips.get(i).landscape();List<Effect> effects=new ArrayList<>();
                            effects.add(Presentation.createForWidthAndHeight(wide?1920:1080,wide?1080:1920,Presentation.LAYOUT_SCALE_TO_FIT));effects.addAll(item.effects.videoEffects);
                            resized.add(item.buildUpon().setEffects(new Effects(item.effects.audioProcessors,effects)).build());
                        }
                        int generation=renderGeneration;main.post(()->{if(generation==renderGeneration)startRender(resized,clips,draft,true,true,false,listener);});return;
                    }
                    listener.error("動画をまとめられませんでした。元のクリップは残っています。");
                }
            }).build();
        try { transformer.start(composition, output.getAbsolutePath()); }
        catch (RuntimeException e) { transformer = null; currentOutput=null;output.delete(); listener.error("書き出しを始められませんでした。元のクリップは残っています。"); }
    }

    public void saveRendered(File file,String name,Listener listener){
        String path=file.getAbsolutePath();if(!SAVING.add(path))return;
        io.execute(()->{try{Uri uri; synchronized(GALLERY_SAVE_LOCK){uri=copyToGallery(file,fileName(name));}main.post(()->{try{listener.saved(uri);}finally{SAVING.remove(path);}});}catch(Exception e){Log.e("DaylogExport","Gallery save failed",e);main.post(()->{try{listener.error("保存できませんでした。レンダリング済み動画は残っています。");}finally{SAVING.remove(path);}});}});
    }
    public int progress(){if(preparing)return -1;if(transformer==null)return 0;androidx.media3.transformer.ProgressHolder holder=new androidx.media3.transformer.ProgressHolder();return transformer.getProgress(holder)==Transformer.PROGRESS_STATE_AVAILABLE?holder.progress:-1;}
    public void cancel(){renderGeneration++;preparing=false;if(transformer!=null){transformer.cancel();transformer=null;}if(currentOutput!=null){currentOutput.delete();currentOutput=null;}}
    public void close(){cancel();io.shutdown();}

    static String fileName(String value){
        String name=value.strip();
        if(name.toLowerCase(java.util.Locale.ROOT).endsWith(".mp4"))name=name.substring(0,name.length()-4).strip();
        if(name.isEmpty()||name.equals(".")||name.equals("..")||name.startsWith(".")||name.indexOf('/')>=0||name.indexOf('\\')>=0||name.codePoints().anyMatch(Character::isISOControl)||name.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>240)
            throw new IllegalArgumentException("Invalid video filename");
        return name;
    }
    private Uri copyToGallery(File input, String name) throws Exception {
        var resolver=context.getContentResolver();Uri collection=MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        String directory=Environment.DIRECTORY_MOVIES+"/Daylog/",displayName=name+".mp4";
        ContentValues values=new ContentValues();values.put(MediaStore.Video.Media.DISPLAY_NAME,"daylog-pending-"+java.util.UUID.randomUUID()+".mp4");
        values.put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");values.put(MediaStore.Video.Media.RELATIVE_PATH,directory);values.put(MediaStore.Video.Media.IS_PENDING,1);
        Uri uri=resolver.insert(collection,values);if(uri==null)throw new IllegalStateException("MediaStore insertion failed");
        List<Uri> replaced=new ArrayList<>();
        try{
            // Finish the new copy before touching the existing named video.
            try(FileInputStream source=new FileInputStream(input);OutputStream target=resolver.openOutputStream(uri,"rwt")){
                if(target==null)throw new IllegalStateException("MediaStore stream unavailable");source.transferTo(target);
            }
            List<Uri> existing=new ArrayList<>();
            try(android.database.Cursor cursor=resolver.query(collection,new String[]{MediaStore.Video.Media._ID},
                MediaStore.Video.Media.DISPLAY_NAME+"=? AND "+MediaStore.Video.Media.RELATIVE_PATH+"=? AND "+MediaStore.Video.Media.OWNER_PACKAGE_NAME+"=? AND "+MediaStore.Video.Media.IS_PENDING+"=0",
                new String[]{displayName,directory,context.getPackageName()},null)){
                if(cursor!=null)while(cursor.moveToNext())existing.add(android.content.ContentUris.withAppendedId(collection,cursor.getLong(0)));
            }
            for(Uri old:existing){
                values.clear();values.put(MediaStore.Video.Media.DISPLAY_NAME,"daylog-replaced-"+java.util.UUID.randomUUID()+".mp4");values.put(MediaStore.Video.Media.IS_PENDING,1);
                if(resolver.update(old,values,null,null)!=1)throw new IllegalStateException("Could not replace existing video");replaced.add(old);
            }
            values.clear();values.put(MediaStore.Video.Media.DISPLAY_NAME,displayName);values.put(MediaStore.Video.Media.IS_PENDING,0);
            if(resolver.update(uri,values,null,null)!=1)throw new IllegalStateException("Could not publish video");
        }catch(Exception e){
            try{resolver.delete(uri,null,null);}catch(Exception cleanup){e.addSuppressed(cleanup);}
            for(Uri old:replaced)try{values.clear();values.put(MediaStore.Video.Media.DISPLAY_NAME,displayName);values.put(MediaStore.Video.Media.IS_PENDING,0);resolver.update(old,values,null,null);}catch(Exception restore){e.addSuppressed(restore);}
            throw e;
        }
        for(Uri old:replaced)try{resolver.delete(old,null,null);}catch(Exception cleanup){Log.w("DaylogExport","Could not remove replaced copy",cleanup);}
        return uri;
    }
}
