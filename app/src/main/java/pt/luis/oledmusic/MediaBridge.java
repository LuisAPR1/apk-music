package pt.luis.oledmusic;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** A local remote-control for YouTube Music's Android media session. */
public final class MediaBridge {
    public static final String YT_PACKAGE = "com.google.android.apps.youtube.music";
    public static final String REVANCED_PACKAGE = "app.revanced.android.apps.youtube.music";
    private static final int MAX_ART_SIZE = 1600;

    /** Use the same app for launching, session discovery and notification artwork.
     * ReVanced wins when both variants are installed, even if the original has
     * a lingering paused/playing session. No version-string dependency. */
    public static String preferredPackage(Context context) {
        try {
            if (context.getPackageManager().getApplicationInfo(REVANCED_PACKAGE, 0).enabled) {
                return REVANCED_PACKAGE;
            }
        } catch (PackageManager.NameNotFoundException ignored) { }
        return YT_PACKAGE;
    }

    public interface Listener { void onState(State state); }

    public static final class State {
        public boolean hasAccess;
        public boolean connected;
        public boolean playing;
        public boolean canPrevious;
        public boolean canNext;
        public boolean canToggle;
        public Bitmap artwork;
        public String title = "";
        public String artist = "";
    }

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MediaSessionManager manager;
    private final ComponentName component;
    private ExecutorService artworkExecutor;
    private MediaController controller;
    private MediaSession.Token destroyedToken;
    private boolean started;
    private boolean sessionsRegistered;
    private Bitmap artwork;
    private String artworkKey = "";
    private String artworkTrackKey = "";
    private int artworkGeneration;

    private final Runnable refreshRunnable = this::refreshSessions;
    private final MediaAccessService.Observer notificationObserver = () -> {
        main.removeCallbacks(refreshRunnable);
        main.post(refreshRunnable);
    };
    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsListener =
            controllers -> refreshSessions();
    private final MediaController.Callback controllerCallback = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata metadata) { updateState(); }
        @Override public void onPlaybackStateChanged(PlaybackState state) { updateState(); }
        @Override public void onSessionDestroyed() {
            destroyedToken = controller == null ? null : controller.getSessionToken();
            detachController();
            refreshSessions();
        }
    };

    public MediaBridge(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        manager = (MediaSessionManager) this.context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        component = new ComponentName(this.context, MediaAccessService.class);
    }

    public static boolean hasAccess(Context context) {
        ComponentName name = new ComponentName(context, MediaAccessService.class);
        try {
            if (Build.VERSION.SDK_INT >= 27) {
                NotificationManager notifications = context.getSystemService(NotificationManager.class);
                return notifications != null && notifications.isNotificationListenerAccessGranted(name);
            }
            String enabled = Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners");
            if (enabled != null) for (String entry : enabled.split(":")) {
                if (name.equals(ComponentName.unflattenFromString(entry))) return true;
            }
        } catch (RuntimeException ignored) { }
        return false;
    }

    /** Call from the activity's onStart, on the main thread. */
    public void start() {
        if (started) { refreshSessions(); return; }
        started = true;
        artworkExecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1), new ThreadPoolExecutor.DiscardOldestPolicy());
        MediaAccessService.addObserver(notificationObserver);
        refreshSessions();
    }

    /** Releases callbacks and decoded artwork while the UI is no longer visible. */
    public void stop() {
        if (!started) return;
        started = false;
        MediaAccessService.removeObserver(notificationObserver);
        main.removeCallbacks(refreshRunnable);
        unregisterSessions();
        detachController();
        if (artworkExecutor != null) artworkExecutor.shutdownNow();
        artworkExecutor = null;
    }

    private void unregisterSessions() {
        if (!sessionsRegistered || manager == null) return;
        try { manager.removeOnActiveSessionsChangedListener(sessionsListener); }
        catch (RuntimeException ignored) { }
        sessionsRegistered = false;
    }

    private void refreshSessions() {
        if (!started) return;
        if (!hasAccess(context)) {
            unregisterSessions();
            detachController();
            emit();
            return;
        }
        List<MediaController> candidates = new ArrayList<>();
        String musicPackage = preferredPackage(context);
        if (manager != null) {
            try {
                if (!sessionsRegistered) {
                    manager.addOnActiveSessionsChangedListener(sessionsListener, component, main);
                    sessionsRegistered = true;
                }
                for (MediaController candidate : manager.getActiveSessions(component)) {
                    if (musicPackage.equals(candidate.getPackageName())) candidates.add(candidate);
                }
            } catch (RuntimeException ignored) { }
        }
        MediaAccessService.Snapshot snapshot = MediaAccessService.snapshot();
        if (snapshot != null && snapshot.token != null) {
            try {
                MediaController notificationController = new MediaController(context, snapshot.token);
                if (musicPackage.equals(notificationController.getPackageName())) candidates.add(notificationController);
            } catch (RuntimeException ignored) { }
        }
        MediaController best = null;
        for (MediaController candidate : candidates) {
            if (candidate.getSessionToken().equals(destroyedToken)) continue;
            if (best == null || (isPlaying(candidate.getPlaybackState()) && !isPlaying(best.getPlaybackState()))) {
                best = candidate;
            }
        }
        if (!sameSession(controller, best)) {
            detachController();
            controller = best;
            if (controller != null) {
                try { controller.registerCallback(controllerCallback, main); }
                catch (RuntimeException ignored) { controller = null; }
            }
        }
        updateState();
    }

    private static boolean sameSession(MediaController first, MediaController second) {
        return first == null ? second == null : second != null && first.getSessionToken().equals(second.getSessionToken());
    }

    private void detachController() {
        if (controller != null) {
            try { controller.unregisterCallback(controllerCallback); }
            catch (RuntimeException ignored) { }
        }
        controller = null;
        artwork = null;
        artworkKey = "";
        artworkTrackKey = "";
        artworkGeneration++;
    }

    private static boolean isPlaying(PlaybackState state) {
        if (state == null) return false;
        int value = state.getState();
        return value == PlaybackState.STATE_PLAYING || value == PlaybackState.STATE_BUFFERING
                || value == PlaybackState.STATE_CONNECTING || value == PlaybackState.STATE_FAST_FORWARDING
                || value == PlaybackState.STATE_REWINDING || value == PlaybackState.STATE_SKIPPING_TO_NEXT
                || value == PlaybackState.STATE_SKIPPING_TO_PREVIOUS || value == PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM;
    }

    private static String title(MediaMetadata metadata) {
        if (metadata == null) return "";
        String title = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        if (TextUtils.isEmpty(title)) title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
        return title == null ? "" : title;
    }

    private void updateState() {
        if (!started) return;
        MediaMetadata metadata = controller == null ? null : controller.getMetadata();
        Bitmap bitmap = null;
        String uri = null;
        if (metadata != null) {
            for (String key : new String[]{MediaMetadata.METADATA_KEY_ALBUM_ART,
                    MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_DISPLAY_ICON}) {
                bitmap = metadata.getBitmap(key);
                if (bitmap != null) break;
            }
            for (String key : new String[]{MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
                    MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI}) {
                String value = metadata.getString(key);
                if (isLocalUri(value)) { uri = value; break; }
            }
        }
        MediaAccessService.Snapshot snapshot = MediaAccessService.snapshot();
        Icon fallbackIcon = null;
        Bitmap fallbackBitmap = null;
        String trackTitle = title(metadata);
        if (controller != null && snapshot != null
                && controller.getSessionToken().equals(snapshot.token)
                && (TextUtils.isEmpty(title(metadata)) || title(metadata).equals(snapshot.title))) {
            fallbackIcon = snapshot.icon;
            fallbackBitmap = snapshot.bitmap;
            if (TextUtils.isEmpty(trackTitle)) trackTitle = snapshot.title;
        }
        String trackKey = (controller == null ? "" : controller.getSessionToken().toString()) + "|"
                + trackTitle + "|" + (metadata == null ? "" : metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID))
                + "|" + (metadata == null ? "" : metadata.getString(MediaMetadata.METADATA_KEY_ALBUM))
                + "|" + (metadata == null ? "" : metadata.getString(MediaMetadata.METADATA_KEY_ARTIST));
        String sourceKey = bitmap != null ? "bitmap:" + System.identityHashCode(bitmap)
                : uri != null ? "uri:" + uri + ":fallback:" + System.identityHashCode(fallbackBitmap)
                        + ":" + System.identityHashCode(fallbackIcon)
                : fallbackBitmap != null ? "notification-bitmap:" + System.identityHashCode(fallbackBitmap)
                : fallbackIcon != null ? "notification-icon:" + System.identityHashCode(fallbackIcon) : "none";
        String key = trackKey + "|" + sourceKey;
        if (!key.equals(artworkKey)) {
            artworkKey = key;
            // A notification is reparceled on pause/play: preserve the same track's image
            // while replacing it, but never display the previous song's cover after a skip.
            if (!trackKey.equals(artworkTrackKey)) artwork = null;
            artworkTrackKey = trackKey;
            final int generation = ++artworkGeneration;
            final Bitmap source = bitmap;
            final String sourceUri = uri;
            final Icon icon = fallbackIcon;
            final Bitmap notificationBitmap = fallbackBitmap;
            if (artworkExecutor != null) try {
                artworkExecutor.submit(() -> {
                    Bitmap decoded = null;
                    try {
                        if (source != null) decoded = shrink(source);
                        if (decoded == null && sourceUri != null) decoded = decodeUri(sourceUri);
                        if (decoded == null && notificationBitmap != null) decoded = shrink(notificationBitmap);
                        if (decoded == null && icon != null) decoded = decodeIcon(icon);
                    } catch (RuntimeException | OutOfMemoryError ignored) { }
                    final Bitmap result = decoded;
                    main.post(() -> {
                        if (!started || generation != artworkGeneration) return;
                        artwork = result;
                        emit();
                    });
                });
            } catch (RejectedExecutionException ignored) { }
        }
        emit();
    }

    private void emit() {
        if (!started) return;
        State state = new State();
        state.hasAccess = hasAccess(context);
        state.connected = state.hasAccess && controller != null;
        if (state.connected) {
            PlaybackState playback = controller.getPlaybackState();
            long actions = playback == null ? 0 : playback.getActions();
            state.playing = isPlaying(playback);
            state.canPrevious = (actions & PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0;
            state.canNext = (actions & PlaybackState.ACTION_SKIP_TO_NEXT) != 0;
            state.canToggle = (actions & (PlaybackState.ACTION_PLAY_PAUSE
                    | (state.playing ? PlaybackState.ACTION_PAUSE : PlaybackState.ACTION_PLAY))) != 0;
            state.title = title(controller.getMetadata());
            MediaMetadata metadata = controller.getMetadata();
            if (metadata != null) {
                state.artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
                if (TextUtils.isEmpty(state.artist)) {
                    state.artist = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
                }
            }
            MediaAccessService.Snapshot snapshot = MediaAccessService.snapshot();
            if (TextUtils.isEmpty(state.title) && snapshot != null
                    && controller.getSessionToken().equals(snapshot.token)) state.title = snapshot.title;
            if (TextUtils.isEmpty(state.artist) && snapshot != null
                    && controller.getSessionToken().equals(snapshot.token)) state.artist = snapshot.artist;
            state.artwork = artwork;
        }
        listener.onState(state);
    }

    public void playPause() {
        if (controller == null || !hasAccess(context)) return;
        try {
            if (isPlaying(controller.getPlaybackState())) controller.getTransportControls().pause();
            else controller.getTransportControls().play();
        } catch (RuntimeException ignored) { refreshSessions(); }
    }

    public void previous() {
        if (controller == null || !hasAccess(context)) return;
        try { controller.getTransportControls().skipToPrevious(); }
        catch (RuntimeException ignored) { refreshSessions(); }
    }

    public void next() {
        if (controller == null || !hasAccess(context)) return;
        try { controller.getTransportControls().skipToNext(); }
        catch (RuntimeException ignored) { refreshSessions(); }
    }

    static boolean pausePreferred(Context context) {
        ArrayList<MediaController> arrayList;
        if (!hasAccess(context)) {
            return false;
        }
        String strPreferredPackage = preferredPackage(context);
        try {
            MediaSessionManager mediaSessionManager = (MediaSessionManager) context.getSystemService(MediaSessionManager.class);
            if (mediaSessionManager == null) {
                arrayList = new ArrayList();
            } else {
                arrayList = new ArrayList(mediaSessionManager.getActiveSessions(new ComponentName(context, (Class<?>) MediaAccessService.class)));
            }
            MediaAccessService.Snapshot snapshot = MediaAccessService.snapshot();
            if (snapshot != null) {
                arrayList.add(new MediaController(context, snapshot.token));
            }
            for (MediaController mediaController : arrayList) {
                if (strPreferredPackage.equals(mediaController.getPackageName()) && isPlaying(mediaController.getPlaybackState())) {
                    mediaController.getTransportControls().pause();
                }
            }
            return true;
        } catch (RuntimeException unused) {
            return false;
        }
    }

    private static boolean isLocalUri(String value) {
        if (TextUtils.isEmpty(value)) return false;
        String scheme = Uri.parse(value).getScheme();
        return "content".equals(scheme) || "android.resource".equals(scheme);
    }

    private Bitmap decodeUri(String value) {
        if (!isLocalUri(value)) return null;
        Uri uri = Uri.parse(value);
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(stream, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / options.inSampleSize > MAX_ART_SIZE) {
                options.inSampleSize *= 2;
            }
            try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(stream, null, options);
            }
        } catch (Exception ignored) { return null; }
    }

    private Bitmap decodeIcon(Icon icon) {
        // An Icon can refer to a URI; never attempt network art or file-system reads.
        if (Build.VERSION.SDK_INT >= 28 && (icon.getType() == Icon.TYPE_URI
                || (Build.VERSION.SDK_INT >= 30 && icon.getType() == Icon.TYPE_URI_ADAPTIVE_BITMAP))) {
            return decodeUri(icon.getUri().toString());
        }
        Drawable drawable = icon.loadDrawable(context);
        if (drawable == null) return null;
        if (drawable instanceof BitmapDrawable) return shrink(((BitmapDrawable) drawable).getBitmap());
        int width = Math.max(1, drawable.getIntrinsicWidth());
        int height = Math.max(1, drawable.getIntrinsicHeight());
        float ratio = Math.min(1f, MAX_ART_SIZE / (float) Math.max(width, height));
        Bitmap bitmap = Bitmap.createBitmap(Math.max(1, Math.round(width * ratio)),
                Math.max(1, Math.round(height * ratio)), Bitmap.Config.ARGB_8888);
        drawable.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
        drawable.draw(new Canvas(bitmap));
        return bitmap;
    }

    private static Bitmap shrink(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) return null;
        int size = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (size <= MAX_ART_SIZE) return bitmap;
        float ratio = MAX_ART_SIZE / (float) size;
        return Bitmap.createScaledBitmap(bitmap, Math.max(1, Math.round(bitmap.getWidth() * ratio)),
                Math.max(1, Math.round(bitmap.getHeight() * ratio)), true);
    }
}
