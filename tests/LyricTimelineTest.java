package pt.luis.oledmusic;

/** Parsing, real playback boundaries, seek/pause behaviour and malformed provider data. */
public final class LyricTimelineTest {
    private static int assertions;

    private static void equal(Object expected, Object actual, String message) {
        assertions++;
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void row(LyricTimeline timeline, int index, long time, String text) {
        equal(time, timeline.timeAt(index), "Time at row " + index);
        equal(text, timeline.lines().get(index).text, "Text at row " + index);
    }

    public static void main(String[] args) {
        LyricTimeline playback = LyricTimeline.parse("[00:01]One\n[00:02.50]Two\n[00:04]\n[00:06]Six");
        equal(true, playback.isSynced(), "Timestamped lyrics are synced");
        equal(-1, playback.activeIndex(-50), "Negative playback precedes first lyric");
        equal(-1, playback.activeIndex(999), "No lyric highlighted before first timestamp");
        equal(0, playback.activeIndex(1000), "Exact first boundary activates lyric");
        equal(0, playback.activeIndex(2499), "Previous lyric holds until next boundary");
        equal(1, playback.activeIndex(2500), "Exact second boundary activates lyric");
        equal(1, playback.activeIndex(2500), "Paused position leaves highlight unchanged");
        equal(3, playback.activeIndex(999999), "Last lyric remains active after its timestamp");
        equal(0, playback.activeIndex(1500), "Seeking backwards reselects the earlier lyric");
        equal(2, playback.activeIndex(4000), "Empty timestamp represents an instrumental gap");
        row(playback, 2, 4000, "");

        LyricTimeline formats = LyricTimeline.parse(
                "[01:02.34][00:03.007]Repeated\n[00:04.5]Tenths\n"
                + "[1:02:03.456]Hours\n[72:02,12]Long minutes\n[00:05]Whole seconds");
        equal(6, formats.lines().size(), "Multiple timestamps make separate entries");
        row(formats, 0, 3007, "Repeated");
        row(formats, 1, 4500, "Tenths");
        row(formats, 2, 5000, "Whole seconds");
        row(formats, 3, 62340, "Repeated");
        row(formats, 4, 3723456, "Hours");
        row(formats, 5, 4322120, "Long minutes");

        LyricTimeline offsets = LyricTimeline.parse(
                "[offset:+100]\n[00:01]First\n[offset:+500]\n[00:02]Second\n[offset:invalid]");
        row(offsets, 0, 1500, "First");
        row(offsets, 1, 2500, "Second");
        equal(-1, offsets.activeIndex(1499), "Positive offset delays the displayed lyrics");
        LyricTimeline negative = LyricTimeline.parse("[offset:-1500]\n[00:01]First\n[00:02]Second");
        row(negative, 0, 0, "First");
        row(negative, 1, 500, "Second");
        equal(0, negative.activeIndex(0), "Negative adjusted times clamp to start");
        LyricTimeline maximum = LyricTimeline.parse("[offset:9223372036854775807]\n[00:01]End");
        row(maximum, 0, Long.MAX_VALUE, "End");
        equal(-1, maximum.activeIndex(Long.MAX_VALUE - 1), "Overflow does not wrap time negative");
        equal(0, maximum.activeIndex(Long.MAX_VALUE), "Saturated timestamp remains usable");
        row(LyricTimeline.parse("[offset:-9223372036854775808]\n[00:01]Start"), 0, 0, "Start");
        row(LyricTimeline.parse("[offset:200]\n[offset:9223372036854775808]\n[00:01]Safe"),
                0, 1200, "Safe");

        LyricTimeline duplicate = LyricTimeline.parse(
                "[00:02]Later\n[00:01]Original\n[00:01]Tradução\n"
                + "[00:01]Original\n[00:01][00:01]Tradução");
        equal(3, duplicate.lines().size(), "Only identical timestamp-and-text duplicates disappear");
        row(duplicate, 0, 1000, "Original");
        row(duplicate, 1, 1000, "Tradução");
        row(duplicate, 2, 2000, "Later");
        equal(0, duplicate.activeIndex(1000), "Simultaneous translations select the first row in order");
        equal(0, duplicate.activeIndex(1999), "Translation group stays active until next time");
        equal(2, duplicate.activeIndex(2000), "Next group activates at exact boundary");

        LyricTimeline malformed = LyricTimeline.parse(
                "\uFEFF[ar:Artist]\r\n[ti:Title]\r[unknown:metadata]\n"
                + "[00:61]Invalid seconds\n[1:99:02]Invalid minutes\n"
                + "[00:02.1234]Invalid fraction\n[999999999999999999:00]Overflow\n"
                + "[00:xx]Broken\n[00:12\nUntimed commentary\n"
                + "[00:02] [00:03.25]Olá 世界 🎵\n[00:04][Chorus] Together");
        equal(3, malformed.lines().size(), "Invalid tags and untimed provider comments are omitted");
        row(malformed, 0, 2000, "Olá 世界 🎵");
        row(malformed, 1, 3250, "Olá 世界 🎵");
        row(malformed, 2, 4000, "[Chorus] Together");
        equal("Olá 世界 🎵\nOlá 世界 🎵\n[Chorus] Together", malformed.plainText(),
                "Formatted text has no timing or metadata artifacts");

        LyricTimeline plain = LyricTimeline.parse(
                "\n[ar:Artist]\n[ti:Title]\n  Primeiro verso  \n\n[Chorus]\nCoração 世界\n\n");
        equal(false, plain.isSynced(), "Plain text never pretends to be synced");
        equal(4, plain.lines().size(), "Plain text keeps interior paragraphs and labels");
        equal("Primeiro verso\n\n[Chorus]\nCoração 世界", plain.plainText(), "Plain text preserves Unicode");
        row(plain, 0, -1, "Primeiro verso");
        row(plain, 1, -1, "");
        equal(-1, plain.activeIndex(0), "Plain text has no active lyric");
        equal(-1, plain.activeIndex(Long.MAX_VALUE), "Plain text never gains a synthetic timeline");
        equal(-1L, plain.timeAt(-1), "Negative row lookup is safe");
        equal(-1L, plain.timeAt(400), "Out-of-range row lookup is safe");
        equal("Words", LyricTimeline.parse("[00:xx]Broken\n[offset:not a number]\nWords").plainText(),
                "Malformed LRC and invalid metadata do not leak into plain text");
        equal("Words", LyricTimeline.parse("[ar:Truncated\n[offset:+2\nWords").plainText(),
                "Unclosed metadata does not leak into plain text");
        row(LyricTimeline.parse("[00:01][00:broken"), 0, 1000, "");
        equal(0, LyricTimeline.parse(null).lines().size(), "Null provider result is empty");
        equal(0, LyricTimeline.parse("\r\n  \n[ar:Artist]\n[offset:+0]").lines().size(),
                "Whitespace and metadata alone have no lyrics");
        equal(false, LyricTimeline.parse("").isSynced(), "Empty lyrics are unsynced");
        equal(true, LyricTimeline.parse("[00:01]").isSynced(), "Explicit empty timed line is valid");
        try {
            playback.lines().clear();
            throw new AssertionError("Timeline list must be immutable");
        } catch (UnsupportedOperationException expected) {
            assertions++;
        }
        System.out.println("PASS: " + assertions + " lyric timeline assertions");
    }
}
