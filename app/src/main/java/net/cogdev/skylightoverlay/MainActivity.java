package net.cogdev.skylightoverlay;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Plain Activity, no AndroidX, so the build needs nothing beyond
 * the Android Gradle Plugin + SDK 33.
 *
 * This activity is only a control panel for the prototype. On the
 * Max it will be launched once via adb; Skylight remains the
 * launcher, this app never tries to replace it.
 */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Skylight Escape Hatch");
        title.setTextSize(24f);
        layout.addView(title);

        TextView body = new TextView(this);
        body.setText(
                "Gear icon next to Sleep on Skylight's bottom bar.\n"
                        + "Tap: return to Skylight (Home).\n"
                        + "Hold 5 seconds: opens Android Settings.\n\n"
                        + "First grant Draw over other apps, then Start.");
        body.setPadding(0, pad, 0, pad);
        layout.addView(body);

        Button permissionButton = new Button(this);
        permissionButton.setText("Open draw-over permission");
        permissionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        });
        layout.addView(permissionButton);

        Button startButton = new Button(this);
        startButton.setText("Start hatch");
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent service = new Intent(MainActivity.this, OverlayService.class);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(service);
                } else {
                    startService(service);
                }
            }
        });
        layout.addView(startButton);

        Button stopButton = new Button(this);
        stopButton.setText("Stop hatch");
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopService(new Intent(MainActivity.this, OverlayService.class));
            }
        });
        layout.addView(stopButton);

        Button rendererButton = new Button(this);
        rendererButton.setText("Open Red Pepper Chicken recipe");
        rendererButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent renderer = new Intent(MainActivity.this, RendererActivity.class);
                renderer.putExtra(RendererActivity.EXTRA_ASSET, "red-pepper-chicken.html");
                renderer.putExtra(RendererActivity.EXTRA_TITLE, "Red Pepper Chicken");
                startActivity(renderer);
            }
        });
        layout.addView(rendererButton);

        TextView ntfyTitle = new TextView(this);
        ntfyTitle.setText("Ntfy push listener");
        ntfyTitle.setTextSize(20f);
        ntfyTitle.setPadding(0, pad, 0, 0);
        layout.addView(ntfyTitle);

        TextView ntfyBody = new TextView(this);
        ntfyBody.setText(
                "The hatch service also listens to an ntfy topic and auto-opens\n"
                        + "renders and notifications. Enter the server, topic, and a\n"
                        + "read token for that topic here, on the device only.\n"
                        + "The token is stored in app-private storage, never in the repo.\n"
                        + "After saving, Stop then Start hatch to pick up a new token.");
        ntfyBody.setPadding(0, 0, 0, pad);
        layout.addView(ntfyBody);

        final EditText serverField = new EditText(this);
        serverField.setHint("ntfy server");
        serverField.setText(NtfyListener.savedServer(this));
        layout.addView(serverField);

        final EditText topicField = new EditText(this);
        topicField.setHint("topic");
        topicField.setText(NtfyListener.savedTopic(this));
        layout.addView(topicField);

        final EditText tokenField = new EditText(this);
        tokenField.setHint("read token for topic");
        tokenField.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        tokenField.setText(NtfyListener.savedToken(this));
        layout.addView(tokenField);

        Button saveNtfyButton = new Button(this);
        saveNtfyButton.setText("Save ntfy config");
        saveNtfyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SharedPreferences prefs = getSharedPreferences(
                        "skylight_config", MODE_PRIVATE);
                prefs.edit()
                        .putString("ntfy_server", serverField.getText().toString().trim())
                        .putString("ntfy_topic", topicField.getText().toString().trim())
                        .putString("ntfy_token", tokenField.getText().toString().trim())
                        .apply();
            }
        });
        layout.addView(saveNtfyButton);

        setContentView(layout);
    }
}
