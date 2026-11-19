package net.cogdev.skylightoverlay;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.DisplayMetrics;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Artifact renderer, in two modes.
 *
 * FULLSCREEN (default): covers the whole screen. Used for pushed
 * content — recipes, notifications — where the page is an
 * interruption and Close is the way back. The content still
 * reserves the bottom menu-bar strip (4.22% of screen height,
 * white): the menu-bar overlay (Hatch / BanKin) draws on top
 * of this activity, and without the reserved strip the WebView
 * content runs under its icons — seen 2026-11-19 on the
 * Hibachi recipe, where Hatch / BanKin sat on top of step 4's
 * text. So the WebView ends at the bar top, and the strip
 * below it is the overlay's space.
 *
 * PAGE (EXTRA_PAGE = true): covers everything *except* the menu
 * bar. Retained as a fallback only — tabs now use
 * PageOverlayView instead (see that class), because an activity
 * still switches Skylight away with a slide animation, however
 * the window is sized. The window is sized to the top 95.78% of the screen
 * (MenuBarView.BAR_TOP) and pinned to the top, and tapping
 * outside the window — i.e. on the menu bar — finishes it.
 * That is the point: the bar stays visible and tappable while
 * a page is open, and the Calendar button on it is the way back
 * to Skylight. The page takes the taps everywhere above the bar.
 * Whether that outside tap also selects the native tab underneath
 * is device behaviour to verify on the Max; closing alone returns
 * to Skylight, which was on Calendar when the page opened, so
 * Calendar is the tab you land on either way.
 *
 * Page mode needs the translucent RendererTheme (styles.xml):
 * with the old opaque app theme, the system stopped Skylight
 * while the renderer was on top, so the uncovered bar strip
 * rendered black instead of Skylight's bar (seen 2026-11-19
 * in BanKin page mode). The content root below is opaque white,
 * so the page area itself stays solid.
 *
 * Native chrome only: a fixed title bar with an explicit Close button.
 * Nothing in the WebView content closes this activity — taps and
 * scrolls stay inside the page, which is what a cooking artifact needs.
 * Close returns to Skylight (the launcher) via an explicit HOME intent,
 * not just finish(): MainActivity is often underneath in the task
 * (opened to configure ntfy), and a plain finish() would reveal it
 * instead of Skylight.
 *
 * v1 loads a bundled asset (e.g. red-pepper-chicken.html). A later
 * push step will load dynamically delivered HTML through the same
 * activity; the Close/timer contract does not change.
 */
public class RendererActivity extends Activity {

    public static final String EXTRA_ASSET = "asset";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_FILE = "file";
    public static final String EXTRA_HTML = "html";
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_PAGE = "page";

    public static String notificationHtml(String level, String title, String message) {
        String badge = level != null && level.equals("urgent") ? "URGENT" : "STANDARD";
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<style>body{font-family:sans-serif;padding:24px;font-size:26px;line-height:1.5;color:#111;}"
                + "h1{font-size:38px;} .badge{display:inline-block;padding:8px 16px;font-size:22px;font-weight:bold;"
                + "background:" + (badge.equals("URGENT") ? "#c62828;color:#fff;" : "#eee;") + "}"
                + "</style></head><body>"
                + "<span class=\"badge\">" + badge + "</span>"
                + "<h1>" + NtfyListener.escapeHtml(title) + "</h1>"
                + "<p>" + NtfyListener.escapeHtml(message).replace("\n", "<br>") + "</p>"
                + "<p>Close this with the Close button above.</p>"
                + "</body></html>";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        boolean pageMode = intent != null && intent.getBooleanExtra(EXTRA_PAGE, false);
        if (pageMode) {
            // Page mode: window covers everything except the menu bar.
            // Height is the bar top (95.78% of screen height), pinned
            // to the top. A tap outside the window — on the menu bar —
            // finishes this activity, returning to Skylight underneath.
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            int pageHeight = (int) (metrics.heightPixels * MenuBarView.BAR_TOP);
            getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, pageHeight);
            getWindow().setGravity(Gravity.TOP);
            setFinishOnTouchOutside(true);
        }

        String asset = intent != null ? intent.getStringExtra(EXTRA_ASSET) : null;
        if (asset == null || asset.isEmpty()) {
            asset = "red-pepper-chicken.html";
        }
        String titleText = intent != null ? intent.getStringExtra(EXTRA_TITLE) : null;
        if (titleText == null || titleText.isEmpty()) {
            titleText = "Red Pepper Chicken";
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        // Opaque white: the theme is translucent (see styles.xml),
        // so without this the page area would show Skylight through.
        root.setBackgroundColor(android.graphics.Color.WHITE);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout chrome = new LinearLayout(this);
        chrome.setOrientation(LinearLayout.HORIZONTAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        chrome.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextSize(22f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        title.setLayoutParams(titleParams);
        chrome.addView(title);

        Button close = new Button(this);
        close.setText("Close");
        close.setTextSize(20f);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goHome();
            }
        });
        chrome.addView(close);
        root.addView(chrome);

        WebView webView = new WebView(this);
        LinearLayout.LayoutParams webParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        webView.setLayoutParams(webParams);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        webView.setWebViewClient(new WebViewClient());
        root.addView(webView);

        if (!pageMode) {
            // Reserve the bottom menu-bar strip for the menu-bar
            // overlay (Hatch / BanKin), which draws on top of
            // this activity. Without it, WebView content runs
            // under the overlay icons — seen 2026-11-19 on the
            // Hibachi recipe, where they sat on step 4's text.
            // The strip is opaque white; the native bar is hidden
            // behind this activity, so the overlay's two buttons
            // are what show there.
            DisplayMetrics spacerMetrics = getResources().getDisplayMetrics();
            int barHeight = spacerMetrics.heightPixels
                    - (int) (spacerMetrics.heightPixels * MenuBarView.BAR_TOP);
            View spacer = new View(this);
            spacer.setBackgroundColor(android.graphics.Color.WHITE);
            LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, barHeight);
            spacer.setLayoutParams(spacerParams);
            root.addView(spacer);
        }

        setContentView(root);
        String filePath = intent != null ? intent.getStringExtra(EXTRA_FILE) : null;
        String html = intent != null ? intent.getStringExtra(EXTRA_HTML) : null;
        String url = intent != null ? intent.getStringExtra(EXTRA_URL) : null;
        if (filePath != null && !filePath.isEmpty()) {
            String content = readFile(filePath);
            if (content != null) {
                webView.loadDataWithBaseURL(null, content, "text/html", "UTF-8", null);
            } else {
                webView.loadDataWithBaseURL(null,
                        notificationHtml("standard", titleText, "Could not read the delivered file."),
                        "text/html", "UTF-8", null);
            }
        } else if (html != null && !html.isEmpty()) {
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
        } else if (url != null && !url.isEmpty()) {
            webView.loadUrl(url);
        } else {
            webView.loadUrl("file:///android_asset/" + asset);
        }
    }

    private void goHome() {
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(home);
        finish();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        goHome();
    }

    private String readFile(String path) {
        try {
            BufferedReader r = new BufferedReader(
                    new InputStreamReader(new FileInputStream(new File(path)), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append("\n");
            }
            r.close();
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
