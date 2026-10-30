package com.sasakiy84.solovlog;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class GeoPointTest {
    @Test public void invalidCoordinatesAndAccuracyAreRejected(){
        assertThrows(IllegalArgumentException.class,()->new GeoPoint(91,0,5));
        assertThrows(IllegalArgumentException.class,()->new GeoPoint(0,181,5));
        assertThrows(IllegalArgumentException.class,()->new GeoPoint(Double.NaN,0,5));
        assertThrows(IllegalArgumentException.class,()->new GeoPoint(0,0,-1));
    }
    @Test public void approximateCoordinatesUseFewerDecimals(){
        assertThat(new GeoPoint(35.681236,139.767125,5).label()).isEqualTo("35.6812, 139.7671");
        assertThat(new GeoPoint(35.681236,139.767125,2500).label()).isEqualTo("35.681, 139.767");
    }
    @Test public void nearbyHistoryDoesNotSuggestAnotherTravelDestination(){
        GeoPoint tokyo=new GeoPoint(35.681236,139.767125,5);
        assertThat(tokyo.distanceMeters(new GeoPoint(35.682136,139.767125,5))).isWithin(1).of(100);
        assertThat(tokyo.distanceMeters(new GeoPoint(34.985849,135.758767,5))).isGreaterThan(300000);
    }
    @Test public void editingTextAndPlacePreservesCoordinatesAndKeepsTheirValuesSeparate(){
        GeoPoint point=new GeoPoint(35.681236,139.767125,5);
        Clip clip=new Clip("a",0,2000,2,false,true,"Asia/Tokyo").withLocation(point).withCaption("朝ごはん").withPlace("東京駅");
        assertThat(clip.withCaption("").place()).isEqualTo("東京駅");
        assertThat(clip.withCaption("").location()).isEqualTo(point);
        assertThat(clip.withPlace("").caption()).isEqualTo("朝ごはん");
    }
}
