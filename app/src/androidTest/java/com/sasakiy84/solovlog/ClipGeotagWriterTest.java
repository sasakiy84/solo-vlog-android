package com.sasakiy84.solovlog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.File;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class ClipGeotagWriterTest {
    @Test public void addingGpsPreservesEncodedVideoAudioAndPortraitRotation()throws Exception{
        File movie=TestVideos.copyToCache("rotated-with-audio.mp4");
        try{
            Map<String,String> samples=encodedTracks(movie);
            ClipGeotagWriter.write(movie,new GeoPoint(35.681236,139.767125,5));
            assertEquals(samples,encodedTracks(movie));
            try(MediaMetadataRetriever metadata=new MediaMetadataRetriever()){
                metadata.setDataSource(movie.getAbsolutePath());
                assertEquals("90",metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));
                String location=metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION);
                assertNotNull(location);assertTrue(location.startsWith("+35.681"));assertTrue(location.contains("+139.767"));
            }
        }finally{movie.delete();}
    }
    private static Map<String,String> encodedTracks(File movie)throws Exception{
        Map<String,String> hashes=new HashMap<>();
        // The fixture has one video and one audio track. Hash payloads, independently of MP4 layout.
        for(int track=0;track<2;track++){
            MediaExtractor extractor=new MediaExtractor();
            try{
                extractor.setDataSource(movie.getAbsolutePath());extractor.selectTrack(track);
                MessageDigest digest=MessageDigest.getInstance("SHA-256");ByteBuffer sample=ByteBuffer.allocate(1024*1024);
                while(true){sample.clear();int size=extractor.readSampleData(sample,0);if(size<0)break;sample.position(0);sample.limit(size);digest.update(sample);extractor.advance();}
                hashes.put(extractor.getTrackFormat(track).getString(MediaFormat.KEY_MIME),HexFormat.of().formatHex(digest.digest()));
            }finally{extractor.release();}
        }
        return hashes;
    }
}
