package net.cogdev.skylightoverlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
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
 * Measured from the 2026-11-19 screenshot (1440x2560, density 320),
 * by pixel analysis of the bar region:
 * - bar height 108px = 54dp; bar background #E9F1F7 (233,241,247)
 * - native cells are 108px wide = 54dp, spaced 108px apart
 * - native icons: 20-29px wide, 26-30px tall, centred at 38% of
 *   bar height. Our 0.4.0 icons were 34-38px and twice the ink
 *   (Hatch 614 dark px vs native Settings 319) — too big and too
 *   heavy. Icons are now 25% smaller (outer radius 10.5% of bar
 *   height) and the gear lost its second circle, leaving one
 *   outer ring plus a small hub.
 * - native labels: cap height 12-13px, baseline ~2530px (74% of
 *   bar height). Our 11sp label measured 16px cap height — too
 *   big. Labels are now 9sp (cap ~13px).
 * - icon/label colour, unselected: #6E7275 (110,114,117);
 *   selected (Calendar): near-black (31,31,31), bold label, on a
 *   white cell (#FFFFFF, full bar height). That is what
 *   setSelected() reproduces for an active tab.
 * - icons are thin outlines: stroke ~1.5dp.
 *
 * The bar itself is no longer covered (Skylight update, 2026-11-19:
 * native icons can be hidden in Skylight settings, so there is
 * nothing to hide). Buttons are drawn on the transparent overlay,
 * with the native bar background showing through.
 *
 * Font: the native face is a rounded humanist sans — the
 * single-story 'g' in "Settings" rules out Roboto (double-story
 * 'g'). This uses Nunito (OFL, bundled in assets/fonts/), a
 * rounded face with the same single-story 'g', at weight 400,
 * and 700 for a selected label. If Seth ever identifies the
 * exact face, swap the font file and nowhere else.
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

    private static final int ICON_COLOR = Color.rgb(110, 114, 117);
    private static final int LABEL_COLOR = Color.rgb(110, 114, 117);
    private static final int SELECTED_COLOR = Color.rgb(31, 31, 31);
    private static final int RING_COLOR = Color.rgb(42, 127, 255);

    private static Typeface nunitoRegular;
    private static Typeface nunitoBold;

    private static void loadFonts(Context context) {
        if (nunitoRegular != null) return;
        try {
            Typeface base = Typeface.createFromAsset(context.getAssets(), "fonts/nunito.ttf");
            if (Build.VERSION.SDK_INT >= 28) {
                // Variable font: pick weights 400 / 700 explicitly.
                nunitoRegular = Typeface.create(base, 400, false);
                nunitoBold = Typeface.create(base, 700, false);
            } else {
                nunitoRegular = base;
                nunitoBold = Typeface.create(base, Typeface.BOLD);
            }
        } catch (Exception e) {
            nunitoRegular = Typeface.SANS_SERIF;
            nunitoBold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
        }
    }

    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedBgPaint = new Paint();
    private final Paint ringBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF ringRect = new RectF();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final String label;
    private final Icon icon;
    private final Runnable onTap;
    private final Runnable onLongPress;
    private final long holdMs;

    private boolean selected = false;

    public String getLabel() {
        return label;
    }

    /** Active tab: white cell, near-black bold label, like Skylight's selected Calendar. */
    public void setSelected(boolean selected) {
        if (this.selected == selected) return;
        this.selected = selected;
        applySelectedStyle();
        invalidate();
    }

    private void applySelectedStyle() {
        int color = selected ? SELECTED_COLOR : ICON_COLOR;
        outlinePaint.setColor(color);
        labelPaint.setColor(color);
        labelPaint.setTypeface(selected ? nunitoBold : nunitoRegular);
    }

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
        loadFonts(getContext());
        // Thin outline stroke, matching the native icons.
        // Native stroke is ~1.5dp at this density.
        outlinePaint.setColor(ICON_COLOR);
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(1.5f * getResources().getDisplayMetrics().density);
        outlinePaint.setStrokeCap(Paint.Cap.ROUND);
        outlinePaint.setStrokeJoin(Paint.Join.ROUND);

        // 9sp: measured native cap height is 12-13px; 11sp drew
        // 16px. Nunito, rounded like the native face.
        labelPaint.setColor(LABEL_COLOR);
        labelPaint.setTypeface(nunitoRegular);
        labelPaint.setTextSize(9f * getResources().getDisplayMetrics().scaledDensity);
        labelPaint.setTextAlign(Paint.Align.CENTER);

        selectedBgPaint.setColor(Color.WHITE);
        selectedBgPaint.setStyle(Paint.Style.FILL);

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

        // Selected tab: white cell, full bar height, like the
        // native selected Calendar. Drawn first, under icon/label.
        if (selected) {
            canvas.drawRect(0, 0, w, h, selectedBgPaint);
        }

        // Native icon centre, 38% of bar height; native label
        // baseline, ~74% of bar height.
        float iconCy = h * 0.385f;
        float labelBaseline = h * 0.74f;

        // Icon: native icons are 26-30px tall on a 108px bar.
        // Outer radius 10.5% of bar height (25% down from the
        // 0.4.0 14%) puts the gear at ~29px including teeth
        // and the bank at ~30px, in native range.
        float outer = h * 0.105f;

        if (icon == Icon.GEAR) {
            // Outline gear, like the native Settings gear: one
            // outer ring, eight short radial teeth, and a small
            // hub hole. The old two-circle version drew twice
            // the ink of the native gear and read as heavier.
            canvas.save();
            canvas.translate(cx, iconCy);
            canvas.drawCircle(0, 0, outer, outlinePaint);
            canvas.drawCircle(0, 0, outer * 0.28f, outlinePaint);
            float toothLen = outer * 0.30f;
            for (int i = 0; i < 8; i++) {
                canvas.save();
                canvas.rotate(i * 45f);
                canvas.drawLine(0, -outer, 0, -outer - toothLen, outlinePaint);
                canvas.restore();
            }
            canvas.restore();
        } else if (icon == Icon.BANK) {
            // Outline bank: triangular roof, three columns, base line.
            // Vertical extent is 1.15 * outer each way, so the
            // whole icon is ~30px tall, in native range.
            float top = iconCy - outer * 1.15f;
            float bottom = iconCy + outer * 1.15f;
            Path roof = new Path();
            roof.moveTo(cx - outer - 2f, iconCy - outer * 0.15f);
            roof.lineTo(cx, top - 2f);
            roof.lineTo(cx + outer + 2f, iconCy - outer * 0.15f);
            roof.close();
            canvas.drawPath(roof, outlinePaint);
            float colTop = iconCy + outer * 0.05f;
            float colBottom = bottom - 2f;
            for (float dx : new float[]{-outer * 0.6f, 0f, outer * 0.6f}) {
                canvas.drawLine(cx + dx, colTop, cx + dx, colBottom, outlinePaint);
            }
            canvas.drawLine(cx - outer - 2f, bottom,
                    cx + outer + 2f, bottom, outlinePaint);
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
