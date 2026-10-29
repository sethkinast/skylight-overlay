package net.cogdev.skylightoverlay;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
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
        webView.loadUrl("file:///android_asset/" + asset);
    }
}
