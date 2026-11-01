package com.sasakiy84.solovlog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class GeoPointTest {
    @Test public void approximateCoordinatesUseFewerDecimals(){
        assertEquals("35.6812, 139.7671",new GeoPoint(35.681236,139.767125,5).label());
        assertEquals("35.681, 139.767",new GeoPoint(35.681236,139.767125,2500).label());
    }
    @Test public void nearbyHistoryDoesNotSuggestAnotherTravelDestination(){
        GeoPoint tokyo=new GeoPoint(35.681236,139.767125,5);
        assertTrue(tokyo.distanceMeters(new GeoPoint(35.682136,139.767125,5))<300);
        assertTrue(tokyo.distanceMeters(new GeoPoint(34.985849,135.758767,5))>5000);
    }
}
