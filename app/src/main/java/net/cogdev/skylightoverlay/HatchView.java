package net.cogdev.skylightoverlay;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * The hatch icon itself.
 *
 * Drawn by hand (no vector drawable to get wrong): a muted grey gear
 * donut with a progress ring. A tap does nothing. A continuous
 * 5-second hold fills the ring and opens Android Settings.
 *
 * Why 5 seconds, custom, not Android's default long-press (~500 ms):
 * this is a hidden hatch on a family calendar. A normal long-press
 * is too easy to hit by accident, especially by kids.
 */
public class HatchView extends View {

    /** Hold duration in milliseconds. Deliberately long. */
    public static final long HOLD_MS = 5000L;

    private final Paint gearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centrePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF ringRect = new RectF();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long downTime = 0L;
    private boolean pressing = false;
    private float progress = 0f; // 0..1

    private final Runnable progressTick = new Runnable() {
        @Override
        public void run() {
            if (!pressing) return;
            long elapsed = SystemClock.uptimeMillis() - downTime;
            progress = Math.min(1f, (float) elapsed / HOLD_MS);
            invalidate();
            if (pressing && elapsed < HOLD_MS) {
                handler.postDelayed(this, 50L);
            }
        }
    };

    private final Runnable fire = new Runnable() {
        @Override
        public void run() {
            if (!pressing) return;
            pressing = false;
            progress = 0f;
            invalidate();
            // Plain Android Settings, not Developer Options directly:
            // general, least surprising, and Developer Options is one
            // tap away from there (System > Developer options).
            Intent intent = new Intent(Settings.ACTION_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                getContext().startActivity(intent);
            } catch (Exception ignored) {
                // If Settings cannot be launched for any reason, stay
                // silent and keep the icon: a failed hatch must never
                // break Skylight itself.
            }
        }
    };

    public HatchView(Context context) {
        super(context);
        init();
    }

    public HatchView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        // Muted grey, close to Skylight's bar icons, slightly transparent
        // so it reads as part of the bar rather than a new app button.
        gearPaint.setColor(Color.argb(200, 90, 105, 120));
        gearPaint.setStyle(Paint.Style.FILL);
        // Bar colour from the 2026-10-29 screenshot, for the donut hole.
        centrePaint.setColor(Color.rgb(230, 238, 243));
        centrePaint.setStyle(Paint.Style.FILL);

        ringBgPaint.setColor(Color.argb(60, 90, 105, 120));
        ringBgPaint.setStyle(Paint.Style.STROKE);
        ringBgPaint.setStrokeWidth(6f);
        ringBgPaint.setStrokeCap(Paint.Cap.ROUND);

        ringPaint.setColor(Color.rgb(42, 127, 255));
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(6f);
        ringPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        // Visual gear is deliberately smaller than the touch view:
        // touch view stays full bar height (~82px) for a fat tap target,
        // the drawn gear is ~42px, matching Skylight's other bar icons.
        float outer = Math.min(getWidth(), getHeight()) * 0.26f;
        float inner = outer * 0.45f;

        // Gear teeth: 8 rectangles around the circle.
        canvas.save();
        canvas.translate(cx, cy);
        float toothW = outer * 0.28f;
        float toothH = outer * 0.30f;
        for (int i = 0; i < 8; i++) {
            canvas.save();
            canvas.rotate(i * 45f);
            canvas.drawRect(-toothW / 2f, -outer - toothH * 0.5f,
                    toothW / 2f, -outer + toothH * 0.5f, gearPaint);
            canvas.restore();
        }
        canvas.drawCircle(0, 0, outer, gearPaint);
        canvas.drawCircle(0, 0, inner, centrePaint);
        canvas.restore();

        // Progress ring, only while holding, sized to the gear.
        if (pressing && progress > 0f) {
            float ringR = outer + 10f;
            ringRect.set(cx - ringR, cy - ringR, cx + ringR, cy + ringR);
            canvas.drawArc(ringRect, -90f, 360f, false, ringBgPaint);
            canvas.drawArc(ringRect, -90f, 360f * progress, false, ringPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressing = true;
                downTime = SystemClock.uptimeMillis();
                progress = 0f;
                handler.post(progressTick);
                handler.postDelayed(fire, HOLD_MS);
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                // Short tap: go Home. On Skylight this is a no-op
                // (Skylight is the default launcher), in Android
                // Settings it returns to the calendar. This is the
                // escape route out of Settings, which has no
                // on-screen Home/Back. If the 5s hold already fired,
                // pressing is false here, so no Home is sent.
                boolean wasPressing = pressing;
                cancelHold();
                if (wasPressing) {
                    try {
                        Intent home = new Intent(Intent.ACTION_MAIN);
                        home.addCategory(Intent.CATEGORY_HOME);
                        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        getContext().startActivity(home);
                    } catch (Exception ignored) {
                    }
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                cancelHold();
                return true;
            case MotionEvent.ACTION_MOVE:
                // Dragging off the icon cancels, like a normal long-press.
                if (event.getX() < 0 || event.getX() > getWidth()
                        || event.getY() < 0 || event.getY() > getHeight()) {
                    cancelHold();
                }
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private void cancelHold() {
        pressing = false;
        progress = 0f;
        handler.removeCallbacks(progressTick);
        handler.removeCallbacks(fire);
        invalidate();
    }
}
