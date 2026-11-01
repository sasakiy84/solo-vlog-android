package com.sasakiy84.solovlog;

import static com.google.common.truth.Truth.assertThat;
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
            assertThat(encodedTracks(movie)).containsExactlyEntriesIn(samples);
            try(MediaMetadataRetriever metadata=new MediaMetadataRetriever()){
                metadata.setDataSource(movie.getAbsolutePath());
                assertThat(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)).isEqualTo("90");
                String location=metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION);
                assertThat(location).isNotNull();assertThat(location).startsWith("+35.681");assertThat(location).contains("+139.767");
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
