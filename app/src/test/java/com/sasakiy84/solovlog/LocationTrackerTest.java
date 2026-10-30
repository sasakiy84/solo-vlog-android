package com.sasakiy84.solovlog;

import static com.google.common.truth.Truth.assertThat;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class LocationTrackerTest {
    private static final long SECOND=1_000_000_000L,NOW=100*SECOND;
    @Test public void staleAndFutureTravelPositionsAreNotReused(){
        assertThat(LocationTracker.fresh(NOW-30*SECOND,NOW)).isTrue();
        assertThat(LocationTracker.fresh(NOW-30*SECOND-1,NOW)).isFalse();
        assertThat(LocationTracker.fresh(NOW+1,NOW)).isFalse();
    }
    @Test public void aFreshAccurateFixIsNotReplacedByANewerCoarseFix(){
        assertThat(LocationTracker.prefer(2500,NOW,5,NOW-SECOND,NOW)).isFalse();
        assertThat(LocationTracker.prefer(5,NOW,2500,NOW-SECOND,NOW)).isTrue();
    }
    @Test public void anOldAccurateFixEventuallyYieldsToACurrentPosition(){
        assertThat(LocationTracker.prefer(500,NOW,5,NOW-15*SECOND,NOW)).isTrue();
    }
}
