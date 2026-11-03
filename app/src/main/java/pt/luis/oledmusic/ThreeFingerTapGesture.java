package pt.luis.oledmusic;

import android.content.Context;
import android.graphics.PointF;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

final class ThreeFingerTapGesture {
    private final Runnable action;
    private boolean capturing;
    private long downTime;
    private boolean lifting;
    private int peakPointers;
    private final float slopSquared;
    private final SparseArray<PointF> starts = new SparseArray<>();
    private boolean tracking;
    private boolean valid;

    ThreeFingerTapGesture(Context context, Runnable runnable) {
        this.action = runnable;
        float scaledTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop() * 2.0f;
        this.slopSquared = scaledTouchSlop * scaledTouchSlop;
    }

    boolean isCapturing() {
        return this.capturing;
    }

    boolean onTouchEvent(MotionEvent motionEvent) {
        int actionMasked = motionEvent.getActionMasked();
        boolean z = false;
        if (actionMasked == 0) {
            reset();
            this.tracking = true;
            this.valid = true;
            this.downTime = motionEvent.getEventTime();
            this.peakPointers = 1;
            remember(motionEvent, 0);
        }
        if (!this.tracking) {
            return false;
        }
        if (actionMasked == 5) {
            this.capturing = true;
            remember(motionEvent, motionEvent.getActionIndex());
            this.peakPointers = Math.max(this.peakPointers, motionEvent.getPointerCount());
            if (this.lifting || motionEvent.getPointerCount() > 3 || motionEvent.getEventTime() - this.downTime > 200) {
                this.valid = false;
            }
        }
        for (int i = 0; i < motionEvent.getPointerCount(); i++) {
            PointF pointF = this.starts.get(motionEvent.getPointerId(i));
            if (pointF == null) {
                this.valid = false;
            } else {
                float x = motionEvent.getX(i) - pointF.x;
                float y = motionEvent.getY(i) - pointF.y;
                if ((x * x) + (y * y) > this.slopSquared) {
                    this.valid = false;
                }
            }
        }
        if (motionEvent.getEventTime() - this.downTime > 450) {
            this.valid = false;
        }
        if (actionMasked == 6) {
            this.lifting = true;
            this.starts.remove(motionEvent.getPointerId(motionEvent.getActionIndex()));
        }
        boolean z2 = this.capturing;
        if (actionMasked == 1 || actionMasked == 3) {
            if (actionMasked == 1 && this.valid && this.peakPointers == 3) {
                z = true;
            }
            reset();
            if (z) {
                this.action.run();
            }
        }
        return z2;
    }

    void reset() {
        this.tracking = false;
        this.capturing = false;
        this.valid = false;
        this.lifting = false;
        this.peakPointers = 0;
        this.starts.clear();
    }

    private void remember(MotionEvent motionEvent, int i) {
        int pointerId = motionEvent.getPointerId(i);
        if (this.starts.get(pointerId) != null) {
            this.valid = false;
        }
        this.starts.put(pointerId, new PointF(motionEvent.getX(i), motionEvent.getY(i)));
    }
}
