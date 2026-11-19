package net.cogdev.skylightoverlay;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A rendered page as a true overlay, not an activity.
 *
 * Why this exists: page mode was originally a RendererActivity
 * sized to the top 95.78% of the screen. That still *switched
 * activities* — Android played the open transition, sliding
 * Skylight off to the left, and Skylight was no longer
 * "underneath" at all (seen 2026-11-19). A translucent theme
 * could not fix that; the activity switch was the bug.
 *
 * This view is added to the WindowManager by OverlayService as
 * a TYPE_APPLICATION_OVERLAY window, fullscreen size. Skylight
 * stays the foreground activity the whole time: no transition,
 * no animation, no black strip, and the menu bar stays live.
 *
 * Layout, top to bottom:
 * - chrome: title + Close button (opaque white, like the
 *   renderer's chrome), height wrap-content
 * - WebView: fills the rest of the top 95.78% of the screen
 * - bottom strip: transparent, exactly the menu-bar height.
 *   A touch there hides this page. It returns false, in the
 *   hope the touch falls through to Skylight underneath — but
 *   on the Max it does not: the tap is consumed (confirmed
 *   2026-11-19). So a bar tap closes the page, and the user
 *   taps again to select the tab.
 *
 * The menu-bar overlay sits on top of this window (it is added
 * after it — see OverlayService), so Hatch / BanKin stay
 * tappable while a page is open. Taps on Hatch / BanKin hit
 * that window first and never reach this view's bottom strip.
 */
public class PageOverlayView extends LinearLayout {

    private final WebView webView;
    private final TextView titleView;
    private final Runnable onClose;

    public PageOverlayView(Context context, String titleText, String url,
                           Runnable onClose) {
        super(context);
        this.onClose = onClose;
        setOrientation(VERTICAL);
        setBackgroundColor(Color.TRANSPARENT);
        setLayoutParams(new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        int pad = (int) (16 * getResources().getDisplayMetrics().density);

        LinearLayout chrome = new LinearLayout(context);
        chrome.setOrientation(HORIZONTAL);
        chrome.setBackgroundColor(Color.WHITE);
        chrome.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(context);
        title.setText(titleText);
        titleView = title;
        title.setTextSize(22f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        title.setLayoutParams(titleParams);
        chrome.addView(title);

        Button close = new Button(context);
        close.setText("Close");
        close.setTextSize(20f);
        close.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (PageOverlayView.this.onClose != null) {
                    PageOverlayView.this.onClose.run();
                }
            }
        });
        chrome.addView(close);

        // Chrome + WebView fill the page area (top 95.78% of
        // the screen); the bottom strip added by setBarHeight()
        // takes the remaining menu-bar height.
        addView(chrome);

        webView = new WebView(context);
        LinearLayout.LayoutParams webParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        webView.setLayoutParams(webParams);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        webView.setWebViewClient(new WebViewClient());
        webView.setBackgroundColor(Color.WHITE);
        addView(webView);
        if (url != null && !url.isEmpty()) {
            webView.loadUrl(url);
        }
    }

    /**
     * Add the transparent bottom strip for the menu bar. The
     * strip is exactly bar height; chrome + WebView (weight 1)
     * fill the rest of the screen, i.e. the top 95.78%.
     * Called once by OverlayService after construction.
     */
    public void setBarHeight(int barHeightPx) {
        View strip = new View(getContext());
        strip.setBackgroundColor(Color.TRANSPARENT);
        LayoutParams stripParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, barHeightPx);
        strip.setLayoutParams(stripParams);
        strip.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    // Close the page. The tap itself is consumed
                    // (confirmed 2026-11-19: returning false does
                    // not pass it through to Skylight), so the tab
                    // needs a second tap to select it. An earlier
                    // version also sent a HOME intent here for
                    // Home / Calendar taps; it had no visible
                    // effect, so it was removed (2026-11-19).
                    if (onClose != null) {
                        onClose.run();
                    }
                }
                return false;
            }
        });
        addView(strip);
    }

    public void setTitle(String titleText) {
        if (titleText != null) {
            titleView.setText(titleText);
        }
    }

    public void loadUrl(String url) {
        if (url != null && !url.isEmpty()) {
            webView.loadUrl(url);
        }
    }

    public void destroyWebView() {
        try {
            webView.stopLoading();
            webView.destroy();
        } catch (Exception ignored) {
        }
    }
}
