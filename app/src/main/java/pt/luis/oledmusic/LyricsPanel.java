package pt.luis.oledmusic;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.LongConsumer;

/** A small OLED-friendly lyric reader; timing and player transport stay with the caller. */
final class LyricsPanel extends LinearLayout {
    private static final int MUTED = Color.rgb(120, 120, 120);
    private final TextView heading;
    private final TextView state;
    private final TextView followButton;
    private final FollowScrollView scroll;
    private final LinearLayout linesColumn;
    private final List<TextView> rows = new ArrayList<>();
    private LyricTimeline timeline;
    private LongConsumer onSeek;
    private Typeface lyricTypeface = Typeface.DEFAULT;
    private int textSizeSp = 23;
    private int activeColor = Color.WHITE;
    private int lineSpacingDp = 10;
    private int alignment;
    private int activeIndex = -1;
    private long positionMs;
    private boolean canSeek;
    private boolean tapToSeek = true;
    private boolean following = true;

    private final Runnable centerActive = new Runnable() {
        @Override public void run() {
            if (!following || timeline == null || !timeline.isSynced()
                    || activeIndex < 0 || activeIndex >= rows.size()
                    || scroll.getHeight() == 0) return;
            TextView active = rows.get(activeIndex);
            if (active.getHeight() == 0) return;
            // Extra space lets the very first and last lines reach the middle too.
            int top = Math.max(dp(12), (scroll.getHeight() - rows.get(0).getHeight()) / 2);
            int bottom = Math.max(dp(12), (scroll.getHeight()
                    - rows.get(rows.size() - 1).getHeight()) / 2);
            if (linesColumn.getPaddingTop() != top || linesColumn.getPaddingBottom() != bottom) {
                linesColumn.setPadding(dp(8), top, dp(8), bottom);
                return; // The layout listener schedules centering after the new padding is laid out.
            }
            int target = active.getTop() + active.getHeight() / 2 - scroll.getHeight() / 2;
            scroll.smoothScrollTo(0, Math.max(0, target));
        }
    };

    LyricsPanel(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setBackgroundColor(Color.BLACK);
        setMinimumHeight(dp(160));

        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(8), dp(8), dp(8));
        header.setBackground(rounded(Color.rgb(10, 10, 10), Color.rgb(34, 34, 34), 16));
        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(VERTICAL);
        heading = new TextView(context);
        heading.setText("Letras");
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(15);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        labels.addView(heading);
        state = new TextView(context);
        state.setTextColor(MUTED);
        state.setTextSize(12);
        state.setVisibility(GONE);
        labels.addView(state);
        header.addView(labels, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));

        followButton = new TextView(context);
        followButton.setText("Seguir");
        followButton.setTextColor(Color.WHITE);
        followButton.setTextSize(13);
        followButton.setGravity(Gravity.CENTER);
        followButton.setPadding(dp(14), dp(10), dp(14), dp(10));
        followButton.setMinHeight(dp(48));
        followButton.setBackground(rounded(Color.rgb(27, 27, 27), Color.rgb(48, 48, 48), 24));
        followButton.setContentDescription("Voltar a seguir a letra da música em reprodução");
        followButton.setOnClickListener(v -> {
            setFollowing(true);
            scheduleCenter();
        });
        followButton.setVisibility(GONE);
        header.addView(followButton, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        addView(header, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        scroll = new FollowScrollView(context);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(OVER_SCROLL_NEVER);
        scroll.setContentDescription("Letras da música");
        linesColumn = new LinearLayout(context);
        linesColumn.setOrientation(VERTICAL);
        linesColumn.setGravity(Gravity.CENTER_VERTICAL);
        linesColumn.setPadding(dp(8), dp(24), dp(8), dp(24));
        scroll.addView(linesColumn, new ScrollView.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
        linesColumn.addOnLayoutChangeListener((v, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> scheduleCenter());
        scroll.addOnLayoutChangeListener((v, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop)
                scheduleCenter();
        });
        setStatus("As letras aparecem aqui quando houver uma música.");
    }

    void setTimeline(LyricTimeline value) {
        if (timeline == value && value != null) return;
        if (value == null || value.lines().isEmpty()) {
            setStatus("Letras indisponíveis para esta música.");
            return;
        }
        cancelCenter();
        timeline = value;
        activeIndex = -1;
        rows.clear();
        linesColumn.removeAllViews();
        linesColumn.setGravity(Gravity.TOP);
        linesColumn.setPadding(dp(8), dp(24), dp(8), dp(24));
        state.setText(value.isSynced() ? "Sincronizadas" : "Texto");
        state.setVisibility(VISIBLE);
        following = true;
        followButton.setVisibility(GONE);
        List<LyricTimeline.Line> lines = value.lines();
        for (int i = 0; i < lines.size(); i++) {
            final int index = i;
            TextView row = new TextView(getContext());
            String text = lines.get(i).text;
            row.setText(text.isEmpty() && value.isSynced() ? "♪" : text);
            row.setOnClickListener(v -> seekTo(index));
            rows.add(row);
            applyRowStyle(row, i);
            updateRowInteraction(row, i);
            linesColumn.addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }
        scroll.scrollTo(0, 0);
        updatePosition(positionMs, canSeek);
    }

    /** Replaces any old lyrics immediately, for loading, unavailable or instrumental states. */
    void setStatus(String message) {
        cancelCenter();
        timeline = null;
        activeIndex = -1;
        rows.clear();
        linesColumn.removeAllViews();
        linesColumn.setGravity(Gravity.CENTER);
        linesColumn.setPadding(dp(16), dp(24), dp(16), dp(24));
        state.setVisibility(GONE);
        following = true;
        followButton.setVisibility(GONE);
        TextView status = new TextView(getContext());
        status.setText(message == null ? "Letras indisponíveis para esta música." : message);
        status.setTypeface(lyricTypeface);
        status.setTextSize(Math.min(18, textSizeSp));
        status.setTextColor(MUTED);
        status.setGravity(Gravity.CENTER);
        status.setLineSpacing(dp(5), 1);
        linesColumn.addView(status, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        scroll.scrollTo(0, 0);
    }

    void updatePosition(long value, boolean seekAvailable) {
        positionMs = Math.max(0, value);
        if (canSeek != seekAvailable) {
            canSeek = seekAvailable;
            updateInteractions();
        }
        if (timeline == null || !timeline.isSynced()) return;
        int next = timeline.activeIndex(positionMs);
        if (next == activeIndex) return;
        int previous = activeIndex;
        activeIndex = next;
        updateHighlight(previous);
        updateHighlight(next);
        scheduleCenter();
    }

    void setStyle(Typeface typeface, int sizeSp, int color, int spacingDp,
            int textAlignment, boolean allowTapToSeek) {
        lyricTypeface = typeface == null ? Typeface.DEFAULT : typeface;
        textSizeSp = Math.max(12, Math.min(48, sizeSp));
        activeColor = color | Color.BLACK;
        lineSpacingDp = Math.max(0, Math.min(40, spacingDp));
        alignment = Math.max(0, Math.min(2, textAlignment));
        tapToSeek = allowTapToSeek;
        heading.setTypeface(lyricTypeface, Typeface.BOLD);
        state.setTypeface(lyricTypeface);
        followButton.setTypeface(lyricTypeface);
        for (int i = 0; i < rows.size(); i++) {
            applyRowStyle(rows.get(i), i);
            updateRowInteraction(rows.get(i), i);
        }
        if (timeline == null && linesColumn.getChildCount() == 1) {
            TextView status = (TextView) linesColumn.getChildAt(0);
            status.setTypeface(lyricTypeface);
            status.setTextSize(Math.min(18, textSizeSp));
        }
        scheduleCenter();
    }

    void setOnSeek(LongConsumer callback) {
        onSeek = callback;
        updateInteractions();
    }

    void reset() {
        positionMs = 0;
        canSeek = false;
        setStatus("As letras aparecem aqui quando houver uma música.");
    }

    private void applyRowStyle(TextView row, int index) {
        row.setTypeface(lyricTypeface);
        row.setTextSize(textSizeSp);
        row.setTextColor(timeline != null && (!timeline.isSynced() || (activeIndex >= 0
                && timeline.timeAt(index) == timeline.timeAt(activeIndex)))
                ? activeColor : MUTED);
        row.setGravity((alignment == 1 ? Gravity.LEFT : alignment == 2 ? Gravity.RIGHT : Gravity.CENTER_HORIZONTAL)
                | Gravity.CENTER_VERTICAL);
        row.setPadding(dp(6), dp(lineSpacingDp / 2 + 5), dp(6), dp((lineSpacingDp + 1) / 2 + 5));
        row.setLineSpacing(dp(4), 1);
        row.setMinHeight(dp(48));
    }

    private void updateHighlight(int index) {
        if (index < 0 || index >= rows.size()) return;
        long time = timeline.timeAt(index);
        boolean active = activeIndex >= 0 && timeline.timeAt(activeIndex) == time;
        while (index > 0 && timeline.timeAt(index - 1) == time) index--;
        while (index < rows.size() && timeline.timeAt(index) == time) {
            TextView row = rows.get(index++);
            row.setTextColor(active ? activeColor : MUTED);
            row.setSelected(active);
        }
    }

    private void updateInteractions() {
        for (int i = 0; i < rows.size(); i++) updateRowInteraction(rows.get(i), i);
    }

    private void updateRowInteraction(TextView row, int index) {
        boolean enabled = timeline != null && timeline.isSynced() && tapToSeek && canSeek && onSeek != null;
        row.setClickable(enabled);
        row.setFocusable(enabled);
        String text = row.getText().toString();
        if (enabled) {
            long seconds = Math.max(0, timeline.timeAt(index)) / 1000;
            row.setContentDescription(text + ". Reproduzir a partir de "
                    + String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60));
        } else {
            row.setContentDescription(text);
        }
    }

    private void seekTo(int index) {
        if (timeline == null || !timeline.isSynced() || !tapToSeek || !canSeek || onSeek == null) return;
        long target = timeline.timeAt(index);
        if (target < 0) return;
        setFollowing(true);
        updatePosition(target, canSeek);
        scheduleCenter();
        onSeek.accept(target);
    }

    private void setFollowing(boolean value) {
        following = value;
        followButton.setVisibility(!value && timeline != null && timeline.isSynced() ? VISIBLE : GONE);
        if (!value) cancelCenter();
    }

    private void scheduleCenter() {
        cancelCenter();
        if (following && isAttachedToWindow()) scroll.post(centerActive);
    }

    private void cancelCenter() {
        if (scroll != null) scroll.removeCallbacks(centerActive);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        scheduleCenter();
    }

    @Override protected void onDetachedFromWindow() {
        cancelCenter();
        super.onDetachedFromWindow();
    }

    private GradientDrawable rounded(int color, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class FollowScrollView extends ScrollView {
        private final int touchSlop;
        private float touchY;

        FollowScrollView(Context context) {
            super(context);
            touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        }

        private void trackTouch(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) touchY = event.getY();
            else if (event.getActionMasked() == MotionEvent.ACTION_MOVE
                    && Math.abs(event.getY() - touchY) > touchSlop) setFollowing(false);
        }

        @Override public boolean onInterceptTouchEvent(MotionEvent event) {
            trackTouch(event);
            return super.onInterceptTouchEvent(event);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            trackTouch(event);
            return super.onTouchEvent(event);
        }

        @Override public boolean performAccessibilityAction(int action, Bundle arguments) {
            if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    || action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) setFollowing(false);
            return super.performAccessibilityAction(action, arguments);
        }
    }
}
