package pt.luis.oledmusic;

import android.app.Notification;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.media.session.MediaSession;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.concurrent.CopyOnWriteArraySet;

/** System-bound listener. Only YouTube Music notification data is inspected or retained. */
public final class MediaAccessService extends NotificationListenerService {
    interface Observer { void onMediaNotificationChanged(); }

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

    private static final CopyOnWriteArraySet<Observer> OBSERVERS = new CopyOnWriteArraySet<>();
    private static volatile Snapshot snapshot;
    private boolean connected;

    static Snapshot snapshot() { return snapshot; }
    static void addObserver(Observer observer) { OBSERVERS.add(observer); }
    static void removeObserver(Observer observer) { OBSERVERS.remove(observer); }

    private static void publish(Snapshot value) {
        snapshot = value;
        for (Observer observer : OBSERVERS) observer.onMediaNotificationChanged();
    }

    @Override public void onListenerConnected() {
        super.onListenerConnected();
        connected = true;
        refresh();
    }

    @Override public void onListenerDisconnected() {
        connected = false;
        publish(null);
        super.onListenerDisconnected();
        if (MediaBridge.hasAccess(this)) {
            try { requestRebind(new android.content.ComponentName(this, MediaAccessService.class)); }
            catch (RuntimeException ignored) { }
        }
    }

    @Override public void onDestroy() {
        connected = false;
        publish(null);
        super.onDestroy();
    }

    @Override public void onNotificationPosted(StatusBarNotification notification) {
        if (MediaBridge.preferredPackage(this).equals(notification.getPackageName())) refresh();
    }

    @Override public void onNotificationRemoved(StatusBarNotification notification) {
        if (MediaBridge.preferredPackage(this).equals(notification.getPackageName())) refresh();
    }

    private void refresh() {
        if (!connected) return;
        Snapshot best = null;
        long newest = Long.MIN_VALUE;
        String musicPackage = MediaBridge.preferredPackage(this);
        try {
            StatusBarNotification[] active = getActiveNotifications();
            if (active != null) for (StatusBarNotification item : active) {
                if (!musicPackage.equals(item.getPackageName())) continue;
                Notification notification = item.getNotification();
                Bundle extras = notification.extras;
                if (extras == null) continue;
                Object rawToken = extras.getParcelable(Notification.EXTRA_MEDIA_SESSION);
                if (!(rawToken instanceof MediaSession.Token)) continue;
                if (item.getPostTime() < newest) continue;
                Object rawBitmap = extras.getParcelable(Notification.EXTRA_LARGE_ICON);
                CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
                CharSequence artist = extras.getCharSequence(Notification.EXTRA_TEXT);
                best = new Snapshot((MediaSession.Token) rawToken, notification.getLargeIcon(),
                        rawBitmap instanceof Bitmap ? (Bitmap) rawBitmap : null,
                        title == null ? "" : title.toString(), artist == null ? "" : artist.toString());
                newest = item.getPostTime();
            }
        } catch (RuntimeException ignored) {
            // Access can be revoked while Android is delivering a callback.
        }
        publish(best);
    }
}
