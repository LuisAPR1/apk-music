package pt.luis.oledmusic;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import android.view.View;
import android.view.ViewOutlineProvider;

import java.lang.ref.WeakReference;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Center-crops without changing the image's proportions; draws the border inside the square. */
final class AlbumArtworkView extends ImageView {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ThreadPoolExecutor COLORS = new ThreadPoolExecutor(0, 1, 30,
            TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF borderBounds = new RectF();
    private boolean showBorder;
    private float borderWidth;
    private float cornerRadius;
    private boolean automaticBorder;
    private int manualColor = android.graphics.Color.WHITE;
    private int coverDim;
    private Bitmap source;
    private int sourceGeneration;
    private int requestGeneration;
    private AnalysisKey analysisKey;
    private FutureTask<Void> pending;

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
        automaticBorder = preferences.autoAlbumBorderColor;
        manualColor = preferences.albumBorderColor;
        coverDim = preferences.coverDim;
        updateAutomaticColor();
        invalidateOutline();
        invalidate();
    }

    void setArtwork(Bitmap bitmap) {
        setScaleType(ScaleType.CENTER_CROP);
        int generation = bitmap.getGenerationId();
        if (source == bitmap && sourceGeneration == generation) return;
        source = bitmap;
        sourceGeneration = generation;
        super.setImageBitmap(bitmap);
        updateAutomaticColor();
    }

    void setPlaceholder(Drawable drawable) {
        source = null;
        sourceGeneration = 0;
        cancelAnalysis();
        border.setColor(manualColor);
        setScaleType(ScaleType.CENTER);
        super.setImageDrawable(drawable);
    }

    private void updateAutomaticColor() {
        if (!automaticBorder || !showBorder || source == null || !isAttachedToWindow()
                || getWidth() == 0 || getHeight() == 0) {
            cancelAnalysis();
            border.setColor(manualColor);
            return;
        }
        int size = Math.min(getWidth(), getHeight());
        AnalysisKey key = new AnalysisKey(source, sourceGeneration,
                Math.round(radius() * AlbumArtworkColors.SAMPLE_SIZE / size),
                Math.max(1, Math.round(borderWidth * AlbumArtworkColors.SAMPLE_SIZE / size)),
                coverDim, manualColor);
        if (key.equals(analysisKey)) return;
        cancelAnalysis();
        analysisKey = key;
        border.setColor(manualColor);
        final int generation = requestGeneration;
        WeakReference<AlbumArtworkView> target = new WeakReference<>(this);
        pending = new FutureTask<>(() -> {
            int color = AlbumArtworkColors.choose(key.bitmap, key.radius, key.stroke, key.dim, key.fallback);
            if (!Thread.currentThread().isInterrupted()) MAIN.post(() -> {
                AlbumArtworkView view = target.get();
                if (view == null || view.requestGeneration != generation || !key.equals(view.analysisKey)) return;
                view.pending = null;
                view.border.setColor(color);
                view.invalidate();
            });
            return null;
        });
        COLORS.execute(pending);
    }

    private void cancelAnalysis() {
        requestGeneration++;
        analysisKey = null;
        if (pending != null) {
            pending.cancel(true);
            COLORS.remove(pending);
            pending = null;
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateAutomaticColor();
    }

    @Override protected void onDetachedFromWindow() {
        cancelAnalysis();
        super.onDetachedFromWindow();
    }

    private float radius() {
        return Math.min(cornerRadius, Math.min(getWidth(), getHeight()) / 2f);
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        invalidateOutline();
        updateAutomaticColor();
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

    private static final class AnalysisKey {
        final Bitmap bitmap;
        final int generation, radius, stroke, dim, fallback;

        AnalysisKey(Bitmap bitmap, int generation, int radius, int stroke, int dim, int fallback) {
            this.bitmap = bitmap;
            this.generation = generation;
            this.radius = radius;
            this.stroke = stroke;
            this.dim = dim;
            this.fallback = fallback;
        }

        @Override public boolean equals(Object other) {
            if (!(other instanceof AnalysisKey)) return false;
            AnalysisKey key = (AnalysisKey) other;
            return bitmap == key.bitmap && generation == key.generation && radius == key.radius
                    && stroke == key.stroke && dim == key.dim && fallback == key.fallback;
        }

        @Override public int hashCode() {
            return System.identityHashCode(bitmap) ^ generation ^ radius ^ stroke ^ dim ^ fallback;
        }
    }
}
