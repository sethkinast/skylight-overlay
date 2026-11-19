package net.cogdev.skylightoverlay;

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

import java.io.File;

/**
 * Foreground service that owns the bottom menu bar overlay.
 *
 * Position is computed from relative fractions measured from the
 * 2026-10-29 screenshot (portrait, 1440x2560, wall orientation), not
 * hardcoded pixels, so it survives a Skylight UI shift that keeps the
 * same relative layout. See MenuBarView for the measured region:
 * Rewards through Photos, x 30.0%-60.2%, bar top 95.78%.
 *
 * No cover any more: Skylight's update lets you hide the native
 * icons, so the overlay is transparent (PixelFormat.TRANSLUCENT)
 * and only draws the registered buttons, on the native bar.
 *
 * The touch area is deliberately kept inside the bar: extending above
 * it would intercept taps on the calendar's bottom row.
 *
 * Registered buttons, in slot order (see MenuBarView.register):
 *   1. Hatch — tap: Home; hold 5s: Android Settings.
 *   2. BanKin — tap: open the kids' bank webapp as a page
 *      overlay (top 95.78% of the screen, menu bar left
 *      visible and tappable; tapping the bar closes the page).
 *      No activity switch: Skylight stays foreground.
 * Slots 3-4 are blank for now, ready for family buttons.
 */
public class OverlayService extends Service {

    private static final String CHANNEL_ID = "skylight_hatch";
    private static final int NOTIFICATION_ID = 1;

    private WindowManager windowManager;
    private MenuBarView menuBarView;
    private PageOverlayView pageView;
    private NtfyListener ntfyListener;
    private final android.os.Handler mainHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());

    /**
     * Show a page as a true overlay (no activity switch).
     * The page window is pre-added at service start, below
     * the menu bar, and hidden with GONE — showing it is just
     * visibility + load, with no add/remove, so the menu bar
     * never flickers (the old remove/re-add on every BanKin
     * tap made it briefly disappear, seen 2026-11-19).
     * Skylight stays the foreground activity; this window sits
     * on top of it for the top 95.78% of the screen, and the
     * menu bar stays live underneath.
     *
     * View work is posted to the main thread: ntfy callbacks
     * arrive on the listener's background thread, and touching
     * a View from there throws.
     */
    private void showPage(final String title, final String url) {
        Runnable work = new Runnable() {
            @Override
            public void run() {
                if (pageView == null) return;
                pageView.setTitle(title);
                pageView.loadUrl(url);
                pageView.setVisibility(android.view.View.VISIBLE);
                if (menuBarView != null) {
                    menuBarView.setActive(title);
                }
            }
        };
        runOnMain(work);
    }

    private void hidePage() {
        Runnable work = new Runnable() {
            @Override
            public void run() {
                if (pageView == null) return;
                pageView.setVisibility(android.view.View.GONE);
                if (menuBarView != null) {
                    menuBarView.setActive(null);
                }
            }
        };
        runOnMain(work);
    }

    /** Run now if already on the main thread, else post there. */
    private void runOnMain(Runnable work) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            work.run();
        } else {
            mainHandler.post(work);
        }
    }

    /** Pre-add the page window, below the menu bar, hidden. */
    private void ensurePageView() {
        if (pageView != null) return;
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        int barHeight = metrics.heightPixels - (int) (metrics.heightPixels * MenuBarView.BAR_TOP);

        PageOverlayView view = new PageOverlayView(this, "", null,
                new Runnable() {
                    @Override
                    public void run() {
                        hidePage();
                    }
                });
        view.setBarHeight(barHeight);
        view.setVisibility(android.view.View.GONE);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 0;

        try {
            windowManager.addView(view, params);
            pageView = view;
        } catch (Exception ignored) {
            pageView = null;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
    }

    private void startNtfyListener() {
        if (ntfyListener != null) return;
        String server = NtfyListener.savedServer(this);
        String topic = NtfyListener.savedTopic(this);
        String token = NtfyListener.savedToken(this);
        ntfyListener = new NtfyListener(this, server, topic, token, new NtfyListener.Callback() {
            @Override
            public void onRenderHtml(String title, String html) {
                hidePage();
                Intent i = new Intent(OverlayService.this, RendererActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                i.putExtra(RendererActivity.EXTRA_TITLE, title);
                i.putExtra(RendererActivity.EXTRA_HTML, html);
                startActivity(i);
            }

            @Override
            public void onRenderUrl(String title, String url) {
                hidePage();
                Intent i = new Intent(OverlayService.this, RendererActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                i.putExtra(RendererActivity.EXTRA_TITLE, title);
                i.putExtra(RendererActivity.EXTRA_URL, url);
                startActivity(i);
            }

            @Override
            public void onRenderFile(String title, File file) {
                hidePage();
                Intent i = new Intent(OverlayService.this, RendererActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                i.putExtra(RendererActivity.EXTRA_TITLE, title);
                i.putExtra(RendererActivity.EXTRA_FILE, file.getAbsolutePath());
                startActivity(i);
            }

            @Override
            public void onNotification(String level, String title, String message) {
                hidePage();
                Intent i = new Intent(OverlayService.this, RendererActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                i.putExtra(RendererActivity.EXTRA_TITLE, title);
                i.putExtra(RendererActivity.EXTRA_HTML,
                        RendererActivity.notificationHtml(level, title, message));
                startActivity(i);
            }
        });
        ntfyListener.start();
    }

    @Override
    @SuppressWarnings("deprecation")
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification());
        startNtfyListener();
        // Page window first, so the menu bar added after it
        // sits on top of it. See showPage() for why it is
        // pre-added instead of added on every BanKin tap.
        ensurePageView();

        if (menuBarView == null) {
            DisplayMetrics metrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
            int width = metrics.widthPixels;
            int height = metrics.heightPixels;

            int left = (int) (width * MenuBarView.COVER_LEFT);
            int top = (int) (height * MenuBarView.BAR_TOP);
            int coverWidth = (int) (width * (MenuBarView.COVER_RIGHT - MenuBarView.COVER_LEFT));
            int coverHeight = height - top;

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    coverWidth,
                    coverHeight,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                            : WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.START;
            params.x = left;
            params.y = top;

            menuBarView = new MenuBarView(this);
            menuBarView.register("Hatch", MenuButtonView.Icon.GEAR,
                    new Runnable() {
                        @Override
                        public void run() {
                            hidePage();
                            Intent home = new Intent(Intent.ACTION_MAIN);
                            home.addCategory(Intent.CATEGORY_HOME);
                            home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(home);
                        }
                    },
                    new Runnable() {
                        @Override
                        public void run() {
                            Intent intent = new Intent(android.provider.Settings.ACTION_SETTINGS);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        }
                    });
            menuBarView.register("BanKin", MenuButtonView.Icon.BANK,
                    new Runnable() {
                        @Override
                        public void run() {
                            showPage("BanKin", "https://bankin.home.kina.st");
                        }
                    },
                    null);
            try {
                windowManager.addView(menuBarView, params);
            } catch (Exception e) {
                // Most likely: draw-over permission not granted yet.
                // Service stays alive (and the notification stays up)
                // so the failure is visible in logcat, not silent.
                menuBarView = null;
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
            channel.setDescription("Keeps the custom menu bar on Skylight's bottom bar.");
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
                .setContentText("Tap Hatch for Home, hold Hatch 5s for Android Settings.")
                .setSmallIcon(android.R.drawable.ic_menu_preferences)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (pageView != null && windowManager != null) {
            try {
                windowManager.removeView(pageView);
            } catch (Exception ignored) {
            }
            pageView.destroyWebView();
            pageView = null;
        }
        if (ntfyListener != null) {
            ntfyListener.stop();
            ntfyListener = null;
        }
        if (menuBarView != null && windowManager != null) {
            try {
                windowManager.removeView(menuBarView);
            } catch (Exception ignored) {
            }
            menuBarView = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
