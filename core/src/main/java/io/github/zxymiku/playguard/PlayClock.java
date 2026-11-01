package io.github.zxymiku.playguard;

import java.util.ArrayList;
import java.util.List;

/** Pure online-time accounting. Elapsed time is provided by a monotonic clock. */
public final class PlayClock {
    public long playedMillis;
    private transient int warnedMask;

    public PlayClock() {}
    public PlayClock(long playedMillis) { this.playedMillis = playedMillis; }

    public List<Integer> advance(long elapsedMillis, long limitMillis) {
        long before = playedMillis;
        playedMillis = Math.min(limitMillis, playedMillis + Math.max(0, elapsedMillis));
        List<Integer> warnings = new ArrayList<>();
        int[] minutes = {10, 5, 1};
        for (int i = 0; i < minutes.length; i++) {
            long threshold = limitMillis - minutes[i] * 60_000L;
            if (threshold >= 0 && before < threshold && playedMillis >= threshold
                    && (warnedMask & (1 << i)) == 0) {
                warnedMask |= 1 << i;
                warnings.add(minutes[i]);
            }
        }
        return warnings;
    }
    public boolean exhausted(long limitMillis) { return playedMillis >= limitMillis; }
}
