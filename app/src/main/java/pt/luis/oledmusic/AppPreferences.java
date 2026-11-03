package pt.luis.oledmusic;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.Serializable;

final class AppPreferences implements Serializable {
    private static final long serialVersionUID = 1;
    int controlsDim;
    int coverDim;
    boolean coverGestures;
    int fontFamily;
    int fontStyle;
    boolean showArtist;
    boolean showTitle;
    boolean showVolume;
    int controlsColor = -1;
    int brightnessColor = -1;
    boolean showBrightness = true;
    boolean showOnLockScreen = true;
    int albumPercent = 85;
    boolean showControls = true;
    int volumeColor = -1;
    int titleColor = -1;
    int artistColor = -4473925;
    int titleSize = 20;
    int artistSize = 16;
    int textPosition = 1;

    AppPreferences() {
    }

    static AppPreferences load(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("appearance", 0);
        AppPreferences appPreferences = new AppPreferences();
        appPreferences.controlsColor = sharedPreferences.getInt("controls_color", -1) | (-16777216);
        appPreferences.brightnessColor = sharedPreferences.getInt("brightness_color", -1) | (-16777216);
        appPreferences.showBrightness = sharedPreferences.getBoolean("show_brightness", true);
        appPreferences.showOnLockScreen = sharedPreferences.getBoolean("show_on_lock_screen", true);
        appPreferences.albumPercent = Math.max(50, Math.min(100, sharedPreferences.getInt("album_percent", 85)));
        boolean z = sharedPreferences.getBoolean("cover_gestures", false);
        appPreferences.coverGestures = z;
        appPreferences.showControls = !z || sharedPreferences.getBoolean("show_controls", true);
        appPreferences.coverDim = clamp(sharedPreferences.getInt("cover_dim", 0), 0, 90);
        appPreferences.controlsDim = clamp(sharedPreferences.getInt("controls_dim", 0), 0, 85);
        appPreferences.showVolume = sharedPreferences.getBoolean("show_volume", false);
        appPreferences.volumeColor = sharedPreferences.getInt("volume_color", -1) | (-16777216);
        appPreferences.showTitle = sharedPreferences.getBoolean("show_title", false);
        appPreferences.showArtist = sharedPreferences.getBoolean("show_artist", false);
        appPreferences.titleColor = sharedPreferences.getInt("title_color", -1) | (-16777216);
        appPreferences.artistColor = sharedPreferences.getInt("artist_color", -4473925) | (-16777216);
        appPreferences.titleSize = clamp(sharedPreferences.getInt("title_size", 20), 12, 36);
        appPreferences.artistSize = clamp(sharedPreferences.getInt("artist_size", 16), 12, 32);
        appPreferences.textPosition = clamp(sharedPreferences.getInt("text_position", 1), 0, 2);
        appPreferences.fontFamily = clamp(sharedPreferences.getInt("font_family", 0), 0, 3);
        appPreferences.fontStyle = clamp(sharedPreferences.getInt("font_style", 0), 0, 3);
        return appPreferences;
    }

    void save(Context context) {
        if (!this.coverGestures) {
            this.showControls = true;
        }
        context.getSharedPreferences("appearance", 0).edit().putInt("controls_color", this.controlsColor).putInt("brightness_color", this.brightnessColor).putBoolean("show_brightness", this.showBrightness).putInt("album_percent", this.albumPercent).putBoolean("show_on_lock_screen", this.showOnLockScreen).putBoolean("cover_gestures", this.coverGestures).putBoolean("show_controls", this.showControls).putInt("cover_dim", this.coverDim).putInt("controls_dim", this.controlsDim).putBoolean("show_volume", this.showVolume).putInt("volume_color", this.volumeColor).putBoolean("show_title", this.showTitle).putBoolean("show_artist", this.showArtist).putInt("title_color", this.titleColor).putInt("artist_color", this.artistColor).putInt("title_size", this.titleSize).putInt("artist_size", this.artistSize).putInt("text_position", this.textPosition).putInt("font_family", this.fontFamily).putInt("font_style", this.fontStyle).apply();
    }

    private static int clamp(int i, int i2, int i3) {
        return Math.max(i2, Math.min(i3, i));
    }
}
