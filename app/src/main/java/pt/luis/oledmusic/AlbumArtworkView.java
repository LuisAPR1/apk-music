package pt.luis.oledmusic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.widget.ImageView;
import android.view.View;
import android.view.ViewOutlineProvider;

/** Center-crops without changing the image's proportions; draws the border inside the square. */
final class AlbumArtworkView extends ImageView {
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF borderBounds = new RectF();
    private boolean showBorder;
    private float borderWidth;
    private float cornerRadius;

    AlbumArtworkView(Context context) {
        super(context);
        border.setStyle(Paint.Style.STROKE);
        setScaleType(ScaleType.CENTER_CROP);
        setBackgroundColor(android.graphics.Color.BLACK);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius());
            }
        });
        setClipToOutline(true);
    }

    void applyAppearance(AppPreferences preferences) {
        float density = getResources().getDisplayMetrics().density;
        showBorder = preferences.showAlbumBorder;
        borderWidth = preferences.albumBorderWidth * density;
        cornerRadius = preferences.albumCornerRadius * density;
        border.setColor(preferences.albumBorderColor);
        invalidateOutline();
        invalidate();
    }

    private float radius() {
        return Math.min(cornerRadius, Math.min(getWidth(), getHeight()) / 2f);
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        invalidateOutline();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!showBorder || borderWidth <= 0 || getWidth() == 0 || getHeight() == 0) return;
        float width = Math.min(borderWidth, Math.min(getWidth(), getHeight()) / 2f);
        float inset = width / 2f;
        border.setStrokeWidth(width);
        borderBounds.set(inset, inset, getWidth() - inset, getHeight() - inset);
        float innerRadius = Math.max(0, radius() - inset);
        canvas.drawRoundRect(borderBounds, innerRadius, innerRadius, border);
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }
}
