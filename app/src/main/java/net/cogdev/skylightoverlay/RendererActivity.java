package net.cogdev.skylightoverlay;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Fullscreen artifact renderer.
 *
 * Native chrome only: a fixed title bar with an explicit Close button.
 * Nothing in the WebView content closes this activity — taps and
 * scrolls stay inside the page, which is what a cooking artifact needs.
 * Close finishes the activity, returning to Skylight (the launcher).
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
                finish();
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
