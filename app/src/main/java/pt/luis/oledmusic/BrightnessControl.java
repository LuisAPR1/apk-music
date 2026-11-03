package pt.luis.oledmusic;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.SeekBar;
import android.widget.Toast;

final class BrightnessControl implements SeekBar.OnSeekBarChangeListener {
    private final Activity activity;
    private boolean cancelled;
    private boolean changedDuringTouch;
    private boolean dragging;
    private int initialBrightness;
    private int initialMode;
    private final ContentObserver observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override
        public void onChange(boolean z) {
            if (BrightnessControl.this.dragging) {
                return;
            }
            BrightnessControl.this.sync();
        }
    };
    private boolean observing;
    private AlertDialog permissionDialog;
    private boolean permitted;
    private final SeekBar slider;
    private boolean writeFailed;

    BrightnessControl(Activity activity, SeekBar seekBar) {
        this.activity = activity;
        this.slider = seekBar;
        seekBar.setMax(100);
        seekBar.setContentDescription("Brilho do telemóvel");
        seekBar.setOnSeekBarChangeListener(this);
    }

    void resume() {
        if (!this.observing) {
            this.activity.getContentResolver().registerContentObserver(Settings.System.getUriFor("screen_brightness"), false, this.observer);
            this.observing = true;
        }
        sync();
    }

    void pause() {
        if (this.observing) {
            this.activity.getContentResolver().unregisterContentObserver(this.observer);
        }
        this.observing = false;
        this.dragging = false;
        this.cancelled = true;
        AlertDialog alertDialog = this.permissionDialog;
        if (alertDialog != null) {
            alertDialog.dismiss();
        }
    }

    public void sync() {
        this.slider.setProgress(Math.round((Math.max(0, Math.min(255, Settings.System.getInt(this.activity.getContentResolver(), "screen_brightness", 128))) * 100.0f) / 255.0f));
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        this.dragging = true;
        this.cancelled = false;
        this.changedDuringTouch = false;
        this.initialBrightness = Settings.System.getInt(this.activity.getContentResolver(), "screen_brightness", 128);
        this.initialMode = Settings.System.getInt(this.activity.getContentResolver(), "screen_brightness_mode", 0);
        this.permitted = Settings.System.canWrite(this.activity);
        this.writeFailed = false;
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int i, boolean z) {
        if (z) {
            if (!this.dragging) {
                this.permitted = Settings.System.canWrite(this.activity);
            }
            if (this.permitted) {
                try {
                    if (Settings.System.getInt(this.activity.getContentResolver(), "screen_brightness_mode", 0) != 0) {
                        if (!Settings.System.putInt(this.activity.getContentResolver(), "screen_brightness_mode", 0)) {
                            this.writeFailed = true;
                            return;
                        } else if (this.dragging) {
                            this.changedDuringTouch = true;
                        }
                    }
                    boolean zPutInt = Settings.System.putInt(this.activity.getContentResolver(), "screen_brightness", Math.max(1, Math.round((i * 255.0f) / 100.0f)));
                    this.writeFailed = !zPutInt;
                    if (this.dragging && zPutInt) {
                        this.changedDuringTouch = true;
                    }
                } catch (SecurityException unused) {
                    this.writeFailed = true;
                }
                if (this.dragging || !this.writeFailed) {
                    return;
                }
                reportFailure();
                return;
            }
            if (this.dragging) {
                return;
            }
            sync();
            requestAccess();
        }
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        this.dragging = false;
        sync();
        if (this.cancelled) {
            return;
        }
        if (!this.permitted) {
            requestAccess();
        } else if (this.writeFailed) {
            reportFailure();
        }
    }

    void cancelTouch() {
        if (this.cancelled) {
            return;
        }
        this.cancelled = true;
        if (this.dragging && this.changedDuringTouch) {
            try {
                boolean zPutInt = Settings.System.putInt(this.activity.getContentResolver(), "screen_brightness", this.initialBrightness);
                if (Settings.System.putInt(this.activity.getContentResolver(), "screen_brightness_mode", this.initialMode) && zPutInt) {
                    return;
                }
                reportFailure();
            } catch (SecurityException unused) {
                reportFailure();
            }
        }
    }

    private void reportFailure() {
        sync();
        Toast.makeText(this.activity, "Não foi possível alterar o brilho do telemóvel.", 0).show();
    }

    private void requestAccess() {
        if (this.activity.isFinishing() || this.activity.isDestroyed()) {
            return;
        }
        AlertDialog alertDialog = this.permissionDialog;
        if (alertDialog == null || !alertDialog.isShowing()) {
            this.permissionDialog = new AlertDialog.Builder(this.activity).setTitle("Controlar o brilho").setMessage("Permite modificar as definições do sistema para usar esta barra. Ao ajustar o brilho, a app muda o telemóvel para brilho manual. Podes voltar a ativar o brilho adaptável nas definições do Android.").setNegativeButton("Agora não", (DialogInterface.OnClickListener) null).setPositiveButton("Permitir", new DialogInterface.OnClickListener() {
                @Override
                public final void onClick(DialogInterface dialogInterface, int i) {
                    BrightnessControl.this.requestBrightnessSettingsAfterUnlock(dialogInterface, i);
                }
            }).show();
        }
    }

    void requestBrightnessSettingsAfterUnlock(DialogInterface dialogInterface, int i) {
        DeviceUnlock.run(this.activity, new Runnable() {
            @Override
            public final void run() {
                BrightnessControl.this.openSystemBrightnessSettings();
            }
        });
    }

    void openSystemBrightnessSettings() {
        try {
            this.activity.startActivity(new Intent("android.settings.action.MANAGE_WRITE_SETTINGS", Uri.parse("package:" + this.activity.getPackageName())));
        } catch (ActivityNotFoundException unused) {
            Toast.makeText(this.activity, "Abre as definições do Android e permite modificar as definições do sistema para OLED Music.", 1).show();
        }
    }
}
