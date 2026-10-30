package net.cogdev.skylightoverlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Subscribes to a self-hosted ntfy topic over SSE (JSON stream) and
 * dispatches payloads to the renderer.
 *
 * Why ntfy, not a direct POST to the Max: anyone with access to the
 * topic can publish with a plain ntfy client (curl, app, CLI), without
 * knowing the Max's IP/port or crafting a custom POST. The Max only
 * needs outbound HTTPS to ntfy.home.kina.st, which fits the IoT VLAN
 * direction (trusted can initiate to IoT, IoT outbound to the public
 * ntfy URL is the normal path).
 *
 * Server constraints, from the ntfy connector (2026-10-24):
 * - auth-default-access: deny-all, every topic operation needs a token.
 * - message size limit 4096 bytes. A full HTML artifact will exceed
 *   it, so render payloads must NOT rely on inline HTML alone.
 *
 * Payload data model (message body, JSON when present):
 *   {"type":"render","title":"...","url":"https://..."}
 *   {"type":"render","title":"...","html":"<html>..."}   (small only, <4096)
 *   {"type":"render","title":"..."} + ntfy file attachment = HTML file
 *   {"type":"notification","level":"standard","title":"...","message":"..."}
 *   {"type":"notification","level":"urgent","title":"...","message":"..."}
 *
 * Fallbacks, so plain ntfy publishes still work:
 * - A plain-text message (not JSON) is a standard notification.
 * - A plain message that looks like a URL is a render of that URL.
 * - ntfy native priority 5 / tag "urgent" maps to level urgent;
 *   otherwise notification level defaults to standard.
 * - Urgent treatment (fullscreen, sound, persistence) is deferred
 *   by design; the level is carried in the data model now so a later
 *   change is only in the notification view, not the wire format.
 *
 * Token handling: the token is NEVER in the repo or in code. It is
 * entered once in MainActivity on the device and stored in app-private
 * SharedPreferences. It is sent only as a Bearer header to the
 * configured ntfy server, and never logged.
 */
public class NtfyListener {

    public interface Callback {
        void onRenderHtml(String title, String html);
        void onRenderUrl(String title, String url);
        void onRenderFile(String title, File file);
        void onNotification(String level, String title, String message);
    }

    private static final String TAG = "NtfyListener";
    private static final String PREFS = "skylight_config";
    private static final String KEY_LAST_ID = "ntfy_last_id";

    private final Context context;
    private final String server;
    private final String topic;
    private final String token;
    private final Callback callback;
    private volatile boolean running = false;
    private Thread thread;

    public NtfyListener(Context context, String server, String topic, String token, Callback callback) {
        this.context = context.getApplicationContext();
        this.server = server != null ? server.replaceAll("/+$", "") : "";
        this.topic = topic != null ? topic : "";
        this.token = token != null ? token : "";
        this.callback = callback;
    }

    public static String savedServer(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString("ntfy_server", "https://ntfy.home.kina.st");
    }

    public static String savedTopic(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString("ntfy_topic", "skylight");
    }

    public static String savedToken(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString("ntfy_token", "");
    }

    public synchronized void start() {
        if (running) return;
        if (server.isEmpty() || topic.isEmpty() || token.isEmpty()) {
            Log.i(TAG, "Not starting: ntfy server/topic/token not configured");
            return;
        }
        running = true;
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                runLoop();
            }
        }, "ntfy-listener");
        thread.start();
        Log.i(TAG, "Started listener for topic " + topic);
    }

    public synchronized void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }

    private void runLoop() {
        long backoffMs = 5000L;
        while (running) {
            try {
                subscribeOnce();
                backoffMs = 5000L;
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) {
                if (!running) return;
                Log.w(TAG, "ntfy connection failed, retry in " + backoffMs + "ms: " + e);
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    return;
                }
                if (backoffMs < 60000L) backoffMs *= 2;
            }
        }
    }

    private void subscribeOnce() throws Exception {
        String urlStr = server + "/" + topic + "/json";
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(0);
        int code = conn.getResponseCode();
        if (code == 401 || code == 403) {
            Log.w(TAG, "ntfy auth rejected (" + code + "): check topic/token ACL, not retrying fast");
            throw new Exception("ntfy auth " + code);
        }
        if (code != 200) {
            throw new Exception("ntfy HTTP " + code);
        }
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new BufferedInputStream(conn.getInputStream()), "UTF-8"));
        String line;
        while (running && (line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            handleEvent(line);
        }
        reader.close();
        conn.disconnect();
    }

    private void handleEvent(String json) {
        String event = extractString(json, "event");
        if (event != null && !event.equals("message")) return;
        String id = extractString(json, "id");
        if (id != null && !id.isEmpty()) {
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String last = prefs.getString(KEY_LAST_ID, "");
            if (id.equals(last)) return;
            // Only dedupe against the immediately previous id: ntfy ids
            // are not guaranteed monotonic strings, and a full seen-set
            // would grow unbounded. Reconnects use live-only SSE, so
            // this guards the common duplicate-delivery case.
            prefs.edit().putString(KEY_LAST_ID, id).apply();
        }
        String title = extractString(json, "title");
        String message = extractString(json, "message");
        if (message == null) message = "";
        String attachmentUrl = extractAttachmentUrl(json);
        String ntfyPriority = extractString(json, "priority");
        boolean ntfyUrgent = "5".equals(ntfyPriority)
                || (json.contains("\"urgent\""));
        Log.i(TAG, "ntfy message id=" + id + " title=" + (title != null ? title : ""));
        dispatch(title, message, attachmentUrl, ntfyUrgent);
    }

    private void dispatch(String title, String message, String attachmentUrl, boolean ntfyUrgent) {
        String t = (title != null && !title.isEmpty()) ? title : "Skylight";
        String body = message.trim();
        if (body.startsWith("{")) {
            String type = extractString(body, "type");
            if ("render".equals(type)) {
                String html = extractString(body, "html");
                String url = extractString(body, "url");
                String rt = extractString(body, "title");
                if (rt != null && !rt.isEmpty()) t = rt;
                if (html != null && !html.isEmpty()) {
                    callback.onRenderHtml(t, html);
                    return;
                }
                if (url != null && !url.isEmpty()) {
                    callback.onRenderUrl(t, url);
                    return;
                }
                if (attachmentUrl != null) {
                    File f = downloadAttachment(attachmentUrl, t);
                    if (f != null) {
                        callback.onRenderFile(t, f);
                        return;
                    }
                }
                // Render with no content: fall through to a notification
                // explaining the payload, rather than silently dropping it.
                callback.onNotification("standard", t, "Render payload had no html, url, or attachment.");
                return;
            }
            if ("notification".equals(type)) {
                String level = extractString(body, "level");
                if (level == null || level.isEmpty()) level = ntfyUrgent ? "urgent" : "standard";
                level = level.toLowerCase();
                if (!level.equals("urgent")) level = "standard";
                String nt = extractString(body, "title");
                if (nt != null && !nt.isEmpty()) t = nt;
                String msg = extractString(body, "message");
                if (msg == null) msg = body;
                callback.onNotification(level, t, msg);
                return;
            }
        }
        // Plain-text fallback.
        if (body.startsWith("http://") || body.startsWith("https://")) {
            String url = body.split("\\s+")[0];
            callback.onRenderUrl(t, url);
            return;
        }
        if (attachmentUrl != null) {
            File f = downloadAttachment(attachmentUrl, t);
            if (f != null) {
                callback.onRenderFile(t, f);
                return;
            }
        }
        callback.onNotification(ntfyUrgent ? "urgent" : "standard", t, body);
    }

    private File downloadAttachment(String urlStr, String title) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            if (conn.getResponseCode() != 200) {
                Log.w(TAG, "attachment download HTTP " + conn.getResponseCode());
                return null;
            }
            File out = new File(context.getFilesDir(), "ntfy-render.html");
            InputStream in = new BufferedInputStream(conn.getInputStream());
            FileOutputStream fos = new FileOutputStream(out);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                fos.write(buf, 0, n);
            }
            fos.close();
            in.close();
            conn.disconnect();
            return out;
        } catch (Exception e) {
            Log.w(TAG, "attachment download failed: " + e);
            return null;
        }
    }

    // Minimal JSON string extraction, no library dependency.
    // Finds "key": "value" (first occurrence) and unescapes it.
    static String extractString(String json, String key) {
        if (json == null) return null;
        String needle = "\"" + key + "\":";
        int i = json.indexOf(needle);
        if (i < 0) return null;
        int j = i + needle.length();
        while (j < json.length() && (json.charAt(j) == ' ' || json.charAt(j) == '\t')) j++;
        if (j >= json.length() || json.charAt(j) != '"') return null;
        StringBuilder sb = new StringBuilder();
        j++;
        while (j < json.length()) {
            char c = json.charAt(j);
            if (c == '\\' && j + 1 < json.length()) {
                char n = json.charAt(j + 1);
                if (n == 'n') sb.append('\n');
                else if (n == 't') sb.append('\t');
                else if (n == 'r') sb.append('\r');
                else if (n == 'u' && j + 5 < json.length()) {
                    try {
                        sb.append((char) Integer.parseInt(json.substring(j + 2, j + 6), 16));
                    } catch (Exception ignored) {
                    }
                    j += 4;
                } else sb.append(n);
                j += 2;
            } else if (c == '"') {
                return sb.toString();
            } else {
                sb.append(c);
                j++;
            }
        }
        return sb.toString();
    }

    static String extractAttachmentUrl(String json) {
        if (json == null) return null;
        int a = json.indexOf("\"attachment\"");
        if (a < 0) return null;
        String tail = json.substring(a);
        return extractString(tail, "url");
    }

    public static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
