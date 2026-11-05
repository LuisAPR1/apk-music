package pt.luis.oledmusic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Immutable LRC lyrics and playback-position lookup, independent of Android. */
public final class LyricTimeline {
    private static final Pattern TIMESTAMP = Pattern.compile(
            "^(?:(\\d+):)?(\\d+):(\\d{1,2})(?:[.,](\\d{1,3}))?$");
    private static final Pattern METADATA = Pattern.compile(
            "^([A-Za-z][A-Za-z0-9_-]*)\\s*:(.*)$");
    private static final Pattern OFFSET = Pattern.compile("^[+-]?\\d+$");

    /** A lyric row. A time of -1 identifies a row from unsynchronised plain text. */
    public static final class Line {
        public final long timeMs;
        public final String text;

        private Line(long timeMs, String text) {
            this.timeMs = timeMs;
            this.text = text;
        }
    }

    private final List<Line> lines;
    private final boolean synced;
    private final String plainText;

    private LyricTimeline(List<Line> rows, boolean synced) {
        this.lines = Collections.unmodifiableList(new ArrayList<>(rows));
        this.synced = synced;
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            if (i != 0) text.append('\n');
            text.append(rows.get(i).text);
        }
        this.plainText = text.toString();
    }

    /**
     * Reads LRC or plain lyrics. Metadata is omitted, the last valid global offset
     * shifts all times (positive values delay lyrics), and negative times clamp to
     * zero. Untimed rows are used only if the input contains no valid timestamps.
     */
    public static LyricTimeline parse(String source) {
        if (source == null || source.isEmpty()) {
            return new LyricTimeline(Collections.<Line>emptyList(), false);
        }
        String normalized = source.replace("\r\n", "\n").replace('\r', '\n');
        if (normalized.startsWith("\uFEFF")) normalized = normalized.substring(1);
        List<Line> timed = new ArrayList<>();
        List<Line> untimed = new ArrayList<>();
        long offset = 0;
        for (String raw : normalized.split("\n", -1)) {
            String row = raw.trim();
            int cursor = 0;
            List<Long> timestamps = new ArrayList<>();
            boolean malformedTimestamp = false;
            boolean hadMetadata = false;
            while (cursor < row.length() && row.charAt(cursor) == '[') {
                int closing = row.indexOf(']', cursor + 1);
                if (closing < 0) {
                    // Truncated timestamp tags are not printable lyric content.
                    String incomplete = row.substring(cursor + 1).trim();
                    if (!incomplete.isEmpty() && Character.isDigit(incomplete.charAt(0))) {
                        malformedTimestamp = true;
                        cursor = row.length();
                    } else if (METADATA.matcher(incomplete).matches()) {
                        hadMetadata = true;
                        cursor = row.length();
                    }
                    break;
                }
                String tag = row.substring(cursor + 1, closing).trim();
                Long time = parseTimestamp(tag);
                if (time != null) {
                    timestamps.add(time);
                } else {
                    Matcher metadata = METADATA.matcher(tag);
                    if (metadata.matches()) {
                        hadMetadata = true;
                        if ("offset".equalsIgnoreCase(metadata.group(1))) {
                            String value = metadata.group(2).trim();
                            if (OFFSET.matcher(value).matches()) {
                                try {
                                    offset = Long.parseLong(value);
                                } catch (NumberFormatException ignored) {
                                    // An invalid offset cannot erase a previous valid value.
                                }
                            }
                        }
                    } else if (!tag.isEmpty() && Character.isDigit(tag.charAt(0))) {
                        malformedTimestamp = true;
                    } else {
                        // Plain section labels, such as [Chorus], are lyrics.
                        break;
                    }
                }
                cursor = closing + 1;
                while (cursor < row.length() && Character.isWhitespace(row.charAt(cursor))) {
                    cursor++;
                }
            }
            String text = row.substring(cursor).trim();
            if (!timestamps.isEmpty()) {
                for (Long time : timestamps) timed.add(new Line(time, text));
            } else if (!malformedTimestamp && (!hadMetadata || !text.isEmpty())) {
                untimed.add(new Line(-1, text));
            }
        }
        if (timed.isEmpty()) {
            int first = 0;
            int last = untimed.size();
            while (first < last && untimed.get(first).text.isEmpty()) first++;
            while (last > first && untimed.get(last - 1).text.isEmpty()) last--;
            return new LyricTimeline(untimed.subList(first, last), false);
        }
        List<Line> shifted = new ArrayList<>(timed.size());
        for (Line line : timed) shifted.add(new Line(shift(line.timeMs, offset), line.text));
        // Collections.sort is stable: translations keep their original source order.
        Collections.sort(shifted, new Comparator<Line>() {
            @Override public int compare(Line first, Line second) {
                return Long.compare(first.timeMs, second.timeMs);
            }
        });
        List<Line> distinct = new ArrayList<>(shifted.size());
        Set<String> textsAtTime = new HashSet<>();
        long previousTime = -1;
        for (Line line : shifted) {
            if (line.timeMs != previousTime) {
                textsAtTime.clear();
                previousTime = line.timeMs;
            }
            if (textsAtTime.add(line.text)) distinct.add(line);
        }
        return new LyricTimeline(distinct, true);
    }

    public List<Line> lines() {
        return lines;
    }

    public boolean isSynced() {
        return synced;
    }

    /** All lyric rows without LRC tags, joined by newlines. */
    public String plainText() {
        return plainText;
    }

    /** Returns -1 for an invalid index or for an unsynchronised row. */
    public long timeAt(int index) {
        return index >= 0 && index < lines.size() ? lines.get(index).timeMs : -1;
    }

    /**
     * Returns the first row in the active timestamp group, or -1 before the first
     * timestamp or for plain text. Lookup depends only on the supplied position,
     * so pausing and seeking backwards require no special state.
     */
    public int activeIndex(long playbackMs) {
        if (!synced || lines.isEmpty()) return -1;
        int low = 0;
        int high = lines.size();
        while (low < high) {
            int middle = low + (high - low) / 2;
            if (lines.get(middle).timeMs <= playbackMs) low = middle + 1;
            else high = middle;
        }
        int active = low - 1;
        if (active < 0) return -1;
        long activeTime = lines.get(active).timeMs;
        while (active > 0 && lines.get(active - 1).timeMs == activeTime) active--;
        return active;
    }

    private static Long parseTimestamp(String tag) {
        Matcher match = TIMESTAMP.matcher(tag);
        if (!match.matches()) return null;
        try {
            long hours = match.group(1) == null ? 0 : Long.parseLong(match.group(1));
            long minutes = Long.parseLong(match.group(2));
            int seconds = Integer.parseInt(match.group(3));
            if (seconds > 59 || (match.group(1) != null && minutes > 59)) return null;
            String fraction = match.group(4);
            int milliseconds = fraction == null ? 0 : Integer.parseInt(fraction);
            if (fraction != null && fraction.length() == 1) milliseconds *= 100;
            else if (fraction != null && fraction.length() == 2) milliseconds *= 10;
            if (hours > Long.MAX_VALUE / 3600000L) return null;
            long result = hours * 3600000L;
            if (minutes > (Long.MAX_VALUE - result) / 60000L) return null;
            result += minutes * 60000L;
            long remainder = seconds * 1000L + milliseconds;
            if (result > Long.MAX_VALUE - remainder) return null;
            return result + remainder;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static long shift(long time, long offset) {
        if (offset > 0 && time > Long.MAX_VALUE - offset) return Long.MAX_VALUE;
        return Math.max(0, time + offset);
    }
}
