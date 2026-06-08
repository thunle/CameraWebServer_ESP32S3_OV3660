#include "esp_camera.h"
#include "esp_wifi.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"
#include "board_config.h"
#include "secrets.h"
#include <stdarg.h>
#include <string.h>
#include <WiFi.h>

// ================================================================
// 2. PIN DEFINITIONEN FÜR FREENOVE ESP32-S3 (OV3660)
// ================================================================
// Prototyp für den Webserver (kommt aus app_httpd.cpp)
void startCameraServer();

volatile uint32_t wifiDisconnectCount = 0;
volatile uint32_t wifiGotIpCount = 0;
volatile uint32_t buzzerPulseUntilMs = 0;
volatile uint32_t sensorLastReadMs = 0;
volatile uint32_t radarLastMotionMs = 0;
volatile uint32_t radarMotionCount = 0;
volatile uint32_t darkAlarmCount = 0;
volatile uint32_t radarUartByteCount = 0;
volatile uint32_t radarUartLastRxMs = 0;
volatile uint32_t radarUartFrameCount = 0;
volatile uint32_t radarUartCurrentBaud = RADAR_UART_BAUD;
volatile int radarUartRange = -1;
volatile int lightRaw = 0;
volatile bool radarActive = false;
volatile bool daylightActive = false;
volatile bool radarUartPresent = false;
char radarUartLastHex[193] = "";
char radarUartLastText[65] = "";

static SemaphoreHandle_t remoteLogMutex = NULL;
static SemaphoreHandle_t radarUartMutex = NULL;
static String remoteLogBuffer;
static const size_t REMOTE_LOG_MAX_BYTES = 16384;
static uint32_t lastDarkAlarmMs = 0;
static uint32_t lastSensorLogMs = 0;
static bool previousRadarActive = false;
static const uint32_t RADAR_UART_BAUD_CANDIDATES[] = {RADAR_UART_BAUD, 256000};
static size_t radarUartBaudIndex = 0;
static uint32_t lastRadarUartBaudSwitchMs = 0;

static void appendRemoteLogLocked(const char *message) {
  remoteLogBuffer += message;
  size_t overflow = remoteLogBuffer.length() > REMOTE_LOG_MAX_BYTES
                        ? remoteLogBuffer.length() - REMOTE_LOG_MAX_BYTES
                        : 0;
  if (overflow > 0) {
    remoteLogBuffer.remove(0, overflow);
  }
}

void initRemoteLogBuffer() {
  if (remoteLogMutex == NULL) {
    remoteLogMutex = xSemaphoreCreateMutex();
  }
  if (radarUartMutex == NULL) {
    radarUartMutex = xSemaphoreCreateMutex();
  }
}

void remoteLogf(const char *format, ...) {
  char message[256];
  va_list args;
  va_start(args, format);
  vsnprintf(message, sizeof(message), format, args);
  va_end(args);

  Serial.print(message);

  if (remoteLogMutex != NULL &&
      xSemaphoreTake(remoteLogMutex, pdMS_TO_TICKS(50)) == pdTRUE) {
    appendRemoteLogLocked(message);
    xSemaphoreGive(remoteLogMutex);
  }
}

void remoteLogln(const char *message) {
  Serial.println(message);

  if (remoteLogMutex != NULL &&
      xSemaphoreTake(remoteLogMutex, pdMS_TO_TICKS(50)) == pdTRUE) {
    appendRemoteLogLocked(message);
    appendRemoteLogLocked("\n");
    xSemaphoreGive(remoteLogMutex);
  }
}

String getRemoteLogSnapshot(bool clearAfterRead) {
  String snapshot;
  if (remoteLogMutex != NULL &&
      xSemaphoreTake(remoteLogMutex, pdMS_TO_TICKS(100)) == pdTRUE) {
    snapshot = remoteLogBuffer;
    if (clearAfterRead) {
      remoteLogBuffer = "";
    }
    xSemaphoreGive(remoteLogMutex);
  }
  return snapshot;
}

String getRadarUartLastHexSnapshot() {
  String snapshot;
  if (radarUartMutex != NULL &&
      xSemaphoreTake(radarUartMutex, pdMS_TO_TICKS(20)) == pdTRUE) {
    snapshot = radarUartLastHex;
    xSemaphoreGive(radarUartMutex);
  }
  return snapshot;
}

String getRadarUartLastTextSnapshot() {
  String snapshot;
  if (radarUartMutex != NULL &&
      xSemaphoreTake(radarUartMutex, pdMS_TO_TICKS(20)) == pdTRUE) {
    snapshot = radarUartLastText;
    xSemaphoreGive(radarUartMutex);
  }
  return snapshot;
}

static void pulseBuzzerFor(uint32_t durationMs) {
  digitalWrite(BUZZER_PIN, BUZZER_ON_LEVEL);
  buzzerPulseUntilMs = durationMs > 0 ? millis() + durationMs : 0;
}

void beginRadarUart(uint32_t baud) {
  Serial1.end();
  Serial1.begin(baud, SERIAL_8N1, RADAR_UART_RX_PIN, RADAR_UART_TX_PIN);
  radarUartByteCount = 0;
  radarUartFrameCount = 0;
  radarUartLastRxMs = 0;
  radarUartRange = -1;
  radarUartPresent = false;
  if (radarUartMutex != NULL &&
      xSemaphoreTake(radarUartMutex, pdMS_TO_TICKS(10)) == pdTRUE) {
    radarUartLastHex[0] = '\0';
    radarUartLastText[0] = '\0';
    xSemaphoreGive(radarUartMutex);
  }
  radarUartCurrentBaud = baud;
  lastRadarUartBaudSwitchMs = millis();
  remoteLogf("[RADAR UART] begin baud=%lu rx=%d tx=%d\n",
             (unsigned long)baud, RADAR_UART_RX_PIN, RADAR_UART_TX_PIN);
}

void sendRadarUartBytes(const char *label, const uint8_t *data, size_t len) {
  Serial1.write(data, len);
  Serial1.flush();
  remoteLogf("[RADAR UART] tx %s len=%u\n", label, (unsigned int)len);
}

void sendLd2420InitCommands() {
  static const uint8_t enableConfigV2[] = {
      0xFD, 0xFC, 0xFB, 0xFA, 0x04, 0x00, 0xFF,
      0x00, 0x02, 0x00, 0x04, 0x03, 0x02, 0x01};
  static const uint8_t setEnergyMode[] = {
      0xFD, 0xFC, 0xFB, 0xFA, 0x08, 0x00, 0x12, 0x00, 0x00, 0x00,
      0x04, 0x00, 0x00, 0x00, 0x04, 0x03, 0x02, 0x01};
  static const uint8_t setSimpleMode[] = {
      0xFD, 0xFC, 0xFB, 0xFA, 0x08, 0x00, 0x12, 0x00, 0x00, 0x00,
      0x64, 0x00, 0x00, 0x00, 0x04, 0x03, 0x02, 0x01};
  static const uint8_t disableConfig[] = {
      0xFD, 0xFC, 0xFB, 0xFA, 0x02, 0x00, 0xFE,
      0x00, 0x04, 0x03, 0x02, 0x01};

  while (Serial1.available() > 0) {
    Serial1.read();
  }
  sendRadarUartBytes("enable_config_v2", enableConfigV2,
                     sizeof(enableConfigV2));
  delay(120);
  sendRadarUartBytes("set_energy_mode", setEnergyMode, sizeof(setEnergyMode));
  delay(120);
  sendRadarUartBytes("set_simple_mode", setSimpleMode, sizeof(setSimpleMode));
  delay(120);
  sendRadarUartBytes("disable_config", disableConfig, sizeof(disableConfig));
}

void readRadarUart() {
  static uint8_t frame[64];
  static size_t frameLen = 0;
  static uint32_t lastByteMs = 0;
  bool gotByte = false;

  while (Serial1.available() > 0) {
    int value = Serial1.read();
    if (value < 0) {
      break;
    }
    gotByte = true;
    lastByteMs = millis();
    radarUartByteCount++;
    radarUartLastRxMs = lastByteMs;

    if (frameLen < sizeof(frame)) {
      frame[frameLen++] = (uint8_t)value;
    } else {
      memmove(frame, frame + 1, sizeof(frame) - 1);
      frame[sizeof(frame) - 1] = (uint8_t)value;
    }
  }

  bool radarUartStale =
      radarUartLastRxMs == 0 || millis() - radarUartLastRxMs >= 10000;
  if (radarUartStale && millis() - lastRadarUartBaudSwitchMs >= 4000) {
    radarUartBaudIndex =
        (radarUartBaudIndex + 1) %
        (sizeof(RADAR_UART_BAUD_CANDIDATES) /
         sizeof(RADAR_UART_BAUD_CANDIDATES[0]));
    beginRadarUart(RADAR_UART_BAUD_CANDIDATES[radarUartBaudIndex]);
    sendLd2420InitCommands();
  }

  if (frameLen == 0 || (!gotByte && millis() - lastByteMs < 30)) {
    return;
  }

  char hex[sizeof(radarUartLastHex)];
  size_t out = 0;
  for (size_t i = 0; i < frameLen && out + 3 < sizeof(hex); i++) {
    out += snprintf(hex + out, sizeof(hex) - out, "%02X", frame[i]);
    if (i + 1 < frameLen && out + 2 < sizeof(hex)) {
      hex[out++] = ' ';
      hex[out] = '\0';
    }
  }
  hex[out] = '\0';

  char text[sizeof(radarUartLastText)];
  size_t textOut = 0;
  for (size_t i = 0; i < frameLen && textOut + 1 < sizeof(text); i++) {
    uint8_t ch = frame[i];
    if (ch == '\r' || ch == '\n') {
      if (textOut > 0 && text[textOut - 1] != ' ') {
        text[textOut++] = ' ';
      }
    } else if (ch >= 32 && ch <= 126) {
      text[textOut++] = (char)ch;
    }
  }
  while (textOut > 0 && text[textOut - 1] == ' ') {
    textOut--;
  }
  text[textOut] = '\0';

  if (strstr(text, "ON") != NULL) {
    radarUartPresent = true;
  } else if (strstr(text, "OFF") != NULL) {
    radarUartPresent = false;
  }

  char *rangeStart = strstr(text, "Range ");
  if (rangeStart != NULL) {
    radarUartRange = atoi(rangeStart + 6);
  }

  if (radarUartMutex != NULL &&
      xSemaphoreTake(radarUartMutex, pdMS_TO_TICKS(10)) == pdTRUE) {
    strncpy(radarUartLastHex, hex, sizeof(radarUartLastHex));
    radarUartLastHex[sizeof(radarUartLastHex) - 1] = '\0';
    strncpy(radarUartLastText, text, sizeof(radarUartLastText));
    radarUartLastText[sizeof(radarUartLastText) - 1] = '\0';
    xSemaphoreGive(radarUartMutex);
  }

  radarUartFrameCount++;
  remoteLogf("[RADAR UART] bytes=%lu frame=%lu text=\"%s\" range=%d hex=%s\n",
             (unsigned long)radarUartByteCount,
             (unsigned long)radarUartFrameCount, text, radarUartRange, hex);
  frameLen = 0;
}

void readAlarmSensors() {
  bool radarNow =
      digitalRead(RADAR_PIN) == (RADAR_ACTIVE_HIGH ? HIGH : LOW);
  int lightNow = analogRead(LIGHT_SENSOR_PIN);
  bool daylightNow = lightNow >= LIGHT_DAY_THRESHOLD;
  uint32_t nowMs = millis();

  sensorLastReadMs = nowMs;
  lightRaw = lightNow;
  radarActive = radarNow;
  daylightActive = daylightNow;

  if (radarNow) {
    radarLastMotionMs = nowMs;
    if (!previousRadarActive) {
      radarMotionCount++;
      remoteLogf("[SENSOR] radar motion #%lu light=%d daylight=%u\n",
                 (unsigned long)radarMotionCount, lightNow,
                 daylightNow ? 1 : 0);
    }
  }
  previousRadarActive = radarNow;

#if DARK_RADAR_ALARM_ENABLED
  if (radarNow && !daylightNow &&
      (lastDarkAlarmMs == 0 || nowMs - lastDarkAlarmMs >= DARK_ALARM_COOLDOWN_MS)) {
    lastDarkAlarmMs = nowMs;
    darkAlarmCount++;
    pulseBuzzerFor(DARK_ALARM_PULSE_MS);
    remoteLogf("[ALARM] dark radar alarm #%lu light=%d pulse=%ums\n",
               (unsigned long)darkAlarmCount, lightNow,
               (unsigned int)DARK_ALARM_PULSE_MS);
  }
#endif

  if (nowMs - lastSensorLogMs >= 5000) {
    lastSensorLogMs = nowMs;
    remoteLogf("[SENSOR] radar=%u light=%d daylight=%u last_motion=%lu uart_bytes=%lu\n",
               radarNow ? 1 : 0, lightNow, daylightNow ? 1 : 0,
               (unsigned long)radarLastMotionMs,
               (unsigned long)radarUartByteCount);
  }
}

void onWiFiEvent(WiFiEvent_t event, WiFiEventInfo_t info) {
  switch (event) {
  case ARDUINO_EVENT_WIFI_STA_DISCONNECTED:
    wifiDisconnectCount++;
    remoteLogf("[WIFI] disconnected reason=%u total=%lu\n",
               info.wifi_sta_disconnected.reason,
               (unsigned long)wifiDisconnectCount);
    break;
  case ARDUINO_EVENT_WIFI_STA_GOT_IP:
    wifiGotIpCount++;
    remoteLogf("[WIFI] got_ip total=%lu ip=%s\n",
               (unsigned long)wifiGotIpCount,
               WiFi.localIP().toString().c_str());
    break;
  default:
    break;
  }
}

void setup() {
  Serial.begin(115200);
  Serial.setDebugOutput(false);
  initRemoteLogBuffer();
  Serial.println();

  // Buzzer Pin Setup
  digitalWrite(BUZZER_PIN, BUZZER_OFF_LEVEL);
  pinMode(BUZZER_PIN, OUTPUT);
  pinMode(RADAR_PIN, RADAR_ACTIVE_HIGH ? INPUT_PULLDOWN : INPUT_PULLUP);
  pinMode(LIGHT_SENSOR_PIN, INPUT);
  beginRadarUart(RADAR_UART_BAUD);
  sendLd2420InitCommands();

  camera_config_t config;
  config.ledc_channel = LEDC_CHANNEL_0;
  config.ledc_timer = LEDC_TIMER_0;
  config.pin_d0 = Y2_GPIO_NUM;
  config.pin_d1 = Y3_GPIO_NUM;
  config.pin_d2 = Y4_GPIO_NUM;
  config.pin_d3 = Y5_GPIO_NUM;
  config.pin_d4 = Y6_GPIO_NUM;
  config.pin_d5 = Y7_GPIO_NUM;
  config.pin_d6 = Y8_GPIO_NUM;
  config.pin_d7 = Y9_GPIO_NUM;
  config.pin_xclk = XCLK_GPIO_NUM;
  config.pin_pclk = PCLK_GPIO_NUM;
  config.pin_vsync = VSYNC_GPIO_NUM;
  config.pin_href = HREF_GPIO_NUM;
  config.pin_sccb_sda = SIOD_GPIO_NUM;
  config.pin_sccb_scl = SIOC_GPIO_NUM;
  config.pin_pwdn = PWDN_GPIO_NUM;
  config.pin_reset = RESET_GPIO_NUM;
  config.xclk_freq_hz = 15000000;
  config.frame_size = FRAMESIZE_VGA; // Weniger Daten pro Frame fuer mehr FPS
  config.pixel_format = PIXFORMAT_JPEG;
  config.grab_mode = CAMERA_GRAB_WHEN_EMPTY;
  config.fb_location = CAMERA_FB_IN_PSRAM;
  config.jpeg_quality = 16;
  config.fb_count = 1;

  // PSRAM Check (Essentiell für S3-WROOM)
  if (psramFound()) {
    config.jpeg_quality = 14;
    config.fb_count = 2;
    config.frame_size = FRAMESIZE_VGA;
    config.grab_mode = CAMERA_GRAB_LATEST;
    remoteLogln("psramFound");
  } else {
    // Falls kein PSRAM gefunden wurde, Auflösung senken
    config.frame_size = FRAMESIZE_VGA;
    config.fb_location = CAMERA_FB_IN_DRAM;
  }

  // Kamera-Initialisierung
  esp_err_t err = esp_camera_init(&config);
  if (err != ESP_OK) {
    remoteLogf("Kamera-Init fehlgeschlagen: 0x%x\n", err);
    return;
  }

  // Sensor-Feineinstellung für OV3660
  sensor_t *s = esp_camera_sensor_get();
  if (s->id.PID == OV3660_PID) {
    s->set_vflip(s, 1);
    s->set_brightness(s, 1);
    s->set_saturation(s, -2);
  }

  // WiFi Verbindung aufbauen
  WiFi.onEvent(onWiFiEvent);
  WiFi.mode(WIFI_STA);
  WiFi.begin(ssid, password);
  WiFi.setSleep(false); // Wichtig fuer stabilen Stream

  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  remoteLogln("WiFi verbunden!");
  esp_wifi_set_ps(WIFI_PS_NONE);
  remoteLogln("WLAN Power Management deaktiviert fuer stabilen Stream");

  // Startet den Webserver aus app_httpd.cpp
  startCameraServer();

  Serial.print("Kamera bereit! Öffne: http://");
  Serial.println(WiFi.localIP());
}

void loop() {
  static uint32_t lastSensorReadLoopMs = 0;
  readRadarUart();

  uint32_t nowMs = millis();
  if (nowMs - lastSensorReadLoopMs >= 50) {
    lastSensorReadLoopMs = nowMs;
    readAlarmSensors();
  }

  uint32_t pulseUntil = buzzerPulseUntilMs;
  if (pulseUntil != 0 && (int32_t)(millis() - pulseUntil) >= 0) {
    digitalWrite(BUZZER_PIN, BUZZER_OFF_LEVEL);
    buzzerPulseUntilMs = 0;
  }

  delay(5);
}
