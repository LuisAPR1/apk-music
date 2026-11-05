package pt.luis.oledmusic;

/** Playback extrapolation, pause/seek behaviour and untrusted session values. */
public final class PlaybackClockTest {
    private static int assertions;

    private static void equal(long expected, long actual, String message) {
        assertions++;
        if (expected != actual) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    public static void main(String[] args) {
        equal(6000, PlaybackClock.position(5000, 10000, 1, true, 20000, 11000),
                "Playing position advances with the monotonic clock");
        equal(5000, PlaybackClock.position(5000, 10000, 1, false, 20000, 11000),
                "Paused position does not advance");
        equal(5000, PlaybackClock.position(5000, 10000, 1, false, 20000, 30000),
                "Pause remains stable across later frames");
        equal(8000, PlaybackClock.position(8000, 20000, 1, false, 20000, 22000),
                "Seeking while paused immediately adopts the new base position");
        equal(9500, PlaybackClock.position(8000, 22000, 1, true, 20000, 23500),
                "Playback resumes from the updated position and timestamp");
        equal(2200, PlaybackClock.position(2000, 24000, 1, true, 20000, 24200),
                "Seeking backwards uses the fresh timestamp instead of old progress");
        equal(5000, PlaybackClock.position(5000, 10000, 0, true, 20000, 11000),
                "A zero-speed playing state stays still");
        equal(5000, PlaybackClock.position(5000, 10000, -0.0f, true, 20000, 11000),
                "Negative zero speed stays still");
        equal(5500, PlaybackClock.position(5000, 10000, 0.5f, true, 20000, 11000),
                "Half-speed position advances by half the elapsed time");
        equal(6250, PlaybackClock.position(5000, 10000, 1.25f, true, 20000, 11000),
                "Fractional playback speed is applied");
        equal(7000, PlaybackClock.position(5000, 10000, 2, true, 20000, 11000),
                "Double speed advances twice as far");
        equal(4000, PlaybackClock.position(5000, 10000, -1, true, 20000, 11000),
                "Finite negative speeds rewind playback");
        equal(0, PlaybackClock.position(500, 10000, -1, true, 20000, 11000),
                "Rewinding cannot go below zero");
        equal(20000, PlaybackClock.position(19500, 10000, 1, true, 20000, 11000),
                "Playback cannot go beyond a known duration");
        equal(21000, PlaybackClock.position(20000, 10000, 1, true, 0, 11000),
                "Zero duration means unknown length");
        equal(21000, PlaybackClock.position(20000, 10000, 1, true, -1, 11000),
                "Negative duration means unknown length");
        equal(20000, PlaybackClock.position(30000, 10000, 1, false, 20000, 11000),
                "Out-of-range paused base is clamped");
        equal(18000, PlaybackClock.position(30000, 10000, -1, true, 20000, 22000),
                "Reverse playback projects raw base before final duration clamping");
        equal(0, PlaybackClock.position(-1, 10000, 1, true, 20000, 11000),
                "An unknown base position cannot invent elapsed progress");
        equal(0, PlaybackClock.position(Long.MIN_VALUE, 10000, 1, false, 20000, 11000),
                "Negative invalid positions clamp safely");
        equal(5000, PlaybackClock.position(5000, 0, 1, true, 20000, 11000),
                "A missing update clock does not extrapolate from boot time");
        equal(5000, PlaybackClock.position(5000, -1, 1, true, 20000, 11000),
                "A negative update clock is unknown");
        equal(5000, PlaybackClock.position(5000, 10000, 1, true, 20000, 0),
                "A missing current clock cannot advance playback");
        equal(5000, PlaybackClock.position(5000, 10000, 1, true, 20000, 9999),
                "A clock reset does not produce negative elapsed time");
        equal(5000, PlaybackClock.position(5000, 10000, 1, true, 20000, 10000),
                "Exact update timestamp returns the reported position");
        equal(5000, PlaybackClock.position(5000, 10000, Float.NaN, true, 20000, 11000),
                "NaN speed keeps the reported position");
        equal(5000, PlaybackClock.position(5000, 10000, Float.POSITIVE_INFINITY,
                true, 20000, 11000), "Infinite speed does not jump to the end");
        equal(5000, PlaybackClock.position(5000, 10000, Float.NEGATIVE_INFINITY,
                true, 20000, 11000), "Negative infinite speed does not jump to the start");
        equal(Long.MAX_VALUE, PlaybackClock.position(Long.MAX_VALUE - 10, 1, 1,
                true, 0, 100), "Addition overflow saturates instead of wrapping");
        equal(Long.MAX_VALUE, PlaybackClock.position(1, 1, Float.MAX_VALUE,
                true, 0, Long.MAX_VALUE), "Huge finite speed saturates safely");
        equal(0, PlaybackClock.position(Long.MAX_VALUE, 1, -Float.MAX_VALUE,
                true, 0, Long.MAX_VALUE), "Huge finite reverse speed clamps safely");
        equal(20000, PlaybackClock.position(1, 1, Float.MAX_VALUE,
                true, 20000, Long.MAX_VALUE), "Overflow still respects known duration");
        equal(Long.MAX_VALUE - 1, PlaybackClock.position(Long.MAX_VALUE - 1, 10000,
                1, false, 0, 11000), "Paused huge positions keep integer precision");
        equal(Long.MAX_VALUE - 2, PlaybackClock.position(Long.MAX_VALUE - 1, 10000,
                -1, true, 0, 10001), "Tiny deltas preserve precision at large positions");
        equal(Long.MAX_VALUE - 1, PlaybackClock.position(0, 1, 1,
                true, 0, Long.MAX_VALUE), "Normal speed retains exact large elapsed time");

        equal(10500, PlaybackClock.seekTarget(10000, 500, 20000),
                "Positive lyric offset delays its seek target");
        equal(9500, PlaybackClock.seekTarget(10000, -500, 20000),
                "Negative lyric offset advances its seek target");
        equal(10000, PlaybackClock.seekTarget(10000, 0, 20000),
                "Zero lyric offset keeps the timestamp");
        equal(0, PlaybackClock.seekTarget(100, -500, 20000),
                "Lyric seeks clamp to the start");
        equal(20000, PlaybackClock.seekTarget(19900, 500, 20000),
                "Lyric seeks clamp to the known end");
        equal(20500, PlaybackClock.seekTarget(20000, 500, 0),
                "Unknown length does not constrain a lyric seek");
        equal(20500, PlaybackClock.seekTarget(20000, 500, -1),
                "Negative unknown length does not constrain a lyric seek");
        equal(0, PlaybackClock.seekTarget(Long.MIN_VALUE, Integer.MIN_VALUE, 0),
                "Negative seek arithmetic cannot wrap to a positive position");
        equal(Long.MAX_VALUE, PlaybackClock.seekTarget(Long.MAX_VALUE, Integer.MAX_VALUE, 0),
                "Positive seek arithmetic saturates safely");
        equal(20000, PlaybackClock.seekTarget(Long.MAX_VALUE, Integer.MAX_VALUE, 20000),
                "Overflowing lyric target still obeys known duration");
        System.out.println("PASS: " + assertions + " playback clock assertions");
    }
}
