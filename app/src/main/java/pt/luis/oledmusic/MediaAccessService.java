package pt.luis.oledmusic;

import android.app.Notification;
import android.content.ComponentName;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.media.session.MediaSession;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Only supported players' media notifications are inspected or retained. */
public final class MediaAccessService extends NotificationListenerService {
    static final class Snapshot {
        final MediaSession.Token token;
        final Icon icon;
        final Bitmap bitmap;
        final String title;
        final String artist;

        Snapshot(MediaSession.Token token, Icon icon, Bitmap bitmap, String title, String artist) {
            this.token = token;
            this.icon = icon;
            this.bitmap = bitmap;
            this.title = title;
            this.artist = artist;
        }
    }

    private static volatile List<Snapshot> snapshots = Collections.emptyList();
    private boolean connected;

    static List<Snapshot> snapshots() { return snapshots; }
    static Snapshot snapshot(MediaSession.Token token) {
        if (token != null) for (Snapshot item : snapshots) {
            if (token.equals(item.token)) return item;
        }
        return null;
    }

    @Override public void onListenerConnected() {
        super.onListenerConnected();
        connected = true;
        refresh();
    }

    @Override public void onListenerDisconnected() {
        connected = false;
        snapshots = Collections.emptyList();
        PlaybackMonitor.get(this).stop();
        super.onListenerDisconnected();
        if (MediaBridge.hasAccess(this)) {
            try { requestRebind(new ComponentName(this, MediaAccessService.class)); }
            catch (RuntimeException ignored) { }
        }
    }

    @Override public void onDestroy() {
        connected = false;
        snapshots = Collections.emptyList();
        PlaybackMonitor.get(this).stop();
        super.onDestroy();
    }

    @Override public void onNotificationPosted(StatusBarNotification notification) {
        if (MediaBridge.isSupportedPackage(notification.getPackageName())) refresh();
    }

    @Override public void onNotificationRemoved(StatusBarNotification notification) {
        if (MediaBridge.isSupportedPackage(notification.getPackageName())) refresh();
    }

    private void refresh() {
        if (!connected) return;
        Map<MediaSession.Token, Snapshot> found = new LinkedHashMap<>();
        Map<MediaSession.Token, Long> times = new LinkedHashMap<>();
        try {
            StatusBarNotification[] active = getActiveNotifications();
            if (active != null) for (StatusBarNotification item : active) {
                if (!MediaBridge.isSupportedPackage(item.getPackageName())) continue;
                Notification notification = item.getNotification();
                Bundle extras = notification.extras;
                if (extras == null) continue;
                Object rawToken = extras.getParcelable(Notification.EXTRA_MEDIA_SESSION);
                if (!(rawToken instanceof MediaSession.Token)) continue;
                MediaSession.Token token = (MediaSession.Token) rawToken;
                Long newest = times.get(token);
                if (newest != null && item.getPostTime() < newest) continue;
                Object rawBitmap = extras.getParcelable(Notification.EXTRA_LARGE_ICON);
                CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
                CharSequence artist = extras.getCharSequence(Notification.EXTRA_TEXT);
                found.put(token, new Snapshot(token, notification.getLargeIcon(),
                        rawBitmap instanceof Bitmap ? (Bitmap) rawBitmap : null,
                        title == null ? "" : title.toString(), artist == null ? "" : artist.toString()));
                times.put(token, item.getPostTime());
            }
        } catch (RuntimeException ignored) { }
        snapshots = Collections.unmodifiableList(new ArrayList<>(found.values()));
        PlaybackMonitor.get(this).refresh();
    }
}
