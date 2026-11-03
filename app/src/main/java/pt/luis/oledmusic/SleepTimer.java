package pt.luis.oledmusic;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;

final class SleepTimer {
    static final String ACTION = "pt.luis.oledmusic.SLEEP_TIMER";

    SleepTimer() {
    }

    private static SharedPreferences store(Context context) {
        return context.getSharedPreferences("sleep_timer", 0);
    }

    static boolean canBeExact(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(AlarmManager.class);
        if (alarmManager != null) {
            return Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms();
        }
        return false;
    }

    static long remaining(Context context) {
        SharedPreferences sharedPreferencesStore = store(context);
        if (!sharedPreferencesStore.contains("deadline")) {
            return 0L;
        }
        if (sharedPreferencesStore.getInt("boot", -1) == boot(context)) {
            return Math.max(0L, sharedPreferencesStore.getLong("deadline", 0L) - SystemClock.elapsedRealtime());
        }
        sharedPreferencesStore.edit().clear().apply();
        return 0L;
    }

    static long deadline(Context context) {
        return store(context).getLong("deadline", 0L);
    }

    static boolean isExact(Context context) {
        return store(context).getBoolean("exact", false);
    }

    static String status(Context context) {
        return store(context).getString("status", "Desligado");
    }

    static boolean start(Context context, int i) {
        return schedule(context, SystemClock.elapsedRealtime() + (((long) Math.max(1, Math.min(180, i))) * 60000));
    }

    private static boolean schedule(Context context, long j) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(AlarmManager.class);
        boolean z = false;
        if (alarmManager == null) {
            return false;
        }
        boolean zCanBeExact = canBeExact(context);
        PendingIntent pendingIntentPending = pending(context, j);
        try {
            if (zCanBeExact) {
                alarmManager.setExactAndAllowWhileIdle(2, j, pendingIntentPending);
            } else {
                alarmManager.setAndAllowWhileIdle(2, j, pendingIntentPending);
            }
            z = zCanBeExact;
        } catch (SecurityException unused) {
            alarmManager.setAndAllowWhileIdle(2, j, pendingIntentPending);
        }
        store(context).edit().putLong("deadline", j).putInt("boot", boot(context)).putBoolean("exact", z).putString("status", "Ativo").apply();
        return true;
    }

    static void upgradePrecision(Context context) {
        if (remaining(context) <= 0 || isExact(context) == canBeExact(context)) {
            return;
        }
        schedule(context, deadline(context));
    }

    static void cancel(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(AlarmManager.class);
        if (alarmManager != null) {
            alarmManager.cancel(pending(context, deadline(context)));
        }
        store(context).edit().remove("deadline").putString("status", "Cancelado").apply();
    }

    static void expire(Context context, Intent intent) {
        if (intent == null || !ACTION.equals(intent.getAction())) {
            return;
        }
        long jDeadline = deadline(context);
        if (jDeadline <= 0 || intent.getLongExtra("deadline", -1L) != jDeadline || store(context).getInt("boot", -1) != boot(context) || SystemClock.elapsedRealtime() < jDeadline) {
            return;
        }
        store(context).edit().remove("deadline").putString("status", "A terminar…").apply();
        store(context).edit().putString("status", MediaBridge.pausePreferred(context) ? "Terminado" : "Sem acesso à sessão de música").apply();
    }

    private static PendingIntent pending(Context context, long j) {
        return PendingIntent.getBroadcast(context, 410, new Intent(context, (Class<?>) SleepTimerReceiver.class).setAction(ACTION).putExtra("deadline", j), 201326592);
    }

    private static int boot(Context context) {
        return Settings.Global.getInt(context.getContentResolver(), "boot_count", 0);
    }
}
