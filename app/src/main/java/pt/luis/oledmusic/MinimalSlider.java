package pt.luis.oledmusic;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.widget.SeekBar;

final class MinimalSlider {
    MinimalSlider() {
    }

    static SeekBar create(Context context, int i) {
        SeekBar seekBar = new SeekBar(context);
        seekBar.setSplitTrack(false);
        seekBar.setPadding(dp(context, 8), 0, dp(context, 8), 0);
        seekBar.setMinimumHeight(dp(context, 48));
        tint(seekBar, i);
        return seekBar;
    }

    static void tint(SeekBar seekBar, int i) {
        Context context = seekBar.getContext();
        int i2 = (16777215 & i) | 1426063360;
        LayerDrawable layerDrawable = new LayerDrawable(new Drawable[]{shape(i2, dp(context, 2)), new ClipDrawable(shape(i, dp(context, 2)), 8388611, 1)});
        layerDrawable.setId(0, android.R.id.background);
        layerDrawable.setId(1, android.R.id.progress);
        for (int i3 = 0; i3 < 2; i3++) {
            layerDrawable.setLayerHeight(i3, dp(context, 2));
            layerDrawable.setLayerGravity(i3, 16);
        }
        seekBar.setProgressDrawable(layerDrawable);
        seekBar.setProgressTintList(ColorStateList.valueOf(i));
        seekBar.setProgressBackgroundTintList(ColorStateList.valueOf(i2));
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setShape(1);
        gradientDrawable.setColor(i);
        gradientDrawable.setSize(dp(context, 12), dp(context, 12));
        seekBar.setThumb(gradientDrawable);
        seekBar.setThumbTintList(ColorStateList.valueOf(i));
        seekBar.setThumbOffset(dp(context, 6));
    }

    private static GradientDrawable shape(int i, int i2) {
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(i);
        gradientDrawable.setCornerRadius(i2);
        return gradientDrawable;
    }

    private static int dp(Context context, int i) {
        return Math.round(i * context.getResources().getDisplayMetrics().density);
    }
}
