package pt.luis.oledmusic;

/** Pure playback-position arithmetic using timestamps from the same monotonic clock. */
public final class PlaybackClock {
    private PlaybackClock() {
    }

    /**
     * Projects a player-reported position to now. Unknown positions (negative) or
     * update times (zero/negative), paused playback and invalid speeds do not
     * extrapolate. Finite negative speeds support reverse playback. A positive
     * duration limits the result; zero/negative durations mean unknown length.
     */
    public static long position(long baseMs, long updatedAtMs, float speed,
                                boolean playing, long durationMs, long nowMs) {
        if (!playing || baseMs < 0 || updatedAtMs <= 0 || nowMs <= updatedAtMs
                || speed == 0 || Float.isNaN(speed) || Float.isInfinite(speed)) {
            return clamp(baseMs, durationMs);
        }
        // Both times are positive and now is later, so subtraction cannot overflow.
        long elapsed = nowMs - updatedAtMs;
        long delta;
        if (speed == 1) delta = elapsed;
        else if (speed == -1) delta = -elapsed;
        else {
            double adjustment = elapsed * (double) speed;
            if (adjustment >= Long.MAX_VALUE) delta = Long.MAX_VALUE;
            else if (adjustment <= Long.MIN_VALUE) delta = Long.MIN_VALUE;
            else delta = (long) adjustment;
        }
        return clamp(saturatingAdd(baseMs, delta), durationMs);
    }

    /** A positive configured lyric offset delays its target position in the song. */
    public static long seekTarget(long lyricTimeMs, int offsetMs, long durationMs) {
        return clamp(saturatingAdd(lyricTimeMs, offsetMs), durationMs);
    }

    private static long clamp(long position, long durationMs) {
        if (position < 0) return 0;
        return durationMs > 0 && position > durationMs ? durationMs : position;
    }

    private static long saturatingAdd(long first, long second) {
        if (second > 0 && first > Long.MAX_VALUE - second) return Long.MAX_VALUE;
        if (second < 0 && first < Long.MIN_VALUE - second) return Long.MIN_VALUE;
        return first + second;
    }
}
