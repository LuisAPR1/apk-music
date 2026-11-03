package pt.luis.oledmusic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.function.IntConsumer;

final class SaturationValueView extends View {
    private final RectF area;
    private final Path clip;
    private final float[] hsv;
    private final float[] hueColor;
    private Shader hueGradient;
    private IntConsumer listener;
    private final Paint paint;
    private Shader shadeGradient;

    SaturationValueView(Context context) {
        super(context);
        this.hsv = new float[]{0.0f, 0.0f, 1.0f};
        this.paint = new Paint(1);
        this.area = new RectF();
        this.clip = new Path();
        this.hueColor = new float[]{0.0f, 1.0f, 1.0f};
        setFocusable(true);
        setContentDescription("Escolher intensidade e claridade da cor. Arrasta no quadrado.");
        setImportantForAccessibility(1);
    }

    void setListener(IntConsumer intConsumer) {
        this.listener = intConsumer;
    }

    void setColor(int i) {
        float[] fArr = this.hsv;
        float f = fArr[0];
        Color.colorToHSV(i, fArr);
        float[] fArr2 = this.hsv;
        if (fArr2[1] == 0.0f) {
            fArr2[0] = f;
        }
        updateHueGradient();
        invalidate();
    }

    void setHue(float f) {
        float[] fArr = this.hsv;
        fArr[0] = f;
        if (fArr[1] == 0.0f || fArr[2] == 0.0f) {
            fArr[1] = 0.85f;
            fArr[2] = 1.0f;
        }
        updateHueGradient();
        changed();
    }

    int getColor() {
        return Color.HSVToColor(this.hsv);
    }

    float getHue() {
        return this.hsv[0];
    }

    float[] getHsv() {
        return (float[]) this.hsv.clone();
    }

    void restoreHsv(float[] fArr) {
        System.arraycopy(fArr, 0, this.hsv, 0, 3);
        updateHueGradient();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int i, int i2, int i3, int i4) {
        super.onSizeChanged(i, i2, i3, i4);
        int iDp = dp(10);
        float f = iDp;
        this.area.set(f, f, i - iDp, i2 - iDp);
        this.clip.reset();
        this.clip.addRoundRect(this.area, dp(10), dp(10), Path.Direction.CW);
        if (this.area.height() > 0.0f) {
            this.shadeGradient = new LinearGradient(0.0f, this.area.top, 0.0f, this.area.bottom, 0, -16777216, Shader.TileMode.CLAMP);
        }
        updateHueGradient();
    }

    private void updateHueGradient() {
        if (this.area.width() <= 0.0f) {
            return;
        }
        this.hueColor[0] = this.hsv[0];
        this.hueGradient = new LinearGradient(this.area.left, 0.0f, this.area.right, 0.0f, -1, Color.HSVToColor(this.hueColor), Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (this.area.width() <= 0.0f || this.area.height() <= 0.0f) {
            return;
        }
        canvas.save();
        canvas.clipPath(this.clip);
        this.paint.setStyle(Paint.Style.FILL);
        this.paint.setShader(this.hueGradient);
        canvas.drawRect(this.area, this.paint);
        this.paint.setShader(this.shadeGradient);
        canvas.drawRect(this.area, this.paint);
        this.paint.setShader(null);
        canvas.restore();
        float fWidth = this.area.left + (this.area.width() * this.hsv[1]);
        float fHeight = this.area.top + (this.area.height() * (1.0f - this.hsv[2]));
        this.paint.setStyle(Paint.Style.STROKE);
        this.paint.setStrokeWidth(dp(4));
        this.paint.setColor(-13619152);
        canvas.drawCircle(fWidth, fHeight, dp(7), this.paint);
        this.paint.setStrokeWidth(dp(2));
        this.paint.setColor(-1);
        canvas.drawCircle(fWidth, fHeight, dp(7), this.paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent motionEvent) {
        if (motionEvent.getPointerCount() != 1) {
            return true;
        }
        int actionMasked = motionEvent.getActionMasked();
        if (actionMasked == 0 || actionMasked == 2 || actionMasked == 1) {
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(actionMasked != 1);
            }
            float fDp = dp(10);
            float f = 2.0f * fDp;
            this.hsv[1] = clamp((motionEvent.getX() - fDp) / Math.max(1.0f, getWidth() - f));
            this.hsv[2] = 1.0f - clamp((motionEvent.getY() - fDp) / Math.max(1.0f, getHeight() - f));
            changed();
            if (actionMasked == 1) {
                performClick();
            }
        } else if (actionMasked == 3 && getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(false);
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    public boolean onKeyDown(int i, KeyEvent keyEvent) {
        if (i == 21) {
            float[] fArr = this.hsv;
            fArr[1] = clamp(fArr[1] - 0.04f);
        } else if (i == 22) {
            float[] fArr2 = this.hsv;
            fArr2[1] = clamp(fArr2[1] + 0.04f);
        } else if (i == 19) {
            float[] fArr3 = this.hsv;
            fArr3[2] = clamp(fArr3[2] + 0.04f);
        } else if (i == 20) {
            float[] fArr4 = this.hsv;
            fArr4[2] = clamp(fArr4[2] - 0.04f);
        } else {
            return super.onKeyDown(i, keyEvent);
        }
        changed();
        return true;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo accessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(accessibilityNodeInfo);
        accessibilityNodeInfo.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.color_more_vivid, "Mais viva"));
        accessibilityNodeInfo.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.color_less_vivid, "Menos viva"));
        accessibilityNodeInfo.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.color_lighter, "Mais clara"));
        accessibilityNodeInfo.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.color_darker, "Mais escura"));
    }

    @Override
    public boolean performAccessibilityAction(int i, Bundle bundle) {
        if (i == R.id.color_more_vivid) {
            float[] fArr = this.hsv;
            fArr[1] = clamp(fArr[1] + 0.1f);
        } else if (i == R.id.color_less_vivid) {
            float[] fArr2 = this.hsv;
            fArr2[1] = clamp(fArr2[1] - 0.1f);
        } else if (i == R.id.color_lighter) {
            float[] fArr3 = this.hsv;
            fArr3[2] = clamp(fArr3[2] + 0.1f);
        } else {
            if (i != R.id.color_darker) {
                return super.performAccessibilityAction(i, bundle);
            }
            float[] fArr4 = this.hsv;
            fArr4[2] = clamp(fArr4[2] - 0.1f);
        }
        changed();
        return true;
    }

    private void changed() {
        invalidate();
        IntConsumer intConsumer = this.listener;
        if (intConsumer != null) {
            intConsumer.accept(getColor());
        }
    }

    private float clamp(float f) {
        return Math.max(0.0f, Math.min(1.0f, f));
    }

    private int dp(int i) {
        return Math.round(i * getResources().getDisplayMetrics().density);
    }
}
