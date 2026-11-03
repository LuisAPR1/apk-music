package pt.luis.oledmusic;

import android.app.Activity;
import android.app.KeyguardManager;
import android.widget.Toast;
import java.util.WeakHashMap;

final class DeviceUnlock {
    private static final WeakHashMap<Activity, Boolean> pending = new WeakHashMap<>();

    DeviceUnlock() {
    }

    static boolean isPending(Activity activity) {
        return pending.containsKey(activity);
    }

    static void run(final Activity activity, final Runnable runnable) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        KeyguardManager keyguardManager = (KeyguardManager) activity.getSystemService(KeyguardManager.class);
        if (keyguardManager == null || !keyguardManager.isKeyguardLocked()) {
            runnable.run();
        } else {
            if (isPending(activity)) {
                return;
            }
            pending.put(activity, true);
            keyguardManager.requestDismissKeyguard(activity, new KeyguardManager.KeyguardDismissCallback() {
                @Override
                public void onDismissSucceeded() {
                    DeviceUnlock.pending.remove(activity);
                    if (activity.isFinishing() || activity.isDestroyed()) {
                        return;
                    }
                    runnable.run();
                }

                @Override
                public void onDismissError() {
                    DeviceUnlock.pending.remove(activity);
                    if (activity.isFinishing() || activity.isDestroyed()) {
                        return;
                    }
                    Toast.makeText(activity, "Desbloqueia o telemóvel para continuar.", 0).show();
                }

                @Override
                public void onDismissCancelled() {
                    DeviceUnlock.pending.remove(activity);
                }
            });
        }
    }
}
