package pt.luis.oledmusic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

final class ColorPreviewView extends View {
    private int color;
    private final boolean controls;
    private final Paint paint;
    private final Path path;
    private String textSample;

    public ColorPreviewView(Context context) {
        this(context, true);
    }

    ColorPreviewView(Context context, boolean z) {
        super(context);
        this.paint = new Paint(1);
        this.path = new Path();
        this.controls = z;
        setImportantForAccessibility(2);
    }

    void setColor(int i) {
        this.color = i;
        invalidate();
    }

    int getColor() {
        return this.color;
    }

    void setTextSample(String str) {
        this.textSample = str;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float width = getWidth() / 2.0f;
        float height = getHeight() / 2.0f;
        float fMin = Math.min(getHeight() / 48.0f, getWidth() / 220.0f);
        this.paint.setStyle(Paint.Style.FILL);
        if (this.textSample != null) {
            this.paint.setColor(this.color);
            this.paint.setTextSize(Math.min(getHeight() * 0.55f, getResources().getDisplayMetrics().density * 24.0f));
            this.paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(this.textSample, width, height - ((this.paint.ascent() + this.paint.descent()) / 2.0f), this.paint);
            return;
        }
        if (this.controls) {
            this.paint.setColor(this.color);
            canvas.drawCircle(width, height, 20.0f * fMin, this.paint);
            float f = fMin * 8.0f;
            triangle(canvas, width + fMin, height, f, Color.luminance(this.color) > 0.179f ? -16777216 : -1, false);
            float f2 = fMin * 68.0f;
            triangle(canvas, width - f2, height, f, this.color, true);
            triangle(canvas, width + f2, height, f, this.color, false);
            return;
        }
        float f3 = 12.0f * fMin;
        float paddingLeft = getPaddingLeft() + f3;
        float width2 = (getWidth() - getPaddingRight()) - f3;
        this.paint.setStrokeWidth(2.0f * fMin);
        this.paint.setStrokeCap(Paint.Cap.ROUND);
        this.paint.setColor((this.color & 16777215) | 1426063360);
        canvas.drawLine(paddingLeft, height, width2, height, this.paint);
        this.paint.setColor(this.color);
        float f4 = paddingLeft + ((width2 - paddingLeft) * 0.65f);
        canvas.drawLine(paddingLeft, height, f4, height, this.paint);
        canvas.drawCircle(f4, height, fMin * 5.0f, this.paint);
    }

    private void triangle(Canvas canvas, float f, float f2, float f3, int i, boolean z) {
        this.paint.setColor(i);
        this.path.reset();
        float f4 = (z ? -1.0f : 1.0f) * f3;
        float f5 = f - (0.65f * f4);
        this.path.moveTo(f5, f2 - f3);
        this.path.lineTo(f + (f4 * 0.8f), f2);
        this.path.lineTo(f5, f2 + f3);
        this.path.close();
        canvas.drawPath(this.path, this.paint);
    }
}
