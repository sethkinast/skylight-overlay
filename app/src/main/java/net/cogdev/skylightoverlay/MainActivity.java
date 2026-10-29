package net.cogdev.skylightoverlay;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
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
                        + "Tap: nothing.\n"
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

        setContentView(layout);
    }
}
