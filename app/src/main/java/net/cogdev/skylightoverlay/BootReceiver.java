package net.cogdev.skylightoverlay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * Re-start the hatch after a reboot, so wireless-off-after-reboot
 * does not also mean hatch-off-after-reboot.
 *
 * Note: this is untested on the Max's watchdog/battery behaviour as
 * of v0.1.0. If the service does not come back after a reboot, that
 * is a known test item, not a surprise: see README.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Intent service = new Intent(context, OverlayService.class);
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(service);
                } else {
                    context.startService(service);
                }
            } catch (Exception ignored) {
                // Background-start restrictions on some builds will
                // refuse this; the USB cable remains the fallback.
            }
        }
    }
}
