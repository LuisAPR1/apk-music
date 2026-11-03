package pt.luis.oledmusic;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Remembers play transitions, rather than notification order or metadata updates.
 * Kept free of Android dependencies so switching sequences can be tested on the JVM. */
final class PlayerSelection<K> {
    private static final class Entry {
        String packageName;
        boolean playing;
        long stateTime;
    }

    private final Map<K, Entry> entries = new LinkedHashMap<>();
    private K selected;
    private String lastPackage;
    private long lastStart = -1;

    PlayerSelection(String lastPackage) {
        this.lastPackage = lastPackage == null ? "" : lastPackage;
    }

    void update(K key, String packageName, boolean playing, long eventTime) {
        Entry entry = entries.get(key);
        if (entry != null && eventTime < entry.stateTime) return;
        boolean wasPlaying = entry != null && entry.playing;
        if (entry == null) {
            entry = new Entry();
            entries.put(key, entry);
        }
        entry.packageName = packageName;
        entry.playing = playing;
        entry.stateTime = eventTime;
        if (playing && !wasPlaying && eventTime >= lastStart) {
            lastStart = eventTime;
            selected = key;
            lastPackage = packageName;
        }
    }

    void retain(Collection<K> keys) {
        entries.keySet().retainAll(keys);
    }

    K selected() {
        if (entries.containsKey(selected)) return selected;
        K fallback = null;
        long newest = Long.MIN_VALUE;
        for (Map.Entry<K, Entry> item : entries.entrySet()) {
            Entry entry = item.getValue();
            // An unrelated paused player must not replace the last player used.
            if (!lastPackage.isEmpty() && !lastPackage.equals(entry.packageName)) continue;
            if (fallback == null || entry.stateTime > newest) {
                fallback = item.getKey();
                newest = entry.stateTime;
            }
        }
        selected = fallback;
        if (fallback != null) lastPackage = entries.get(fallback).packageName;
        return selected;
    }

    String lastPackage() { return lastPackage; }
}
