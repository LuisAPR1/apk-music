package pt.luis.oledmusic;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** One process-wide monitor, shared by the notification listener and visible UI.
 * Watches ALL supported sessions, including players currently paused in the background. */
final class PlaybackMonitor {
    interface Observer { void onSessionsChanged(); }
    private static PlaybackMonitor instance;

    static PlaybackMonitor get(Context context) {
        if (instance == null) instance = new PlaybackMonitor(context.getApplicationContext());
        return instance;
    }

    private final Context context;
    private final MediaSessionManager manager;
    private final ComponentName component;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final PlayerSelection<MediaSession.Token> selection;
    private final Map<MediaSession.Token, Session> sessions = new LinkedHashMap<>();
    private final Set<MediaSession.Token> destroyed = new HashSet<>();
    private final Set<Observer> observers = new LinkedHashSet<>();
    private final MediaSessionManager.OnActiveSessionsChangedListener activeListener = ignored -> refresh();
    private boolean registered;

    private PlaybackMonitor(Context context) {
        this.context = context;
        manager = context.getSystemService(MediaSessionManager.class);
        component = new ComponentName(context, MediaAccessService.class);
        selection = new PlayerSelection<>(context.getSharedPreferences("player_selection", 0)
                .getString("last_package", ""));
    }

    void addObserver(Observer observer) { observers.add(observer); }
    void removeObserver(Observer observer) { observers.remove(observer); }
    String lastPackage() { return selection.lastPackage(); }

    MediaController selectedController() {
        Session session = sessions.get(selection.selected());
        return session == null ? null : session.controller;
    }

    void refresh() {
        if (!MediaBridge.hasAccess(context)) {
            stop();
            return;
        }
        Map<MediaSession.Token, MediaController> candidates = new LinkedHashMap<>();
        try {
            if (manager != null) {
                if (!registered) {
                    manager.addOnActiveSessionsChangedListener(activeListener, component, main);
                    registered = true;
                }
                for (MediaController controller : manager.getActiveSessions(component)) {
                    addCandidate(candidates, controller);
                }
            }
        } catch (RuntimeException ignored) { }
        for (MediaAccessService.Snapshot snapshot : MediaAccessService.snapshots()) {
            try { addCandidate(candidates, new MediaController(context, snapshot.token)); }
            catch (RuntimeException ignored) { }
        }
        // Ignore stale notification tokens after onSessionDestroyed until they disappear.
        destroyed.retainAll(candidates.keySet());
        for (MediaSession.Token token : destroyed) candidates.remove(token);
        for (MediaSession.Token token : new ArrayList<>(sessions.keySet())) {
            if (!candidates.containsKey(token)) sessions.remove(token).close();
        }
        for (Map.Entry<MediaSession.Token, MediaController> item : candidates.entrySet()) {
            Session session = sessions.get(item.getKey());
            try {
                if (session == null) {
                    session = new Session(item.getValue());
                    session.controller.registerCallback(session.callback, main);
                    sessions.put(item.getKey(), session);
                }
                PlaybackState state = session.controller.getPlaybackState();
                selection.update(item.getKey(), session.controller.getPackageName(), isPlaying(state),
                        stateTime(state));
            } catch (RuntimeException ignored) {
                if (session != null) session.close();
                sessions.remove(item.getKey());
            }
        }
        selection.retain(sessions.keySet());
        changed();
    }

    private void addCandidate(Map<MediaSession.Token, MediaController> candidates, MediaController controller) {
        if (MediaBridge.isSupportedPackage(controller.getPackageName())) {
            candidates.put(controller.getSessionToken(), controller);
        }
    }

    void stop() {
        if (registered && manager != null) {
            try { manager.removeOnActiveSessionsChangedListener(activeListener); }
            catch (RuntimeException ignored) { }
        }
        registered = false;
        for (Session session : sessions.values()) session.close();
        sessions.clear();
        destroyed.clear();
        selection.retain(sessions.keySet());
        changed();
    }

    private void changed() {
        // Resolve initial paused sessions before persisting; callbacks run on the main thread.
        selection.selected();
        String last = selection.lastPackage();
        android.content.SharedPreferences store = context.getSharedPreferences("player_selection", 0);
        if (!last.equals(store.getString("last_package", ""))) {
            store.edit().putString("last_package", last).apply();
        }
        for (Observer observer : new ArrayList<>(observers)) observer.onSessionsChanged();
    }

    private static boolean isPlaying(PlaybackState state) {
        if (state == null) return false;
        switch (state.getState()) {
            case PlaybackState.STATE_PLAYING:
            case PlaybackState.STATE_FAST_FORWARDING:
            case PlaybackState.STATE_REWINDING:
            case PlaybackState.STATE_SKIPPING_TO_NEXT:
            case PlaybackState.STATE_SKIPPING_TO_PREVIOUS:
            case PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM: return true;
            default: return false;
        }
    }

    private static long stateTime(PlaybackState state) {
        long now = SystemClock.elapsedRealtime();
        long time = state == null ? 0 : state.getLastPositionUpdateTime();
        // Android timestamps share the elapsedRealtime clock. Some players omit them.
        return time > 0 && time <= now ? time : now;
    }

    private final class Session {
        final MediaController controller;
        final MediaController.Callback callback = new MediaController.Callback() {
            @Override public void onPlaybackStateChanged(PlaybackState state) {
                if (sessions.get(controller.getSessionToken()) != Session.this) return;
                selection.update(controller.getSessionToken(), controller.getPackageName(), isPlaying(state),
                        stateTime(state));
                changed();
            }
            @Override public void onMetadataChanged(MediaMetadata metadata) {
                if (sessions.get(controller.getSessionToken()) == Session.this) changed();
            }
            @Override public void onSessionDestroyed() {
                MediaSession.Token token = controller.getSessionToken();
                if (sessions.get(token) != Session.this) return;
                destroyed.add(token);
                refresh();
            }
        };
        Session(MediaController controller) { this.controller = controller; }
        void close() {
            try { controller.unregisterCallback(callback); }
            catch (RuntimeException ignored) { }
        }
    }
}
