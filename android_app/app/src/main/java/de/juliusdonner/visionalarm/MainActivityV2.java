package de.juliusdonner.visionalarm;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Uri;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.Spinner;
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
import java.io.IOException;
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
    private static final String KEY_NORMAL_WIFI_HOST = "normal_wifi_host";
    private static final String KEY_HOTSPOT_HOST = "hotspot_host";
    private static final String KEY_PHONE_STREAM_ENABLED = "phone_stream_enabled";
    private static final String KEY_CAMERA_FRAMESIZE = "camera_framesize";
    private static final String KEY_RADAR_PHONE_ALARM_ENABLED = "radar_phone_alarm_enabled";
    private static final String KEY_RADAR_CAL_MOVE_FACTOR = "radar_cal_move_factor";
    private static final String KEY_RADAR_CAL_STILL_FACTOR = "radar_cal_still_factor";
    private static final String PROFILE_NORMAL = "normal";
    private static final String PROFILE_ANDROID_HOTSPOT = "android_hotspot";
    private static final String DEFAULT_CONNECTION_PROFILE = PROFILE_ANDROID_HOTSPOT;
    private static final String DEFAULT_ESP_HOST = "192.168.178.53";
    private static final String DEFAULT_ANDROID_HOTSPOT_ESP_HOST = "192.168.202.222";
    private static final String DEFAULT_ANDROID_HOTSPOT_MONITOR_HOST = "192.168.43.1";
    private static final String[] ANDROID_HOTSPOT_PREFIXES = {
            "192.168.202.", "192.168.186.", "192.168.43.", "192.168.42.",
            "192.168.44.", "192.168.45.", "192.168.49.", "192.168.50."
    };
    private static final int DEFAULT_CAMERA_FRAMESIZE = 6; // FRAMESIZE_QVGA, 320x240
    private static final int[] CAMERA_FRAMESIZE_VALUES = {
            6, 8, 10, 11, 12, 13, 14, 15
    };
    private static final String[] CAMERA_FRAMESIZE_LABELS = {
            "QVGA 320x240",
            "CIF 400x296",
            "VGA 640x480",
            "SVGA 800x600",
            "XGA 1024x768",
            "HD 1280x720",
            "SXGA 1280x1024",
            "UXGA 1600x1200"
    };

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
    private static final long SENSOR_POLL_INTERVAL_MS = 750;
    private static final int STATUS_POLL_SECONDS = 3;
    private static final int MONITOR_POLL_SECONDS = 5;
    private static final int NOTIFICATION_PERMISSION_REQUEST = 44;
    private static final int RADAR_NOTIFICATION_ID = 2001;
    private static final long RADAR_ALARM_COOLDOWN_MS = 10000;
    private static final String RADAR_NOTIFICATION_CHANNEL = "radar_alarm";
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
    private Spinner resolutionSpinner;
    private TextView connectionText;
    private TextView cameraText;
    private TextView radarText;
    private TextView lightText;
    private TextView alarmText;
    private TextView radarPhoneAlarmText;
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
    private ImageView liveGestureIcon;
    private ImageView cameraImage;
    private Switch buzzerSwitch;
    private Switch monitorSwitch;
    private Switch radarPhoneAlarmSwitch;
    private TextView radarCalibrationStatusText;
    private TextView radarCalibrationMoveFactorText;
    private TextView radarCalibrationStillFactorText;
    private SeekBar radarCalibrationMoveFactorSeekBar;
    private SeekBar radarCalibrationStillFactorSeekBar;
    private Button radarCalibrationActionButton;
    private Button radarCalibrationCancelButton;
    private TextView radarMinimumGateText;
    private TextView radarMaximumGateText;
    private TextView radarPresenceDelayText;
    private TextView radarRangeSummaryText;
    private TextView radarLiveGateValuesText;
    private RadarRangeView radarRangeView;
    private SeekBar radarMinimumGateSeekBar;
    private SeekBar radarMaximumGateSeekBar;
    private SeekBar radarPresenceDelaySeekBar;
    private TextView radarManualThresholdGateText;
    private SeekBar radarManualThresholdGateSeekBar;
    private EditText radarTriggerThresholdInput;
    private EditText radarMaintainThresholdInput;
    private final int[] radarTriggerThresholds = new int[16];
    private final int[] radarMaintainThresholds = new int[16];
    private int radarSelectedThresholdGate = 0;
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
    private boolean suppressResolutionEvents = false;
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
    private int liveGestureAnimationId = 0;
    private volatile boolean appInForeground = false;
    private volatile HttpURLConnection streamConnection;
    // A fast MJPEG source can otherwise enqueue dozens of full-size Bitmaps
    // on the main thread and eventually make Android kill the process.
    private final AtomicBoolean cameraFrameRenderPending = new AtomicBoolean(false);
    private volatile long lastCameraFrameDecodeAtMs = 0;
    private volatile Network hotspotLocalNetwork;
    private boolean hotspotLocalNetworkRequested;
    private volatile String connectionProfile = PROFILE_NORMAL;
    private volatile boolean phoneStreamEnabled = true;
    private volatile boolean radarPhoneAlarmEnabled = false;
    private final AtomicBoolean espAutoScanInFlight = new AtomicBoolean(false);
    private final AtomicBoolean sensorRefreshInFlight = new AtomicBoolean(false);
    private long lastEspAutoScanAtMs = 0;
    private boolean radarAlarmStateInitialized = false;
    private boolean lastRadarActive = false;
    private int lastRadarMotionCount = -1;
    private long lastRadarAlarmAtMs = 0;
    private volatile int sensorFailureCount = 0;
    private volatile int statusFailureCount = 0;
    private volatile boolean radarCalibrationActive = false;
    private volatile boolean radarCalibrationReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        createNotificationChannel();
        clearOldClipCache();
        loadHiddenHistoryIds();
        setContentView(buildContentView());
        loadSettings();
        requestHotspotLocalNetwork();
        startPolling();
        startEspScan(true);
        captureFrame();
    }

    @Override
    protected void onResume() {
        super.onResume();
        appInForeground = true;
        // The live card used to stay "Offline" after every app start because
        // the MJPEG stream was only opened after tapping the preview.  In the
        // hotspot profile the phone is the viewer, so reconnect automatically
        // whenever the activity becomes visible again.
        if (phoneStreamEnabled && !streamRunning) {
            mainHandler.post(this::startStream);
        }
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
        releaseHotspotLocalNetwork();
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

        contentRoot.addView(sectionTitle("Radar", 20));
        contentRoot.addView(radarCalibrationPanel());

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

        panel.addView(text("ESP-Verbindung", 16, TEXT, Typeface.BOLD));
        connectionProfileText = valueText("");
        panel.addView(connectionProfileText);

        LinearLayout profileButtons = new LinearLayout(this);
        profileButtons.setOrientation(LinearLayout.HORIZONTAL);
        profileButtons.setPadding(0, dp(8), 0, dp(4));
        profileButtons.addView(button("Normales WLAN", BLUE,
                v -> applyConnectionProfile(PROFILE_NORMAL, true)));
        profileButtons.addView(button("Handy-Hotspot", AMBER,
                v -> applyConnectionProfile(PROFILE_ANDROID_HOTSPOT, true)));
        panel.addView(profileButtons);

        panel.addView(label("ESP32-Adresse (automatisch gefunden)"));
        panel.addView(hostInput);
        Button scanButton = button("ESP automatisch suchen", BLACK, v -> scanForEsp());
        LinearLayout scanRow = new LinearLayout(this);
        scanRow.setOrientation(LinearLayout.HORIZONTAL);
        scanRow.setPadding(0, dp(8), 0, 0);
        scanRow.addView(scanButton);
        panel.addView(scanRow);

        panel.addView(label("HTTP-Port"));
        panel.addView(httpPortInput);
        panel.addView(label("Stream-Port"));
        panel.addView(streamPortInput);
        panel.addView(label("Aufloesung"));
        resolutionSpinner = resolutionSpinner();
        panel.addView(resolutionSpinner);
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
        panel.addView(radarPhoneAlarmRow());

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

    private LinearLayout radarCalibrationPanel() {
        LinearLayout panel = card(TILE, dp(TILE_RADIUS_DP));
        panel.setPadding(dp(16), dp(16), dp(16), dp(16));

        panel.addView(text("Radar-Alarmbereich", 18, TEXT, Typeface.BOLD));
        TextView rangeHelp = valueText(
                "Nur Bewegungen innerhalb des blau markierten Entfernungsbereichs lösen einen Alarm aus.");
        rangeHelp.setPadding(0, dp(6), 0, dp(8));
        panel.addView(rangeHelp);

        radarRangeSummaryText = text("Alarmbereich wird geladen ...", 16, BLUE, Typeface.BOLD);
        radarRangeSummaryText.setPadding(0, dp(4), 0, dp(6));
        panel.addView(radarRangeSummaryText);

        radarRangeView = new RadarRangeView(this);
        panel.addView(radarRangeView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(104)));

        panel.addView(label("Live Gate-Werte"));
        radarLiveGateValuesText = text("Warte auf Radarwerte ...", 11, MUTED, Typeface.NORMAL);
        radarLiveGateValuesText.setTypeface(Typeface.MONOSPACE);
        radarLiveGateValuesText.setPadding(dp(4), dp(6), dp(4), dp(6));
        radarLiveGateValuesText.setBackground(rounded(TILE, dp(8)));
        panel.addView(radarLiveGateValuesText);

        panel.addView(radarRangeRow("Alarm ab", 1, 0));
        panel.addView(radarRangeRow("Alarm bis", 12, 1));
        panel.addView(radarRangeRow("Alarm-Nachlauf", 5, 2));
        updateRadarRangePreview();
        Button saveRange = button("Alarmbereich übernehmen", BLUE, v -> saveRadarRangeSettings());
        LinearLayout.LayoutParams saveRangeParams = (LinearLayout.LayoutParams) saveRange.getLayoutParams();
        saveRangeParams.width = LinearLayout.LayoutParams.MATCH_PARENT;
        saveRangeParams.weight = 0;
        saveRange.setLayoutParams(saveRangeParams);
        LinearLayout rangeButtonRow = new LinearLayout(this);
        rangeButtonRow.setPadding(0, dp(8), 0, 0);
        rangeButtonRow.addView(saveRange);
        panel.addView(rangeButtonRow);

        TextView calibrationTitle = text("Personenbewegung kalibrieren", 16, TEXT, Typeface.BOLD);
        calibrationTitle.setPadding(0, dp(22), 0, 0);
        panel.addView(calibrationTitle);
        TextView calibrationHelp = valueText(
                "Der Radar unterscheidet keine Identitäten. Die Kalibrierung filtert Grundrauschen und kleine Störungen, damit deutliche menschliche Bewegung zuverlässig auslöst.");
        calibrationHelp.setPadding(0, dp(6), 0, dp(4));
        panel.addView(calibrationHelp);

        radarCalibrationStatusText = valueText("Bereit. Radarbereich fuer 60 Sekunden frei halten.");
        radarCalibrationStatusText.setPadding(0, dp(6), 0, dp(8));
        panel.addView(radarCalibrationStatusText);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, dp(12), 0, 0);
        radarCalibrationActionButton = button("Kalibrierung starten", BLACK,
                v -> onRadarCalibrationAction());
        radarCalibrationCancelButton = button("Abbrechen", RED,
                v -> requestRadarCalibration("cancel"));
        radarCalibrationCancelButton.setEnabled(false);
        buttons.addView(radarCalibrationActionButton);
        buttons.addView(radarCalibrationCancelButton);
        panel.addView(buttons);

        LinearLayout advanced = new LinearLayout(this);
        advanced.setOrientation(LinearLayout.VERTICAL);
        advanced.setVisibility(View.GONE);
        float moveFactor = prefs.getFloat(KEY_RADAR_CAL_MOVE_FACTOR, 0.5f);
        float stillFactor = prefs.getFloat(KEY_RADAR_CAL_STILL_FACTOR, 0.5f);
        advanced.addView(radarCalibrationFactorRow("Bewegungsfilter", moveFactor, true));
        advanced.addView(radarCalibrationFactorRow("Haltefilter", stillFactor, false));

        TextView thresholdTitle = text("Erweiterte Gate-Schwellen", 16, TEXT, Typeface.BOLD);
        thresholdTitle.setPadding(0, dp(18), 0, 0);
        advanced.addView(thresholdTitle);
        advanced.addView(valueText("Trigger und Halten gelten nur fuer das unten "
                + "ausgewählte Gate. Gate-Nummer und Meterbereich werden angezeigt."));
        advanced.addView(radarThresholdGateRow());
        advanced.addView(radarThresholdInputRow("Trigger-Schwelle", true));
        advanced.addView(radarThresholdInputRow("Halte-Schwelle", false));
        Button saveThreshold = button("Gate speichern", BLACK, v -> saveRadarGateThreshold());
        LinearLayout.LayoutParams saveThresholdParams = (LinearLayout.LayoutParams) saveThreshold.getLayoutParams();
        saveThresholdParams.width = LinearLayout.LayoutParams.MATCH_PARENT;
        saveThresholdParams.weight = 0;
        saveThreshold.setLayoutParams(saveThresholdParams);
        LinearLayout thresholdButtonRow = new LinearLayout(this);
        thresholdButtonRow.setPadding(0, dp(8), 0, 0);
        thresholdButtonRow.addView(saveThreshold);
        advanced.addView(thresholdButtonRow);

        Button advancedToggle = button("Erweiterte Einstellungen anzeigen", Color.rgb(75, 85, 99), v -> {
            boolean show = advanced.getVisibility() != View.VISIBLE;
            advanced.setVisibility(show ? View.VISIBLE : View.GONE);
            ((Button) v).setText(show
                    ? "Erweiterte Einstellungen ausblenden"
                    : "Erweiterte Einstellungen anzeigen");
        });
        LinearLayout.LayoutParams advancedToggleParams =
                (LinearLayout.LayoutParams) advancedToggle.getLayoutParams();
        advancedToggleParams.width = LinearLayout.LayoutParams.MATCH_PARENT;
        advancedToggleParams.weight = 0;
        advancedToggle.setLayoutParams(advancedToggleParams);
        LinearLayout advancedToggleRow = new LinearLayout(this);
        advancedToggleRow.setPadding(0, dp(14), 0, 0);
        advancedToggleRow.addView(advancedToggle);
        panel.addView(advancedToggleRow);
        panel.addView(advanced);
        return panel;
    }

    private LinearLayout radarCalibrationFactorRow(String title, float initialValue, boolean move) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(8), 0, 0);

        LinearLayout labelRow = new LinearLayout(this);
        labelRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView titleText = text(title, 13, TEXT, Typeface.NORMAL);
        labelRow.addView(titleText, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView valueText = text(formatRadarCalibrationFactor(initialValue), 13, MUTED, Typeface.NORMAL);
        labelRow.addView(valueText);
        row.addView(labelRow);

        SeekBar slider = new SeekBar(this);
        slider.setMax(50);
        slider.setProgress(Math.max(0, Math.min(50, Math.round(initialValue * 10f))));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                valueText.setText(formatRadarCalibrationFactor(progress / 10f));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        row.addView(slider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(34)));
        if (move) {
            radarCalibrationMoveFactorSeekBar = slider;
            radarCalibrationMoveFactorText = valueText;
        } else {
            radarCalibrationStillFactorSeekBar = slider;
            radarCalibrationStillFactorText = valueText;
        }
        return row;
    }

    private String formatRadarCalibrationFactor(float factor) {
        return String.format(Locale.US, "%.1f", factor);
    }

    private LinearLayout radarRangeRow(String title, int initialValue, int type) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(8), 0, 0);
        LinearLayout labelRow = new LinearLayout(this);
        labelRow.setOrientation(LinearLayout.HORIZONTAL);
        labelRow.addView(text(title, 13, TEXT, Typeface.NORMAL),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView value = text(formatRadarRangeValue(type, initialValue), 13, MUTED, Typeface.NORMAL);
        labelRow.addView(value);
        row.addView(labelRow);

        SeekBar slider = new SeekBar(this);
        slider.setMax(15);
        slider.setProgress(initialValue);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && type == 0 && radarMaximumGateSeekBar != null
                        && progress > radarMaximumGateSeekBar.getProgress()) {
                    radarMaximumGateSeekBar.setProgress(progress);
                } else if (fromUser && type == 1 && radarMinimumGateSeekBar != null
                        && progress < radarMinimumGateSeekBar.getProgress()) {
                    radarMinimumGateSeekBar.setProgress(progress);
                }
                value.setText(formatRadarRangeValue(type, progress));
                updateRadarRangePreview();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        row.addView(slider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(34)));
        if (type == 0) {
            radarMinimumGateSeekBar = slider;
            radarMinimumGateText = value;
        } else if (type == 1) {
            radarMaximumGateSeekBar = slider;
            radarMaximumGateText = value;
        } else {
            radarPresenceDelaySeekBar = slider;
            radarPresenceDelayText = value;
        }
        return row;
    }

    private String formatRadarRangeValue(int type, int value) {
        if (type == 2) {
            return value == 1 ? "1 Sekunde" : value + " Sekunden";
        }
        float distance = type == 1 ? (value + 1) * 0.7f : value * 0.7f;
        return String.format(Locale.GERMANY, "%.1f m", distance);
    }

    private String formatRadarGateSelection(int gate) {
        float startMetres = gate * 0.7f;
        float endMetres = (gate + 1) * 0.7f;
        return String.format(Locale.GERMANY, "Gate %d · %.1f–%.1f m",
                gate, startMetres, endMetres);
    }

    private void updateRadarRangePreview() {
        if (radarMinimumGateSeekBar == null || radarMaximumGateSeekBar == null) {
            return;
        }
        int minimumGate = radarMinimumGateSeekBar.getProgress();
        int maximumGate = radarMaximumGateSeekBar.getProgress();
        float startMetres = minimumGate * 0.7f;
        float endMetres = (maximumGate + 1) * 0.7f;
        if (radarRangeSummaryText != null) {
            radarRangeSummaryText.setText(String.format(Locale.GERMANY,
                    "Alarm bei Bewegung von %.1f bis %.1f m", startMetres, endMetres));
        }
        if (radarRangeView != null) {
            radarRangeView.setRange(minimumGate, maximumGate);
        }
    }

    private void saveRadarRangeSettings() {
        if (radarCalibrationActive) {
            setConnection("Radarbereich erst nach der Kalibrierung speichern.", ERROR);
            return;
        }
        int minimumGate = radarMinimumGateSeekBar == null ? 1 : radarMinimumGateSeekBar.getProgress();
        int maximumGate = radarMaximumGateSeekBar == null ? 12 : radarMaximumGateSeekBar.getProgress();
        int delay = radarPresenceDelaySeekBar == null ? 5 : radarPresenceDelaySeekBar.getProgress();
        if (minimumGate > maximumGate) {
            setConnection("Minimum-Gate darf nicht groesser als Maximum-Gate sein.", ERROR);
            return;
        }
        ioExecutor.execute(() -> {
            try {
                getString(httpUrl("/radar/range?min_gate=" + minimumGate
                        + "&max_gate=" + maximumGate + "&delay=" + delay), 8000);
                mainHandler.post(() -> {
                    setConnection(String.format(Locale.GERMANY,
                            "Alarmbereich %.1f bis %.1f m gespeichert.",
                            minimumGate * 0.7f, (maximumGate + 1) * 0.7f), GREEN);
                    refreshSensorsOnly();
                });
            } catch (Exception ex) {
                mainHandler.post(() -> setConnection(
                        "Radarbereich fehlgeschlagen: " + cleanError(ex), ERROR));
            }
        });
    }

    private LinearLayout radarThresholdGateRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(8), 0, 0);
        LinearLayout labelRow = new LinearLayout(this);
        labelRow.setOrientation(LinearLayout.HORIZONTAL);
        labelRow.addView(text("Bearbeitetes Gate", 13, TEXT, Typeface.NORMAL),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        radarManualThresholdGateText = text(formatRadarGateSelection(0), 13, MUTED, Typeface.NORMAL);
        labelRow.addView(radarManualThresholdGateText);
        row.addView(labelRow);
        radarManualThresholdGateSeekBar = new SeekBar(this);
        radarManualThresholdGateSeekBar.setMax(15);
        radarManualThresholdGateSeekBar.setProgress(0);
        radarManualThresholdGateSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                radarSelectedThresholdGate = progress;
                radarManualThresholdGateText.setText(formatRadarGateSelection(progress));
                updateRadarThresholdInputs();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        row.addView(radarManualThresholdGateSeekBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(34)));
        return row;
    }

    private LinearLayout radarThresholdInputRow(String title, boolean trigger) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(8), 0, 0);
        row.addView(text(title, 13, TEXT, Typeface.NORMAL));
        EditText field = input("0 bis 65535");
        field.setInputType(InputType.TYPE_CLASS_NUMBER);
        field.setText("0");
        row.addView(field);
        if (trigger) {
            radarTriggerThresholdInput = field;
        } else {
            radarMaintainThresholdInput = field;
        }
        return row;
    }

    private void updateRadarThresholdInputs() {
        if (radarTriggerThresholdInput != null && !radarTriggerThresholdInput.hasFocus()) {
            radarTriggerThresholdInput.setText(String.valueOf(
                    radarTriggerThresholds[radarSelectedThresholdGate]));
        }
        if (radarMaintainThresholdInput != null && !radarMaintainThresholdInput.hasFocus()) {
            radarMaintainThresholdInput.setText(String.valueOf(
                    radarMaintainThresholds[radarSelectedThresholdGate]));
        }
    }

    private void saveRadarGateThreshold() {
        if (radarCalibrationActive) {
            setConnection("Gate-Schwellen erst nach der Kalibrierung speichern.", ERROR);
            return;
        }
        try {
            int trigger = Integer.parseInt(radarTriggerThresholdInput.getText().toString().trim());
            int maintain = Integer.parseInt(radarMaintainThresholdInput.getText().toString().trim());
            if (trigger < 0 || trigger > 65535 || maintain < 0 || maintain > 65535) {
                throw new NumberFormatException();
            }
            int gate = radarSelectedThresholdGate;
            ioExecutor.execute(() -> {
                try {
                    getString(httpUrl("/radar/threshold?gate=" + gate + "&trigger=" + trigger
                            + "&maintain=" + maintain), 8000);
                    mainHandler.post(() -> {
                        setConnection(formatRadarGateSelection(gate)
                                + ": Schwellen gespeichert.", GREEN);
                        refreshSensorsOnly();
                    });
                } catch (Exception ex) {
                    mainHandler.post(() -> setConnection(
                            "Gate-Schwellen fehlgeschlagen: " + cleanError(ex), ERROR));
                }
            });
        } catch (Exception ex) {
            setConnection("Schwellen muessen zwischen 0 und 65535 liegen.", ERROR);
        }
    }

    private void onRadarCalibrationAction() {
        if (radarCalibrationActive) {
            if (radarCalibrationReady) {
                requestRadarCalibration("apply");
            }
            return;
        }
        requestRadarCalibration("start");
    }

    private void requestRadarCalibration(String action) {
        final float moveFactor = radarCalibrationMoveFactorSeekBar == null
                ? 0.5f : radarCalibrationMoveFactorSeekBar.getProgress() / 10f;
        final float stillFactor = radarCalibrationStillFactorSeekBar == null
                ? 0.5f : radarCalibrationStillFactorSeekBar.getProgress() / 10f;
        ioExecutor.execute(() -> {
            try {
                String path = "/radar/calibration?action=" + action;
                if ("start".equals(action)) {
                    path += String.format(Locale.US, "&move_factor=%.1f&still_factor=%.1f",
                            moveFactor, stillFactor);
                }
                getString(httpUrl(path), 8000);
                if ("start".equals(action)) {
                    prefs.edit()
                            .putFloat(KEY_RADAR_CAL_MOVE_FACTOR, moveFactor)
                            .putFloat(KEY_RADAR_CAL_STILL_FACTOR, stillFactor)
                            .apply();
                }
                mainHandler.post(() -> {
                    setConnection("Radar-Kalibrierung aktualisiert.", GREEN);
                    refreshSensorsOnly();
                });
            } catch (Exception ex) {
                mainHandler.post(() -> setConnection(
                        "Radar-Kalibrierung fehlgeschlagen: " + cleanError(ex), ERROR));
            }
        });
    }

    private void updateRadarCalibrationUi(JSONObject json) {
        if (radarCalibrationStatusText == null || radarCalibrationActionButton == null
                || radarCalibrationCancelButton == null) {
            return;
        }
        radarCalibrationActive = json.optBoolean("radar_calibration_active", false);
        radarCalibrationReady = json.optBoolean("radar_calibration_ready", false);
        boolean applied = json.optBoolean("radar_calibration_applied", false);
        long elapsedMs = json.optLong("radar_calibration_elapsed_ms", 0);
        long samples = json.optLong("radar_calibration_samples", 0);
        JSONArray peaks = json.optJSONArray("radar_calibration_peaks");
        String peak = formatRadarGateSummary(peaks);

        boolean active = radarCalibrationActive;
        if (active) {
            long seconds = Math.min(60, elapsedMs / 1000);
            radarCalibrationStatusText.setText(String.format(Locale.GERMANY,
                    "Kalibrierung: %d / 60 s, %d Samples, %s", seconds, samples, peak));
            radarCalibrationActionButton.setText(radarCalibrationReady
                    ? "Schwellen speichern" : "Sammelt...");
            radarCalibrationActionButton.setEnabled(radarCalibrationReady);
            radarCalibrationCancelButton.setEnabled(true);
        } else {
            radarCalibrationStatusText.setText(applied
                    ? "Schwellen im Radar gespeichert."
                    : "Bereit. Radarbereich fuer 60 Sekunden frei halten.");
            radarCalibrationActionButton.setText("Kalibrierung starten");
            radarCalibrationActionButton.setEnabled(true);
            radarCalibrationCancelButton.setEnabled(false);
        }
        if (radarCalibrationMoveFactorSeekBar != null) {
            radarCalibrationMoveFactorSeekBar.setEnabled(!active);
        }
        if (radarCalibrationStillFactorSeekBar != null) {
            radarCalibrationStillFactorSeekBar.setEnabled(!active);
        }
        updateRadarRangeUi(json);
    }

    private void updateRadarRangeUi(JSONObject json) {
        int minimumGate = json.optInt("radar_minimum_gate", 1);
        int maximumGate = json.optInt("radar_maximum_gate", 12);
        int delay = json.optInt("radar_presence_delay", 5);
        if (radarMinimumGateSeekBar != null && !radarMinimumGateSeekBar.isPressed()) {
            radarMinimumGateSeekBar.setProgress(minimumGate);
        }
        if (radarMaximumGateSeekBar != null && !radarMaximumGateSeekBar.isPressed()) {
            radarMaximumGateSeekBar.setProgress(maximumGate);
        }
        if (radarPresenceDelaySeekBar != null && !radarPresenceDelaySeekBar.isPressed()) {
            radarPresenceDelaySeekBar.setProgress(delay);
        }
        if (radarMinimumGateText != null) {
            radarMinimumGateText.setText(formatRadarRangeValue(0, minimumGate));
        }
        if (radarMaximumGateText != null) {
            radarMaximumGateText.setText(formatRadarRangeValue(1, maximumGate));
        }
        if (radarPresenceDelayText != null) {
            radarPresenceDelayText.setText(formatRadarRangeValue(2, delay));
        }
        updateRadarRangePreview();
        if (radarRangeView != null) {
            radarRangeView.setEnergy(json.optJSONArray("radar_gate_energy"));
        }
        JSONArray triggerThresholds = json.optJSONArray("radar_trigger_threshold");
        JSONArray maintainThresholds = json.optJSONArray("radar_maintain_threshold");
        for (int i = 0; i < 16; i++) {
            radarTriggerThresholds[i] = triggerThresholds == null ? 0 : triggerThresholds.optInt(i, 0);
            radarMaintainThresholds[i] = maintainThresholds == null ? 0 : maintainThresholds.optInt(i, 0);
        }
        updateRadarLiveGateValues(json.optJSONArray("radar_gate_energy"),
                minimumGate, maximumGate);
        updateRadarThresholdInputs();
    }

    private void updateRadarLiveGateValues(JSONArray energy, int minimumGate, int maximumGate) {
        if (radarLiveGateValuesText == null) {
            return;
        }
        StringBuilder values = new StringBuilder("* = aktiver Alarmbereich\n");
        values.append("Gate  Bereich      Energie  Trigger/Halten\n");
        for (int gate = 0; gate < 16; gate++) {
            boolean active = gate >= minimumGate && gate <= maximumGate;
            float startMetres = gate * 0.7f;
            float endMetres = (gate + 1) * 0.7f;
            int currentEnergy = energy == null ? 0 : energy.optInt(gate, 0);
            values.append(String.format(Locale.GERMANY,
                    "%s G%-2d  %4.1f-%4.1fm  %6d  %5d/%-5d\n",
                    active ? "*" : " ", gate, startMetres, endMetres,
                    currentEnergy, radarTriggerThresholds[gate],
                    radarMaintainThresholds[gate]));
        }
        radarLiveGateValuesText.setText(values.toString().trim());
    }

    private Spinner resolutionSpinner() {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, CAMERA_FRAMESIZE_LABELS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setMinimumHeight(dp(42));
        spinner.setPadding(dp(10), 0, dp(10), 0);
        spinner.setBackground(rounded(TILE, dp(DEFAULT_RADIUS_DP)));
        spinner.setSelection(resolutionIndexForValue(
                prefs.getInt(KEY_CAMERA_FRAMESIZE, DEFAULT_CAMERA_FRAMESIZE)), false);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int framesize = CAMERA_FRAMESIZE_VALUES[position];
                if (!suppressResolutionEvents
                        && framesize != prefs.getInt(KEY_CAMERA_FRAMESIZE, DEFAULT_CAMERA_FRAMESIZE)) {
                    setCameraResolution(framesize);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        return spinner;
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

    private LinearLayout radarPhoneAlarmRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, 0);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text("Radar-Alarm am Handy", 14, TEXT, Typeface.BOLD));
        radarPhoneAlarmText = text("Benachrichtigung und Buzzer bei Radar", 12, MUTED, Typeface.NORMAL);
        labels.addView(radarPhoneAlarmText);
        row.addView(labels, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        radarPhoneAlarmSwitch = new Switch(this);
        radarPhoneAlarmSwitch.setShowText(false);
        radarPhoneAlarmSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!suppressSwitchEvents) {
                setRadarPhoneAlarmEnabled(isChecked);
            }
        });
        applyTapAnimation(radarPhoneAlarmSwitch);
        row.addView(radarPhoneAlarmSwitch);
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
                showLiveGestureIcon(R.drawable.ic_pause_24);
                stopStream();
            } else {
                if (startStream()) {
                    showLiveGestureIcon(R.drawable.ic_play_24);
                }
            }
        });

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

        liveGestureIcon = new ImageView(this);
        liveGestureIcon.setVisibility(View.GONE);
        liveGestureIcon.setAlpha(0f);
        liveGestureIcon.setColorFilter(Color.WHITE);
        liveGestureIcon.setScaleType(ImageView.ScaleType.CENTER);
        liveGestureIcon.setBackground(oval(Color.argb(145, 0, 0, 0)));
        liveGestureIcon.setPadding(dp(18), dp(18), dp(18), dp(18));
        FrameLayout.LayoutParams gestureParams = new FrameLayout.LayoutParams(
                dp(74), dp(74), Gravity.CENTER);
        frame.addView(liveGestureIcon, gestureParams);
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
        // The ESP must sound locally for every radar event whenever the
        // system is armed. This setting is persisted by the ESP, so it also
        // continues to work if the phone app is no longer in the foreground.
        setEspLocalRadarBuzzer(active);
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

    private void showLiveGestureIcon(int iconRes) {
        if (liveGestureIcon == null) {
            return;
        }
        int animationId = ++liveGestureAnimationId;
        liveGestureIcon.animate().cancel();
        liveGestureIcon.setImageResource(iconRes);
        liveGestureIcon.setVisibility(View.VISIBLE);
        liveGestureIcon.setScaleX(0.88f);
        liveGestureIcon.setScaleY(0.88f);
        liveGestureIcon.setAlpha(0f);
        liveGestureIcon.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(120)
                .withEndAction(() -> mainHandler.postDelayed(() -> {
                    if (liveGestureAnimationId != animationId || liveGestureIcon == null) {
                        return;
                    }
                    liveGestureIcon.animate()
                            .alpha(0f)
                            .scaleX(1.08f)
                            .scaleY(1.08f)
                            .setDuration(220)
                            .withEndAction(() -> {
                                if (liveGestureAnimationId == animationId && liveGestureIcon != null) {
                                    liveGestureIcon.setVisibility(View.GONE);
                                }
                            })
                            .start();
                }, 260))
                .start();
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
        connectionProfile = prefs.getString(KEY_CONNECTION_PROFILE, DEFAULT_CONNECTION_PROFILE);
        phoneStreamEnabled = prefs.getBoolean(KEY_PHONE_STREAM_ENABLED, true);
        if (PROFILE_ANDROID_HOTSPOT.equals(connectionProfile) && !phoneStreamEnabled) {
            phoneStreamEnabled = true;
            prefs.edit().putBoolean(KEY_PHONE_STREAM_ENABLED, true).apply();
        }
        radarPhoneAlarmEnabled = prefs.getBoolean(KEY_RADAR_PHONE_ALARM_ENABLED, false);
        String fallbackHost = PROFILE_ANDROID_HOTSPOT.equals(connectionProfile)
                ? defaultAndroidHotspotEspHost() : DEFAULT_ESP_HOST;
        String savedHost = prefs.getString(hostKeyForProfile(connectionProfile),
                prefs.getString(KEY_HOST, fallbackHost));
        if (savedHost == null || savedHost.trim().isEmpty()) {
            savedHost = fallbackHost;
        }
        prefs.edit()
                .putString(KEY_HOST, savedHost)
                .putString(hostKeyForProfile(connectionProfile), savedHost)
                .apply();
        if (hostInput != null) {
            hostInput.setText(savedHost);
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
        updateResolutionSelection(prefs.getInt(KEY_CAMERA_FRAMESIZE, DEFAULT_CAMERA_FRAMESIZE));
        updateConnectionProfileUi();
        if (phoneStreamSwitch != null) {
            suppressSwitchEvents = true;
            phoneStreamSwitch.setChecked(phoneStreamEnabled);
            suppressSwitchEvents = false;
        }
        updateRadarPhoneAlarmUi();
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
        setEspLocalRadarBuzzer(enabled);
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

    private int resolutionIndexForValue(int framesize) {
        for (int i = 0; i < CAMERA_FRAMESIZE_VALUES.length; i++) {
            if (CAMERA_FRAMESIZE_VALUES[i] == framesize) {
                return i;
            }
        }
        return resolutionIndexForValue(DEFAULT_CAMERA_FRAMESIZE);
    }

    private int selectedResolutionValue() {
        if (resolutionSpinner == null) {
            return prefs.getInt(KEY_CAMERA_FRAMESIZE, DEFAULT_CAMERA_FRAMESIZE);
        }
        int position = resolutionSpinner.getSelectedItemPosition();
        if (position < 0 || position >= CAMERA_FRAMESIZE_VALUES.length) {
            return DEFAULT_CAMERA_FRAMESIZE;
        }
        return CAMERA_FRAMESIZE_VALUES[position];
    }

    private String resolutionLabelForValue(int framesize) {
        return CAMERA_FRAMESIZE_LABELS[resolutionIndexForValue(framesize)];
    }

    private void updateResolutionSelection(int framesize) {
        if (resolutionSpinner == null) {
            return;
        }
        suppressResolutionEvents = true;
        resolutionSpinner.setSelection(resolutionIndexForValue(framesize), false);
        suppressResolutionEvents = false;
    }

    private void saveSettings() {
        prefs.edit()
                .putString(KEY_HOST, host())
                .putString(hostKeyForProfile(connectionProfile), host())
                .putInt(KEY_HTTP_PORT, parsePort(httpPortInput, 80))
                .putInt(KEY_STREAM_PORT, parsePort(streamPortInput, 81))
                .putString(KEY_MONITOR_HOST, monitorHost())
                .putInt(KEY_MONITOR_PORT, parsePort(monitorPortInput, 8765))
                .putInt(KEY_CAMERA_FRAMESIZE, selectedResolutionValue())
                .putString(KEY_CONNECTION_PROFILE, connectionProfile)
                .putBoolean(KEY_PHONE_STREAM_ENABLED, phoneStreamEnabled)
                .putBoolean(KEY_RADAR_PHONE_ALARM_ENABLED, radarPhoneAlarmEnabled)
                .apply();
        setConnection("Einstellungen gespeichert.", GREEN);
        refreshAll();
    }

    private void applyConnectionProfile(String profile, boolean saveNow) {
        String previousEspHost = host();
        int previousEspPort = currentPort(KEY_HTTP_PORT, httpPortInput, 80);
        connectionProfile = profile;
        String rememberedHost = prefs.getString(hostKeyForProfile(profile), "");
        if (PROFILE_ANDROID_HOTSPOT.equals(profile)) {
            if (hostInput != null) {
                hostInput.setText(rememberedHost.trim().isEmpty()
                        ? defaultAndroidHotspotEspHost() : rememberedHost);
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
            phoneStreamEnabled = true;
            if (phoneStreamSwitch != null) {
                suppressSwitchEvents = true;
                phoneStreamSwitch.setChecked(true);
                suppressSwitchEvents = false;
            }
            setLiveState(false, "Hotspot-Modus: Stream auf diesem Handy aktiv.");
        } else {
            if (hostInput != null) {
                hostInput.setText(rememberedHost.trim().isEmpty()
                        ? DEFAULT_ESP_HOST : rememberedHost);
            }
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
        stopStream(false);
        requestEspWifiProfile(previousEspHost, previousEspPort, profile);
    }

    private void updateConnectionProfileUi() {
        if (connectionProfileText == null) {
            return;
        }
        if (PROFILE_ANDROID_HOTSPOT.equals(connectionProfile)) {
            connectionProfileText.setText("Modus: Handy-Hotspot. Die ESP-Adresse wird automatisch gesucht und gespeichert.");
            connectionProfileText.setTextColor(AMBER);
        } else {
            connectionProfileText.setText("Modus: Normales WLAN. Die ESP-Adresse wird im aktuellen WLAN automatisch gesucht und gespeichert.");
            connectionProfileText.setTextColor(MUTED);
        }
    }

    private void requestEspWifiProfile(String currentEspHost, int currentEspPort,
                                       String requestedProfile) {
        if (currentEspHost == null || currentEspHost.trim().isEmpty()) {
            startEspScan(true);
            return;
        }
        String targetMode = PROFILE_ANDROID_HOTSPOT.equals(requestedProfile)
                ? "hotspot" : "normal";
        ioExecutor.execute(() -> {
            try {
                String url = "http://" + currentEspHost.trim() + ":" + currentEspPort
                        + "/wifi/profile?mode=" + targetMode;
                getString(url, 3500);
                mainHandler.post(() -> setConnection(
                        "ESP wechselt zu " + ("hotspot".equals(targetMode)
                                ? "Handy-Hotspot" : "normalem WLAN") + "...", AMBER));
            } catch (Exception ex) {
                mainHandler.post(() -> setConnection(
                        "ESP-Wechsel nicht direkt erreichbar. Suche im Zielnetz...", AMBER));
            } finally {
                // The ESP returns the response before it disconnects, then
                // obtains a fresh DHCP address in the selected network.
                mainHandler.postDelayed(() -> startEspScan(true), 6500);
            }
        });
    }

    private String hostKeyForProfile(String profile) {
        return PROFILE_ANDROID_HOTSPOT.equals(profile)
                ? KEY_HOTSPOT_HOST : KEY_NORMAL_WIFI_HOST;
    }

    private final ConnectivityManager.NetworkCallback hotspotLocalNetworkCallback =
            new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    hotspotLocalNetwork = network;
                    Log.i(TAG, "Android hotspot local network available: " + network);
                    mainHandler.post(() -> {
                        if (PROFILE_ANDROID_HOTSPOT.equals(connectionProfile)) {
                            refreshAll();
                            if (!streamRunning && phoneStreamEnabled && appInForeground) {
                                startStream();
                            }
                        }
                    });
                }

                @Override
                public void onLost(Network network) {
                    if (network.equals(hotspotLocalNetwork)) {
                        hotspotLocalNetwork = null;
                        Log.i(TAG, "Android hotspot local network lost");
                    }
                }
            };

    private void requestHotspotLocalNetwork() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM
                || hotspotLocalNetworkRequested) {
            return;
        }
        ConnectivityManager manager =
                (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (manager == null) {
            return;
        }
        try {
            NetworkRequest request = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_LOCAL_NETWORK)
                    .build();
            manager.requestNetwork(request, hotspotLocalNetworkCallback);
            hotspotLocalNetworkRequested = true;
        } catch (Exception ex) {
            Log.w(TAG, "Unable to request Android hotspot local network", ex);
        }
    }

    private void releaseHotspotLocalNetwork() {
        if (!hotspotLocalNetworkRequested) {
            return;
        }
        ConnectivityManager manager =
                (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (manager != null) {
            try {
                manager.unregisterNetworkCallback(hotspotLocalNetworkCallback);
            } catch (Exception ignored) {
                // Callback can already be gone while Android shuts down the process.
            }
        }
        hotspotLocalNetworkRequested = false;
        hotspotLocalNetwork = null;
    }

    private void startPolling() {
        poller = Executors.newSingleThreadScheduledExecutor();
        poller.scheduleAtFixedRate(this::refreshSensorsOnly, 0,
                SENSOR_POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
        poller.scheduleAtFixedRate(this::refreshStatusOnly, 0, STATUS_POLL_SECONDS, TimeUnit.SECONDS);
        poller.scheduleAtFixedRate(this::refreshMonitorOnly, 0, MONITOR_POLL_SECONDS, TimeUnit.SECONDS);
    }

    private void refreshAll() {
        refreshSensorsOnly();
        refreshStatusOnly();
        refreshMonitorOnly();
        captureFrame();
    }

    private void scanForEsp() {
        Toast.makeText(this, "ESP32-Suche gestartet", Toast.LENGTH_SHORT).show();
        startEspScan(true);
    }

    private void startEspScan(boolean automatic) {
        if (automatic && !espAutoScanInFlight.compareAndSet(false, true)) {
            return;
        }
        if (!automatic) {
            espAutoScanInFlight.set(true);
        }
        setConnection(automatic ? "Suche ESP32 automatisch..."
                : "Suche ESP32...", AMBER);
        ioExecutor.execute(() -> {
            try {
                LinkedHashSet<String> rememberedHosts = new LinkedHashSet<>();
                rememberedHosts.add(host());
                rememberedHosts.add(prefs.getString(KEY_HOST, ""));
                // The ESP may switch to the stable fallback network after a
                // weak normal-WLAN link drops. Always test both remembered
                // addresses before scanning a whole subnet.
                rememberedHosts.add(prefs.getString(KEY_NORMAL_WIFI_HOST, ""));
                rememberedHosts.add(prefs.getString(KEY_HOTSPOT_HOST, ""));
                rememberedHosts.add(prefs.getString(hostKeyForProfile(connectionProfile), ""));
                for (String remembered : rememberedHosts) {
                    if (remembered == null || remembered.trim().isEmpty()) {
                        continue;
                    }
                    String candidate = remembered.trim();
                    if (isEspCandidate(candidate)) {
                        mainHandler.post(() -> applyFoundEspHost(candidate, true));
                        return;
                    }
                }
                List<String> hotspotClients = hotspotClientIpsFromArp();
                Log.i(TAG, "ESP scan ARP clients=" + hotspotClients);
                for (String candidate : hotspotClients) {
                    if (isEspCandidate(candidate)) {
                        Log.i(TAG, "ESP found via ARP candidate=" + candidate);
                        mainHandler.post(() -> applyFoundEspHost(candidate, automatic));
                        return;
                    }
                }

                LinkedHashSet<String> scanPrefixes = new LinkedHashSet<>();
                for (String prefix : localIpv4Prefixes()) {
                    boolean hotspotPrefix = isAndroidHotspotPrefix(prefix);
                    if (PROFILE_ANDROID_HOTSPOT.equals(connectionProfile)
                            ? hotspotPrefix : !hotspotPrefix) {
                        scanPrefixes.add(prefix);
                    }
                }
                if (PROFILE_ANDROID_HOTSPOT.equals(connectionProfile)) {
                    scanPrefixes.addAll(Arrays.asList(ANDROID_HOTSPOT_PREFIXES));
                } else if (!automatic) {
                    scanPrefixes.addAll(Arrays.asList(
                            "172.20.10.",
                            "172.16.0.",
                            "172.16.1.",
                            "192.168.137.",
                            "192.168.178.",
                            "192.168.1.",
                            "192.168.4."));
                }
                List<String> prefixes = new ArrayList<>(scanPrefixes);
                Log.i(TAG, "ESP scan prefixes=" + prefixes);
                mainHandler.post(() -> setConnection(String.format(Locale.GERMANY,
                        "Suche ESP32: %d Hotspot-Geraete, %d Netze...",
                        hotspotClients.size(), prefixes.size()), AMBER));
                AtomicBoolean found = new AtomicBoolean(false);
                ExecutorService scanner = Executors.newFixedThreadPool(32);
                for (String prefix : prefixes) {
                    for (int i = 2; i <= 254; i++) {
                        if (found.get()) {
                            break;
                        }
                        String candidate = prefix + i;
                        scanner.execute(() -> {
                            if (!found.get() && isEspCandidate(candidate, 800)) {
                                if (found.compareAndSet(false, true)) {
                                    Log.i(TAG, "ESP found via scan candidate=" + candidate);
                                    mainHandler.post(() -> applyFoundEspHost(candidate, automatic));
                                }
                            }
                        });
                    }
                }
                scanner.shutdown();
                try {
                    if (!scanner.awaitTermination(12, TimeUnit.SECONDS)) {
                        scanner.shutdownNow();
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                if (!found.get()) {
                    Log.w(TAG, "ESP scan finished without match");
                    mainHandler.post(() -> setConnection(
                            automatic
                                    ? "ESP32 nicht automatisch gefunden. Warte auf naechsten Versuch."
                                    : "Kein ESP32 gefunden. Suche wird bei der nächsten Verbindung erneut versucht.",
                            ERROR));
                }
            } finally {
                espAutoScanInFlight.set(false);
            }
        });
    }

    private void applyFoundEspHost(String candidate, boolean automatic) {
        String previousHost = host();
        boolean hostChanged = !candidate.equals(previousHost);
        String detectedProfile = profileForEspHost(candidate);
        boolean profileChanged = !detectedProfile.equals(connectionProfile);
        connectionProfile = detectedProfile;
        if (hostInput != null) {
            hostInput.setText(candidate);
        }
        prefs.edit()
                .putString(KEY_HOST, candidate)
                .putString(KEY_CONNECTION_PROFILE, connectionProfile)
                .putString(hostKeyForProfile(connectionProfile), candidate)
                .apply();
        if (profileChanged) {
            updateConnectionProfileUi();
        }
        setConnection((automatic ? "ESP32 automatisch gefunden: http://"
                : "ESP32 gefunden: http://") + candidate, GREEN);
        if (hostChanged && streamRunning) {
            // The former connection may be blocked against the old WLAN host.
            // Close it explicitly so the new MJPEG request uses the discovered IP.
            stopStream(false);
            if (phoneStreamEnabled && appInForeground) {
                mainHandler.postDelayed(this::startStream, 150);
            }
        }
        refreshAll();
    }

    private String profileForEspHost(String candidate) {
        if (candidate != null) {
            for (String prefix : ANDROID_HOTSPOT_PREFIXES) {
                if (candidate.startsWith(prefix)) {
                    return PROFILE_ANDROID_HOTSPOT;
                }
            }
        }
        return PROFILE_NORMAL;
    }

    private void triggerEspAutoScan() {
        long nowMs = System.currentTimeMillis();
        if (nowMs - lastEspAutoScanAtMs < 5000) {
            return;
        }
        lastEspAutoScanAtMs = nowMs;
        startEspScan(true);
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

    private boolean isAndroidHotspotActive() {
        for (String prefix : localIpv4Prefixes()) {
            if (isAndroidHotspotPrefix(prefix)) {
                return true;
            }
        }
        return false;
    }

    private boolean isCurrentAndroidHotspotHost(String host) {
        for (String prefix : localIpv4Prefixes()) {
            if (isAndroidHotspotPrefix(prefix) && host.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAndroidHotspotPrefix(String prefix) {
        for (String knownPrefix : ANDROID_HOTSPOT_PREFIXES) {
            if (knownPrefix.equals(prefix)) {
                return true;
            }
        }
        return false;
    }

    private String defaultAndroidHotspotEspHost() {
        for (String prefix : localIpv4Prefixes()) {
            if (isAndroidHotspotPrefix(prefix)) {
                // DHCP currently leases .222 to this ESP32. Discovery remains
                // available for devices that receive a different lease.
                return prefix + "222";
            }
        }
        return DEFAULT_ANDROID_HOTSPOT_ESP_HOST;
    }

    private boolean isEspCandidate(String host) {
        return isEspCandidate(host, 1800);
    }

    private boolean isEspCandidate(String host, int timeoutMs) {
        try {
            String response = getString("http://" + host + ":" + currentPort(KEY_HTTP_PORT, httpPortInput, 80)
                    + "/status", timeoutMs);
            boolean esp = response.contains("framesize") || response.contains("quality");
            Log.i(TAG, "ESP probe " + host + " matched=" + esp);
            return esp;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void refreshSensorsOnly() {
        if (!sensorRefreshInFlight.compareAndSet(false, true)) {
            return;
        }
        ioExecutor.execute(() -> {
            try {
                JSONObject json = getJsonWithRetry(httpUrl("/sensors"), 2, 5500);
                sensorFailureCount = 0;
                boolean radar = json.optBoolean("radar_active", false);
                boolean daylight = json.optBoolean("daylight", false);
                int light = json.optInt("light_raw", 0);
                int threshold = json.optInt("light_day_threshold", 0);
                int radarCount = json.optInt("radar_motion_count", 0);
                int darkCount = json.optInt("dark_alarm_count", 0);
                boolean buzzer = json.optBoolean("buzzer_active", false);
                boolean buzzerAlarmEnabled = json.optBoolean("buzzer_alarm_enabled", true);
                boolean radarEnergyPresent = json.optBoolean("radar_energy_present", false);
                int radarEnergyDistanceCm = json.optInt("radar_energy_distance_cm", -1);
                int radarEnergyFrames = json.optInt("radar_energy_frames", 0);
                int radarUartRange = json.optInt("radar_uart_range", -1);
                JSONArray gateEnergy = json.optJSONArray("radar_gate_energy");
                boolean hasEnergyFrames = radarEnergyFrames > 0;
                int radarDistanceCm = hasEnergyFrames ? radarEnergyDistanceCm : radarUartRange;
                String radarMode = hasEnergyFrames ? "Energy" : "Simple";
                String radarGateSummary = hasEnergyFrames
                        ? formatRadarGateSummary(gateEnergy)
                        : "Gate max: keine Energy-Frames";
                String radarGateLine = hasEnergyFrames
                        ? formatRadarGateLine(gateEnergy)
                        : "Gates: Simple-Mode liefert nur Distanz";
                boolean espLocalRadarBuzzer =
                        json.optBoolean("local_radar_buzzer_enabled", radarPhoneAlarmEnabled);
                handleRadarPhoneAlarm(radar, radarCount);

                mainHandler.post(() -> {
                    setConnection("ESP32 erreichbar: " + httpUrl(""), GREEN);
                    updateRadarCalibrationUi(json);
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
                                "Radar: %s (%d), Modus: %s, Distanz: %s, %s, Frames: %d\n%s",
                                radar ? "Bewegung" : "ruhig",
                                radarCount,
                                radarMode,
                                radarDistanceCm >= 0
                                        ? radarDistanceCm + " cm"
                                        : "unbekannt",
                                radarGateSummary,
                                radarEnergyFrames,
                                radarGateLine));
                        radarText.setTextColor((radar || radarEnergyPresent) ? RED : MUTED);
                    }
                    if (lightText != null) {
                        lightText.setText(String.format(Locale.GERMANY,
                                "Licht: %d / %d (%s)", light, threshold, daylight ? "hell" : "dunkel"));
                        lightText.setTextColor(daylight ? MUTED : AMBER);
                    }
                    if (alarmText != null) {
                        alarmText.setText(String.format(Locale.GERMANY,
                            "Buzzer bei Alarm: %s%s, Handy-Alarm: %s, ESP-lokal: %s, Nachtalarme: %d",
                            buzzerAlarmEnabled ? "ein" : "aus",
                            buzzer ? " (ertönt)" : "",
                            radarPhoneAlarmEnabled ? "ein" : "aus",
                            espLocalRadarBuzzer ? "ein" : "aus",
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
                sensorFailureCount++;
                // A changed DHCP address or WLAN profile should be corrected
                // before the user sees three long failed polls.
                triggerEspAutoScan();
                mainHandler.post(() -> {
                    setConnection(sensorFailureCount >= 3
                            ? "Keine Verbindung zu /sensors: " + cleanError(ex)
                            : "Kurzzeitiger Funk-Aussetzer bei /sensors (" + sensorFailureCount + "/3)",
                            sensorFailureCount >= 3 ? ERROR : AMBER);
                    if (sensorFailureCount >= 3 && espStatusTileText != null) {
                        espStatusTileText.setText("OFFLINE");
                        espStatusTileText.setTextColor(ERROR);
                    }
                    if (sensorFailureCount >= 3 && radarTileText != null) {
                        radarTileText.setText("unbekannt");
                        radarTileText.setTextColor(MUTED);
                    }
                    if (sensorFailureCount >= 3 && lightTileText != null) {
                        lightTileText.setText("unbekannt");
                        lightTileText.setTextColor(MUTED);
                    }
                    if (sensorFailureCount >= 3 && radarText != null) {
                        radarText.setText("Radar: unbekannt");
                    }
                    if (sensorFailureCount >= 3 && lightText != null) {
                        lightText.setText("Licht: unbekannt");
                    }
                    if (sensorFailureCount >= 3 && alarmText != null) {
                        alarmText.setText("Buzzer: unbekannt");
                    }
                });
            } finally {
                sensorRefreshInFlight.set(false);
            }
        });
    }

    private String formatRadarGateSummary(JSONArray gates) {
        if (gates == null || gates.length() == 0) {
            return "Gate max: unbekannt";
        }
        int maxGate = 0;
        int maxEnergy = gates.optInt(0, 0);
        for (int i = 1; i < gates.length(); i++) {
            int value = gates.optInt(i, 0);
            if (value > maxEnergy) {
                maxEnergy = value;
                maxGate = i;
            }
        }
        return String.format(Locale.GERMANY, "Gate max: G%d=%d", maxGate, maxEnergy);
    }

    private String formatRadarGateLine(JSONArray gates) {
        if (gates == null || gates.length() == 0) {
            return "Gates: keine Rohwerte";
        }
        StringBuilder builder = new StringBuilder("Gates:");
        for (int i = 0; i < gates.length(); i++) {
            builder.append(' ')
                    .append(i)
                    .append('=')
                    .append(gates.optInt(i, 0));
        }
        return builder.toString();
    }

    private void refreshStatusOnly() {
        ioExecutor.execute(() -> {
            try {
                JSONObject json = getJsonWithRetry(httpUrl("/status"), 2, 5500);
                statusFailureCount = 0;
                String ssid = json.optString("wifi_ssid", "unbekannt");
                String ip = json.optString("wifi_ip", host());
                int rssi = json.optInt("wifi_rssi", 0);
                int framesize = json.optInt("framesize", DEFAULT_CAMERA_FRAMESIZE);
                String status = String.format(Locale.GERMANY,
                        "Kamera: %s, q=%d, b=%d | WLAN: %s, IP: %s, RSSI: %d dBm",
                        resolutionLabelForValue(framesize),
                        json.optInt("quality", -1),
                        json.optInt("brightness", 0),
                        ssid,
                        ip,
                        rssi);
                mainHandler.post(() -> {
                    prefs.edit().putInt(KEY_CAMERA_FRAMESIZE, framesize).apply();
                    updateResolutionSelection(framesize);
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
                statusFailureCount++;
                triggerEspAutoScan();
                mainHandler.post(() -> {
                    if (statusFailureCount >= 3 && espStatusTileText != null) {
                        espStatusTileText.setText("OFFLINE");
                        espStatusTileText.setTextColor(ERROR);
                    }
                    if (statusFailureCount >= 3 && cameraText != null) {
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

    private void setRadarPhoneAlarmEnabled(boolean active) {
        radarPhoneAlarmEnabled = active;
        radarAlarmStateInitialized = false;
        prefs.edit().putBoolean(KEY_RADAR_PHONE_ALARM_ENABLED, active).apply();
        if (active) {
            requestNotificationPermissionIfNeeded();
        }
        updateRadarPhoneAlarmUi();
        setConnection(active ? "Handy-Benachrichtigung bei Radar aktiv."
                : "Handy-Benachrichtigung bei Radar aus.", GREEN);
    }

    private void setEspLocalRadarBuzzer(boolean active) {
        ioExecutor.execute(() -> {
            try {
                getString(httpUrl("/buzzer?radar_buzzer=" + (active ? "1" : "0")), 3000);
            } catch (Exception ex) {
                mainHandler.post(() -> setConnection(
                        "ESP-Radar-Buzzer konnte nicht gesetzt werden: " + cleanError(ex),
                        ERROR));
            }
        });
    }

    private void updateRadarPhoneAlarmUi() {
        if (radarPhoneAlarmSwitch != null) {
            suppressSwitchEvents = true;
            radarPhoneAlarmSwitch.setChecked(radarPhoneAlarmEnabled);
            suppressSwitchEvents = false;
        }
        if (radarPhoneAlarmText != null) {
            radarPhoneAlarmText.setText(radarPhoneAlarmEnabled
                    ? "Aktiv: Handy-Benachrichtigung bei Radar"
                    : "Aus: keine Handy-Benachrichtigung");
            radarPhoneAlarmText.setTextColor(radarPhoneAlarmEnabled ? GREEN : MUTED);
        }
    }

    private synchronized void handleRadarPhoneAlarm(boolean radar, int radarCount) {
        if (!radarAlarmStateInitialized) {
            lastRadarActive = radar;
            lastRadarMotionCount = radarCount;
            radarAlarmStateInitialized = true;
            return;
        }

        boolean motionCountIncreased = lastRadarMotionCount >= 0
                && radarCount > lastRadarMotionCount;
        boolean newMotion = motionCountIncreased || (radar && !lastRadarActive);
        lastRadarActive = radar;
        lastRadarMotionCount = radarCount;

        if (!radarPhoneAlarmEnabled || !newMotion) {
            return;
        }

        long nowMs = System.currentTimeMillis();
        if (nowMs - lastRadarAlarmAtMs < RADAR_ALARM_COOLDOWN_MS) {
            return;
        }
        lastRadarAlarmAtMs = nowMs;
        triggerRadarPhoneAlarm(radarCount);
    }

    private void triggerRadarPhoneAlarm(int radarCount) {
        ioExecutor.execute(() -> {
            try {
                getString(httpUrl("/buzzer?state=on&duration_ms=1000&force=1"), 3000);
                mainHandler.post(() -> {
                    showRadarNotification(radarCount);
                    if (radarPhoneAlarmText != null) {
                        radarPhoneAlarmText.setText("Radar-Alarm ausgeloest");
                        radarPhoneAlarmText.setTextColor(RED);
                    }
                });
            } catch (Exception ex) {
                mainHandler.post(() -> {
                    showRadarNotification(radarCount);
                    if (radarPhoneAlarmText != null) {
                        radarPhoneAlarmText.setText("Radar erkannt, Buzzer nicht erreichbar");
                        radarPhoneAlarmText.setTextColor(ERROR);
                    }
                });
            }
        });
    }

    private void setCameraResolution(int framesize) {
        ioExecutor.execute(() -> {
            try {
                getString(httpUrl("/control?var=framesize&val=" + framesize), 3000);
                prefs.edit().putInt(KEY_CAMERA_FRAMESIZE, framesize).apply();
                mainHandler.post(() -> {
                    updateResolutionSelection(framesize);
                    if (cameraText != null) {
                        cameraText.setText("Kamera: Aufloesung " + resolutionLabelForValue(framesize));
                        cameraText.setTextColor(MUTED);
                    }
                    setConnection("Aufloesung gesetzt: " + resolutionLabelForValue(framesize), GREEN);
                });
                refreshStatusOnly();
            } catch (Exception ex) {
                mainHandler.post(() -> {
                    updateResolutionSelection(prefs.getInt(
                            KEY_CAMERA_FRAMESIZE, DEFAULT_CAMERA_FRAMESIZE));
                    setConnection("Aufloesung fehlgeschlagen: " + cleanError(ex), ERROR);
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

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                RADAR_NOTIFICATION_CHANNEL,
                "Radar-Alarm",
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Benachrichtigung bei Radar-Bewegung am ESP32");
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST);
        }
    }

    private boolean canPostNotifications() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void showRadarNotification(int radarCount) {
        if (!canPostNotifications()) {
            Toast.makeText(this, "Radar erkannt. Benachrichtigung nicht erlaubt.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, MainActivityV2.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, pendingFlags);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, RADAR_NOTIFICATION_CHANNEL)
                : new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ic_bell_24)
                .setContentTitle("Radar-Bewegung erkannt")
                .setContentText(String.format(Locale.GERMANY,
                        "ESP32 hat Bewegung erkannt (%d).", radarCount))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setDefaults(Notification.DEFAULT_ALL)
                .setPriority(Notification.PRIORITY_HIGH);

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(RADAR_NOTIFICATION_ID, builder.build());
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

    private boolean startStream() {
        if (streamRunning) {
            return false;
        }
        if (!phoneStreamEnabled) {
            setLiveState(false, "Handy steuert nur. Stream am Laptop oeffnen.");
            return false;
        }
        streamRunning = true;
        setLiveState(false, "Stream verbindet...");
        ioExecutor.execute(this::streamLoop);
        return true;
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
        int reconnectCount = 0;
        try {
            while (streamRunning && appInForeground) {
                HttpURLConnection connection = null;
                try {
                    connection = openHttpConnection(streamUrl("/stream"));
                    streamConnection = connection;
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(20000);
                    connection.setUseCaches(false);
                    connection.connect();
                    if (connection.getResponseCode() >= 400) {
                        throw new IllegalStateException("HTTP " + connection.getResponseCode());
                    }
                    try (InputStream input = connection.getInputStream()) {
                        readMjpeg(input);
                    }
                } catch (Exception ex) {
                    if (!streamRunning || !appInForeground) {
                        break;
                    }
                    reconnectCount++;
                    Log.w(TAG, "MJPEG stream failed; retry=" + reconnectCount, ex);
                    triggerEspAutoScan();
                    mainHandler.post(() -> setLiveState(false, "Stream verbindet..."));
                    if (!snapshotStreamLoop(ex)) {
                        break;
                    }
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                    if (streamConnection == connection) {
                        streamConnection = null;
                    }
                }
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

    private boolean snapshotStreamLoop(Exception streamError) {
        int failures = 0;
        int snapshots = 0;
        while (streamRunning && appInForeground && snapshots < 3) {
            try {
                byte[] bytes = getBytes(httpUrl("/capture"), 5000);
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bitmap == null) {
                    throw new IllegalStateException("JPEG konnte nicht dekodiert werden");
                }
                failures = 0;
                snapshots++;
                mainHandler.post(() -> {
                    if (cameraImage != null) {
                        cameraImage.setImageBitmap(bitmap);
                    }
                    setLiveState(false, "Snapshot-Fallback");
                });
                Thread.sleep(450);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            } catch (Exception ex) {
                failures++;
                if (failures >= 3) {
                    if (appInForeground) {
                        mainHandler.post(() -> setLiveState(false, "Streamfehler: " + cleanError(streamError)));
                    }
                    return false;
                }
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return streamRunning && appInForeground;
    }

    private void readMjpeg(InputStream input) throws Exception {
        ByteArrayOutputStream frame = new ByteArrayOutputStream(96 * 1024);
        boolean inJpeg = false;
        int previous = -1;
        int current;
        int framesInWindow = 0;
        long fpsWindowStartedAt = System.currentTimeMillis();

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
                    long now = System.currentTimeMillis();
                    // Keep the network stream live at full rate, but render at
                    // a bounded rate and never queue more than one full VGA
                    // bitmap for the UI. This is the critical memory guard.
                    boolean renderFrame = now - lastCameraFrameDecodeAtMs >= 100
                            && cameraFrameRenderPending.compareAndSet(false, true);
                    if (renderFrame) {
                        lastCameraFrameDecodeAtMs = now;
                        byte[] jpeg = frame.toByteArray();
                        Bitmap bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
                        if (bitmap == null) {
                            cameraFrameRenderPending.set(false);
                        } else {
                        framesInWindow++;
                        if (now - fpsWindowStartedAt >= 1000) {
                            float fps = framesInWindow * 1000f / (now - fpsWindowStartedAt);
                            Log.i(TAG, String.format(Locale.US,
                                    "MJPEG render_fps=%.1f jpeg=%dB", fps, jpeg.length));
                            framesInWindow = 0;
                            fpsWindowStartedAt = now;
                        }
                        mainHandler.post(() -> {
                            try {
                                if (cameraImage != null) {
                                    cameraImage.setImageBitmap(bitmap);
                                }
                                if (streamRunning) {
                                    setLiveState(true, "");
                                }
                            } finally {
                                cameraFrameRenderPending.set(false);
                            }
                        });
                        }
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
        if (streamRunning) {
            throw new IOException("MJPEG stream closed");
        }
    }

    private JSONObject getJson(String url) throws Exception {
        return new JSONObject(getString(url, 4000));
    }

    private JSONObject getJsonWithRetry(String url, int attempts, int timeoutMs) throws Exception {
        Exception lastError = null;
        for (int i = 0; i < attempts; i++) {
            try {
                return new JSONObject(getString(url, timeoutMs));
            } catch (Exception ex) {
                lastError = ex;
                if (i + 1 < attempts) {
                    try {
                        Thread.sleep(450);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw interrupted;
                    }
                }
            }
        }
        throw lastError;
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
        HttpURLConnection connection = openHttpConnection(urlString);
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

    private HttpURLConnection openHttpConnection(String urlString) throws Exception {
        URL url = new URL(urlString);
        Network network = networkForHost(url.getHost());
        if (network != null) {
            return (HttpURLConnection) network.openConnection(url);
        }
        return (HttpURLConnection) url.openConnection();
    }

    private Network networkForHost(String host) {
        try {
            InetAddress target = InetAddress.getByName(host);
            if (!(target instanceof Inet4Address)) {
                return null;
            }
            ConnectivityManager manager =
                    (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (manager == null) {
                return null;
            }
            Network localHotspot = hotspotLocalNetwork;
            if (localHotspot != null && networkContainsHost(manager, localHotspot, target)) {
                return localHotspot;
            }
            for (Network network : manager.getAllNetworks()) {
                if (networkContainsHost(manager, network, target)) {
                    return network;
                }
            }
        } catch (Exception ignored) {
            // The platform default connection remains a safe fallback.
        }
        return null;
    }

    private boolean networkContainsHost(ConnectivityManager manager, Network network,
                                        InetAddress target) {
        LinkProperties properties = manager.getLinkProperties(network);
        if (properties == null) {
            return false;
        }
        for (LinkAddress address : properties.getLinkAddresses()) {
            InetAddress local = address.getAddress();
            if (local instanceof Inet4Address && sameIpv4Subnet(local.getAddress(),
                    target.getAddress(), address.getPrefixLength())) {
                return true;
            }
        }
        return false;
    }

    private boolean sameIpv4Subnet(byte[] first, byte[] second, int prefixLength) {
        if (first == null || second == null || first.length != 4 || second.length != 4
                || prefixLength < 0 || prefixLength > 32) {
            return false;
        }
        int fullBytes = prefixLength / 8;
        int remainingBits = prefixLength % 8;
        for (int i = 0; i < fullBytes; i++) {
            if (first[i] != second[i]) {
                return false;
            }
        }
        if (remainingBits == 0 || fullBytes >= 4) {
            return true;
        }
        int mask = (0xFF << (8 - remainingBits)) & 0xFF;
        return ((first[fullBytes] & 0xFF) & mask)
                == ((second[fullBytes] & 0xFF) & mask);
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

    private final class RadarRangeView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int[] energy = new int[16];
        private int minimumGate = 1;
        private int maximumGate = 12;

        RadarRangeView(MainActivityV2 context) {
            super(context);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        void setRange(int minimum, int maximum) {
            minimumGate = Math.max(0, Math.min(15, minimum));
            maximumGate = Math.max(minimumGate, Math.min(15, maximum));
            invalidate();
        }

        void setEnergy(JSONArray values) {
            for (int i = 0; i < energy.length; i++) {
                energy[i] = values == null ? 0 : Math.max(0, values.optInt(i, 0));
            }
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float left = dp(8);
            float right = getWidth() - dp(8);
            float top = dp(10);
            float bottom = getHeight() - dp(26);
            float gateWidth = (right - left) / 16f;

            paint.setColor(Color.rgb(229, 231, 235));
            canvas.drawRoundRect(new RectF(left, top, right, bottom),
                    dp(10), dp(10), paint);

            float selectedLeft = left + minimumGate * gateWidth;
            float selectedRight = left + (maximumGate + 1) * gateWidth;
            paint.setColor(Color.rgb(219, 234, 254));
            canvas.drawRect(selectedLeft, top, selectedRight, bottom, paint);

            double maxLogEnergy = 1.0;
            for (int value : energy) {
                maxLogEnergy = Math.max(maxLogEnergy, Math.log10(value + 1.0));
            }
            for (int gate = 0; gate < 16; gate++) {
                float x = left + gate * gateWidth;
                float normalized = (float) (Math.log10(energy[gate] + 1.0) / maxLogEnergy);
                float barHeight = Math.max(dp(2), normalized * (bottom - top - dp(10)));
                boolean selected = gate >= minimumGate && gate <= maximumGate;
                paint.setColor(selected ? Color.rgb(37, 99, 235) : Color.rgb(156, 163, 175));
                canvas.drawRoundRect(new RectF(x + dp(2), bottom - barHeight,
                                x + gateWidth - dp(2), bottom),
                        dp(2), dp(2), paint);

                paint.setColor(Color.argb(75, 17, 24, 39));
                paint.setStrokeWidth(dp(1));
                canvas.drawLine(x, top, x, bottom, paint);
            }

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(BLUE);
            canvas.drawRoundRect(new RectF(selectedLeft, top, selectedRight, bottom),
                    dp(8), dp(8), paint);
            paint.setStyle(Paint.Style.FILL);

            paint.setTextSize(dp(11));
            paint.setColor(MUTED);
            canvas.drawText("0 m", left, getHeight() - dp(7), paint);
            String middle = "5,6 m";
            canvas.drawText(middle, (left + right - paint.measureText(middle)) / 2f,
                    getHeight() - dp(7), paint);
            String end = "11,2 m";
            canvas.drawText(end, right - paint.measureText(end), getHeight() - dp(7), paint);
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


