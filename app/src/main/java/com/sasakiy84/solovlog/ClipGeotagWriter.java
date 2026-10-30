package com.sasakiy84.solovlog;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Adds a just-captured position without re-encoding the video or replacing a failed original. */
final class ClipGeotagWriter {
    private ClipGeotagWriter(){}
    static void write(File source,GeoPoint point) throws Exception{
        File output=File.createTempFile(".daylog-gps-",".mp4",source.getParentFile());
        MediaExtractor extractor=new MediaExtractor();MediaMuxer muxer=null;
        try{
            int rotation=0;
            try(MediaMetadataRetriever metadata=new MediaMetadataRetriever()){
                metadata.setDataSource(source.getAbsolutePath());String value=metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);if(value!=null)rotation=Integer.parseInt(value);
            }
            extractor.setDataSource(source.getAbsolutePath());muxer=new MediaMuxer(output.getAbsolutePath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            muxer.setOrientationHint(rotation);muxer.setLocation((float)point.latitude(),(float)point.longitude());
            int[] tracks=new int[extractor.getTrackCount()];int capacity=1024*1024;
            for(int n=0;n<tracks.length;n++){
                MediaFormat format=extractor.getTrackFormat(n);tracks[n]=muxer.addTrack(format);extractor.selectTrack(n);
                if(format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE))capacity=Math.max(capacity,format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));
            }
            ByteBuffer buffer=ByteBuffer.allocateDirect(capacity);MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();muxer.start();
            while(true){
                buffer.clear();int size=extractor.readSampleData(buffer,0);if(size<0)break;
                info.set(0,size,extractor.getSampleTime(),(extractor.getSampleFlags()&MediaExtractor.SAMPLE_FLAG_SYNC)!=0?MediaCodec.BUFFER_FLAG_KEY_FRAME:0);
                muxer.writeSampleData(tracks[extractor.getSampleTrackIndex()],buffer,info);extractor.advance();
            }
            muxer.stop();muxer.release();muxer=null;
            Files.move(output.toPath(),source.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }finally{extractor.release();if(muxer!=null)muxer.release();output.delete();}
    }
}
