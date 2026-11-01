package io.github.zxymiku.playguard;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class PlayClockTest {
    private static final long LIMIT = 180 * 60_000L;
    @Test void warnsAtTenFiveOneMinutesAndBansAtThreeHours() {
        PlayClock clock = new PlayClock();
        assertEquals(List.of(), clock.advance(169 * 60_000L, LIMIT));
        assertEquals(List.of(10), clock.advance(60_000, LIMIT));
        assertEquals(List.of(5), clock.advance(5 * 60_000L, LIMIT));
        assertEquals(List.of(1), clock.advance(4 * 60_000L, LIMIT));
        assertFalse(clock.exhausted(LIMIT));
        clock.advance(60_000, LIMIT);
        assertTrue(clock.exhausted(LIMIT));
        assertEquals(List.of(), clock.advance(60_000, LIMIT));
    }
    @Test void restoresAccumulatedTimeWithoutReplayingWarnings() {
        PlayClock clock = new PlayClock(176 * 60_000L);
        assertEquals(List.of(), clock.advance(60_000, LIMIT));
        assertEquals(List.of(1), clock.advance(2 * 60_000L, LIMIT));
    }
    @Test void handlesLagCrossingMultipleThresholds() {
        assertEquals(List.of(10, 5, 1), new PlayClock().advance(LIMIT, LIMIT));
    }
    @Test void negativeElapsedDoesNotReduceTimeAndLargeDeltaIsCapped() {
        PlayClock clock = new PlayClock(100);
        clock.advance(-500, LIMIT);
        assertEquals(100, clock.playedMillis);
        clock.advance(LIMIT * 2, LIMIT);
        assertEquals(LIMIT, clock.playedMillis);
    }
}
