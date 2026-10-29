package com.cogdev.skylightoverlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.WindowManager;

/**
 * Foreground service that owns the hatch overlay.
 *
 * Position is computed from relative fractions measured from the
 * 2026-10-29 screenshot (portrait, 1440x2560, wall orientation), not
 * hardcoded pixels, so it survives a Skylight UI shift that keeps the
 * same relative layout:
 *
 *   bottom bar top  = 96.8% of height
 *   Sleep centre    = 89.1% of width
 *   Settings centre = 96.3% of width
 *   hatch centre    = 82.0% of width, 97.8% of height
 *                     (immediately left of Sleep, in the empty gap
 *                      between Photos and Sleep)
 *   touch size      = bar height, ~3.2% of height
 *
 * The touch area is deliberately kept inside the bar: extending above
 * it would intercept taps on the calendar's bottom row.
 */
public class OverlayService extends Service {

    private static final String CHANNEL_ID = "skylight_hatch";
    private static final int NOTIFICATION_ID = 1;

    private WindowManager windowManager;
    private HatchView hatchView;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
    }

    @Override
    @SuppressWarnings("deprecation")
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification());

        if (hatchView == null) {
            DisplayMetrics metrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
            int width = metrics.widthPixels;
            int height = metrics.heightPixels;

            int size = Math.max(48, (int) (height * 0.032f));
            int centreX = (int) (width * 0.82f);
            int centreY = (int) (height * 0.978f);

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    size,
                    size,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                            : WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.START;
            params.x = centreX - size / 2;
            params.y = centreY - size / 2;

            hatchView = new HatchView(this);
            try {
                windowManager.addView(hatchView, params);
            } catch (Exception e) {
                // Most likely: draw-over permission not granted yet.
                // Service stays alive (and the notification stays up)
                // so the failure is visible in logcat, not silent.
                hatchView = null;
            }
        }
        return START_STICKY;
    }

    private Notification buildNotification() {
        NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Skylight hatch",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Keeps the Settings hatch icon on Skylight's bottom bar.");
            nm.createNotificationChannel(channel);
        }
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        return builder
                .setContentTitle("Skylight hatch running")
                .setContentText("Long-press the gear next to Sleep for 5s to open Android Settings.")
                .setSmallIcon(android.R.drawable.ic_menu_preferences)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (hatchView != null && windowManager != null) {
            try {
                windowManager.removeView(hatchView);
            } catch (Exception ignored) {
            }
            hatchView = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
