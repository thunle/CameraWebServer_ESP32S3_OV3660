package de.juliusdonner.visionalarm;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {
    private static final String PREFS = "vision_alarm";
    private static final String KEY_HOST = "host";
    private static final String KEY_HTTP_PORT = "http_port";
    private static final String KEY_STREAM_PORT = "stream_port";
    private static final String KEY_MONITOR_HOST = "monitor_host";
    private static final String KEY_MONITOR_PORT = "monitor_port";
    private static final int BLUE = Color.rgb(37, 99, 235);
    private static final int GREEN = Color.rgb(22, 163, 74);
    private static final int RED = Color.rgb(220, 38, 38);
    private static final int AMBER = Color.rgb(217, 119, 6);
    private static final int TEXT = Color.rgb(17, 24, 39);
    private static final int MUTED = Color.rgb(75, 85, 99);
    private static final int SURFACE = Color.WHITE;
    private static final int BACKGROUND = Color.rgb(245, 247, 250);

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService ioExecutor = Executors.newCachedThreadPool();
    private ScheduledExecutorService poller;

    private SharedPreferences prefs;
    private EditText hostInput;
    private EditText httpPortInput;
    private EditText streamPortInput;
    private EditText monitorHostInput;
    private EditText monitorPortInput;
    private TextView connectionText;
    private TextView sensorText;
    private TextView radarText;
    private TextView lightText;
    private TextView alarmText;
    private TextView cameraText;
    private TextView logsText;
    private TextView streamText;
    private TextView monitorText;
    private ImageView cameraImage;
    private ImageView alarmImage;

    private volatile boolean streamRunning = false;
    private volatile HttpURLConnection streamConnection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        setContentView(buildContentView());
        loadSettings();
        startPolling();
    }

    @Override
    protected void onDestroy() {
        stopStream();
        if (poller != null) {
            poller.shutdownNow();
        }
        ioExecutor.shutdownNow();
        super.onDestroy();
    }

    private View buildContentView() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(BACKGROUND);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(24));
        scrollView.addView(root);

        TextView title = text("Vision Alarm", 28, TEXT, Typeface.BOLD);
        root.addView(title);
        root.addView(text("ESP32-S3 Kamera, Sensorfusion und Alarmsteuerung", 14, MUTED, Typeface.NORMAL));

        LinearLayout connectionCard = card();
        root.addView(connectionCard);
        connectionCard.addView(sectionTitle("Verbindung"));

        hostInput = input("192.168.178.53");
        httpPortInput = input("80");
        streamPortInput = input("81");
        monitorHostInput = input("10.0.2.2");
        monitorPortInput = input("8765");
        httpPortInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        streamPortInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        monitorPortInput.setInputType(InputType.TYPE_CLASS_NUMBER);

        connectionCard.addView(label("ESP32-IP oder Hostname"));
        connectionCard.addView(hostInput);
        connectionCard.addView(label("HTTP-Port"));
        connectionCard.addView(httpPortInput);
        connectionCard.addView(label("Stream-Port"));
        connectionCard.addView(streamPortInput);
        connectionCard.addView(label("Laptop-Monitor-Host"));
        connectionCard.addView(monitorHostInput);
        connectionCard.addView(label("Monitor-API-Port"));
        connectionCard.addView(monitorPortInput);

        LinearLayout connectionButtons = buttonRow();
        connectionButtons.addView(button("Speichern", BLUE, v -> saveSettings()));
        connectionButtons.addView(button("Alles aktualisieren", GREEN, v -> refreshAll()));
        connectionCard.addView(connectionButtons);

        connectionText = valueText("Noch nicht verbunden");
        connectionCard.addView(connectionText);

        LinearLayout dashboardCard = card();
        root.addView(dashboardCard);
        dashboardCard.addView(sectionTitle("Dashboard"));
        sensorText = valueText("Sensoren: warten...");
        radarText = valueText("Radar: unbekannt");
        lightText = valueText("Licht: unbekannt");
        alarmText = valueText("Alarm: unbekannt");
        cameraText = valueText("Kamera: unbekannt");
        dashboardCard.addView(sensorText);
        dashboardCard.addView(radarText);
        dashboardCard.addView(lightText);
        dashboardCard.addView(alarmText);
        dashboardCard.addView(cameraText);

        LinearLayout monitorCard = card();
        root.addView(monitorCard);
        monitorCard.addView(sectionTitle("Alarm-App"));
        monitorText = valueText("Monitor-API: wartet...");
        monitorCard.addView(monitorText);
        LinearLayout monitorButtons = buttonRow();
        monitorButtons.addView(button("Aktivieren", GREEN, v -> setSystemActive(true)));
        monitorButtons.addView(button("Deaktivieren", AMBER, v -> setSystemActive(false)));
        monitorCard.addView(monitorButtons);
        alarmImage = new ImageView(this);
        alarmImage.setBackgroundColor(Color.rgb(15, 23, 42));
        alarmImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        monitorCard.addView(alarmImage, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(220)));

        LinearLayout cameraCard = card();
        root.addView(cameraCard);
        cameraCard.addView(sectionTitle("Kamera"));
        streamText = valueText("Stream: gestoppt");
        cameraCard.addView(streamText);
        cameraImage = new ImageView(this);
        cameraImage.setBackgroundColor(Color.rgb(15, 23, 42));
        cameraImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        cameraCard.addView(cameraImage, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(260)));

        LinearLayout cameraButtons = buttonRow();
        cameraButtons.addView(button("Capture", BLUE, v -> captureFrame()));
        cameraButtons.addView(button("Stream", GREEN, v -> startStream()));
        cameraButtons.addView(button("Stopp", AMBER, v -> stopStream()));
        cameraCard.addView(cameraButtons);

        LinearLayout alarmCard = card();
        root.addView(alarmCard);
        alarmCard.addView(sectionTitle("Buzzer"));
        LinearLayout buzzerButtons = buttonRow();
        buzzerButtons.addView(button("Test 1,5 s", RED, v -> pulseBuzzer(1500)));
        buzzerButtons.addView(button("Aus", AMBER, v -> stopBuzzer()));
        alarmCard.addView(buzzerButtons);

        LinearLayout logsCard = card();
        root.addView(logsCard);
        logsCard.addView(sectionTitle("ESP32-Logs"));
        LinearLayout logButtons = buttonRow();
        logButtons.addView(button("Logs laden", BLUE, v -> loadLogs(false)));
        logButtons.addView(button("Laden + leeren", AMBER, v -> loadLogs(true)));
        logsCard.addView(logButtons);
        logsText = valueText("Noch keine Logs geladen.");
        logsText.setTypeface(Typeface.MONOSPACE);
        logsCard.addView(logsText);

        return scrollView;
    }

    private LinearLayout card() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(dp(14), dp(14), dp(14), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(SURFACE);
        bg.setCornerRadius(dp(8));
        bg.setStroke(1, Color.rgb(229, 231, 235));
        view.setBackground(bg);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(14);
        view.setLayoutParams(params);
        return view;
    }

    private TextView sectionTitle(String value) {
        TextView text = text(value, 18, TEXT, Typeface.BOLD);
        text.setPadding(0, 0, 0, dp(8));
        return text;
    }

    private TextView label(String value) {
        TextView text = text(value, 12, MUTED, Typeface.BOLD);
        text.setPadding(0, dp(8), 0, dp(4));
        return text;
    }

    private TextView valueText(String value) {
        TextView text = text(value, 14, TEXT, Typeface.NORMAL);
        text.setPadding(0, dp(4), 0, dp(4));
        return text;
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(sp);
        text.setTextColor(color);
        text.setTypeface(Typeface.DEFAULT, style);
        text.setLineSpacing(0, 1.08f);
        return text;
    }

    private EditText input(String hint) {
        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setHint(hint);
        edit.setTextColor(TEXT);
        edit.setHintTextColor(Color.rgb(156, 163, 175));
        edit.setTextSize(16);
        edit.setPadding(dp(10), 0, dp(10), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(249, 250, 251));
        bg.setCornerRadius(dp(6));
        bg.setStroke(1, Color.rgb(209, 213, 219));
        edit.setBackground(bg);
        edit.setMinHeight(dp(44));
        return edit;
    }

    private LinearLayout buttonRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(4));
        return row;
    }

    private Button button(String value, int color, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(value);
        button.setTextColor(Color.WHITE);
        button.setTextSize(13);
        button.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(6));
        button.setBackground(bg);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, dp(44), 1f);
        params.setMargins(dp(3), 0, dp(3), 0);
        button.setLayoutParams(params);
        return button;
    }

    private void loadSettings() {
        hostInput.setText(prefs.getString(KEY_HOST, "192.168.178.53"));
        httpPortInput.setText(String.valueOf(prefs.getInt(KEY_HTTP_PORT, 80)));
        streamPortInput.setText(String.valueOf(prefs.getInt(KEY_STREAM_PORT, 81)));
        monitorHostInput.setText(prefs.getString(KEY_MONITOR_HOST, "10.0.2.2"));
        monitorPortInput.setText(String.valueOf(prefs.getInt(KEY_MONITOR_PORT, 8765)));
    }

    private void saveSettings() {
        prefs.edit()
                .putString(KEY_HOST, host())
                .putInt(KEY_HTTP_PORT, parsePort(httpPortInput, 80))
                .putInt(KEY_STREAM_PORT, parsePort(streamPortInput, 81))
                .putString(KEY_MONITOR_HOST, monitorHost())
                .putInt(KEY_MONITOR_PORT, parsePort(monitorPortInput, 8765))
                .apply();
        setConnection("Einstellungen gespeichert.", GREEN);
        refreshAll();
    }

    private void startPolling() {
        poller = Executors.newSingleThreadScheduledExecutor();
        poller.scheduleAtFixedRate(this::refreshSensorsOnly, 0, 1, TimeUnit.SECONDS);
        poller.scheduleAtFixedRate(this::refreshStatusOnly, 0, 4, TimeUnit.SECONDS);
        poller.scheduleAtFixedRate(this::refreshMonitorOnly, 0, 2, TimeUnit.SECONDS);
    }

    private void refreshAll() {
        refreshSensorsOnly();
        refreshStatusOnly();
        refreshMonitorOnly();
        captureFrame();
    }

    private void refreshSensorsOnly() {
        ioExecutor.execute(() -> {
            try {
                JSONObject json = getJson(httpUrl("/sensors"));
                boolean radar = json.optBoolean("radar_active", false);
                boolean daylight = json.optBoolean("daylight", false);
                int light = json.optInt("light_raw", 0);
                int threshold = json.optInt("light_day_threshold", 0);
                int radarCount = json.optInt("radar_motion_count", 0);
                int darkCount = json.optInt("dark_alarm_count", 0);
                boolean buzzer = json.optBoolean("buzzer_active", false);
                long sensorAge = json.optLong("sensor_age_ms", 0);

                mainHandler.post(() -> {
                    setConnection("ESP32 erreichbar: " + httpUrl(""), GREEN);
                    sensorText.setText(String.format(Locale.GERMANY,
                            "Sensorfusion: %s, Alter %d ms",
                            daylight ? "Tagbetrieb" : "Nachtbetrieb", sensorAge));
                    sensorText.setTextColor(daylight ? GREEN : AMBER);
                    radarText.setText(String.format(Locale.GERMANY,
                            "Radar: %s, Bewegungen: %d",
                            radar ? "Bewegung erkannt" : "keine Bewegung", radarCount));
                    radarText.setTextColor(radar ? RED : GREEN);
                    lightText.setText(String.format(Locale.GERMANY,
                            "Licht: %d / Schwelle %d (%s)",
                            light, threshold, daylight ? "hell genug" : "dunkel"));
                    lightText.setTextColor(daylight ? GREEN : AMBER);
                    alarmText.setText(String.format(Locale.GERMANY,
                            "Buzzer: %s, Nachtalarme: %d",
                            buzzer ? "aktiv" : "aus", darkCount));
                    alarmText.setTextColor(buzzer ? RED : TEXT);
                });
            } catch (Exception ex) {
                mainHandler.post(() -> {
                    setConnection("Keine Verbindung zu /sensors: " + cleanError(ex), RED);
                    sensorText.setText("Sensorfusion: nicht erreichbar");
                    sensorText.setTextColor(RED);
                });
            }
        });
    }

    private void refreshStatusOnly() {
        ioExecutor.execute(() -> {
            try {
                JSONObject json = getJson(httpUrl("/status"));
                String status = String.format(Locale.GERMANY,
                        "Kamera: framesize=%d quality=%d brightness=%d contrast=%d saturation=%d",
                        json.optInt("framesize", -1),
                        json.optInt("quality", -1),
                        json.optInt("brightness", 0),
                        json.optInt("contrast", 0),
                        json.optInt("saturation", 0));
                mainHandler.post(() -> {
                    cameraText.setText(status);
                    cameraText.setTextColor(TEXT);
                });
            } catch (Exception ex) {
                mainHandler.post(() -> {
                    cameraText.setText("Kamera: /status nicht erreichbar: " + cleanError(ex));
                    cameraText.setTextColor(RED);
                });
            }
        });
    }

    private void refreshMonitorOnly() {
        ioExecutor.execute(() -> {
            try {
                JSONObject json = getJson(monitorUrl("/api/status"));
                boolean active = json.optBoolean("active", false);
                int alarmCount = json.optInt("alarm_count", 0);
                JSONObject latest = json.optJSONObject("latest_alarm");
                String latestText = "noch kein Alarmbild";
                boolean hasImage = false;
                if (latest != null) {
                    latestText = latest.optString("timestamp", "") + " - "
                            + latest.optString("message", "Alarm");
                    hasImage = latest.optString("image_url", "").length() > 0;
                }
                String finalLatestText = latestText;
                boolean finalHasImage = hasImage;
                mainHandler.post(() -> {
                    monitorText.setText(String.format(Locale.GERMANY,
                            "Monitor: %s, Alarme: %d, letzter Alarm: %s",
                            active ? "aktiv" : "deaktiviert", alarmCount, finalLatestText));
                    monitorText.setTextColor(active ? GREEN : AMBER);
                });
                if (finalHasImage) {
                    loadLatestAlarmImage();
                }
            } catch (Exception ex) {
                mainHandler.post(() -> {
                    monitorText.setText("Monitor-API nicht erreichbar: " + cleanError(ex));
                    monitorText.setTextColor(RED);
                });
            }
        });
    }

    private void setSystemActive(boolean active) {
        ioExecutor.execute(() -> {
            try {
                getString(monitorUrl("/api/system/active?value=" + (active ? "1" : "0")), 4000);
                mainHandler.post(() -> {
                    monitorText.setText(active ? "Monitor aktiviert." : "Monitor deaktiviert.");
                    monitorText.setTextColor(active ? GREEN : AMBER);
                });
                refreshMonitorOnly();
            } catch (Exception ex) {
                mainHandler.post(() -> {
                    monitorText.setText("Aktiv-Schalter fehlgeschlagen: " + cleanError(ex));
                    monitorText.setTextColor(RED);
                });
            }
        });
    }

    private void loadLatestAlarmImage() {
        try {
            byte[] bytes = getBytes(monitorUrl("/api/alarm/latest.jpg"), 5000);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap != null) {
                mainHandler.post(() -> alarmImage.setImageBitmap(bitmap));
            }
        } catch (Exception ignored) {
            // Status text already reports API availability; missing image is not fatal.
        }
    }

    private void captureFrame() {
        ioExecutor.execute(() -> {
            try {
                byte[] bytes = getBytes(httpUrl("/capture"), 10000);
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bitmap == null) {
                    throw new IllegalStateException("JPEG konnte nicht dekodiert werden.");
                }
                mainHandler.post(() -> {
                    cameraImage.setImageBitmap(bitmap);
                    streamText.setText("Capture geladen: " + bitmap.getWidth() + " x " + bitmap.getHeight());
                    streamText.setTextColor(TEXT);
                });
            } catch (Exception ex) {
                mainHandler.post(() -> {
                    streamText.setText("Capture fehlgeschlagen: " + cleanError(ex));
                    streamText.setTextColor(RED);
                });
            }
        });
    }

    private void startStream() {
        if (streamRunning) {
            return;
        }
        streamRunning = true;
        streamText.setText("Stream: verbindet...");
        streamText.setTextColor(AMBER);
        ioExecutor.execute(this::streamLoop);
    }

    private void stopStream() {
        streamRunning = false;
        HttpURLConnection connection = streamConnection;
        if (connection != null) {
            connection.disconnect();
        }
        mainHandler.post(() -> {
            streamText.setText("Stream: gestoppt");
            streamText.setTextColor(TEXT);
        });
    }

    private void streamLoop() {
        try {
            URL url = new URL(streamUrl("/stream"));
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            streamConnection = connection;
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(15000);
            connection.setUseCaches(false);
            connection.connect();
            if (connection.getResponseCode() >= 400) {
                throw new IllegalStateException("HTTP " + connection.getResponseCode());
            }
            mainHandler.post(() -> {
                streamText.setText("Stream: live");
                streamText.setTextColor(GREEN);
            });
            try (InputStream input = connection.getInputStream()) {
                readMjpeg(input);
            }
        } catch (Exception ex) {
            if (streamRunning) {
                mainHandler.post(() -> {
                    streamText.setText("Streamfehler: " + cleanError(ex));
                    streamText.setTextColor(RED);
                });
            }
        } finally {
            streamRunning = false;
            HttpURLConnection connection = streamConnection;
            if (connection != null) {
                connection.disconnect();
            }
            streamConnection = null;
        }
    }

    private void readMjpeg(InputStream input) throws Exception {
        ByteArrayOutputStream frame = new ByteArrayOutputStream(96 * 1024);
        boolean inJpeg = false;
        int previous = -1;
        int current;

        while (streamRunning && (current = input.read()) != -1) {
            if (!inJpeg) {
                if (previous == 0xFF && current == 0xD8) {
                    inJpeg = true;
                    frame.reset();
                    frame.write(0xFF);
                    frame.write(0xD8);
                }
            } else {
                frame.write(current);
                if (previous == 0xFF && current == 0xD9) {
                    byte[] jpeg = frame.toByteArray();
                    Bitmap bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
                    if (bitmap != null) {
                        mainHandler.post(() -> cameraImage.setImageBitmap(bitmap));
                    }
                    inJpeg = false;
                    frame.reset();
                } else if (frame.size() > 2_000_000) {
                    inJpeg = false;
                    frame.reset();
                }
            }
            previous = current;
        }
    }

    private void pulseBuzzer(int durationMs) {
        ioExecutor.execute(() -> {
            try {
                getString(httpUrl("/buzzer?state=on&duration_ms=" + durationMs), 3000);
                mainHandler.post(() -> {
                    alarmText.setText("Buzzer: Testimpuls gesendet (" + durationMs + " ms)");
                    alarmText.setTextColor(RED);
                });
            } catch (Exception ex) {
                mainHandler.post(() -> {
                    alarmText.setText("Buzzer-Test fehlgeschlagen: " + cleanError(ex));
                    alarmText.setTextColor(RED);
                });
            }
        });
    }

    private void stopBuzzer() {
        ioExecutor.execute(() -> {
            try {
                getString(httpUrl("/buzzer?state=off"), 3000);
                mainHandler.post(() -> {
                    alarmText.setText("Buzzer: ausgeschaltet");
                    alarmText.setTextColor(GREEN);
                });
            } catch (Exception ex) {
                mainHandler.post(() -> {
                    alarmText.setText("Buzzer-Aus fehlgeschlagen: " + cleanError(ex));
                    alarmText.setTextColor(RED);
                });
            }
        });
    }

    private void loadLogs(boolean clear) {
        ioExecutor.execute(() -> {
            try {
                String logs = getString(httpUrl("/logs" + (clear ? "?clear=1" : "")), 5000);
                mainHandler.post(() -> logsText.setText(logs.trim().isEmpty() ? "Keine Logs." : logs));
            } catch (Exception ex) {
                mainHandler.post(() -> logsText.setText("Logs nicht erreichbar: " + cleanError(ex)));
            }
        });
    }

    private JSONObject getJson(String url) throws Exception {
        return new JSONObject(getString(url, 4000));
    }

    private String getString(String url, int timeoutMs) throws Exception {
        byte[] bytes = getBytes(url, timeoutMs);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private byte[] getBytes(String urlString, int timeoutMs) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setConnectTimeout(timeoutMs);
        connection.setReadTimeout(timeoutMs);
        connection.setUseCaches(false);
        try {
            int responseCode = connection.getResponseCode();
            if (responseCode >= 400) {
                throw new IllegalStateException("HTTP " + responseCode);
            }
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                return output.toByteArray();
            }
        } finally {
            connection.disconnect();
        }
    }

    private String host() {
        return hostInput.getText().toString().trim();
    }

    private String monitorHost() {
        return monitorHostInput.getText().toString().trim();
    }

    private int parsePort(EditText editText, int fallback) {
        try {
            int port = Integer.parseInt(editText.getText().toString().trim());
            return port > 0 && port <= 65535 ? port : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String httpUrl(String path) {
        String cleanPath = path == null ? "" : path;
        if (!cleanPath.isEmpty() && !cleanPath.startsWith("/")) {
            cleanPath = "/" + cleanPath;
        }
        return "http://" + host() + ":" + parsePort(httpPortInput, 80) + cleanPath;
    }

    private String streamUrl(String path) {
        String cleanPath = path == null ? "" : path;
        if (!cleanPath.isEmpty() && !cleanPath.startsWith("/")) {
            cleanPath = "/" + cleanPath;
        }
        return "http://" + host() + ":" + parsePort(streamPortInput, 81) + cleanPath;
    }

    private String monitorUrl(String path) {
        String cleanPath = path == null ? "" : path;
        if (!cleanPath.isEmpty() && !cleanPath.startsWith("/")) {
            cleanPath = "/" + cleanPath;
        }
        return "http://" + monitorHost() + ":" + parsePort(monitorPortInput, 8765) + cleanPath;
    }

    private void setConnection(String message, int color) {
        String prefix = hasWifiOrEthernet() ? "" : "Kein WLAN/LAN erkannt. ";
        connectionText.setText(prefix + message);
        connectionText.setTextColor(color);
    }

    private boolean hasWifiOrEthernet() {
        ConnectivityManager manager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (manager == null) {
            return true;
        }
        Network network = manager.getActiveNetwork();
        if (network == null) {
            return false;
        }
        NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
        return capabilities != null
                && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
    }

    private String cleanError(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return ex.getClass().getSimpleName();
        }
        return message;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
