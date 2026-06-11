package de.juliusdonner.visionalarm;

import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Typeface;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.net.ConnectivityManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivityV2 extends Activity {
    private static final String TAG = "VisionAlarm";
    private static final String PREFS = "vision_alarm";
    private static final String KEY_HOST = "host";
    private static final String KEY_HTTP_PORT = "http_port";
    private static final String KEY_STREAM_PORT = "stream_port";
    private static final String KEY_MONITOR_HOST = "monitor_host";
    private static final String KEY_MONITOR_PORT = "monitor_port";
    private static final String KEY_SELECTED_MODE = "selected_mode";
    private static final String KEY_CONNECTION_PROFILE = "connection_profile";
    private static final String KEY_PHONE_STREAM_ENABLED = "phone_stream_enabled";
    private static final String PROFILE_NORMAL = "normal";
    private static final String PROFILE_ANDROID_HOTSPOT = "android_hotspot";
    private static final String DEFAULT_ESP_HOST = "192.168.178.53";
    private static final String DEFAULT_ANDROID_HOTSPOT_ESP_HOST = "192.168.124.222";
    private static final String DEFAULT_ANDROID_HOTSPOT_MONITOR_HOST = "192.168.43.1";

    private static final int BLACK = Color.rgb(16, 16, 18);
    private static final int TEXT = Color.rgb(21, 21, 24);
    private static final int MUTED = Color.rgb(145, 145, 150);
    private static final int TILE = Color.rgb(247, 247, 248);
    private static final int SOFT = Color.rgb(244, 244, 245);
    private static final int BLUE = Color.rgb(37, 99, 235);
    private static final int RED = Color.rgb(255, 52, 68);
    private static final int GREEN = Color.rgb(62, 221, 168);
    private static final int AMBER = Color.rgb(217, 119, 6);
    private static final int ERROR = Color.rgb(220, 38, 38);
    private static final int MODE_DISABLED = 0;
    private static final int MODE_HOME = 1;
    private static final int MODE_AWAY = 2;
    private static final int DEFAULT_RADIUS_DP = 16;
    private static final int TILE_RADIUS_DP = 12;
    private static final int LIVE_RADIUS_DP = 16;
    private static final String HISTORY_CACHE_DIR = "history_cache";
    private static final String HISTORY_CACHE_FILE = "alarm_events.json";
    private static final String KEY_HIDDEN_HISTORY_IDS = "hidden_history_ids";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService ioExecutor = Executors.newCachedThreadPool();
    private ScheduledExecutorService poller;

    private SharedPreferences prefs;
    private LinearLayout contentRoot;
    private TextView screenTitle;
    private EditText hostInput;
    private EditText httpPortInput;
    private EditText streamPortInput;
    private EditText monitorHostInput;
    private EditText monitorPortInput;
    private TextView connectionText;
    private TextView cameraText;
    private TextView radarText;
    private TextView lightText;
    private TextView alarmText;
    private TextView monitorText;
    private TextView streamText;
    private TextView connectionProfileText;
    private Switch phoneStreamSwitch;
    private TextView espStatusTileText;
    private TextView laptopStatusTileText;
    private volatile boolean laptopMonitorActive;
    private TextView radarTileText;
    private TextView lightTileText;
    private TextView buzzerTestTileText;
    private View liveDot;
    private TextView liveBadgeText;
    private ImageView cameraImage;
    private Switch buzzerSwitch;
    private Switch monitorSwitch;
    private final FrameLayout[] modeCircles = new FrameLayout[3];
    private final ImageView[] modeIcons = new ImageView[3];
    private final TextView[] modeLabels = new TextView[3];
    private final LinearLayout[] navItems = new LinearLayout[3];
    private final TextView[] navLabels = new TextView[3];
    private int selectedNavIndex = 0;
    private final LinkedHashSet<Integer> selectedHistoryEventIds = new LinkedHashSet<>();
    private final LinkedHashSet<Integer> hiddenHistoryEventIds = new LinkedHashSet<>();
    private boolean historySelectionMode = false;
    private JSONObject currentHistoryPayload;
    private LinearLayout historyActionBar;
    private TextView historySelectionText;
    private Button historyDeleteButton;
    private Button historyCancelButton;

    private boolean suppressSwitchEvents = false;
    private int selectedMode = MODE_HOME;
    private boolean showingClipPlayer = false;
    private boolean showingSettingsScreen = false;
    private boolean showingZonesEditor = false;
    private VideoView activeClipVideo;
    private SeekBar activeClipSeekBar;
    private TextView activeClipTimeText;
    private ImageButton activeClipPlayButton;
    private volatile boolean activeClipScrubbing;
    private volatile boolean activeClipWasPlayingBeforeScrub;
    private ZoneEditorView activeZoneEditorView;
    private TextView zonesStatusText;
    private TextView zonesDetailsText;
    private Button zonesRefreshButton;
    private LinearLayout zonesActionBar;
    private ImageButton zonesRenameButton;
    private ImageButton zonesDeleteButton;
    private volatile boolean zonesSaveInFlight;
    private volatile boolean zonesSaveQueued;
    private volatile boolean streamRunning = false;
    private volatile boolean appInForeground = false;
    private volatile HttpURLConnection streamConnection;
    private volatile String connectionProfile = PROFILE_NORMAL;
    private volatile boolean phoneStreamEnabled = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        clearOldClipCache();
        loadHiddenHistoryIds();
        setContentView(buildContentView());
        loadSettings();
        startPolling();
        captureFrame();
    }

    @Override
    protected void onResume() {
        super.onResume();
        appInForeground = true;
    }

    @Override
    protected void onPause() {
        appInForeground = false;
        stopStream(false);
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (showingClipPlayer) {
            showHistoryScreen();
            return;
        }
        if (showingSettingsScreen) {
            showHomeScreen();
            selectNav(0);
            return;
        }
        if (showingZonesEditor) {
            showHomeScreen();
            selectNav(0);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        stopStream(false);
        if (poller != null) {
            poller.shutdownNow();
        }
        ioExecutor.shutdownNow();
        super.onDestroy();
    }

    private View buildContentView() {
        FrameLayout shell = new FrameLayout(this);
        shell.setBackgroundColor(Color.WHITE);
        shell.setClipChildren(false);
        shell.setClipToPadding(false);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setClipChildren(false);
        scrollView.setClipToPadding(false);
        scrollView.setBackgroundColor(Color.WHITE);
        shell.addView(scrollView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        contentRoot = new LinearLayout(this);
        contentRoot.setOrientation(LinearLayout.VERTICAL);
        contentRoot.setClipChildren(false);
        contentRoot.setClipToPadding(false);
        contentRoot.setPadding(dp(16), dp(28), dp(16), dp(112));
        scrollView.addView(contentRoot, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        showHomeScreen();
        shell.addView(bottomNav(), bottomNavParams());
        return shell;
    }

    private void setContentRootMode(boolean clipMode) {
        if (contentRoot == null) {
            return;
        }
        ScrollView.LayoutParams params = new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                clipMode ? ScrollView.LayoutParams.MATCH_PARENT : ScrollView.LayoutParams.WRAP_CONTENT);
        contentRoot.setLayoutParams(params);
        contentRoot.setGravity(clipMode ? (Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL) : Gravity.TOP);
    }

    private void showHomeScreen() {
        if (contentRoot == null) {
            return;
        }
        showingClipPlayer = false;
        showingSettingsScreen = false;
        showingZonesEditor = false;
        activeClipVideo = null;
        activeClipSeekBar = null;
        activeClipTimeText = null;
        activeClipPlayButton = null;
        activeClipScrubbing = false;
        activeClipWasPlayingBeforeScrub = false;
        setContentRootMode(false);
        contentRoot.setPadding(dp(16), dp(28), dp(16), dp(112));
        contentRoot.removeAllViews();
        contentRoot.setBackgroundColor(Color.WHITE);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        contentRoot.addView(header());
        contentRoot.addView(modeRow());

        contentRoot.addView(sectionTitle("Wohnzimmer", 22));
        contentRoot.addView(liveCard());

        contentRoot.addView(sectionTitle("Dashboard", 22));
        contentRoot.addView(dashboardTiles());

        contentRoot.addView(sectionTitle("Details", 22));
        contentRoot.addView(dashboard());
        loadSettings();
        refreshAll();
    }

    private void showSettingsScreen() {
        if (contentRoot == null) {
            return;
        }
        showingClipPlayer = false;
        showingSettingsScreen = true;
        showingZonesEditor = false;
        stopStream(false);
        setContentRootMode(false);
        contentRoot.setPadding(dp(16), dp(28), dp(16), dp(112));
        contentRoot.removeAllViews();
        contentRoot.setBackgroundColor(Color.WHITE);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(18));

        FrameLayout back = circleIcon(R.drawable.ic_dashboard_24, BLACK, TILE, dp(42), dp(22));
        back.setOnClickListener(v -> {
            showHomeScreen();
            selectNav(0);
        });
        applyTapAnimation(back);
        header.addView(back);

        LinearLayout titleColumn = new LinearLayout(this);
        titleColumn.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = dp(12);
        header.addView(titleColumn, titleParams);
        titleColumn.addView(text("Einstellungen", 25, TEXT, Typeface.BOLD));
        titleColumn.addView(text("Verbindung, Alarm und App-Verhalten", 13, MUTED, Typeface.NORMAL));

        contentRoot.addView(header);

        contentRoot.addView(sectionTitle("System", 20));
        contentRoot.addView(settingsPanel());

        LinearLayout futurePanel = card(TILE, dp(TILE_RADIUS_DP));
        futurePanel.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams futureParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        futureParams.topMargin = dp(14);
        futurePanel.setLayoutParams(futureParams);
        futurePanel.addView(text("Weitere Einstellungen", 16, TEXT, Typeface.BOLD));
        TextView futureText = text(
                "Hier ist Platz fuer Kamera-Qualitaet, Zonen-Regeln, Benachrichtigungen und Zeitplaene.",
                13, MUTED, Typeface.NORMAL);
        futureText.setPadding(0, dp(6), 0, 0);
        futurePanel.addView(futureText);
        contentRoot.addView(futurePanel);

        loadSettings();
        refreshSensorsOnly();
        refreshMonitorOnly();
    }

    private void showHistoryScreen() {
        if (contentRoot == null) {
            return;
        }
        showingClipPlayer = false;
        showingSettingsScreen = false;
        showingZonesEditor = false;
        activeClipVideo = null;
        activeClipSeekBar = null;
        activeClipTimeText = null;
        activeClipPlayButton = null;
        setContentRootMode(false);
        contentRoot.setPadding(dp(16), dp(28), dp(16), dp(112));
        stopStream(false);
        contentRoot.removeAllViews();
        contentRoot.setBackgroundColor(Color.rgb(24, 28, 31));
        getWindow().setStatusBarColor(Color.rgb(24, 28, 31));
        getWindow().setNavigationBarColor(Color.rgb(24, 28, 31));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(18));
        TextView title = text("Ergebnisverlauf", 25, Color.WHITE, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button refresh = button("Aktualisieren", Color.rgb(39, 44, 49), v -> loadAlarmEvents());
        header.addView(refresh, new LinearLayout.LayoutParams(dp(132), dp(42)));
        contentRoot.addView(header);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        tabs.setPadding(0, 0, 0, dp(14));
        tabs.addView(historyPill("Personen"));
        tabs.addView(historyPill("Bewegung"));
        contentRoot.addView(tabs);

        historyActionBar = new LinearLayout(this);
        historyActionBar.setOrientation(LinearLayout.HORIZONTAL);
        historyActionBar.setGravity(Gravity.CENTER_VERTICAL);
        historyActionBar.setPadding(0, dp(4), 0, dp(12));
        historyActionBar.setVisibility(View.GONE);

        historySelectionText = text("0 ausgewählt", 13, MUTED, Typeface.BOLD);
        historyActionBar.addView(historySelectionText, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        historyDeleteButton = button("Löschen", RED, v -> deleteSelectedHistoryEvents());
        historyDeleteButton.setEnabled(false);
        historyActionBar.addView(historyDeleteButton, new LinearLayout.LayoutParams(
                0, dp(42), 1f));

        historyCancelButton = button("Abbrechen", Color.rgb(39, 44, 49), v -> exitHistorySelectionMode());
        historyActionBar.addView(historyCancelButton, new LinearLayout.LayoutParams(
                0, dp(42), 1f));
        contentRoot.addView(historyActionBar);

        TextView loading = text("Lade Events...", 14, Color.rgb(185, 190, 195), Typeface.NORMAL);
        loading.setGravity(Gravity.CENTER);
        loading.setPadding(0, dp(40), 0, 0);
        loading.setTag("history-loading");
        contentRoot.addView(loading);
        loadAlarmEvents();
    }

    private TextView historyPill(String label) {
        TextView pill = text(label, 13, Color.WHITE, Typeface.BOLD);
        pill.setGravity(Gravity.CENTER);
        pill.setBackground(rounded(Color.rgb(39, 44, 49), dp(DEFAULT_RADIUS_DP)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, dp(36), 1f);
        params.rightMargin = dp(8);
        pill.setLayoutParams(params);
        return pill;
    }

    private void showZonesScreen() {
        if (contentRoot == null) {
            return;
        }
        showingClipPlayer = false;
        showingSettingsScreen = false;
        showingZonesEditor = true;
        activeClipVideo = null;
        activeClipSeekBar = null;
        activeClipTimeText = null;
        activeClipPlayButton = null;
        zonesSaveInFlight = false;
        zonesSaveQueued = false;
        stopStream(false);
        setContentRootMode(false);
        contentRoot.setPadding(dp(16), dp(28), dp(16), dp(112));
        contentRoot.removeAllViews();
        contentRoot.setBackgroundColor(Color.rgb(24, 28, 31));
        getWindow().setStatusBarColor(Color.rgb(24, 28, 31));
        getWindow().setNavigationBarColor(Color.rgb(24, 28, 31));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(18));
        TextView title = text("No-Go Zonen", 25, Color.WHITE, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        zonesRefreshButton = button("Aktualisieren", Color.rgb(39, 44, 49), v -> loadZonesEditor(true));
        header.addView(zonesRefreshButton, new LinearLayout.LayoutParams(dp(132), dp(42)));
        contentRoot.addView(header);

        zonesStatusText = text("Lade aktuelles Bild ...", 14, Color.rgb(185, 190, 195), Typeface.NORMAL);
        zonesStatusText.setPadding(0, 0, 0, dp(8));
        contentRoot.addView(zonesStatusText);

        zonesDetailsText = text(
                "Tippe Punkte ins Bild. Den ersten Punkt erneut antippen, um die Zone zu schließen.",
                13,
                Color.rgb(185, 190, 195),
                Typeface.NORMAL);
        zonesDetailsText.setPadding(0, 0, 0, dp(14));
        contentRoot.addView(zonesDetailsText);

        FrameLayout editorFrame = new FrameLayout(this);
        editorFrame.setBackground(rounded(Color.rgb(16, 16, 18), dp(DEFAULT_RADIUS_DP)));
        editorFrame.setClipToOutline(true);
        editorFrame.setOutlineProvider(new RoundedOutline(dp(DEFAULT_RADIUS_DP)));
        LinearLayout.LayoutParams editorParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(480));
        contentRoot.addView(editorFrame, editorParams);

        activeZoneEditorView = new ZoneEditorView(this);
        editorFrame.addView(activeZoneEditorView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        zonesActionBar = new LinearLayout(this);
        zonesActionBar.setOrientation(LinearLayout.HORIZONTAL);
        zonesActionBar.setGravity(Gravity.END);
        zonesActionBar.setPadding(0, dp(10), 0, 0);
        zonesActionBar.setVisibility(View.GONE);
        zonesRenameButton = iconActionButton(R.drawable.ic_edit_24, "Zone umbenennen", v -> renameSelectedZone());
        zonesDeleteButton = iconActionButton(R.drawable.ic_delete_24, "Zone löschen", v -> deleteSelectedZone());
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        actionParams.rightMargin = dp(10);
        zonesActionBar.addView(zonesRenameButton, actionParams);
        zonesActionBar.addView(zonesDeleteButton, new LinearLayout.LayoutParams(dp(42), dp(42)));
        contentRoot.addView(zonesActionBar);

        updateZoneActionButtons();
        loadZonesEditor(false);
    }

    private void updateZoneEditorSummary(String message) {
        if (activeZoneEditorView == null) {
            return;
        }
        String summary = activeZoneEditorView.summaryText();
        if (zonesDetailsText != null) {
            zonesDetailsText.setText(summary);
        }
        if (zonesStatusText != null && message != null && !message.trim().isEmpty()) {
            zonesStatusText.setText(message);
            zonesStatusText.setTextColor(MUTED);
        }
    }

    private void updateZoneActionButtons() {
        if (activeZoneEditorView == null) {
            return;
        }
        ZoneDraft selected = activeZoneEditorView.getSelectedZone();
        boolean enabled = selected != null;
        if (zonesActionBar != null) {
            zonesActionBar.setVisibility(enabled ? View.VISIBLE : View.GONE);
        }
        if (zonesRenameButton != null) {
            zonesRenameButton.setEnabled(enabled);
        }
        if (zonesDeleteButton != null) {
            zonesDeleteButton.setEnabled(enabled);
        }
    }

    private void renameSelectedZone() {
        if (activeZoneEditorView == null) {
            return;
        }
        ZoneDraft selected = activeZoneEditorView.getSelectedZone();
        if (selected == null) {
            return;
        }

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(selected.name);
        input.setSelection(input.getText().length());
        input.setTextColor(TEXT);
        input.setHintTextColor(Color.rgb(165, 165, 170));
        input.setPadding(dp(18), dp(14), dp(18), dp(14));
        input.setBackground(rounded(Color.rgb(32, 32, 36), dp(DEFAULT_RADIUS_DP)));

        new AlertDialog.Builder(this)
                .setTitle("Zone umbenennen")
                .setView(input)
                .setPositiveButton("Speichern", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this,
                                "Der Zonenname darf nicht leer sein.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    selected.name = name;
                    activeZoneEditorView.invalidate();
                    updateZoneEditorSummary("Zone umbenannt.");
                    updateZoneActionButtons();
                    saveZonesFromEditor();
                })
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    private void deleteSelectedZone() {
        if (activeZoneEditorView == null) {
            return;
        }
        ZoneDraft selected = activeZoneEditorView.getSelectedZone();
        if (selected == null) {
            return;
        }
        activeZoneEditorView.deleteSelectedZone();
        updateZoneEditorSummary("Zone gelöscht.");
        updateZoneActionButtons();
        saveZonesFromEditor();
    }

    private ImageButton iconActionButton(int iconRes, String contentDescription, View.OnClickListener listener) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(iconRes);
        button.setColorFilter(Color.WHITE);
        button.setBackground(rounded(Color.rgb(39, 39, 41), dp(DEFAULT_RADIUS_DP)));
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setAdjustViewBounds(true);
        button.setContentDescription(contentDescription);
        button.setOnClickListener(listener);
        applyTapAnimation(button);
        button.setPadding(dp(8), dp(8), dp(8), dp(8));
        return button;
    }

    private void loadZonesEditor(boolean keepCurrentZones) {
        ioExecutor.execute(() -> {
            JSONObject zonesJson = null;
            Bitmap bitmap = null;
            boolean zonesOk = false;
            boolean imageOk = false;
            String zonesMessage = "";
            String imageMessage = "";
            try {
                zonesJson = getJson(monitorUrl("/api/zones"));
                zonesOk = true;
                zonesMessage = "Zonen geladen";
            } catch (Exception ex) {
                zonesMessage = "Zonen nicht geladen: " + cleanError(ex);
            }

            try {
                byte[] bytes;
                try {
                    bytes = getBytes(monitorUrl("/api/zones/frame.jpg"), 10000);
                } catch (Exception frameError) {
                    bytes = getBytes(httpUrl("/capture"), 10000);
                }
                bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bitmap == null) {
                    throw new IllegalStateException("JPEG konnte nicht dekodiert werden.");
                }
                imageOk = true;
                imageMessage = "Bild geladen: " + bitmap.getWidth() + " x " + bitmap.getHeight();
            } catch (Exception ex) {
                imageMessage = "Bild nicht geladen: " + cleanError(ex);
            }

            StringBuilder statusBuilder = new StringBuilder();
            if (!zonesMessage.isEmpty()) {
                statusBuilder.append(zonesMessage);
            }
            if (!imageMessage.isEmpty()) {
                if (statusBuilder.length() > 0) {
                    statusBuilder.append(" | ");
                }
                statusBuilder.append(imageMessage);
            }

            JSONObject finalZonesJson = zonesJson;
            Bitmap finalBitmap = bitmap;
            String finalStatus = statusBuilder.toString();
            boolean finalZonesOk = zonesOk;
            boolean finalImageOk = imageOk;
            mainHandler.post(() -> {
                if (!showingZonesEditor || activeZoneEditorView == null) {
                    return;
                }
                if (finalImageOk && finalBitmap != null) {
                    activeZoneEditorView.setImage(finalBitmap);
                }
                if (finalZonesOk && finalZonesJson != null
                        && (!keepCurrentZones || activeZoneEditorView.isEmpty())) {
                    activeZoneEditorView.setZonesFromJson(finalZonesJson);
                }
                if (finalImageOk || finalZonesOk) {
                    zonesStatusText.setText(finalStatus);
                    zonesStatusText.setTextColor(finalImageOk && finalZonesOk ? GREEN : AMBER);
                    updateZoneEditorSummary(null);
                } else {
                    zonesStatusText.setText(finalStatus);
                    zonesStatusText.setTextColor(ERROR);
                }
            });
        });
    }

    private void saveZonesFromEditor() {
        if (activeZoneEditorView == null) {
            return;
        }
        if (zonesSaveInFlight) {
            zonesSaveQueued = true;
            return;
        }
        zonesSaveInFlight = true;
        updateZoneEditorSummary(null);
        JSONObject payload = activeZoneEditorView.toPayload();
        ioExecutor.execute(() -> {
            try {
                getString(
                        monitorUrl("/api/zones"),
                        5000,
                        "PUT",
                        payload.toString(),
                        "application/json; charset=utf-8");
                mainHandler.post(() -> {
                    if (zonesStatusText != null) {
                        zonesStatusText.setText("No-Go-Zonen gespeichert.");
                        zonesStatusText.setTextColor(GREEN);
                    }
                    if (zonesDetailsText != null && activeZoneEditorView != null) {
                        zonesDetailsText.setText(activeZoneEditorView.summaryText());
                    }
                });
            } catch (Exception ex) {
                mainHandler.post(() -> {
                    if (zonesStatusText != null) {
                        zonesStatusText.setText("Speichern fehlgeschlagen: " + cleanError(ex));
                        zonesStatusText.setTextColor(ERROR);
                    }
                });
            } finally {
                zonesSaveInFlight = false;
                if (zonesSaveQueued) {
                    zonesSaveQueued = false;
                    mainHandler.post(this::saveZonesFromEditor);
                }
            }
        });
    }

    private void loadAlarmEvents() {
        ioExecutor.execute(() -> {
            try {
                JSONObject json = getJson(monitorUrl("/api/alarm/events"));
                JSONObject filtered = applyHiddenHistoryFilter(json);
                saveAlarmEventsCache(filtered);
                JSONArray events = filtered.optJSONArray("events");
                mainHandler.post(() -> renderAlarmEvents(filtered));
                prefetchAlarmAssets(events);
            } catch (Exception ex) {
                JSONObject cached = loadAlarmEventsCache();
                if (cached != null) {
                    mainHandler.post(() -> {
                        renderAlarmEvents(applyHiddenHistoryFilter(cached));
                        setConnection("Monitor offline. Zeige Cache.", AMBER);
                    });
                } else {
                    mainHandler.post(() -> renderHistoryError("Monitor-API nicht erreichbar"));
                }
            }
        });
    }

    private void renderAlarmEvents(JSONObject payload) {
        JSONObject visiblePayload = applyHiddenHistoryFilter(payload);
        currentHistoryPayload = visiblePayload;
        clearHistoryRows();
        updateHistorySelectionUi();
        JSONArray events = visiblePayload == null ? null : visiblePayload.optJSONArray("events");
        if (events == null || events.length() == 0) {
            TextView empty = text("Noch keine Events gespeichert.", 14,
                    Color.rgb(185, 190, 195), Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(40), 0, 0);
            empty.setTag("history-row");
            contentRoot.addView(empty);
            return;
        }

        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.optJSONObject(i);
            if (event != null) {
                contentRoot.addView(historyRow(event));
            }
        }
    }

    private void renderHistoryError(String message) {
        currentHistoryPayload = null;
        clearHistoryRows();
        updateHistorySelectionUi();
        TextView error = text(message, 14, ERROR, Typeface.BOLD);
        error.setGravity(Gravity.CENTER);
        error.setPadding(0, dp(40), 0, 0);
        error.setTag("history-row");
        contentRoot.addView(error);
    }

    private void clearHistoryRows() {
        for (int i = contentRoot.getChildCount() - 1; i >= 0; i--) {
            View child = contentRoot.getChildAt(i);
            Object tag = child.getTag();
            if ("history-row".equals(tag) || "history-loading".equals(tag)) {
                contentRoot.removeViewAt(i);
            }
        }
    }

    private LinearLayout historyRow(JSONObject event) {
        LinearLayout row = new LinearLayout(this);
        row.setTag("history-row");
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));
        String clipUrl = event.optString("clip_url", "");
        int eventId = event.optInt("id", Math.abs(clipUrl.hashCode()));
        boolean selected = selectedHistoryEventIds.contains(eventId);
        row.setBackgroundColor(selected ? Color.rgb(32, 41, 58) : Color.rgb(24, 28, 31));
        row.setClickable(true);
        row.setLongClickable(true);
        row.setOnLongClickListener(v -> {
            enterHistorySelectionMode();
            toggleHistorySelection(eventId);
            return true;
        });
        row.setOnClickListener(v -> {
            if (historySelectionMode) {
                toggleHistorySelection(eventId);
            } else if (!clipUrl.isEmpty()) {
                openClip(eventId, clipUrl);
            }
        });
        applyTapAnimation(row);

        View selectionStrip = new View(this);
        selectionStrip.setBackgroundColor(selected ? BLUE : Color.TRANSPARENT);
        row.addView(selectionStrip, new LinearLayout.LayoutParams(dp(4), dp(84)));

        FrameLayout thumb = historyThumbnail(event);
        LinearLayout.LayoutParams thumbParams = new LinearLayout.LayoutParams(dp(118), dp(84));
        thumbParams.leftMargin = dp(10);
        row.addView(thumb, thumbParams);

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textParams.leftMargin = dp(14);
        row.addView(textColumn, textParams);

        String category = event.optString("category", "person");
        boolean person = "person".equals(category);
        String zone = event.optString("zone_name", person ? "Person" : "Bewegung");
        textColumn.addView(text(zone.isEmpty() ? (person ? "Person" : "Bewegung") : zone,
                18, Color.WHITE, Typeface.BOLD));

        int count = event.optJSONArray("tracker_ids") == null
                ? 0
                : event.optJSONArray("tracker_ids").length();
        String subtitle = person
                ? (count > 1 ? count + " Personen" : "Person erkannt")
                : "Bewegung erkannt";
        textColumn.addView(text(subtitle, 14, Color.rgb(192, 197, 202), Typeface.NORMAL));

        TextView time = text(formatEventTime(event.optString("timestamp", "")),
                13, Color.rgb(192, 197, 202), Typeface.NORMAL);
        row.addView(time);
        return row;
    }

    private void openClip(int eventId, String clipUrl) {
        File clip = clipFile(eventId, clipUrl);
        if (isPlayableClip(clip)) {
            showClipPlayer(clip);
            return;
        } else if (clip.exists()) {
            clip.delete();
        }

        Toast.makeText(this, "Clip wird geladen...", Toast.LENGTH_SHORT).show();
        ioExecutor.execute(() -> {
            try {
                if (!isPlayableClip(clip)) {
                    byte[] bytes = getBytes(monitorUrl(clipUrl), 30000);
                    if (bytes.length <= 64_000) {
                        throw new IllegalStateException("Clip response too small: " + bytes.length);
                    }
                    writeBytes(clip, bytes);
                    if (!isPlayableClip(clip)) {
                        clip.delete();
                        throw new IllegalStateException("Downloaded clip is not playable");
                    }
                }
                mainHandler.post(() -> showClipPlayer(clip));
            } catch (Exception ex) {
                mainHandler.post(() ->
                        Toast.makeText(this, "Clip konnte nicht geladen werden.", Toast.LENGTH_LONG).show());
            }
        });
    }

    private void prefetchAlarmAssets(JSONArray events) {
        if (events == null || events.length() == 0) {
            return;
        }
        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.optJSONObject(i);
            if (event == null) {
                continue;
            }
            int eventId = event.optInt("id", i + 1);
            String imageUrl = event.optString("image_url", "");
            String clipUrl = event.optString("clip_url", "");
            if (!imageUrl.isEmpty()) {
                prefetchThumbnail(eventId, imageUrl);
            }
            if (!clipUrl.isEmpty()) {
                prefetchClip(eventId, clipUrl);
            }
        }
    }

    private void prefetchThumbnail(int eventId, String imageUrl) {
        ioExecutor.execute(() -> {
            try {
                File cached = thumbnailFile(eventId, imageUrl);
                if (cached.exists()) {
                    return;
                }
                byte[] bytes = getBytes(monitorUrl(imageUrl), 5000);
                writeBytes(cached, bytes);
            } catch (Exception ignored) {
                // Best effort.
            }
        });
    }

    private void prefetchClip(int eventId, String clipUrl) {
        ioExecutor.execute(() -> {
            try {
                File clip = clipFile(eventId, clipUrl);
                if (isPlayableClip(clip)) {
                    return;
                }
                byte[] bytes = getBytes(monitorUrl(clipUrl), 30000);
                if (bytes.length <= 64_000) {
                    return;
                }
                writeBytes(clip, bytes);
                if (!isPlayableClip(clip)) {
                    clip.delete();
                }
            } catch (Exception ignored) {
                // Best effort.
            }
        });
    }

    private File clipFile(int eventId, String clipUrl) {
        File dir = new File(getFilesDir(), "event_clips");
        return new File(dir, "event_v6_" + sha1(clipUrl) + ".mp4");
    }

    private File thumbnailFile(int eventId, String imageUrl) {
        return new File(new File(getFilesDir(), "event_thumbs"),
                "event_v3_" + eventId + "_" + sha1(imageUrl) + ".jpg");
    }

    private File historyCacheFile() {
        return new File(new File(getFilesDir(), HISTORY_CACHE_DIR), HISTORY_CACHE_FILE);
    }

    private void saveAlarmEventsCache(JSONObject json) {
        try {
            writeBytes(historyCacheFile(), json.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // Cache writes are best effort.
        }
    }

    private JSONObject loadAlarmEventsCache() {
        File file = historyCacheFile();
        if (!file.exists()) {
            return null;
        }
        try {
            return new JSONObject(new String(readBytes(file), StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            return null;
        }
    }

    private void loadHiddenHistoryIds() {
        hiddenHistoryEventIds.clear();
        String raw = prefs.getString(KEY_HIDDEN_HISTORY_IDS, "");
        if (raw == null || raw.trim().isEmpty()) {
            return;
        }
        String[] parts = raw.split(",");
        for (String part : parts) {
            String cleaned = part.trim();
            if (cleaned.isEmpty()) {
                continue;
            }
            try {
                hiddenHistoryEventIds.add(Integer.parseInt(cleaned));
            } catch (NumberFormatException ignored) {
                continue;
            }
        }
    }

    private void saveHiddenHistoryIds() {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Integer id : hiddenHistoryEventIds) {
            if (!first) {
                builder.append(',');
            }
            builder.append(id);
            first = false;
        }
        prefs.edit().putString(KEY_HIDDEN_HISTORY_IDS, builder.toString()).apply();
    }

    private JSONObject applyHiddenHistoryFilter(JSONObject payload) {
        if (payload == null || hiddenHistoryEventIds.isEmpty()) {
            return payload;
        }
        return filterAlarmEvents(payload, new ArrayList<>(hiddenHistoryEventIds));
    }

    private void clearOldClipCache() {
        File dir = new File(getFilesDir(), "event_clips");
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            String name = file.getName();
            if (name.startsWith("event_v2_")
                    || name.startsWith("event_v3_")
                    || name.startsWith("event_v4_")
                    || name.startsWith("event_v5_")
                    || !isPlayableClip(file)) {
                file.delete();
            }
        }
    }

    private byte[] readBytes(File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private void writeBytes(File file, byte[] bytes) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(bytes);
        }
    }

    private boolean isPlayableClip(File clip) {
        if (clip == null || !clip.exists() || clip.length() <= 64_000) {
            return false;
        }
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(clip.getAbsolutePath());
            String durationText = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION);
            long durationMs = Long.parseLong(durationText == null ? "0" : durationText);
            return durationMs >= 18_000;
        } catch (Exception ignored) {
            return false;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
    }

    private void showClipPlayer(File clip) {
        stopStream(false);
        showingClipPlayer = true;
        showingZonesEditor = false;
        activeClipVideo = null;
        activeClipSeekBar = null;
        activeClipTimeText = null;
        activeClipPlayButton = null;
        setContentRootMode(true);
        contentRoot.setPadding(dp(16), dp(20), dp(16), dp(112));
        contentRoot.removeAllViews();
        contentRoot.setBackgroundColor(Color.rgb(24, 28, 31));
        getWindow().setStatusBarColor(Color.rgb(24, 28, 31));
        getWindow().setNavigationBarColor(Color.rgb(24, 28, 31));

        FrameLayout player = new FrameLayout(this);
        player.setPadding(0, 0, 0, 0);
        contentRoot.addView(player, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams columnParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        player.addView(column, columnParams);

        FrameLayout videoFrame = new FrameLayout(this);
        videoFrame.setBackgroundColor(Color.BLACK);
        LinearLayout.LayoutParams videoFrameParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(220));
        column.addView(videoFrame, videoFrameParams);

        VideoView video = new VideoView(this);
        video.setBackgroundColor(Color.BLACK);
        videoFrame.addView(video, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        activeClipVideo = video;

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setBackgroundColor(Color.TRANSPARENT);
        controls.setPadding(0, dp(10), 0, 0);

        LinearLayout transport = new LinearLayout(this);
        transport.setOrientation(LinearLayout.HORIZONTAL);
        transport.setGravity(Gravity.CENTER_VERTICAL);
        transport.setBackgroundColor(Color.TRANSPARENT);

        ImageButton playPause = new ImageButton(this);
        playPause.setBackgroundColor(Color.TRANSPARENT);
        playPause.setImageResource(R.drawable.ic_pause_24);
        playPause.setColorFilter(Color.WHITE);
        playPause.setOnClickListener(v -> toggleClipPlayback());
        applyTapAnimation(playPause);
        activeClipPlayButton = playPause;
        transport.addView(playPause, new LinearLayout.LayoutParams(dp(52), dp(52)));

        SeekBar seek = new SeekBar(this);
        seek.setMax(1000);
        seek.setEnabled(true);
        seek.setBackgroundColor(Color.TRANSPARENT);
        activeClipSeekBar = seek;
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f);
        seekParams.leftMargin = dp(8);
        seekParams.rightMargin = 0;
        transport.addView(seek, seekParams);
        seek.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
        seek.setProgressTintList(ColorStateList.valueOf(Color.WHITE));
        seek.setProgressBackgroundTintList(ColorStateList.valueOf(Color.rgb(110, 110, 110)));
        controls.addView(transport, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView time = text("00:00 / 00:00", 12, Color.WHITE, Typeface.BOLD);
        activeClipTimeText = time;
        time.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        timeParams.leftMargin = dp(60);
        timeParams.rightMargin = 0;
        controls.addView(time, timeParams);
        column.addView(controls, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean user) {
                if (user && activeClipVideo != null) {
                    int duration = Math.max(1, activeClipVideo.getDuration());
                    int position = Math.round((progress / 1000f) * duration);
                    activeClipVideo.seekTo(position);
                    updateClipTimeLabel(position, duration);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                activeClipScrubbing = true;
                if (activeClipVideo != null) {
                    activeClipWasPlayingBeforeScrub = activeClipVideo.isPlaying();
                    if (activeClipWasPlayingBeforeScrub) {
                        activeClipVideo.pause();
                    }
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (activeClipVideo != null) {
                    int duration = Math.max(1, activeClipVideo.getDuration());
                    int position = Math.round((seekBar.getProgress() / 1000f) * duration);
                    activeClipVideo.seekTo(position);
                    if (activeClipWasPlayingBeforeScrub) {
                        activeClipVideo.start();
                    } else {
                        updateClipTimeLabel(position, duration);
                    }
                }
                activeClipScrubbing = false;
                activeClipWasPlayingBeforeScrub = false;
            }
        });

        video.setVideoURI(Uri.fromFile(clip));
        video.setOnPreparedListener(mp -> {
            video.setBackgroundColor(Color.TRANSPARENT);
            mp.setLooping(false);
            seek.setEnabled(true);
            int videoWidth = Math.max(1, mp.getVideoWidth());
            int videoHeight = Math.max(1, mp.getVideoHeight());
            videoFrame.post(() -> {
                int availableWidth = Math.max(dp(220), contentRoot.getWidth() - dp(32));
                int targetHeight = Math.round(availableWidth * (videoHeight / (float) videoWidth));
                int maxHeight = Math.max(dp(220), (int) (getResources().getDisplayMetrics().heightPixels * 0.46f));
                if (targetHeight > maxHeight) {
                    targetHeight = maxHeight;
                }
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) videoFrame.getLayoutParams();
                params.width = LinearLayout.LayoutParams.MATCH_PARENT;
                params.height = targetHeight;
                videoFrame.setLayoutParams(params);
            });
            video.start();
            playPause.setImageResource(R.drawable.ic_pause_24);
            updateClipTransportUi(video, seek, time, playPause);
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!showingClipPlayer || activeClipVideo != video) {
                        return;
                    }
                    if (!activeClipScrubbing) {
                        updateClipTransportUi(video, seek, time, playPause);
                    }
                    mainHandler.postDelayed(this, 250);
                }
            }, 250);
        });
        video.setOnCompletionListener(mp -> {
            playPause.setImageResource(R.drawable.ic_play_24);
            updateClipTransportUi(video, seek, time, playPause);
        });
        video.setOnErrorListener((mp, what, extra) -> {
            if (clip.exists()) {
                clip.delete();
            }
            Toast.makeText(this, "Clip kann nicht abgespielt werden.", Toast.LENGTH_LONG).show();
            showHistoryScreen();
            return true;
        });
    }

    private void toggleClipPlayback() {
        if (activeClipVideo == null || activeClipPlayButton == null) {
            return;
        }
        if (activeClipVideo.isPlaying()) {
            activeClipVideo.pause();
        } else {
            activeClipVideo.start();
        }
        if (activeClipSeekBar != null && activeClipTimeText != null) {
            updateClipTransportUi(activeClipVideo, activeClipSeekBar, activeClipTimeText, activeClipPlayButton);
        }
    }

    private void updateClipTransportUi(
            VideoView video, SeekBar seek, TextView time, ImageButton playPause) {
        if (video == null || seek == null || time == null || playPause == null) {
            return;
        }
        if (activeClipScrubbing) {
            return;
        }
        int duration = Math.max(0, video.getDuration());
        int position = Math.max(0, video.getCurrentPosition());
        if (duration > 0) {
            int progress = Math.round((position / (float) duration) * 1000f);
            seek.setProgress(Math.max(0, Math.min(1000, progress)));
        }
        time.setText(formatClipTime(position) + " / " + formatClipTime(duration));
        playPause.setImageResource(video.isPlaying()
                ? R.drawable.ic_pause_24
                : R.drawable.ic_play_24);
    }

    private void updateClipTimeLabel(int position, int duration) {
        if (activeClipTimeText == null) {
            return;
        }
        activeClipTimeText.setText(formatClipTime(position) + " / " + formatClipTime(duration));
    }

    private String formatClipTime(int millis) {
        int totalSeconds = Math.max(0, millis / 1000);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format(Locale.GERMANY, "%02d:%02d", minutes, seconds);
    }

    private FrameLayout historyThumbnail(JSONObject event) {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(rounded(Color.rgb(44, 49, 54), dp(TILE_RADIUS_DP)));
        frame.setClipToOutline(true);
        frame.setOutlineProvider(new RoundedOutline(dp(TILE_RADIUS_DP)));
        frame.setLayoutParams(new LinearLayout.LayoutParams(dp(118), dp(84)));

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        frame.addView(image, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        boolean hasClip = event.optString("clip_url", "").length() > 0;
        if (hasClip) {
            FrameLayout play = circleIcon(R.drawable.ic_play_24, Color.rgb(50, 90, 105),
                    Color.WHITE, dp(34), dp(20));
            FrameLayout.LayoutParams playParams = new FrameLayout.LayoutParams(
                    dp(34), dp(34), Gravity.CENTER);
            frame.addView(play, playParams);
        }

        TextView duration = text(hasClip && event.optInt("duration_seconds", 0) > 0 ? "00:20" : "",
                12, Color.WHITE, Typeface.BOLD);
        duration.setShadowLayer(3f, 0, 1f, Color.argb(180, 0, 0, 0));
        FrameLayout.LayoutParams durationParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.RIGHT);
        durationParams.rightMargin = dp(6);
        durationParams.bottomMargin = dp(5);
        frame.addView(duration, durationParams);

        int eventId = event.optInt("id",
                Math.abs((event.optString("image_url", "") + event.optString("clip_url", "")).hashCode()));
        String imageUrl = event.optString("image_url", "");
        if (!imageUrl.isEmpty()) {
            loadHistoryThumbnail(image, eventId, imageUrl);
        }
        return frame;
    }

    private void loadHistoryThumbnail(ImageView image, int eventId, String imageUrl) {
        ioExecutor.execute(() -> {
            try {
                File cached = thumbnailFile(eventId, imageUrl);
                byte[] bytes;
                if (cached.exists()) {
                    bytes = readBytes(cached);
                } else {
                    bytes = getBytes(monitorUrl(imageUrl), 5000);
                    writeBytes(cached, bytes);
                }
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bitmap != null) {
                    mainHandler.post(() -> image.setImageBitmap(bitmap));
                }
            } catch (Exception ignored) {
                // Missing thumbnails are represented by the neutral placeholder.
            }
        });
    }

    private void enterHistorySelectionMode() {
        if (!historySelectionMode) {
            historySelectionMode = true;
            updateHistorySelectionUi();
        }
    }

    private void exitHistorySelectionMode() {
        historySelectionMode = false;
        selectedHistoryEventIds.clear();
        updateHistorySelectionUi();
        renderCurrentHistory();
    }

    private void toggleHistorySelection(int eventId) {
        if (selectedHistoryEventIds.contains(eventId)) {
            selectedHistoryEventIds.remove(eventId);
        } else {
            selectedHistoryEventIds.add(eventId);
        }
        if (selectedHistoryEventIds.isEmpty()) {
            historySelectionMode = false;
        } else {
            historySelectionMode = true;
        }
        updateHistorySelectionUi();
        renderCurrentHistory();
    }

    private void updateHistorySelectionUi() {
        if (historyActionBar == null || historySelectionText == null || historyDeleteButton == null
                || historyCancelButton == null) {
            return;
        }
        boolean visible = historySelectionMode || !selectedHistoryEventIds.isEmpty();
        historyActionBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        historySelectionText.setText(String.format(Locale.GERMANY, "%d ausgewählt",
                selectedHistoryEventIds.size()));
        historyDeleteButton.setEnabled(!selectedHistoryEventIds.isEmpty());
        historyDeleteButton.setAlpha(selectedHistoryEventIds.isEmpty() ? 0.45f : 1f);
        historyCancelButton.setText(historySelectionMode ? "Abbrechen" : "Fertig");
    }

    private void renderCurrentHistory() {
        if (currentHistoryPayload != null) {
            renderAlarmEvents(currentHistoryPayload);
        }
    }

    private void deleteSelectedHistoryEvents() {
        if (selectedHistoryEventIds.isEmpty()) {
            return;
        }
        List<Integer> ids = new ArrayList<>(selectedHistoryEventIds);
        ioExecutor.execute(() -> {
            JSONObject updatedPayload = null;
            boolean remoteSucceeded = false;
            try {
                updatedPayload = deleteAlarmEventsRemote(ids);
                remoteSucceeded = true;
            } catch (Exception ignored) {
                updatedPayload = null;
            }
            if (updatedPayload == null) {
                JSONObject cached = loadAlarmEventsCache();
                updatedPayload = filterAlarmEvents(cached, ids);
            }
            if (updatedPayload == null) {
                mainHandler.post(() -> setConnection("Löschen fehlgeschlagen.", ERROR));
                return;
            }
            if (remoteSucceeded) {
                hiddenHistoryEventIds.removeAll(ids);
            } else {
                hiddenHistoryEventIds.addAll(ids);
            }
            saveHiddenHistoryIds();
            saveAlarmEventsCache(updatedPayload);
            JSONObject finalPayload = updatedPayload;
            final boolean remoteDeleted = remoteSucceeded;
            mainHandler.post(() -> {
                historySelectionMode = false;
                selectedHistoryEventIds.clear();
                renderAlarmEvents(finalPayload);
                setConnection(remoteDeleted ? "Events gelöscht." : "Lokale Historie gelöscht.", remoteDeleted ? GREEN : AMBER);
            });
        });
    }

    private JSONObject deleteAlarmEventsRemote(List<Integer> ids) throws Exception {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(ids.get(i));
        }
        return new JSONObject(getString(monitorUrl("/api/alarm/events?ids=" + builder), 5000, "DELETE"));
    }

    private JSONObject filterAlarmEvents(JSONObject payload, List<Integer> idsToRemove) {
        if (payload == null) {
            return null;
        }
        JSONArray events = payload.optJSONArray("events");
        JSONArray filtered = new JSONArray();
        Set<Integer> removalSet = new LinkedHashSet<>(idsToRemove);
        if (events != null) {
            for (int i = 0; i < events.length(); i++) {
                JSONObject event = events.optJSONObject(i);
                if (event == null) {
                    continue;
                }
                if (!removalSet.contains(event.optInt("id", -1))) {
                    filtered.put(event);
                }
            }
        }
        JSONObject copy = new JSONObject();
        try {
            copy = new JSONObject(payload.toString());
        } catch (Exception ignored) {
            // Fall through with a fresh object.
        }
        try {
            copy.put("events", filtered);
        } catch (Exception ignored) {
            return null;
        }
        return copy;
    }

    private String formatEventTime(String timestamp) {
        if (timestamp == null || timestamp.length() < 16) {
            return "";
        }
        return timestamp.substring(11, 16);
    }

    private LinearLayout header() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(20));

        screenTitle = text("Zu Hause", 25, TEXT, Typeface.BOLD);
        header.addView(screenTitle, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        FrameLayout add = circleIcon(R.drawable.ic_settings_24, Color.WHITE, BLACK, dp(42), dp(23));
        add.setOnClickListener(v -> showSettingsScreen());
        applyTapAnimation(add);
        header.addView(add);
        return header;
    }

    private LinearLayout settingsPanel() {
        LinearLayout panel = card(TILE, dp(TILE_RADIUS_DP));
        panel.setPadding(dp(16), dp(16), dp(16), dp(16));

        hostInput = input(DEFAULT_ESP_HOST);
        httpPortInput = input("80");
        streamPortInput = input("81");
        monitorHostInput = input("10.0.2.2");
        monitorPortInput = input("8765");
        httpPortInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        streamPortInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        monitorPortInput.setInputType(InputType.TYPE_CLASS_NUMBER);

        panel.addView(text("Verbindung", 16, TEXT, Typeface.BOLD));
        connectionProfileText = valueText("");
        panel.addView(connectionProfileText);

        LinearLayout profileButtons = new LinearLayout(this);
        profileButtons.setOrientation(LinearLayout.HORIZONTAL);
        profileButtons.setPadding(0, dp(8), 0, dp(4));
        profileButtons.addView(button("Schul-WLAN", BLUE, v -> applyConnectionProfile(PROFILE_NORMAL, true)));
        profileButtons.addView(button("Android-Hotspot", AMBER, v -> applyConnectionProfile(PROFILE_ANDROID_HOTSPOT, true)));
        panel.addView(profileButtons);

        panel.addView(label("ESP32-IP oder Hostname"));
        panel.addView(hostInput);
        Button scanButton = button("ESP im Hotspot suchen", BLACK, v -> scanForEsp());
        LinearLayout scanRow = new LinearLayout(this);
        scanRow.setOrientation(LinearLayout.HORIZONTAL);
        scanRow.setPadding(0, dp(8), 0, 0);
        scanRow.addView(scanButton);
        panel.addView(scanRow);

        panel.addView(label("HTTP-Port"));
        panel.addView(httpPortInput);
        panel.addView(label("Stream-Port"));
        panel.addView(streamPortInput);
        panel.addView(label("Laptop-Monitor-Host"));
        panel.addView(monitorHostInput);
        panel.addView(label("Monitor-API-Port"));
        panel.addView(monitorPortInput);

        TextView presentationTitle = text("Praesentation", 16, TEXT, Typeface.BOLD);
        presentationTitle.setPadding(0, dp(18), 0, 0);
        panel.addView(presentationTitle);
        panel.addView(phoneStreamRow());

        TextView behaviorTitle = text("Alarmverhalten", 16, TEXT, Typeface.BOLD);
        behaviorTitle.setPadding(0, dp(18), 0, 0);
        panel.addView(behaviorTitle);
        panel.addView(settingSwitchRow("Buzzer bei Alarm", true));
        panel.addView(settingSwitchRow("Alarm-App aktiv", false));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, dp(12), 0, 0);
        buttons.addView(button("Speichern", BLACK, v -> saveSettings()));
        buttons.addView(button("Aktualisieren", RED, v -> refreshAll()));
        panel.addView(buttons);

        connectionText = valueText("Noch nicht verbunden");
        connectionText.setPadding(0, dp(10), 0, 0);
        panel.addView(connectionText);
        return panel;
    }

    private LinearLayout phoneStreamRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, 0);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text("Stream auf diesem Handy", 14, TEXT, Typeface.BOLD));
        labels.addView(text("Aus = Handy steuert nur, Laptop zeigt Video", 12, MUTED, Typeface.NORMAL));
        row.addView(labels, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        phoneStreamSwitch = new Switch(this);
        phoneStreamSwitch.setShowText(false);
        phoneStreamSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressSwitchEvents) {
                return;
            }
            phoneStreamEnabled = isChecked;
            prefs.edit().putBoolean(KEY_PHONE_STREAM_ENABLED, isChecked).apply();
            if (!isChecked) {
                stopStream();
                setLiveState(false, "Handy steuert nur. Stream am Laptop oeffnen.");
            } else {
                captureFrame();
            }
        });
        applyTapAnimation(phoneStreamSwitch);
        row.addView(phoneStreamSwitch);
        return row;
    }

    private LinearLayout settingSwitchRow(String title, boolean buzzer) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, 0);

        TextView text = text(title, 14, TEXT, Typeface.BOLD);
        row.addView(text, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Switch toggle = new Switch(this);
        toggle.setShowText(false);
        applyTapAnimation(toggle);
        if (buzzer) {
            buzzerSwitch = toggle;
            buzzerSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (!suppressSwitchEvents) {
                    setBuzzer(isChecked);
                }
            });
        } else {
            monitorSwitch = toggle;
            monitorSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (!suppressSwitchEvents) {
                    setSystemActive(isChecked);
                }
            });
        }
        row.addView(toggle);
        return row;
    }

    private LinearLayout modeRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 0, 0, dp(28));
        row.addView(modeItem(MODE_DISABLED, "Deaktivieren", R.drawable.ic_security_24,
                () -> setMode(MODE_DISABLED, "Deaktiviert", false)));
        row.addView(modeItem(MODE_HOME, "Zu Hause", R.drawable.ic_home_24,
                () -> setMode(MODE_HOME, "Zu Hause", true)));
        row.addView(modeItem(MODE_AWAY, "Unterwegs", R.drawable.ic_walk_24,
                () -> setMode(MODE_AWAY, "Unterwegs", true)));
        updateModeSelection();
        return row;
    }

    private LinearLayout modeItem(int mode, String label, int iconRes, Runnable action) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setOnClickListener(v -> action.run());
        item.setClickable(true);
        applyTapAnimation(item);
        item.setPadding(0, 0, 0, 0);
        item.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        FrameLayout circle = new FrameLayout(this);
        circle.setLayoutParams(new LinearLayout.LayoutParams(dp(70), dp(70)));
        ImageView icon = icon(iconRes, Color.WHITE, dp(34));
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(34), dp(34), Gravity.CENTER);
        circle.addView(icon, iconParams);
        item.addView(circle);

        TextView text = text(label, 12, BLACK, Typeface.BOLD);
        text.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        textParams.topMargin = dp(8);
        item.addView(text, textParams);

        modeCircles[mode] = circle;
        modeIcons[mode] = icon;
        modeLabels[mode] = text;
        return item;
    }

    private void updateModeSelection() {
        for (int mode = 0; mode < modeCircles.length; mode++) {
            boolean active = selectedMode == mode;
            int activeColor = mode == MODE_AWAY ? RED : BLUE;
            modeCircles[mode].setBackground(oval(active ? activeColor : Color.rgb(236, 236, 238)));
            modeIcons[mode].setColorFilter(active ? Color.WHITE : BLACK);
            modeLabels[mode].setTextColor(active ? BLACK : BLACK);
            modeCircles[mode].setAlpha(active ? 1f : 0.4f);
            modeIcons[mode].setAlpha(active ? 1f : 0.4f);
            modeLabels[mode].setAlpha(active ? 1f : 0.4f);
        }
    }

    private FrameLayout liveCard() {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(rounded(Color.rgb(226, 226, 228), dp(LIVE_RADIUS_DP)));
        frame.setClipToOutline(true);
        frame.setOutlineProvider(new RoundedOutline(dp(LIVE_RADIUS_DP)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(198));
        params.bottomMargin = dp(14);
        frame.setLayoutParams(params);
        frame.setOnClickListener(v -> {
            if (streamRunning) {
                stopStream();
            } else {
                startStream();
            }
        });
        applyTapAnimation(frame);

        cameraImage = new ImageView(this);
        cameraImage.setBackgroundColor(Color.rgb(32, 32, 34));
        cameraImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        frame.addView(cameraImage, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout badge = new LinearLayout(this);
        badge.setOrientation(LinearLayout.HORIZONTAL);
        badge.setGravity(Gravity.CENTER_VERTICAL);
        liveDot = new View(this);
        liveDot.setBackground(oval(RED));
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(6), dp(6));
        badge.addView(liveDot, dotParams);
        liveBadgeText = text("Offline", 14, Color.WHITE, Typeface.NORMAL);
        LinearLayout.LayoutParams liveParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        liveParams.leftMargin = dp(8);
        badge.addView(liveBadgeText, liveParams);
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.LEFT);
        badgeParams.leftMargin = dp(16);
        badgeParams.topMargin = dp(18);
        frame.addView(badge, badgeParams);

        streamText = text("", 12, Color.WHITE, Typeface.NORMAL);
        streamText.setShadowLayer(3f, 0, 1f, Color.argb(180, 0, 0, 0));
        streamText.setVisibility(View.GONE);
        FrameLayout.LayoutParams streamParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.LEFT);
        streamParams.leftMargin = dp(16);
        streamParams.bottomMargin = dp(14);
        frame.addView(streamText, streamParams);
        return frame;
    }

    private LinearLayout dashboardTiles() {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);

        LinearLayout firstRow = tileRow();
        firstRow.addView(statusTile("ESP32", R.drawable.ic_chip_24, true, null));
        firstRow.addView(statusTile("Laptop", R.drawable.ic_laptop_24, false, v -> toggleLaptopMonitor()));
        grid.addView(firstRow);

        LinearLayout secondRow = tileRow();
        secondRow.addView(infoTile("Radar", R.drawable.ic_air_24, "unbekannt", 0));
        secondRow.addView(infoTile("Licht", R.drawable.ic_light_24, "unbekannt", 1));
        grid.addView(secondRow);

        grid.addView(buzzerTestTile());
        return grid;
    }

    private LinearLayout tileRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        return row;
    }

    private LinearLayout statusTile(String title, int iconRes, boolean esp, View.OnClickListener onClick) {
        LinearLayout tile = dashboardTileBase();
        tile.setLayoutParams(tileParams(esp));
        if (onClick != null) {
            tile.setClickable(true);
            tile.setOnClickListener(onClick);
            applyTapAnimation(tile);
        }
        tile.addView(icon(iconRes, BLACK, dp(30)));
        tile.addView(tileTitle(title));
        TextView value = tileValue("OFFLINE", ERROR);
        tile.addView(value);
        if (esp) {
            espStatusTileText = value;
        } else {
            laptopStatusTileText = value;
        }
        return tile;
    }

    private LinearLayout infoTile(String title, int iconRes, String initialValue, int type) {
        LinearLayout tile = dashboardTileBase();
        tile.setLayoutParams(tileParams(type == 0));
        tile.addView(icon(iconRes, BLACK, dp(30)));
        tile.addView(tileTitle(title));
        TextView value = tileValue(initialValue, MUTED);
        tile.addView(value);
        if (type == 0) {
            radarTileText = value;
        } else {
            lightTileText = value;
        }
        return tile;
    }

    private LinearLayout buzzerTestTile() {
        LinearLayout tile = dashboardTileBase();
        tile.setOrientation(LinearLayout.HORIZONTAL);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setClickable(true);
        tile.setOnClickListener(v -> testBuzzer());
        applyTapAnimation(tile);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(94));
        params.bottomMargin = dp(16);
        tile.setLayoutParams(params);

        tile.addView(icon(R.drawable.ic_bell_24, BLACK, dp(32)));
        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textParams.leftMargin = dp(14);
        tile.addView(textColumn, textParams);
        textColumn.addView(text("Buzzer-Test", 16, TEXT, Typeface.BOLD));
        buzzerTestTileText = text("Tippen zum Testen", 12, MUTED, Typeface.BOLD);
        textColumn.addView(buzzerTestTileText);
        return tile;
    }

    private LinearLayout dashboardTileBase() {
        LinearLayout tile = card(TILE, dp(TILE_RADIUS_DP));
        tile.setPadding(dp(16), dp(18), dp(16), dp(16));
        tile.setGravity(Gravity.LEFT);
        return tile;
    }

    private LinearLayout.LayoutParams tileParams(boolean left) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, dp(142), 1f);
        params.leftMargin = left ? 0 : dp(8);
        params.rightMargin = left ? dp(8) : 0;
        params.bottomMargin = dp(16);
        return params;
    }

    private TextView tileTitle(String title) {
        TextView text = text(title, 15, TEXT, Typeface.BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(10);
        text.setLayoutParams(params);
        return text;
    }

    private TextView tileValue(String value, int color) {
        TextView text = text(value, 12, color, Typeface.BOLD);
        text.setSingleLine(false);
        text.setMaxLines(3);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(3);
        text.setLayoutParams(params);
        return text;
    }

    private LinearLayout quickCard(String title, int iconRes, boolean buzzer) {
        LinearLayout card = card(TILE, dp(TILE_RADIUS_DP));
        card.setPadding(dp(16), dp(16), dp(16), dp(12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, dp(98), 1f);
        params.rightMargin = buzzer ? dp(8) : 0;
        params.leftMargin = buzzer ? 0 : dp(8);
        params.bottomMargin = dp(16);
        card.setLayoutParams(params);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = icon(iconRes, BLACK, dp(30));
        top.addView(icon);
        Space(top, 0, 1f);
        Switch toggle = new Switch(this);
        toggle.setShowText(false);
        toggle.setScaleX(0.78f);
        toggle.setScaleY(0.78f);
        applyTapAnimation(toggle);
        top.addView(toggle);
        card.addView(top);

        TextView name = text(title, 16, TEXT, Typeface.BOLD);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        nameParams.topMargin = dp(14);
        card.addView(name, nameParams);

        if (buzzer) {
            buzzerSwitch = toggle;
            buzzerSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (!suppressSwitchEvents) {
                    setBuzzer(isChecked);
                }
            });
        } else {
            monitorSwitch = toggle;
            monitorSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (!suppressSwitchEvents) {
                    setSystemActive(isChecked);
                }
            });
        }
        return card;
    }

    private LinearLayout dashboard() {
        LinearLayout dash = new LinearLayout(this);
        dash.setOrientation(LinearLayout.VERTICAL);
        dash.setPadding(dp(14), 0, 0, 0);

        cameraText = dashboardRow(dash, R.drawable.ic_camera_24, "Kamera: unbekannt");
        radarText = dashboardRow(dash, R.drawable.ic_air_24, "Radar: unbekannt");
        lightText = dashboardRow(dash, R.drawable.ic_light_24, "Licht: unbekannt");
        alarmText = dashboardRow(dash, R.drawable.ic_bell_24, "Buzzer: unbekannt");
        monitorText = dashboardRow(dash, R.drawable.ic_security_24, "Monitor: unbekannt");
        return dash;
    }

    private TextView dashboardRow(LinearLayout parent, int iconRes, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(3), 0, dp(3));
        row.addView(icon(iconRes, MUTED, dp(18)));
        TextView text = text(value, 12, MUTED, Typeface.NORMAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textParams.leftMargin = dp(8);
        row.addView(text, textParams);
        parent.addView(row);
        return text;
    }

    private LinearLayout bottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(8), dp(6), dp(8), dp(6));
        nav.setBackground(rounded(BLACK, dp(DEFAULT_RADIUS_DP)));
        nav.addView(navItem(0, "Dashboard", R.drawable.ic_camera_24, v -> {
            selectNav(0);
            showHomeScreen();
        }));
        nav.addView(navItem(1, "Events", R.drawable.ic_bell_24, v -> {
            selectNav(1);
            showHistoryScreen();
        }));
        nav.addView(navItem(2, "Zonen", R.drawable.ic_person_24, v -> {
            selectNav(2);
            showZonesScreen();
        }));
        return nav;
    }

    private View navItem(int index, String label, int iconRes, View.OnClickListener listener) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER);
        item.setClickable(true);
        item.setOnClickListener(listener);
        applyTapAnimation(item);
        boolean active = index == selectedNavIndex;
        item.setPadding(active ? dp(12) : dp(8), 0, active ? dp(12) : dp(8), 0);
        if (active) {
            item.setBackground(rounded(Color.rgb(39, 39, 41), dp(TILE_RADIUS_DP)));
        }
        item.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, active ? 1.65f : 0.75f));
        item.addView(icon(iconRes, Color.WHITE, dp(24)));
        TextView text = text(label, 13, Color.WHITE, Typeface.BOLD);
        text.setSingleLine(true);
        text.setVisibility(active ? View.VISIBLE : View.GONE);
        text.setAlpha(active ? 1f : 0f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dp(7);
        item.addView(text, params);
        navItems[index] = item;
        navLabels[index] = text;
        return item;
    }

    private void selectNav(int index) {
        if (index == selectedNavIndex) {
            animateNavPress(navItems[index]);
            return;
        }

        int previous = selectedNavIndex;
        selectedNavIndex = index;
        for (int i = 0; i < navItems.length; i++) {
            boolean active = i == index;
            LinearLayout item = navItems[i];
            TextView label = navLabels[i];
            if (item == null || label == null) {
                continue;
            }
            item.setBackground(active ? rounded(Color.rgb(39, 39, 41), dp(TILE_RADIUS_DP)) : null);
            item.setPadding(active ? dp(12) : dp(8), 0, active ? dp(12) : dp(8), 0);
            animateNavWeight(item, active ? 1.65f : 0.75f);
            animateNavLabel(label, active);
        }
        animateNavPress(navItems[index]);
        animateNavExit(navItems[previous]);
    }

    private void animateNavWeight(View item, float targetWeight) {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) item.getLayoutParams();
        float startWeight = params.weight;
        ValueAnimator animator = ValueAnimator.ofFloat(startWeight, targetWeight);
        animator.setDuration(220);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            params.weight = (float) animation.getAnimatedValue();
            item.setLayoutParams(params);
        });
        animator.start();
    }

    private void animateNavLabel(TextView label, boolean visible) {
        if (visible) {
            label.setVisibility(View.VISIBLE);
            label.animate().alpha(1f).translationX(0f).setDuration(170).start();
        } else {
            label.animate()
                    .alpha(0f)
                    .translationX(dp(-4))
                    .setDuration(120)
                    .withEndAction(() -> label.setVisibility(View.GONE))
                    .start();
        }
    }

    private void animateNavPress(View item) {
        if (item == null) {
            return;
        }
        item.animate().scaleX(1.05f).scaleY(1.05f).setDuration(90)
                .withEndAction(() -> item.animate().scaleX(1f).scaleY(1f).setDuration(140).start())
                .start();
    }

    private void animateNavExit(View item) {
        if (item == null) {
            return;
        }
        item.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90)
                .withEndAction(() -> item.animate().scaleX(1f).scaleY(1f).setDuration(120).start())
                .start();
    }

    private FrameLayout.LayoutParams bottomNavParams() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(56),
                Gravity.BOTTOM);
        params.leftMargin = dp(16);
        params.rightMargin = dp(16);
        params.bottomMargin = dp(20);
        return params;
    }

    private void setMode(String title, boolean active) {
        screenTitle.setText(title);
        setSystemActive(active);
        setBuzzer(active);
    }

    private void setMode(int mode, String title, boolean active) {
        selectedMode = mode;
        prefs.edit().putInt(KEY_SELECTED_MODE, mode).apply();
        updateModeSelection();
        setMode(title, active);
    }

    private void setLiveState(boolean live, String message) {
        if (liveDot == null || liveBadgeText == null || cameraImage == null || streamText == null) {
            return;
        }
        liveDot.setBackground(oval(live ? Color.rgb(43, 232, 84) : RED));
        liveBadgeText.setText(live ? "Live" : "Offline");
        cameraImage.setAlpha(live ? 1f : 0.4f);
        if (message == null || message.trim().isEmpty()) {
            streamText.setText("");
            streamText.setVisibility(View.GONE);
        } else {
            streamText.setText(message);
            streamText.setVisibility(View.VISIBLE);
        }
    }

    private LinearLayout card(int color, int radius) {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setBackground(rounded(color, radius));
        view.setClipToOutline(true);
        view.setOutlineProvider(new RoundedOutline(radius));
        return view;
    }

    private TextView sectionTitle(String value, int sp) {
        TextView text = text(value, sp, TEXT, Typeface.NORMAL);
        text.setPadding(0, 0, 0, dp(14));
        return text;
    }

    private TextView label(String value) {
        TextView text = text(value, 12, MUTED, Typeface.BOLD);
        text.setPadding(0, dp(8), 0, dp(4));
        return text;
    }

    private TextView valueText(String value) {
        TextView text = text(value, 13, MUTED, Typeface.NORMAL);
        text.setPadding(0, dp(4), 0, dp(4));
        return text;
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(sp);
        text.setTextColor(color);
        text.setTypeface(Typeface.DEFAULT, style);
        text.setIncludeFontPadding(true);
        return text;
    }

    private EditText input(String hint) {
        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setHint(hint);
        edit.setTextColor(TEXT);
        edit.setHintTextColor(Color.rgb(165, 165, 170));
        edit.setTextSize(15);
        edit.setPadding(dp(10), 0, dp(10), 0);
        edit.setBackground(rounded(TILE, dp(DEFAULT_RADIUS_DP)));
        edit.setMinHeight(dp(42));
        return edit;
    }

    private Button button(String value, int color, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(value);
        button.setTextColor(Color.WHITE);
        button.setTextSize(13);
        button.setGravity(Gravity.CENTER);
        button.setBackground(rounded(color, dp(DEFAULT_RADIUS_DP)));
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, dp(42), 1f);
        params.setMargins(dp(3), 0, dp(3), 0);
        button.setLayoutParams(params);
        return button;
    }

    private <T extends View> T applyTapAnimation(T view) {
        view.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().cancel();
                    v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().cancel();
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                    break;
                default:
                    break;
            }
            return false;
        });
        return view;
    }

    private FrameLayout circleIcon(int iconRes, int iconColor, int bgColor, int size, int iconSize) {
        FrameLayout holder = new FrameLayout(this);
        holder.setBackground(oval(bgColor));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        holder.setLayoutParams(params);
        ImageView image = icon(iconRes, iconColor, iconSize);
        FrameLayout.LayoutParams imageParams = new FrameLayout.LayoutParams(
                iconSize, iconSize, Gravity.CENTER);
        holder.addView(image, imageParams);
        applyTapAnimation(holder);
        return holder;
    }

    private ImageView icon(int iconRes, int color, int size) {
        ImageView image = new ImageView(this);
        image.setImageResource(iconRes);
        image.setColorFilter(color);
        image.setScaleType(ImageView.ScaleType.CENTER);
        image.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return image;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(radius);
        return bg;
    }

    private GradientDrawable oval(int color) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(color);
        return bg;
    }

    private void Space(LinearLayout parent, int width, float weight) {
        View spacer = new View(this);
        parent.addView(spacer, new LinearLayout.LayoutParams(width, 1, weight));
    }

    private void loadSettings() {
        connectionProfile = prefs.getString(KEY_CONNECTION_PROFILE, PROFILE_NORMAL);
        phoneStreamEnabled = prefs.getBoolean(KEY_PHONE_STREAM_ENABLED, true);
        if (hostInput != null) {
            hostInput.setText(prefs.getString(KEY_HOST, DEFAULT_ESP_HOST));
        }
        if (httpPortInput != null) {
            httpPortInput.setText(String.valueOf(getPortSetting(KEY_HTTP_PORT, 80)));
        }
        if (streamPortInput != null) {
            streamPortInput.setText(String.valueOf(getPortSetting(KEY_STREAM_PORT, 81)));
        }
        if (monitorHostInput != null) {
            monitorHostInput.setText(prefs.getString(KEY_MONITOR_HOST, "10.0.2.2"));
        }
        if (monitorPortInput != null) {
            monitorPortInput.setText(String.valueOf(getPortSetting(KEY_MONITOR_PORT, 8765)));
        }
        updateConnectionProfileUi();
        if (phoneStreamSwitch != null) {
            suppressSwitchEvents = true;
            phoneStreamSwitch.setChecked(phoneStreamEnabled);
            suppressSwitchEvents = false;
        }
        selectedMode = prefs.getInt(KEY_SELECTED_MODE, MODE_HOME);
        applySelectedModeUi();
        syncSelectedModeToDevice();
    }

    private void applySelectedModeUi() {
        if (screenTitle != null) {
            screenTitle.setText(modeTitle(selectedMode));
        }
        updateModeSelection();
    }

    private String modeTitle(int mode) {
        if (mode == MODE_DISABLED) {
            return "Deaktiviert";
        }
        if (mode == MODE_AWAY) {
            return "Unterwegs";
        }
        return "Zu Hause";
    }

    private void syncSelectedModeToDevice() {
        boolean enabled = selectedMode != MODE_DISABLED;
        setSystemActive(enabled);
        setBuzzer(enabled);
    }

    private int getPortSetting(String key, int fallback) {
        Object value = prefs.getAll().get(key);
        if (value instanceof Number) {
            int port = ((Number) value).intValue();
            return port > 0 && port <= 65535 ? port : fallback;
        }
        if (value instanceof String) {
            try {
                int port = Integer.parseInt(((String) value).trim());
                return port > 0 && port <= 65535 ? port : fallback;
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private void saveSettings() {
        prefs.edit()
                .putString(KEY_HOST, host())
                .putInt(KEY_HTTP_PORT, parsePort(httpPortInput, 80))
                .putInt(KEY_STREAM_PORT, parsePort(streamPortInput, 81))
                .putString(KEY_MONITOR_HOST, monitorHost())
                .putInt(KEY_MONITOR_PORT, parsePort(monitorPortInput, 8765))
                .putString(KEY_CONNECTION_PROFILE, connectionProfile)
                .putBoolean(KEY_PHONE_STREAM_ENABLED, phoneStreamEnabled)
                .apply();
        setConnection("Einstellungen gespeichert.", GREEN);
        refreshAll();
    }

    private void applyConnectionProfile(String profile, boolean saveNow) {
        connectionProfile = profile;
        if (PROFILE_ANDROID_HOTSPOT.equals(profile)) {
            if (hostInput != null) {
                hostInput.setText(DEFAULT_ANDROID_HOTSPOT_ESP_HOST);
            }
            if (httpPortInput != null) {
                httpPortInput.setText("80");
            }
            if (streamPortInput != null) {
                streamPortInput.setText("81");
            }
            if (monitorHostInput != null &&
                    (monitorHostInput.getText().toString().trim().isEmpty()
                            || "10.0.2.2".equals(monitorHostInput.getText().toString().trim()))) {
                monitorHostInput.setText(DEFAULT_ANDROID_HOTSPOT_MONITOR_HOST);
            }
            phoneStreamEnabled = false;
            if (phoneStreamSwitch != null) {
                suppressSwitchEvents = true;
                phoneStreamSwitch.setChecked(false);
                suppressSwitchEvents = false;
            }
            stopStream();
            setLiveState(false, "Hotspot-Modus: Stream am Laptop oeffnen.");
        } else {
            phoneStreamEnabled = true;
            if (phoneStreamSwitch != null) {
                suppressSwitchEvents = true;
                phoneStreamSwitch.setChecked(true);
                suppressSwitchEvents = false;
            }
        }
        updateConnectionProfileUi();
        if (saveNow) {
            saveSettings();
        }
    }

    private void updateConnectionProfileUi() {
        if (connectionProfileText == null) {
            return;
        }
        if (PROFILE_ANDROID_HOTSPOT.equals(connectionProfile)) {
            connectionProfileText.setText("Modus: Android-Hotspot. ESP und Laptop mit dem Handy-Hotspot verbinden; das Handy steuert nur, wenn der Laptop das Video zeigt.");
            connectionProfileText.setTextColor(AMBER);
        } else {
            connectionProfileText.setText("Modus: Schul-WLAN / normales WLAN.");
            connectionProfileText.setTextColor(MUTED);
        }
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

    private void scanForEsp() {
        setConnection("Suche ESP32 im Hotspot...", AMBER);
        Toast.makeText(this, "ESP32-Suche gestartet", Toast.LENGTH_SHORT).show();
        ioExecutor.execute(() -> {
            List<String> hotspotClients = hotspotClientIpsFromArp();
            Log.i(TAG, "ESP scan ARP clients=" + hotspotClients);
            for (String candidate : hotspotClients) {
                if (isEspCandidate(candidate)) {
                    Log.i(TAG, "ESP found via ARP candidate=" + candidate);
                    mainHandler.post(() -> applyFoundEspHost(candidate));
                    return;
                }
            }

            LinkedHashSet<String> scanPrefixes = new LinkedHashSet<>();
            scanPrefixes.addAll(localIpv4Prefixes());
            scanPrefixes.addAll(Arrays.asList(
                    "192.168.43.",
                    "192.168.42.",
                    "192.168.44.",
                    "192.168.45.",
                    "192.168.49.",
                    "192.168.50.",
                    "172.20.10.",
                    "172.16.0.",
                    "172.16.1.",
                    "192.168.137.",
                    "192.168.1.",
                    "192.168.4."));
            List<String> prefixes = new ArrayList<>(scanPrefixes);
            Log.i(TAG, "ESP scan prefixes=" + prefixes);
            mainHandler.post(() -> setConnection(String.format(Locale.GERMANY,
                    "Suche ESP32: %d Hotspot-Geraete, %d Netze...",
                    hotspotClients.size(), prefixes.size()), AMBER));
            AtomicBoolean found = new AtomicBoolean(false);
            ExecutorService scanner = Executors.newFixedThreadPool(24);
            for (String prefix : prefixes) {
                for (int i = 2; i <= 254; i++) {
                    if (found.get()) {
                        break;
                    }
                    String candidate = prefix + i;
                    scanner.execute(() -> {
                        if (!found.get() && isEspCandidate(candidate)) {
                            if (found.compareAndSet(false, true)) {
                                Log.i(TAG, "ESP found via scan candidate=" + candidate);
                                mainHandler.post(() -> applyFoundEspHost(candidate));
                            }
                        }
                    });
                }
            }
            scanner.shutdown();
            try {
                scanner.awaitTermination(12, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            if (!found.get()) {
                Log.w(TAG, "ESP scan finished without match");
                mainHandler.post(() -> setConnection(
                        "Kein ESP32 gefunden. In der App die Hotspot-IP manuell eintragen.", ERROR));
            }
        });
    }

    private void applyFoundEspHost(String candidate) {
        if (hostInput != null) {
            hostInput.setText(candidate);
        }
        prefs.edit().putString(KEY_HOST, candidate).apply();
        setConnection("ESP32 gefunden: http://" + candidate, GREEN);
        refreshAll();
    }

    private List<String> hotspotClientIpsFromArp() {
        LinkedHashSet<String> ips = new LinkedHashSet<>();
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/net/arp"))) {
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (first) {
                    first = false;
                    continue;
                }
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 4 && parts[0].matches("\\d+\\.\\d+\\.\\d+\\.\\d+")
                        && !"00:00:00:00:00:00".equals(parts[3])) {
                    ips.add(parts[0]);
                }
            }
        } catch (Exception ignored) {
            // Some Android builds restrict this file; prefix scanning remains as fallback.
        }
        return new ArrayList<>(ips);
    }

    private List<String> localIpv4Prefixes() {
        LinkedHashSet<String> prefixes = new LinkedHashSet<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return new ArrayList<>(prefixes);
            }
            for (NetworkInterface networkInterface : Collections.list(interfaces)) {
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
                for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                    InetAddress address = interfaceAddress.getAddress();
                    if (!(address instanceof Inet4Address) || !address.isSiteLocalAddress()) {
                        continue;
                    }
                    String hostAddress = address.getHostAddress();
                    String[] parts = hostAddress.split("\\.");
                    if (parts.length == 4) {
                        Log.i(TAG, "Interface " + networkInterface.getName()
                                + " address=" + hostAddress);
                        prefixes.add(parts[0] + "." + parts[1] + "." + parts[2] + ".");
                    }
                }
            }
        } catch (Exception ignored) {
            // Fallback prefixes are used below.
        }
        return new ArrayList<>(prefixes);
    }

    private boolean isEspCandidate(String host) {
        try {
            String response = getString("http://" + host + ":" + currentPort(KEY_HTTP_PORT, httpPortInput, 80)
                    + "/status", 700);
            return response.contains("framesize") || response.contains("quality");
        } catch (Exception ignored) {
            return false;
        }
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
                boolean buzzerAlarmEnabled = json.optBoolean("buzzer_alarm_enabled", true);

                mainHandler.post(() -> {
                    setConnection("ESP32 erreichbar: " + httpUrl(""), GREEN);
                    if (espStatusTileText != null) {
                        espStatusTileText.setText("ONLINE");
                        espStatusTileText.setTextColor(GREEN);
                    }
                    if (radarTileText != null) {
                        radarTileText.setText(radar ? "Bewegung erkannt" : "Keine Bewegung");
                        radarTileText.setTextColor(radar ? RED : MUTED);
                    }
                    if (lightTileText != null) {
                        lightTileText.setText(String.format(Locale.GERMANY,
                                "%s (%d)", daylight ? "Hell" : "Dunkel", light));
                        lightTileText.setTextColor(daylight ? GREEN : AMBER);
                    }
                    if (radarText != null) {
                        radarText.setText(String.format(Locale.GERMANY,
                                "Radar: %s (%d)", radar ? "Bewegung" : "ruhig", radarCount));
                        radarText.setTextColor(radar ? RED : MUTED);
                    }
                    if (lightText != null) {
                        lightText.setText(String.format(Locale.GERMANY,
                                "Licht: %d / %d (%s)", light, threshold, daylight ? "hell" : "dunkel"));
                        lightText.setTextColor(daylight ? MUTED : AMBER);
                    }
                    if (alarmText != null) {
                        alarmText.setText(String.format(Locale.GERMANY,
                            "Buzzer bei Alarm: %s%s, Nachtalarme: %d",
                            buzzerAlarmEnabled ? "ein" : "aus",
                            buzzer ? " (ertönt)" : "",
                            darkCount));
                        alarmText.setTextColor(buzzer ? RED : MUTED);
                    }
                    if (buzzerSwitch != null) {
                        suppressSwitchEvents = true;
                        buzzerSwitch.setChecked(buzzerAlarmEnabled);
                        suppressSwitchEvents = false;
                    }
                });
            } catch (Exception ex) {
                mainHandler.post(() -> {
                    setConnection("Keine Verbindung zu /sensors: " + cleanError(ex), ERROR);
                    if (espStatusTileText != null) {
                        espStatusTileText.setText("OFFLINE");
                        espStatusTileText.setTextColor(ERROR);
                    }
                    if (radarTileText != null) {
                        radarTileText.setText("unbekannt");
                        radarTileText.setTextColor(MUTED);
                    }
                    if (lightTileText != null) {
                        lightTileText.setText("unbekannt");
                        lightTileText.setTextColor(MUTED);
                    }
                    if (radarText != null) {
                        radarText.setText("Radar: unbekannt");
                    }
                    if (lightText != null) {
                        lightText.setText("Licht: unbekannt");
                    }
                    if (alarmText != null) {
                        alarmText.setText("Buzzer: unbekannt");
                    }
                });
            }
        });
    }

    private void refreshStatusOnly() {
        ioExecutor.execute(() -> {
            try {
                JSONObject json = getJson(httpUrl("/status"));
                String ssid = json.optString("wifi_ssid", "unbekannt");
                String ip = json.optString("wifi_ip", host());
                int rssi = json.optInt("wifi_rssi", 0);
                String status = String.format(Locale.GERMANY,
                        "Kamera: q=%d, fs=%d, b=%d | WLAN: %s, IP: %s, RSSI: %d dBm",
                        json.optInt("quality", -1),
                        json.optInt("framesize", -1),
                        json.optInt("brightness", 0),
                        ssid,
                        ip,
                        rssi);
                mainHandler.post(() -> {
                    if (espStatusTileText != null) {
                        espStatusTileText.setText(String.format(Locale.GERMANY,
                                "%s\n%s\n%d dBm", ssid, ip, rssi));
                        espStatusTileText.setTextColor(GREEN);
                    }
                    if (cameraText != null) {
                        cameraText.setText(status);
                        cameraText.setTextColor(MUTED);
                    }
                });
            } catch (Exception ex) {
                mainHandler.post(() -> {
                    if (espStatusTileText != null) {
                        espStatusTileText.setText("OFFLINE");
                        espStatusTileText.setTextColor(ERROR);
                    }
                    if (cameraText != null) {
                        cameraText.setText("Kamera: nicht erreichbar");
                        cameraText.setTextColor(ERROR);
                    }
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
                mainHandler.post(() -> {
                    if (laptopStatusTileText != null) {
                        laptopStatusTileText.setText("ONLINE");
                        laptopStatusTileText.setTextColor(GREEN);
                    }
                    laptopMonitorActive = active;
                    if (monitorText != null) {
                        monitorText.setText(String.format(Locale.GERMANY,
                                "Monitor: %s, Alarme: %d",
                                active ? "aktiv" : "deaktiviert", alarmCount));
                        monitorText.setTextColor(active ? MUTED : AMBER);
                    }
                    if (monitorSwitch != null) {
                        suppressSwitchEvents = true;
                        monitorSwitch.setChecked(active);
                        suppressSwitchEvents = false;
                    }
                });
            } catch (Exception ex) {
                mainHandler.post(() -> {
                    if (laptopStatusTileText != null) {
                        laptopStatusTileText.setText("OFFLINE");
                        laptopStatusTileText.setTextColor(ERROR);
                    }
                    laptopMonitorActive = false;
                    if (monitorText != null) {
                        monitorText.setText("Monitor: nicht erreichbar");
                        monitorText.setTextColor(ERROR);
                    }
                });
            }
        });
    }

    private void toggleLaptopMonitor() {
        setSystemActive(!laptopMonitorActive);
    }

    private void setSystemActive(boolean active) {
        ioExecutor.execute(() -> {
            try {
                getString(monitorUrl("/api/system/active?value=" + (active ? "1" : "0")), 50000);
                mainHandler.post(() -> {
                    laptopMonitorActive = active;
                    if (monitorText != null) {
                        monitorText.setText(active ? "Monitor: aktiv" : "Monitor: deaktiviert");
                        monitorText.setTextColor(active ? MUTED : AMBER);
                    }
                    if (monitorSwitch != null) {
                        suppressSwitchEvents = true;
                        monitorSwitch.setChecked(active);
                        suppressSwitchEvents = false;
                    }
                });
                refreshMonitorOnly();
            } catch (Exception ex) {
                mainHandler.post(() -> {
                    if (monitorText != null) {
                        monitorText.setText("Monitor-Schalter fehlgeschlagen");
                        monitorText.setTextColor(ERROR);
                    }
                    if (monitorSwitch != null) {
                        suppressSwitchEvents = true;
                        monitorSwitch.setChecked(!active);
                        suppressSwitchEvents = false;
                    }
                });
            }
        });
    }

    private void setBuzzer(boolean active) {
        ioExecutor.execute(() -> {
            try {
                getString(httpUrl("/buzzer?alarm_enabled=" + (active ? "1" : "0")), 3000);
                mainHandler.post(() -> {
                    if (alarmText != null) {
                        alarmText.setText(active ? "Buzzer bei Alarm: ein" : "Buzzer bei Alarm: aus");
                        alarmText.setTextColor(MUTED);
                    }
                });
            } catch (Exception ex) {
                mainHandler.post(() -> {
                    if (alarmText != null) {
                        alarmText.setText("Buzzer-Einstellung fehlgeschlagen");
                        alarmText.setTextColor(ERROR);
                    }
                    if (buzzerSwitch != null) {
                        suppressSwitchEvents = true;
                        buzzerSwitch.setChecked(!active);
                        suppressSwitchEvents = false;
                    }
                });
            }
        });
    }

    private void testBuzzer() {
        if (buzzerTestTileText == null) {
            return;
        }
        buzzerTestTileText.setText("TEST LAEUFT");
        buzzerTestTileText.setTextColor(AMBER);
        ioExecutor.execute(() -> {
            try {
                getString(httpUrl("/buzzer?state=on&duration_ms=1000&force=1"), 3000);
                mainHandler.postDelayed(() -> {
                    if (buzzerTestTileText != null) {
                        buzzerTestTileText.setText("Tippen zum Testen");
                        buzzerTestTileText.setTextColor(MUTED);
                    }
                }, 1200);
            } catch (Exception ex) {
                mainHandler.post(() -> {
                    if (buzzerTestTileText != null) {
                        buzzerTestTileText.setText("TEST FEHLGESCHLAGEN");
                        buzzerTestTileText.setTextColor(ERROR);
                    }
                });
            }
        });
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
                    if (cameraImage != null) {
                        cameraImage.setImageBitmap(bitmap);
                    }
                    if (!streamRunning) {
                        setLiveState(false, phoneStreamEnabled
                                ? ""
                                : "Handy steuert nur. Stream am Laptop oeffnen.");
                    }
                });
            } catch (Exception ex) {
                mainHandler.post(() -> {
                    if (!streamRunning) {
                        setLiveState(false, "Kamera nicht erreichbar");
                    }
                });
            }
        });
    }

    private void startStream() {
        if (streamRunning) {
            return;
        }
        if (!phoneStreamEnabled) {
            setLiveState(false, "Handy steuert nur. Stream am Laptop oeffnen.");
            return;
        }
        streamRunning = true;
        setLiveState(false, "Stream verbindet...");
        ioExecutor.execute(this::streamLoop);
    }

    private void stopStream() {
        stopStream(true);
    }

    private void stopStream(boolean updateStatus) {
        streamRunning = false;
        HttpURLConnection connection = streamConnection;
        if (connection != null) {
            connection.disconnect();
        }
        if (updateStatus) {
            mainHandler.post(() -> setLiveState(false, ""));
        }
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
            mainHandler.post(() -> setLiveState(true, ""));
            try (InputStream input = connection.getInputStream()) {
                readMjpeg(input);
            }
        } catch (Exception ex) {
            if (streamRunning && appInForeground) {
                mainHandler.post(() -> setLiveState(false, "Stream verbindet..."));
                snapshotStreamLoop(ex);
            }
        } finally {
            streamRunning = false;
            HttpURLConnection connection = streamConnection;
            if (connection != null) {
                connection.disconnect();
            }
            streamConnection = null;
            if (appInForeground) {
                mainHandler.post(() -> setLiveState(false, ""));
            }
        }
    }

    private void snapshotStreamLoop(Exception streamError) {
        int failures = 0;
        while (streamRunning) {
            try {
                byte[] bytes = getBytes(httpUrl("/capture"), 5000);
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bitmap == null) {
                    throw new IllegalStateException("JPEG konnte nicht dekodiert werden");
                }
                failures = 0;
                mainHandler.post(() -> {
                    if (cameraImage != null) {
                        cameraImage.setImageBitmap(bitmap);
                    }
                    setLiveState(true, "");
                });
                Thread.sleep(250);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ex) {
                failures++;
                if (failures >= 3) {
                    if (appInForeground) {
                        mainHandler.post(() -> setLiveState(false, "Streamfehler: " + cleanError(streamError)));
                    }
                    return;
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
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
                        mainHandler.post(() -> {
                            if (cameraImage != null) {
                                cameraImage.setImageBitmap(bitmap);
                            }
                        });
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

    private JSONObject getJson(String url) throws Exception {
        return new JSONObject(getString(url, 4000));
    }

    private String getString(String url, int timeoutMs) throws Exception {
        byte[] bytes = getBytes(url, timeoutMs);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private byte[] getBytes(String urlString, int timeoutMs) throws Exception {
        return requestBytes(urlString, timeoutMs, "GET");
    }

    private String getString(String url, int timeoutMs, String method) throws Exception {
        byte[] bytes = requestBytes(url, timeoutMs, method);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private String getString(String url, int timeoutMs, String method, String body, String contentType)
            throws Exception {
        byte[] bytes = requestBytes(url, timeoutMs, method, body, contentType);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private byte[] requestBytes(String urlString, int timeoutMs, String method) throws Exception {
        return requestBytes(urlString, timeoutMs, method, null, null);
    }

    private byte[] requestBytes(String urlString, int timeoutMs, String method, String body, String contentType)
            throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setConnectTimeout(timeoutMs);
        connection.setReadTimeout(timeoutMs);
        connection.setUseCaches(false);
        connection.setRequestMethod(method);
        if (body != null) {
            connection.setDoOutput(true);
            if (contentType != null && !contentType.trim().isEmpty()) {
                connection.setRequestProperty("Content-Type", contentType);
            }
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
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

    private String sha1(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) {
                builder.append(String.format(Locale.US, "%02x", b));
            }
            return builder.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private String host() {
        if (hostInput == null) {
            return prefs.getString(KEY_HOST, "192.168.178.53").trim();
        }
        return hostInput.getText().toString().trim();
    }

    private String monitorHost() {
        if (monitorHostInput == null) {
            return prefs.getString(KEY_MONITOR_HOST, "10.0.2.2").trim();
        }
        return monitorHostInput.getText().toString().trim();
    }

    private int parsePort(EditText editText, int fallback) {
        if (editText == null) {
            return fallback;
        }
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
        return "http://" + host() + ":" + currentPort(KEY_HTTP_PORT, httpPortInput, 80) + cleanPath;
    }

    private String streamUrl(String path) {
        String cleanPath = path == null ? "" : path;
        if (!cleanPath.isEmpty() && !cleanPath.startsWith("/")) {
            cleanPath = "/" + cleanPath;
        }
        return "http://" + host() + ":" + currentPort(KEY_STREAM_PORT, streamPortInput, 81) + cleanPath;
    }

    private String monitorUrl(String path) {
        String cleanPath = path == null ? "" : path;
        if (!cleanPath.isEmpty() && !cleanPath.startsWith("/")) {
            cleanPath = "/" + cleanPath;
        }
        return "http://" + monitorHost() + ":" + currentPort(KEY_MONITOR_PORT, monitorPortInput, 8765) + cleanPath;
    }

    private int currentPort(String key, EditText editText, int fallback) {
        if (editText == null) {
            return getPortSetting(key, fallback);
        }
        return parsePort(editText, fallback);
    }

    private void setConnection(String message, int color) {
        if (connectionText == null) {
            return;
        }
        String prefix = hasNetwork() ? "" : "Kein Netzwerk erkannt. ";
        connectionText.setText(prefix + message);
        connectionText.setTextColor(color);
    }

    private boolean hasNetwork() {
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

    private static class ZoneDraft {
        String id;
        String name;
        final ArrayList<PointF> points = new ArrayList<>();

        ZoneDraft(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private class ZoneEditorView extends View {
        private final int[] ZONE_COLORS = new int[] {
                Color.rgb(0, 165, 255),
                Color.rgb(255, 180, 0),
                Color.rgb(255, 0, 180),
                Color.rgb(0, 220, 0),
                Color.rgb(220, 80, 80),
        };

        private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint zoneFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint zoneStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint currentStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint currentPointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint placeholderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF imageRect = new RectF();
        private final ArrayList<ZoneDraft> zones = new ArrayList<>();
        private final ArrayList<PointF> currentPoints = new ArrayList<>();
        private Bitmap bitmap;
        private JSONObject pendingPayload;
        private ZoneDraft selectedZone;

        ZoneEditorView(MainActivityV2 context) {
            super(context);
            setClickable(true);

            bitmapPaint.setFilterBitmap(true);

            zoneStrokePaint.setStyle(Paint.Style.STROKE);
            zoneStrokePaint.setStrokeWidth(dp(2));

            zoneFillPaint.setStyle(Paint.Style.FILL);

            currentStrokePaint.setStyle(Paint.Style.STROKE);
            currentStrokePaint.setStrokeWidth(dp(2));
            currentStrokePaint.setColor(Color.WHITE);

            currentPointPaint.setStyle(Paint.Style.FILL);
            currentPointPaint.setColor(Color.WHITE);

            labelPaint.setColor(Color.WHITE);
            labelPaint.setTextSize(dp(13));
            labelPaint.setTypeface(Typeface.DEFAULT_BOLD);
            labelPaint.setShadowLayer(4f, 0f, dp(1), Color.argb(180, 0, 0, 0));

            labelBackgroundPaint.setColor(Color.argb(170, 0, 0, 0));

            placeholderPaint.setColor(Color.rgb(180, 185, 190));
            placeholderPaint.setTextSize(dp(14));
            placeholderPaint.setTextAlign(Paint.Align.CENTER);
        }

        void setImage(Bitmap image) {
            bitmap = image;
            if (bitmap != null && pendingPayload != null) {
                applyZonesPayload(pendingPayload);
                pendingPayload = null;
            }
            invalidate();
        }

        void setZonesFromJson(JSONObject payload) {
            if (payload == null) {
                zones.clear();
                currentPoints.clear();
                selectedZone = null;
                invalidate();
                MainActivityV2.this.updateZoneActionButtons();
                return;
            }
            if (bitmap == null) {
                try {
                    pendingPayload = new JSONObject(payload.toString());
                } catch (Exception ignored) {
                    pendingPayload = payload;
                }
                return;
            }
            applyZonesPayload(payload);
        }

        ZoneDraft getSelectedZone() {
            return selectedZone;
        }

        void deleteSelectedZone() {
            if (selectedZone == null) {
                return;
            }
            zones.remove(selectedZone);
            selectedZone = null;
            invalidate();
            MainActivityV2.this.updateZoneActionButtons();
        }

        JSONObject toPayload() {
            JSONObject payload = new JSONObject();
            JSONArray zonesArray = new JSONArray();
            int width = bitmap != null ? bitmap.getWidth() : 0;
            int height = bitmap != null ? bitmap.getHeight() : 0;
            try {
                payload.put("zone_resolution", new JSONObject()
                        .put("width", width)
                        .put("height", height));
                for (ZoneDraft zone : zones) {
                    zonesArray.put(zoneToJson(zone));
                }
                payload.put("zones", zonesArray);
            } catch (Exception ignored) {
                // The payload is best-effort and remains valid JSON.
            }
            return payload;
        }

        boolean hasOpenPoints() {
            return !currentPoints.isEmpty();
        }

        boolean finishCurrentZone() {
            if (currentPoints.size() < 3) {
                return false;
            }
            int zoneIndex = zones.size() + 1;
            ZoneDraft zone = new ZoneDraft("zone-" + zoneIndex, "No-Go Zone " + zoneIndex);
            zone.points.addAll(copyPoints(currentPoints));
            zones.add(zone);
            currentPoints.clear();
            invalidate();
            MainActivityV2.this.saveZonesFromEditor();
            return true;
        }

        void clearAllZones() {
            zones.clear();
            currentPoints.clear();
            selectedZone = null;
            invalidate();
            MainActivityV2.this.updateZoneActionButtons();
        }

        boolean isEmpty() {
            return zones.isEmpty() && currentPoints.isEmpty();
        }

        String summaryText() {
            int totalPoints = currentPoints.size();
            for (ZoneDraft zone : zones) {
                totalPoints += zone.points.size();
            }
            return String.format(Locale.GERMANY, "%d Zonen, %d Punkte", zones.size(), totalPoints);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            updateImageRect(w, h);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawColor(Color.rgb(18, 18, 20));
            if (bitmap == null) {
                canvas.drawText("Kein Bild geladen", getWidth() / 2f,
                        getHeight() / 2f, placeholderPaint);
                return;
            }

            updateImageRect(getWidth(), getHeight());
            canvas.drawBitmap(bitmap, null, imageRect, bitmapPaint);
            drawCommittedZones(canvas);
            drawCurrentPolygon(canvas);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (bitmap == null || event.getAction() != MotionEvent.ACTION_UP) {
                return super.onTouchEvent(event);
            }
            if (currentPoints.size() >= 3 && isNearFirstPoint(event.getX(), event.getY())) {
                return finishCurrentZone();
            }
            ZoneDraft tappedZone = findZoneAt(event.getX(), event.getY());
            if (tappedZone != null) {
                selectedZone = tappedZone;
                invalidate();
                MainActivityV2.this.updateZoneActionButtons();
                MainActivityV2.this.updateZoneEditorSummary("Zone ausgewählt.");
                return true;
            }
            PointF imagePoint = toImagePoint(event.getX(), event.getY());
            if (imagePoint == null) {
                return true;
            }
            currentPoints.add(imagePoint);
            invalidate();
            updateZoneEditorSummary(String.format(Locale.GERMANY,
                    "Punkt %d gesetzt.", currentPoints.size()));
            return true;
        }

        private void applyZonesPayload(JSONObject payload) {
            zones.clear();
            currentPoints.clear();
            selectedZone = null;
            JSONArray zonesArray = payload.optJSONArray("zones");
            if (zonesArray == null || bitmap == null) {
                invalidate();
                MainActivityV2.this.updateZoneActionButtons();
                return;
            }

            JSONObject resolution = payload.optJSONObject("zone_resolution");
            float sourceWidth = resolution != null
                    ? resolution.optInt("width", bitmap.getWidth())
                    : bitmap.getWidth();
            float sourceHeight = resolution != null
                    ? resolution.optInt("height", bitmap.getHeight())
                    : bitmap.getHeight();
            if (sourceWidth <= 0f) {
                sourceWidth = bitmap.getWidth();
            }
            if (sourceHeight <= 0f) {
                sourceHeight = bitmap.getHeight();
            }

            for (int i = 0; i < zonesArray.length(); i++) {
                JSONObject zoneJson = zonesArray.optJSONObject(i);
                if (zoneJson == null) {
                    continue;
                }
                ZoneDraft zone = new ZoneDraft(
                        zoneJson.optString("id", "zone-" + (i + 1)),
                        zoneJson.optString("name", "No-Go Zone " + (i + 1)));
                JSONArray pointsArray = zoneJson.optJSONArray("points");
                if (pointsArray != null) {
                    for (int p = 0; p < pointsArray.length(); p++) {
                        JSONArray point = pointsArray.optJSONArray(p);
                        if (point == null || point.length() < 2) {
                            continue;
                        }
                        float x = (float) point.optDouble(0, 0.0) / sourceWidth * bitmap.getWidth();
                        float y = (float) point.optDouble(1, 0.0) / sourceHeight * bitmap.getHeight();
                        zone.points.add(new PointF(x, y));
                    }
                }
                if (zone.points.size() >= 3) {
                    zones.add(zone);
                }
            }
            invalidate();
            MainActivityV2.this.updateZoneActionButtons();
        }

        private JSONObject zoneToJson(ZoneDraft zone) {
            JSONObject json = new JSONObject();
            JSONArray points = new JSONArray();
            try {
                json.put("id", zone.id);
                json.put("name", zone.name);
                for (PointF point : zone.points) {
                    JSONArray item = new JSONArray();
                    item.put(Math.round(point.x));
                    item.put(Math.round(point.y));
                    points.put(item);
                }
                json.put("points", points);
            } catch (Exception ignored) {
                // JSON building is best effort.
            }
            return json;
        }

        private ArrayList<PointF> copyPoints(List<PointF> points) {
            ArrayList<PointF> copy = new ArrayList<>(points.size());
            for (PointF point : points) {
                copy.add(new PointF(point.x, point.y));
            }
            return copy;
        }

        private void updateImageRect(int width, int height) {
            if (bitmap == null || width <= 0 || height <= 0) {
                imageRect.setEmpty();
                return;
            }
            float bitmapRatio = bitmap.getWidth() / (float) bitmap.getHeight();
            float viewRatio = width / (float) height;
            if (bitmapRatio > viewRatio) {
                float scaledHeight = width / bitmapRatio;
                float top = (height - scaledHeight) / 2f;
                imageRect.set(0f, top, width, top + scaledHeight);
            } else {
                float scaledWidth = height * bitmapRatio;
                float left = (width - scaledWidth) / 2f;
                imageRect.set(left, 0f, left + scaledWidth, height);
            }
        }

        private void drawCommittedZones(Canvas canvas) {
            for (int i = 0; i < zones.size(); i++) {
                ZoneDraft zone = zones.get(i);
                if (zone.points.size() < 3) {
                    continue;
                }
                int color = ZONE_COLORS[i % ZONE_COLORS.length];
                boolean selected = zone == selectedZone;
                zoneFillPaint.setColor(Color.argb(selected ? 88 : 56,
                        Color.red(color), Color.green(color), Color.blue(color)));
                zoneStrokePaint.setColor(color);
                zoneStrokePaint.setStrokeWidth(dp(selected ? 4 : 2));
                drawZonePath(canvas, zone.points, zoneFillPaint, zoneStrokePaint);
                drawZoneLabel(canvas, zone.points, zone.name, color);
            }
        }

        private void drawCurrentPolygon(Canvas canvas) {
            if (currentPoints.isEmpty()) {
                return;
            }
            drawPolyline(canvas, currentPoints, currentStrokePaint);
            for (PointF point : currentPoints) {
                PointF mapped = toViewPoint(point);
                if (mapped != null) {
                    if (point == currentPoints.get(0)) {
                        currentPointPaint.setColor(Color.rgb(255, 196, 0));
                        canvas.drawCircle(mapped.x, mapped.y, dp(7), currentPointPaint);
                        currentPointPaint.setColor(Color.WHITE);
                    } else {
                        canvas.drawCircle(mapped.x, mapped.y, dp(4), currentPointPaint);
                    }
                }
            }
        }

        private boolean isNearFirstPoint(float x, float y) {
            if (currentPoints.isEmpty()) {
                return false;
            }
            PointF first = currentPoints.get(0);
            PointF mapped = toViewPoint(first);
            if (mapped == null) {
                return false;
            }
            float dx = x - mapped.x;
            float dy = y - mapped.y;
            float radius = dp(18);
            return dx * dx + dy * dy <= radius * radius;
        }

        private void drawZonePath(Canvas canvas, List<PointF> points, Paint fill, Paint stroke) {
            Path path = new Path();
            boolean first = true;
            for (PointF point : points) {
                PointF mapped = toViewPoint(point);
                if (mapped == null) {
                    continue;
                }
                if (first) {
                    path.moveTo(mapped.x, mapped.y);
                    first = false;
                } else {
                    path.lineTo(mapped.x, mapped.y);
                }
            }
            path.close();
            canvas.drawPath(path, fill);
            canvas.drawPath(path, stroke);
        }

        private void drawPolyline(Canvas canvas, List<PointF> points, Paint paint) {
            Path path = new Path();
            boolean first = true;
            for (PointF point : points) {
                PointF mapped = toViewPoint(point);
                if (mapped == null) {
                    continue;
                }
                if (first) {
                    path.moveTo(mapped.x, mapped.y);
                    first = false;
                } else {
                    path.lineTo(mapped.x, mapped.y);
                }
            }
            canvas.drawPath(path, paint);
        }

        private void drawZoneLabel(Canvas canvas, List<PointF> points, String label, int color) {
            PointF centroid = centroid(points);
            if (centroid == null) {
                return;
            }
            float paddingX = dp(8);
            float paddingY = dp(5);
            float textWidth = labelPaint.measureText(label);
            float textHeight = labelPaint.getTextSize();
            float left = centroid.x - textWidth / 2f - paddingX;
            float top = centroid.y - textHeight / 2f - paddingY;
            RectF background = new RectF(left, top, left + textWidth + paddingX * 2f, top + textHeight + paddingY * 2f);
            canvas.drawRoundRect(background, dp(8), dp(8), labelBackgroundPaint);
            canvas.drawText(label, background.left + paddingX, background.bottom - paddingY, labelPaint);
        }

        private PointF centroid(List<PointF> points) {
            if (points.isEmpty()) {
                return null;
            }
            float x = 0f;
            float y = 0f;
            int count = 0;
            for (PointF point : points) {
                PointF mapped = toViewPoint(point);
                if (mapped == null) {
                    continue;
                }
                x += mapped.x;
                y += mapped.y;
                count++;
            }
            if (count == 0) {
                return null;
            }
            return new PointF(x / count, y / count);
        }

        private PointF toViewPoint(PointF point) {
            if (bitmap == null || imageRect.isEmpty() || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
                return null;
            }
            float x = imageRect.left + (point.x / bitmap.getWidth()) * imageRect.width();
            float y = imageRect.top + (point.y / bitmap.getHeight()) * imageRect.height();
            return new PointF(x, y);
        }

        private PointF toImagePoint(float x, float y) {
            if (!imageRect.contains(x, y) || bitmap == null || imageRect.width() <= 0f || imageRect.height() <= 0f) {
                return null;
            }
            float imageX = ((x - imageRect.left) / imageRect.width()) * bitmap.getWidth();
            float imageY = ((y - imageRect.top) / imageRect.height()) * bitmap.getHeight();
            return new PointF(imageX, imageY);
        }

        private ZoneDraft findZoneAt(float x, float y) {
            for (int i = zones.size() - 1; i >= 0; i--) {
                ZoneDraft zone = zones.get(i);
                if (zone.points.size() < 3) {
                    continue;
                }
                if (isPointInZone(x, y, zone)) {
                    return zone;
                }
            }
            return null;
        }

        private boolean isPointInZone(float x, float y, ZoneDraft zone) {
            ArrayList<PointF> mappedPoints = new ArrayList<>(zone.points.size());
            for (PointF point : zone.points) {
                PointF mapped = toViewPoint(point);
                if (mapped != null) {
                    mappedPoints.add(mapped);
                }
            }
            if (mappedPoints.size() < 3) {
                return false;
            }
            return isPointInPolygon(x, y, mappedPoints);
        }

        private boolean isPointInPolygon(float x, float y, List<PointF> polygon) {
            boolean inside = false;
            for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
                PointF pi = polygon.get(i);
                PointF pj = polygon.get(j);
                float denominator = pj.y - pi.y;
                if (denominator == 0f) {
                    denominator = 0.00001f;
                }
                boolean intersects = ((pi.y > y) != (pj.y > y))
                        && (x < (pj.x - pi.x) * (y - pi.y) / denominator + pi.x);
                if (intersects) {
                    inside = !inside;
                }
            }
            return inside;
        }

    }

    private static class RoundedOutline extends ViewOutlineProvider {
        private final int radius;

        RoundedOutline(int radius) {
            this.radius = radius;
        }

        @Override
        public void getOutline(View view, Outline outline) {
            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
        }
    }
}


