package pt.luis.oledmusic;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class SleepTimerReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        SleepTimer.expire(context, intent);
    }
}
