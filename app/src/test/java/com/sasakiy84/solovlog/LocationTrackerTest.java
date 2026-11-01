package com.sasakiy84.solovlog;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class LocationTrackerTest {
    private static final long SECOND=1_000_000_000L,NOW=100*SECOND;
    @Test public void staleAndFutureTravelPositionsAreNotReused(){
        assertTrue(LocationTracker.fresh(NOW-10*SECOND,NOW));
        assertFalse(LocationTracker.fresh(NOW-10*SECOND-1,NOW));
        assertFalse(LocationTracker.fresh(NOW+1,NOW));
    }
    @Test public void aFreshAccurateFixIsNotReplacedByANewerCoarseFix(){
        assertFalse(LocationTracker.prefer(2500,NOW,5,NOW-SECOND,NOW));
        assertTrue(LocationTracker.prefer(5,NOW,2500,NOW-SECOND,NOW));
    }
    @Test public void anOldAccurateFixEventuallyYieldsToACurrentPosition(){
        assertTrue(LocationTracker.prefer(500,NOW,5,NOW-15*SECOND,NOW));
    }
}
