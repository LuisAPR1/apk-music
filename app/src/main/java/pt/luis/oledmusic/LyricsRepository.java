package pt.luis.oledmusic;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;

/** Optional lyrics source. Construction performs no disk or network access. */
public final class LyricsRepository implements AutoCloseable {
    public interface Listener {
        void onLyrics(String trackKey, LyricTimeline timeline, String status);
    }

    private static final String USER_AGENT =
            "OLED-Music/1.9 (https://github.com/LuisAPR1/apk-music)";
    private static final String NOT_FOUND = "Letra não disponível";
    private static final String NETWORK_ERROR = "Não foi possível obter a letra";
    private static final String INSTRUMENTAL = "Faixa instrumental";
    private static final String RETRY_LATER =
            "Serviço temporariamente limitado. Tenta novamente mais tarde.";
    private static final String LOCAL_MISSING =
            "Escolhe um ficheiro LRC nas definições para esta música.";
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private static final int MAX_CACHE_FILE_BYTES = 256 * 1024;
    private static final int MEMORY_LIMIT = 20;
    private static final int DISK_LIMIT = 30;
    private static final long DAY_MS = TimeUnit.DAYS.toMillis(1);
    private static final long POSITIVE_TTL_MS = 7 * DAY_MS;
    private static final long NEGATIVE_TTL_MS = DAY_MS;

    private final Context applicationContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Object requestLock = new Object();
    private final ThreadPoolExecutor worker;
    // Only the worker accesses the caches and the service cooldown.
    private final LinkedHashMap<String, Entry> memory =
            new LinkedHashMap<String, Entry>(MEMORY_LIMIT, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
                    return size() > MEMORY_LIMIT;
                }
            };
    private File cacheDirectory;
    private long nextRequestElapsedMs;
    // A worker task never captures the listener (which may belong to an Activity).
    private Listener currentListener;
    private Future<?> pendingTask;
    private volatile long generation;
    private volatile boolean closed;

    public LyricsRepository(Context context) {
        applicationContext = context.getApplicationContext();
        worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(), new ThreadFactory() {
                    @Override public Thread newThread(final Runnable runnable) {
                        return new Thread(new Runnable() {
                            @Override public void run() {
                                android.os.Process.setThreadPriority(
                                        android.os.Process.THREAD_PRIORITY_BACKGROUND);
                                runnable.run();
                            }
                        }, "oled-lyrics");
                    }
                });
    }

    /** Fetches only the supplied track signature; it never guesses a search result. */
    public void load(String key, String title, String artist, String album,
                     long durationMs, Listener listener) {
        start(key, title, artist, album, durationMs, false, listener);
    }

    /** Reads an explicitly imported, private LRC file; never contacts the network. */
    public void loadLocal(String key, Listener listener) {
        start(key, "", "", "", 0, true, listener);
    }

    private void start(String key, String title, String artist, String album,
                       long durationMs, boolean local, Listener listener) {
        synchronized (requestLock) {
            cancelLocked();
            if (closed || listener == null) return;
            currentListener = listener;
            final Request request = new Request(generation, key == null ? "" : key, safe(title),
                    safe(artist), safe(album), durationMs, local);
            pendingTask = worker.submit(new Runnable() {
                @Override public void run() {
                    runRequest(request);
                }
            });
        }
    }

    public void cancel() {
        synchronized (requestLock) {
            cancelLocked();
        }
    }

    private void cancelLocked() {
        generation++;
        currentListener = null;
        mainHandler.removeCallbacksAndMessages(null);
        if (pendingTask != null) {
            pendingTask.cancel(true);
            pendingTask = null;
        }
        // Canceled songs must not accumulate behind a slow request.
        worker.getQueue().clear();
    }

    @Override public void close() {
        synchronized (requestLock) {
            if (closed) return;
            closed = true;
            cancelLocked();
            worker.shutdownNow();
        }
    }

    private void runRequest(Request request) {
        if (!isCurrent(request)) return;
        if (request.local) {
            readLocal(request);
            return;
        }
        Entry result;
        try {
            result = cached(request);
            if (result == null) {
                checkCurrent(request);
                if (request.title.isEmpty() || request.artist.isEmpty()) {
                    deliver(request, null, NOT_FOUND);
                    return;
                }
                if (SystemClock.elapsedRealtime() < nextRequestElapsedMs) {
                    deliver(request, null, RETRY_LATER);
                    return;
                }
                result = fetch(request);
                if (result != null && isCurrent(request)) {
                    memory.put(request.key, result);
                    writeCache(request, result);
                }
            }
            if (result != null) deliver(request, result.timeline, result.status);
        } catch (InterruptedIOException ignored) {
            // Cancellation has no UI result. A socket timeout is still an error.
            if (isCurrent(request)) deliver(request, null, NETWORK_ERROR);
        } catch (IOException | JSONException | SecurityException ignored) {
            if (isCurrent(request)) deliver(request, null, NETWORK_ERROR);
        }
    }

    private void readLocal(Request request) {
        try {
            File file = new File(new File(applicationContext.getFilesDir(), "lyrics-local"),
                    hash(request.key) + ".lrc");
            if (!file.isFile()) {
                deliver(request, null, LOCAL_MISSING);
                return;
            }
            if (file.length() > MAX_RESPONSE_BYTES) {
                deliver(request, null, "O ficheiro LRC é demasiado grande (máximo 1 MiB).");
                return;
            }
            String source;
            try (InputStream input = new FileInputStream(file)) {
                source = readBounded(input, MAX_RESPONSE_BYTES, request);
            }
            LyricTimeline timeline = LyricTimeline.parse(source);
            if (hasWords(timeline)) deliver(request, timeline, "");
            else deliver(request, null, "Este ficheiro não contém letra válida.");
        } catch (IOException | SecurityException ignored) {
            if (isCurrent(request)) {
                deliver(request, null, "Não foi possível ler o ficheiro LRC.");
            }
        }
    }

    private Entry fetch(Request request) throws IOException, JSONException {
        Uri.Builder builder = new Uri.Builder().scheme("https").authority("lrclib.net")
                .appendPath("api").appendPath("get")
                .appendQueryParameter("track_name", request.title)
                .appendQueryParameter("artist_name", request.artist);
        if (!request.album.isEmpty()) builder.appendQueryParameter("album_name", request.album);
        if (request.durationMs >= 1000 && request.durationMs <= 3600000) {
            builder.appendQueryParameter("duration", Double.toString(request.durationMs / 1000.0));
        }
        HttpsURLConnection connection = (HttpsURLConnection)
                new java.net.URL(builder.build().toString()).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setInstanceFollowRedirects(false);
        connection.setUseCaches(false);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "application/json");
        try {
            checkCurrent(request);
            int responseCode = connection.getResponseCode();
            checkCurrent(request);
            if (responseCode == 429 || (responseCode == 503
                    && connection.getHeaderField("Retry-After") != null)) {
                long delay = retryDelayMs(connection.getHeaderField("Retry-After"));
                long now = SystemClock.elapsedRealtime();
                nextRequestElapsedMs = delay > Long.MAX_VALUE - now ? Long.MAX_VALUE : now + delay;
                deliver(request, null, RETRY_LATER);
                return null;
            }
            if (responseCode == 404) return new Entry("", NOT_FOUND, true,
                    System.currentTimeMillis());
            if (responseCode != 200) throw new IOException("Lyrics service response");
            if (connection.getContentLengthLong() > MAX_RESPONSE_BYTES) {
                throw new IOException("Lyrics response too large");
            }
            String contentType = connection.getContentType();
            if (contentType != null
                    && !contentType.toLowerCase(Locale.ROOT).contains("application/json")) {
                throw new IOException("Unexpected lyrics response type");
            }
            JSONObject response;
            try (InputStream input = connection.getInputStream()) {
                response = new JSONObject(readBounded(input, MAX_RESPONSE_BYTES, request));
            }
            checkCurrent(request);
            String source = textField(response, "syncedLyrics");
            LyricTimeline timeline = LyricTimeline.parse(source);
            if (!hasWords(timeline) || !timeline.isSynced()) {
                source = textField(response, "plainLyrics");
                timeline = LyricTimeline.parse(source);
            }
            if (hasWords(timeline)) {
                return new Entry(source, "", false, System.currentTimeMillis(), timeline);
            }
            if (response.optBoolean("instrumental", false)) {
                return new Entry("", INSTRUMENTAL, false, System.currentTimeMillis());
            }
            return new Entry("", NOT_FOUND, true, System.currentTimeMillis());
        } finally {
            connection.disconnect();
        }
    }

    private Entry cached(Request request) throws IOException {
        Entry result = memory.get(request.key);
        long now = System.currentTimeMillis();
        if (result != null) {
            if (result.validAt(now)) return result;
            memory.remove(request.key);
        }
        File directory = cacheDirectory();
        if (directory == null) return null;
        File file = new File(directory, hash(request.key) + ".json");
        if (!file.isFile()) return null;
        try {
            if (file.length() > MAX_CACHE_FILE_BYTES) {
                file.delete();
                return null;
            }
            JSONObject value;
            try (InputStream input = new FileInputStream(file)) {
                value = new JSONObject(readBounded(input, MAX_CACHE_FILE_BYTES, request));
            }
            if (value.optInt("version") != 1) {
                file.delete();
                return null;
            }
            result = new Entry(value.getString("source"), value.getString("status"),
                    value.getBoolean("negative"), value.getLong("storedAt"));
            if (!result.validAt(now)) {
                file.delete();
                return null;
            }
            memory.put(request.key, result);
            file.setLastModified(now);
            return result;
        } catch (InterruptedIOException canceled) {
            throw canceled;
        } catch (IOException | JSONException | SecurityException ignored) {
            file.delete();
            return null;
        }
    }

    private void writeCache(Request request, Entry result) {
        File temporary = null;
        try {
            checkCurrent(request);
            File directory = cacheDirectory();
            if (directory == null) return;
            JSONObject value = new JSONObject();
            value.put("version", 1);
            value.put("storedAt", result.storedAt);
            value.put("source", result.source);
            value.put("status", result.status);
            value.put("negative", result.negative);
            byte[] bytes = value.toString().getBytes(StandardCharsets.UTF_8);
            // Very large lyrics can be displayed but do not fill private storage.
            if (bytes.length > MAX_CACHE_FILE_BYTES) return;
            temporary = File.createTempFile("lyrics-", ".tmp", directory);
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                output.write(bytes);
            }
            checkCurrent(request);
            File destination = new File(directory, hash(request.key) + ".json");
            if (temporary.renameTo(destination)) temporary = null;
            trimDiskCache(directory);
        } catch (IOException | JSONException | SecurityException ignored) {
            // Cache failures must not prevent a successful lyric from appearing.
        } finally {
            if (temporary != null) temporary.delete();
        }
    }

    private File cacheDirectory() {
        if (cacheDirectory != null) return cacheDirectory;
        try {
            File directory = new File(applicationContext.getCacheDir(), "lyrics-v1");
            if (!directory.isDirectory() && !directory.mkdirs()) return null;
            trimDiskCache(directory);
            cacheDirectory = directory;
            return directory;
        } catch (SecurityException ignored) {
            return null;
        }
    }

    private static void trimDiskCache(File directory) {
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.getName().startsWith("lyrics-") && file.getName().endsWith(".tmp")) {
                file.delete();
            }
        }
        files = directory.listFiles(file -> file.getName().matches("[0-9a-f]{64}\\.json"));
        if (files == null || files.length <= DISK_LIMIT) return;
        Arrays.sort(files, new Comparator<File>() {
            @Override public int compare(File first, File second) {
                return Long.compare(second.lastModified(), first.lastModified());
            }
        });
        for (int i = DISK_LIMIT; i < files.length; i++) files[i].delete();
    }

    private String readBounded(InputStream input, int limit, Request request) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            checkCurrent(request);
            if (count > limit - output.size()) throw new IOException("Lyrics file too large");
            output.write(buffer, 0, count);
        }
        checkCurrent(request);
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private void deliver(final Request request, final LyricTimeline timeline, final String status) {
        if (!isCurrent(request)) return;
        mainHandler.post(new Runnable() {
            @Override public void run() {
                synchronized (requestLock) {
                    if (!isCurrent(request) || currentListener == null) return;
                    Listener listener = currentListener;
                    currentListener = null;
                    pendingTask = null;
                    listener.onLyrics(request.key, timeline, status);
                }
            }
        });
    }

    private boolean isCurrent(Request request) {
        return !closed && generation == request.generation
                && !Thread.currentThread().isInterrupted();
    }

    private void checkCurrent(Request request) throws InterruptedIOException {
        if (!isCurrent(request)) throw new InterruptedIOException("Lyrics request canceled");
    }

    private static boolean hasWords(LyricTimeline timeline) {
        return !timeline.lines().isEmpty() && !timeline.plainText().trim().isEmpty();
    }

    private static String textField(JSONObject value, String name) {
        Object text = value.opt(name);
        return text instanceof String ? (String) text : "";
    }

    private static long retryDelayMs(String retryAfter) {
        if (retryAfter != null) {
            String value = retryAfter.trim();
            try {
                long seconds = Long.parseLong(value);
                if (seconds >= 0) {
                    return seconds > Long.MAX_VALUE / 1000
                            ? Long.MAX_VALUE : Math.max(1000, seconds * 1000);
                }
            } catch (NumberFormatException ignored) {
                try {
                    SimpleDateFormat format = new SimpleDateFormat(
                            "EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
                    format.setTimeZone(TimeZone.getTimeZone("GMT"));
                    format.setLenient(false);
                    return Math.max(1000, format.parse(value).getTime() - System.currentTimeMillis());
                } catch (ParseException ignoredDate) {
                    // A conservative cooldown also applies when the header is absent or invalid.
                }
            }
        }
        return 60000;
    }

    private static String hash(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest) {
                result.append(Character.forDigit((value >>> 4) & 15, 16));
                result.append(Character.forDigit(value & 15, 16));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class Request {
        final long generation;
        final String key;
        final String title;
        final String artist;
        final String album;
        final long durationMs;
        final boolean local;

        Request(long generation, String key, String title, String artist, String album,
                long durationMs, boolean local) {
            this.generation = generation;
            this.key = key;
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.durationMs = durationMs;
            this.local = local;
        }
    }

    private static final class Entry {
        final String source;
        final String status;
        final boolean negative;
        final long storedAt;
        final LyricTimeline timeline;

        Entry(String source, String status, boolean negative, long storedAt) {
            this(source, status, negative, storedAt, source.isEmpty() ? null
                    : LyricTimeline.parse(source));
        }

        Entry(String source, String status, boolean negative, long storedAt,
              LyricTimeline timeline) {
            this.source = source;
            this.status = status;
            this.negative = negative;
            this.storedAt = storedAt;
            this.timeline = timeline;
        }

        boolean validAt(long now) {
            long age = now - storedAt;
            return age >= 0 && age <= (negative ? NEGATIVE_TTL_MS : POSITIVE_TTL_MS);
        }
    }
}
