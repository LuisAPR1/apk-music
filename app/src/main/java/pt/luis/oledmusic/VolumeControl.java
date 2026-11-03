package pt.luis.oledmusic;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Build;
import android.widget.SeekBar;
import android.widget.Toast;

final class VolumeControl implements SeekBar.OnSeekBarChangeListener {
    private final Activity activity;
    private final AudioManager audio;
    private boolean dragging;
    private int initial;
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (VolumeControl.this.dragging) {
                return;
            }
            VolumeControl.this.sync();
        }
    };
    private boolean registered;
    private final SeekBar slider;

    VolumeControl(Activity activity, SeekBar seekBar) {
        this.activity = activity;
        this.slider = seekBar;
        this.audio = (AudioManager) activity.getSystemService(AudioManager.class);
        seekBar.setContentDescription("Volume multimédia");
        seekBar.setOnSeekBarChangeListener(this);
    }

    void resume() {
        if (!this.registered) {
            IntentFilter intentFilter = new IntentFilter("android.media.VOLUME_CHANGED_ACTION");
            if (Build.VERSION.SDK_INT >= 33) {
                this.activity.registerReceiver(this.receiver, intentFilter, 2);
            } else {
                this.activity.registerReceiver(this.receiver, intentFilter);
            }
            this.registered = true;
        }
        sync();
    }

    void pause() {
        if (this.registered) {
            this.activity.unregisterReceiver(this.receiver);
        }
        this.registered = false;
        this.dragging = false;
    }

    public void sync() {
        AudioManager audioManager = this.audio;
        if (audioManager == null) {
            this.slider.setEnabled(false);
            return;
        }
        this.slider.setMax(audioManager.getStreamMaxVolume(3));
        this.slider.setMin(Build.VERSION.SDK_INT >= 28 ? this.audio.getStreamMinVolume(3) : 0);
        this.slider.setEnabled(!this.audio.isVolumeFixed());
        this.slider.setProgress(this.audio.getStreamVolume(3));
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        this.dragging = true;
        AudioManager audioManager = this.audio;
        this.initial = audioManager == null ? 0 : audioManager.getStreamVolume(3);
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int i, boolean z) {
        AudioManager audioManager;
        if (!z || (audioManager = this.audio) == null || audioManager.isVolumeFixed()) {
            return;
        }
        try {
            this.audio.setStreamVolume(3, i, 0);
        } catch (SecurityException unused) {
            Toast.makeText(this.activity, "O Android não permitiu alterar o volume.", 0).show();
            sync();
        }
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        this.dragging = false;
        sync();
    }

    void cancelTouch() {
        AudioManager audioManager;
        if (!this.dragging || (audioManager = this.audio) == null) {
            return;
        }
        try {
            audioManager.setStreamVolume(3, this.initial, 0);
        } catch (SecurityException unused) {
        }
        this.dragging = false;
        sync();
    }
}
