package pt.luis.oledmusic;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

final class CoverGestures implements View.OnTouchListener {
    private boolean active;
    private final GestureDetector detector;
    private boolean doubleTap;
    private boolean enabled;
    private final float minimumSwipe;
    private final Runnable next;
    private final Runnable previous;
    private float startX;
    private float startY;
    private final Runnable toggle;

    CoverGestures(Context context, Runnable runnable, Runnable runnable2, Runnable runnable3) {
        this.toggle = runnable;
        this.previous = runnable2;
        this.next = runnable3;
        this.minimumSwipe = context.getResources().getDisplayMetrics().density * 48.0f;
        GestureDetector gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTapEvent(MotionEvent motionEvent) {
                return true;
            }

            @Override
            public boolean onDown(MotionEvent motionEvent) {
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent motionEvent) {
                CoverGestures.this.doubleTap = true;
                return true;
            }
        });
        this.detector = gestureDetector;
        gestureDetector.setIsLongpressEnabled(false);
    }

    void setEnabled(boolean z) {
        cancel();
        this.enabled = z;
    }

    void cancel() {
        this.active = false;
        this.doubleTap = false;
        MotionEvent motionEventObtain = MotionEvent.obtain(0L, 0L, 3, 0.0f, 0.0f, 0);
        this.detector.onTouchEvent(motionEventObtain);
        motionEventObtain.recycle();
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        if (!this.enabled) {
            return false;
        }
        int actionMasked = motionEvent.getActionMasked();
        if (motionEvent.getPointerCount() != 1 || actionMasked == 3) {
            cancel();
            return true;
        }
        if (actionMasked == 0) {
            this.active = true;
            this.startX = motionEvent.getX();
            this.startY = motionEvent.getY();
        }
        if (!this.active) {
            return true;
        }
        float x = motionEvent.getX() - this.startX;
        float y = motionEvent.getY() - this.startY;
        boolean z = Math.abs(x) >= Math.min(this.minimumSwipe, ((float) view.getWidth()) * 0.3f) && Math.abs(x) > Math.abs(y) * 1.5f;
        if (actionMasked == 1 && z) {
            cancel();
            (x < 0.0f ? this.next : this.previous).run();
            return true;
        }
        this.detector.onTouchEvent(motionEvent);
        if (actionMasked == 1) {
            boolean z2 = this.doubleTap;
            this.active = false;
            this.doubleTap = false;
            view.performClick();
            if (z2 && Math.abs(x) < this.minimumSwipe && Math.abs(y) < this.minimumSwipe) {
                this.toggle.run();
            }
        }
        return true;
    }
}
