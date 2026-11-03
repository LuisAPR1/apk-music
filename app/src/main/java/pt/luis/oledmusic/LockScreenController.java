package pt.luis.oledmusic;

import android.app.Activity;
import android.os.Build;
import android.os.PowerManager;

final class LockScreenController {
    private final Activity activity;
    private boolean connected;
    private boolean enabled;
    private final PowerManager power;
    private Boolean showing;
    private boolean userLeft;

    LockScreenController(Activity activity) {
        this.activity = activity;
        this.power = (PowerManager) activity.getSystemService(PowerManager.class);
        apply(false);
    }

    void onForeground(boolean z) {
        this.userLeft = false;
        setEnabled(z);
    }

    void setEnabled(boolean z) {
        this.enabled = z;
        update();
    }

    void onMediaState(boolean z) {
        this.connected = z;
        update();
    }

    void onUserLeave() {
        this.userLeft = true;
        update();
    }

    void onStopped() {
        if (keepMonitoringWhileScreenOff()) {
            return;
        }
        onUserLeave();
    }

    boolean keepMonitoringWhileScreenOff() {
        PowerManager powerManager;
        return (!this.enabled || !this.connected || this.userLeft || (powerManager = this.power) == null || powerManager.isInteractive()) ? false : true;
    }

    void destroy() {
        apply(false);
    }

    private void update() {
        apply(this.enabled && this.connected && !this.userLeft);
    }

    private void apply(boolean z) {
        Boolean bool = this.showing;
        if (bool == null || z != bool.booleanValue()) {
            this.showing = Boolean.valueOf(z);
            if (Build.VERSION.SDK_INT >= 27) {
                this.activity.setShowWhenLocked(z);
            } else if (z) {
                this.activity.getWindow().addFlags(524288);
            } else {
                this.activity.getWindow().clearFlags(524288);
            }
        }
    }
}
