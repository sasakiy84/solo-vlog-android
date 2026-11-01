package com.sasakiy84.solovlog;

import static com.google.common.truth.Truth.assertThat;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class GeoPointTest {
    @Test public void approximateCoordinatesUseFewerDecimals(){
        assertThat(new GeoPoint(35.681236,139.767125,5).label()).isEqualTo("35.6812, 139.7671");
        assertThat(new GeoPoint(35.681236,139.767125,2500).label()).isEqualTo("35.681, 139.767");
    }
    @Test public void nearbyHistoryDoesNotSuggestAnotherTravelDestination(){
        GeoPoint tokyo=new GeoPoint(35.681236,139.767125,5);
        assertThat(tokyo.distanceMeters(new GeoPoint(35.682136,139.767125,5))).isLessThan(300);
        assertThat(tokyo.distanceMeters(new GeoPoint(34.985849,135.758767,5))).isGreaterThan(5000);
    }
}
