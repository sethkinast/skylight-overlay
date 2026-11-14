package net.cogdev.skylightoverlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * One button in the bottom menu bar, drawn to match Skylight's native
 * buttons as closely as the on-device fonts allow.
 *
 * Measured from the 2026-10-29 screenshot (1440x2560, density 320):
 * - bar height 108px = 54dp; bar background #E9F1F7 (233,241,247)
 * - native cells are 108px wide = 54dp, spaced 108px apart
 * - icon: ~30px tall, centred at 38% of bar height
 * - label: cap height ~12px, centred at ~68% of bar height
 * - icon/label colour, unselected: #6E7275 (110,114,117);
 *   selected (Calendar) is black bold on a white cell
 * - label size is ~11sp by that cap height
 *
 * Font: the native label font could not be identified definitively
 * from the screenshot alone — it reads as a rounded humanist sans,
 * likely a Skylight-proprietary face, and there is no font file on
 * the device to pull without root. The closest available on-device
 * face is the system sans-serif (Roboto) at 11sp, which is what
 * this uses. If Seth ever identifies the exact face, swap the
 * Typeface here and nowhere else.
 *
 * Touch: tap runs onTap immediately on release. If onLongPress is
 * set, a continuous hold of holdMs (5s for Hatch, deliberately long
 * so kids can't hit Settings by accident) fills the blue progress
 * ring and runs onLongPress instead; release after the fire does
 * not also run onTap.
 */
public class MenuButtonView extends View {

    public enum Icon {
        GEAR,
        BANK
        // Future icons go here, drawn in the same outline style
        // and colour as the native bar icons.
    }

    public static final long DEFAULT_HOLD_MS = 5000L;

    private static final int BAR_COLOR = Color.rgb(233, 241, 247);
    private static final int ICON_COLOR = Color.rgb(110, 114, 117);
    private static final int LABEL_COLOR = Color.rgb(110, 114, 117);
    private static final int RING_COLOR = Color.rgb(42, 127, 255);

    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centrePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF ringRect = new RectF();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final String label;
    private final Icon icon;
    private final Runnable onTap;
    private final Runnable onLongPress;
    private final long holdMs;

    private long downTime = 0L;
    private boolean pressing = false;
    private float progress = 0f; // 0..1

    private final Runnable progressTick = new Runnable() {
        @Override
        public void run() {
            if (!pressing) return;
            long elapsed = SystemClock.uptimeMillis() - downTime;
            progress = Math.min(1f, (float) elapsed / holdMs);
            invalidate();
            if (pressing && elapsed < holdMs) {
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
            if (onLongPress != null) {
                onLongPress.run();
            }
        }
    };

    public MenuButtonView(Context context, String label, Icon icon,
                          Runnable onTap, Runnable onLongPress, long holdMs) {
        super(context);
        this.label = label;
        this.icon = icon;
        this.onTap = onTap;
        this.onLongPress = onLongPress;
        this.holdMs = holdMs > 0 ? holdMs : DEFAULT_HOLD_MS;
        init();
    }

    public MenuButtonView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.label = "";
        this.icon = Icon.GEAR;
        this.onTap = null;
        this.onLongPress = null;
        this.holdMs = DEFAULT_HOLD_MS;
        init();
    }

    private void init() {
        setClickable(true);
        iconPaint.setColor(ICON_COLOR);
        iconPaint.setStyle(Paint.Style.FILL);
        outlinePaint.setColor(ICON_COLOR);
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(2.5f * getResources().getDisplayMetrics().density);
        outlinePaint.setStrokeCap(Paint.Cap.ROUND);
        outlinePaint.setStrokeJoin(Paint.Join.ROUND);
        centrePaint.setColor(BAR_COLOR);
        centrePaint.setStyle(Paint.Style.FILL);

        labelPaint.setColor(LABEL_COLOR);
        labelPaint.setTypeface(Typeface.SANS_SERIF);
        labelPaint.setTextSize(11f * getResources().getDisplayMetrics().scaledDensity);
        labelPaint.setTextAlign(Paint.Align.CENTER);

        float stroke = 3f * getResources().getDisplayMetrics().density;
        ringBgPaint.setColor(Color.argb(60, 110, 114, 117));
        ringBgPaint.setStyle(Paint.Style.STROKE);
        ringBgPaint.setStrokeWidth(stroke);
        ringBgPaint.setStrokeCap(Paint.Cap.ROUND);

        ringPaint.setColor(RING_COLOR);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(stroke);
        ringPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        // Native icon centre, 38% of bar height; native label
        // baseline, ~74% of bar height.
        float iconCy = h * 0.385f;
        float labelBaseline = h * 0.74f;

        // Icon: ~30px tall on a 108px bar, so radius ~14% of height.
        float outer = h * 0.14f;
        float inner = outer * 0.45f;

        if (icon == Icon.GEAR) {
            canvas.save();
            canvas.translate(cx, iconCy);
            float toothW = outer * 0.28f;
            float toothH = outer * 0.30f;
            for (int i = 0; i < 8; i++) {
                canvas.save();
                canvas.rotate(i * 45f);
                canvas.drawRect(-toothW / 2f, -outer - toothH * 0.5f,
                        toothW / 2f, -outer + toothH * 0.5f, iconPaint);
                canvas.restore();
            }
            canvas.drawCircle(0, 0, outer, iconPaint);
            canvas.drawCircle(0, 0, inner, centrePaint);
            canvas.restore();
        } else if (icon == Icon.BANK) {
            // Outline bank: triangular roof, three columns, base line.
            // Drawn in stroke, like the native outline icons, so it
            // reads closer to them than the filled gear does.
            Path roof = new Path();
            roof.moveTo(cx - outer - 2f, iconCy - outer * 0.15f);
            roof.lineTo(cx, iconCy - outer - 2f);
            roof.lineTo(cx + outer + 2f, iconCy - outer * 0.15f);
            roof.close();
            canvas.drawPath(roof, outlinePaint);
            float colTop = iconCy + outer * 0.05f;
            float colBottom = iconCy + outer * 0.85f;
            for (float dx : new float[]{-outer * 0.6f, 0f, outer * 0.6f}) {
                canvas.drawLine(cx + dx, colTop, cx + dx, colBottom, outlinePaint);
            }
            canvas.drawLine(cx - outer - 2f, colBottom + 2f,
                    cx + outer + 2f, colBottom + 2f, outlinePaint);
        }

        // Progress ring, only while holding, sized to the icon.
        if (pressing && progress > 0f) {
            float ringR = outer + 6f * getResources().getDisplayMetrics().density;
            ringRect.set(cx - ringR, iconCy - ringR, cx + ringR, iconCy + ringR);
            canvas.drawArc(ringRect, -90f, 360f, false, ringBgPaint);
            canvas.drawArc(ringRect, -90f, 360f * progress, false, ringPaint);
        }

        if (label != null && !label.isEmpty()) {
            canvas.drawText(label, cx, labelBaseline, labelPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (onLongPress == null) {
                    // No hold action: a plain tap button.
                    pressing = true;
                    downTime = SystemClock.uptimeMillis();
                    return true;
                }
                pressing = true;
                downTime = SystemClock.uptimeMillis();
                progress = 0f;
                handler.post(progressTick);
                handler.postDelayed(fire, holdMs);
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                boolean wasPressing = pressing;
                cancelHold();
                if (wasPressing && onTap != null) {
                    onTap.run();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                cancelHold();
                return true;
            case MotionEvent.ACTION_MOVE:
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
