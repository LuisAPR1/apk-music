package pt.luis.oledmusic;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.function.Consumer;

/** Compact cover/video selector. The caller decides whether video is available. */
final class MediaModeSwitch extends LinearLayout {
    private final TextView cover;
    private final TextView video;
    private boolean videoSelected;
    private Consumer<Boolean> onModeChanged;

    MediaModeSwitch(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setMinimumHeight(dp(56));
        setPadding(dp(4), dp(4), dp(4), dp(4));
        setBackground(pill(Color.rgb(20, 20, 20), Color.rgb(40, 40, 40)));
        setContentDescription("Modo de apresentação da música");
        cover = segment(context, "Capa", false);
        video = segment(context, "Vídeo", true);
        addView(cover, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1));
        addView(video, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1));
        setVideoSelected(false);
    }

    /** Updates the selected segment without notifying the listener. */
    void setVideoSelected(boolean selected) {
        videoSelected = selected;
        styleSegment(cover, !selected, "Capa do álbum");
        styleSegment(video, selected, "Vídeo da música");
    }

    void setOnModeChanged(Consumer<Boolean> listener) {
        onModeChanged = listener;
    }

    private TextView segment(Context context, String text, boolean selectsVideo) {
        TextView segment = new TextView(context);
        segment.setText(text);
        segment.setTextSize(15);
        segment.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        segment.setGravity(Gravity.CENTER);
        segment.setMinHeight(dp(48));
        segment.setPadding(dp(12), dp(8), dp(12), dp(8));
        segment.setFocusable(true);
        segment.setOnClickListener(v -> {
            if (videoSelected == selectsVideo) return;
            setVideoSelected(selectsVideo);
            if (onModeChanged != null) onModeChanged.accept(selectsVideo);
        });
        segment.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @SuppressWarnings("deprecation") // Boolean checked state also supports Android 8.
            @Override public void onInitializeAccessibilityNodeInfo(View host,
                    AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName("android.widget.RadioButton");
                info.setCheckable(true);
                info.setChecked(host.isSelected());
            }
        });
        return segment;
    }

    private void styleSegment(TextView segment, boolean selected, String description) {
        segment.setSelected(selected);
        segment.setTextColor(selected ? Color.BLACK : Color.rgb(153, 153, 153));
        GradientDrawable background = pill(selected ? Color.rgb(238, 238, 238) : Color.TRANSPARENT, 0);
        GradientDrawable mask = pill(Color.WHITE, 0);
        segment.setBackground(new RippleDrawable(ColorStateList.valueOf(
                selected ? 0x22000000 : 0x22FFFFFF), background, mask));
        segment.setContentDescription(description + (selected ? ". Selecionado" : ""));
    }

    private GradientDrawable pill(int color, int stroke) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(100));
        if (stroke != 0) shape.setStroke(dp(1), stroke);
        return shape;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
