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
 *   A touch there hides this page and returns false, so the
 *   touch falls through to Skylight underneath — that is how
 *   tapping the Calendar button on the bar closes the page.
 *   If the fall-through does not happen on this device, the
 *   page still closes, and Skylight was on Calendar when the
 *   page opened, so a second tap selects the tab if needed.
 *
 * The menu-bar overlay is re-added on top of this window by
 * OverlayService, so Hatch / BanKin stay tappable while a
 * page is open. Taps on Hatch / BanKin hit that window first
 * and never reach this view's bottom strip.
 */
public class PageOverlayView extends LinearLayout {

    private final WebView webView;
    private final Runnable onClose;

    public PageOverlayView(Context context, String titleText, String url, Runnable onClose) {
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
                    if (onClose != null) {
                        onClose.run();
                    }
                }
                // Do not consume: let Skylight underneath have it.
                return false;
            }
        });
        addView(strip);
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
