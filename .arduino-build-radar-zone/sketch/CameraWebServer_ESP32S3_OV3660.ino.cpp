#include <Arduino.h>
#line 1 "C:\\Users\\Julius\\Documents\\Arduino\\CameraWebServer_ESP32S3_OV3660\\CameraWebServer_ESP32S3_OV3660.ino"
#include "esp_camera.h"
#include "esp_system.h"
#include "esp_wifi.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"
#include "board_config.h"
#include "secrets.h"
#include <stdarg.h>
#include <string.h>
#include <Preferences.h>
#include <WiFi.h>

// ================================================================
// 2. PIN DEFINITIONEN FÜR FREENOVE ESP32-S3 (OV3660)
// ================================================================
// Prototyp für den Webserver (kommt aus app_httpd.cpp)
void startCameraServer();

volatile uint32_t wifiDisconnectCount = 0;
volatile uint32_t wifiGotIpCount = 0;
volatile uint32_t buzzerPulseUntilMs = 0;
volatile bool buzzerAlarmEnabled = true;
volatile bool localRadarBuzzerEnabled = false;
volatile uint32_t sensorLastReadMs = 0;
volatile uint32_t radarLastMotionMs = 0;
volatile uint32_t radarMotionCount = 0;
volatile uint32_t darkAlarmCount = 0;
volatile uint32_t localRadarBuzzerCount = 0;
volatile uint32_t radarUartByteCount = 0;
volatile uint32_t radarUartLastRxMs = 0;
volatile uint32_t radarUartFrameCount = 0;
volatile uint32_t radarUartCurrentBaud = RADAR_UART_BAUD;
volatile uint32_t radarEnergyFrameCount = 0;
volatile int radarUartRange = -1;
volatile uint16_t radarEnergyDistanceCm = 0;
volatile int lightRaw = 0;
volatile bool radarActive = false;
volatile bool daylightActive = false;
volatile bool radarUartPresent = false;
volatile bool radarEnergyPresent = false;
char radarUartLastHex[193] = "";
char radarUartLastText[65] = "";
uint16_t radarGateEnergy[16] = {0};
static constexpr uint32_t RADAR_CALIBRATION_DURATION_MS = 60000;
static bool radarCalibrationActive = false;
static bool radarCalibrationApplied = false;
static uint32_t radarCalibrationStartedAtMs = 0;
static uint32_t radarCalibrationSampleCount = 0;
static float radarCalibrationMoveFactor = 0.5f;
static float radarCalibrationStillFactor = 0.5f;
static uint16_t radarCalibrationPeak[16] = {0};
static uint8_t radarMinimumGate = 1;
static uint8_t radarMaximumGate = 12;
static uint8_t radarPresenceDelay = 5;
static uint16_t radarTriggerThreshold[16] = {
    60000, 30000, 400, 250, 250, 250, 250, 250,
    250,   250,   250, 250, 250, 250, 250, 250};
static uint16_t radarMaintainThreshold[16] = {
    40000, 20000, 200, 200, 200, 200, 200, 150,
    150,   100,   100, 100, 100, 100, 100, 100};
static bool radarZoneActive = false;
static uint32_t radarZoneLastDetectionMs = 0;

static SemaphoreHandle_t remoteLogMutex = NULL;
static SemaphoreHandle_t radarUartMutex = NULL;
Preferences settingsPrefs;
static String remoteLogBuffer;
static const size_t REMOTE_LOG_MAX_BYTES = 16384;
static uint32_t lastDarkAlarmMs = 0;
static uint32_t lastLocalRadarBuzzerMs = 0;
static uint32_t lastSensorLogMs = 0;
static uint32_t lastWifiReconnectAttemptMs = 0;
static uint32_t wifiDisconnectedSinceMs = 0;
static uint32_t lastWifiStatusLogMs = 0;
static uint32_t nextWifiReconnectAttemptMs = 0;
static uint32_t wifiReconnectBackoffMs = 10000;
static bool previousRadarActive = false;
static char lastConnectedWifiProfile[20] = "";
static const uint32_t RADAR_UART_BAUD_CANDIDATES[] = {RADAR_UART_BAUD, 256000};
static size_t radarUartBaudIndex = 0;
static uint32_t lastRadarUartBaudSwitchMs = 0;
static WiFiServer simpleHttpServer(80);

#line 84 "C:\\Users\\Julius\\Documents\\Arduino\\CameraWebServer_ESP32S3_OV3660\\CameraWebServer_ESP32S3_OV3660.ino"
static void appendRemoteLogLocked(const char *message);
#line 94 "C:\\Users\\Julius\\Documents\\Arduino\\CameraWebServer_ESP32S3_OV3660\\CameraWebServer_ESP32S3_OV3660.ino"
void initRemoteLogBuffer();
#line 103 "C:\\Users\\Julius\\Documents\\Arduino\\CameraWebServer_ESP32S3_OV3660\\CameraWebServer_ESP32S3_OV3660.ino"
void remoteLogf(const char *format, ...);
#line 119 "C:\\Users\\Julius\\Documents\\Arduino\\CameraWebServer_ESP32S3_OV3660\\CameraWebServer_ESP32S3_OV3660.ino"
void remoteLogln(const char *message);
#line 130 "C:\\Users\\Julius\\Documents\\Arduino\\CameraWebServer_ESP32S3_OV3660\\CameraWebServer_ESP32S3_OV3660.ino"
String getRemoteLogSnapshot(bool clearAfterRead);
#line 143 "C:\\Users\\Julius\\Documents\\Arduino\\CameraWebServer_ESP32S3_OV3660\\CameraWebServer_ESP32S3_OV3660.ino"
String getRadarUartLastHexSnapshot();
#line 153 "C:\\Users\\Julius\\Documents\\Arduino\\CameraWebServer_ESP32S3_OV3660\\CameraWebServer_ESP32S3_OV3660.ino"
String getRadarUartLastTextSnapshot();
#line 163 "C:\\Users\\Julius\\Documents\\Arduino\\CameraWebServer_ESP32S3_OV3660\\CameraWebServer_ESP32S3_OV3660.ino"
void getRadarGateEnergySnapshot(uint16_t *out, size_t count);
#line 177 "C:\\Users\\Julius\\Documents\\Arduino\\CameraWebServer_ESP32S3_OV3660\\CameraWebServer_ESP32S3_OV3660.ino"
bool startRadarCalibration(float moveFactor, float stillFactor);
#line 201 "C:\\Users\\Julius\\Documents\\Arduino\\CameraWebServer_ESP32S3_OV3660\\CameraWebServer_ESP32S3_OV3660.ino"
bool cancelRadarCalibration();
#line 215 "C:\\Users\\Julius\\Documents\\Arduino\\CameraWebServer_ESP32S3_OV3660\\CameraWebServer_ESP32S3_OV3660.ino"
void getRadarCalibrationStatus(bool *active, bool *ready, bool *applied, uint32_t *elapsedMs, uint32_t *sampleCount, float *moveFactor, float *stillFactor, uint16_t *peaks, size_t peakCount);
#line 249 "C:\\Users\\Julius\\Documents\\Arduino\\CameraWebServer_ESP32S3_OV3660\\CameraWebServer_ESP32S3_OV3660.ino"
void getRadarRangeSettings(uint8_t *minimumGate, uint8_t *maximumGate, uint8_t *presenceDelay);
#line 256 "C:\\Users\\Julius\\Documents\\Arduino\\CameraWebServer_ESP32S3_OV3660\\CameraWebServer_ESP32S3_OV3660.ino"
void getRadarGateThresholdSettings(uint16_t *trigger, uint16_t *maintain, size_t count);
#line 273 "C:\\Users\\Julius\\Documents\\Arduino\\CameraWebServer_ESP32S3_OV3660\\CameraWebServer_ESP32S3_OV3660.ino"
static void pulseBuzzerFor(uint32_t durationMs);
#line 289 "C:\\Users\\Julius\\Documents\\Arduino\\CameraWebServer_ESP32S3_OV3660\\CameraWebServer_ESP32S3_OV3660.ino"
void beginRadarUart(uint32_t baud);
#line 313 "C:\\Users\\Julius\\Documents\\Arduino\\CameraWebServer_ESP32S3_OV3660\\CameraWebServer_ESP32S3_OV3660.ino"
void sendRadarUartBytes(const char *label, const uint8_t *data, size_t len);
#line 319 "C:\\Users\\Julius\\Documents\\Arduino\\CameraWebServer_ESP32S3_OV3660\\CameraWebServer_ESP32S3_OV3660.ino"
void sendLd2420InitCommands();
#line 346 "C:\\Users\\Julius\\Documents\\Arduino\\CameraWebServer_ESP32S3_OV3660\\CameraWebServer_ESP32S3_OV3660.ino"
static void sendLd2420GateThresholds(uint8_t gate, uint16_t moveThreshold, uint16_t stillThreshold);
#line 360 "C:\\Users\\Julius\\Documents\\Arduino\\CameraWebServer_ESP32S3_OV3660\\CameraWebServer_ESP32S3_OV3660.ino"
bool applyRadarRangeSettings(uint8_t minimumGate, uint8_t maximumGate, uint8_t presenceDelay);
#line 401 "C:\\Users\\Julius\\Documents\\Arduino\\CameraWebServer_ESP32S3_OV3660\\CameraWebServer_ESP32S3_OV3660.ino"
bool applyRadarGateThresholdSettings(uint8_t gate, uint16_t trigger, uint16_t maintain);
#line 438 "C:\\Users\\Julius\\Documents\\Arduino\\CameraWebServer_ESP32S3_OV3660\\CameraWebServer_ESP32S3_OV3660.ino"
bool applyRadarCalibration();
#line 506 "C:\\Users\\Julius\\Documents\\Arduino\\CameraWebServer_ESP32S3_OV3660\\CameraWebServer_ESP32S3_OV3660.ino"
static bool isLd2420EnergyFrame(const uint8_t *frame, size_t len);
#line 514 "C:\\Users\\Julius\\Documents\\Arduino\\CameraWebServer_ESP32S3_OV3660\\CameraWebServer_ESP32S3_OV3660.ino"
static void parseLd2420EnergyFrame(const uint8_t *frame, uint32_t nowMs);
#line 583 "C:\\Users\\Julius\\Documents\\Arduino\\CameraWebServer_ESP32S3_OV3660\\CameraWebServer_ESP32S3_OV3660.ino"
void readRadarUart();
#line 730 "C:\\Users\\Julius\\Documents\\Arduino\\CameraWebServer_ESP32S3_OV3660\\CameraWebServer_ESP32S3_OV3660.ino"
void readAlarmSensors();
#line 798 "C:\\Users\\Julius\\Documents\\Arduino\\CameraWebServer_ESP32S3_OV3660\\CameraWebServer_ESP32S3_OV3660.ino"
static void sendSimpleHttpJson(WiFiClient &client, const char *body);
#line 810 "C:\\Users\\Julius\\Documents\\Arduino\\CameraWebServer_ESP32S3_OV3660\\CameraWebServer_ESP32S3_OV3660.ino"
static void handleSimpleHttpClient();
#line 861 "C:\\Users\\Julius\\Documents\\Arduino\\CameraWebServer_ESP32S3_OV3660\\CameraWebServer_ESP32S3_OV3660.ino"
const char * wifiDisconnectReasonName(uint8_t reason);
#line 898 "C:\\Users\\Julius\\Documents\\Arduino\\CameraWebServer_ESP32S3_OV3660\\CameraWebServer_ESP32S3_OV3660.ino"
void onWiFiEvent(arduino_event_id_t event, arduino_event_info_t info);
#line 925 "C:\\Users\\Julius\\Documents\\Arduino\\CameraWebServer_ESP32S3_OV3660\\CameraWebServer_ESP32S3_OV3660.ino"
static bool hasWifiSsid(const char *networkSsid);
#line 929 "C:\\Users\\Julius\\Documents\\Arduino\\CameraWebServer_ESP32S3_OV3660\\CameraWebServer_ESP32S3_OV3660.ino"
static void configureWifiStationRadio();
#line 949 "C:\\Users\\Julius\\Documents\\Arduino\\CameraWebServer_ESP32S3_OV3660\\CameraWebServer_ESP32S3_OV3660.ino"
static void resetWifiRadioForReconnect(const char *label);
#line 959 "C:\\Users\\Julius\\Documents\\Arduino\\CameraWebServer_ESP32S3_OV3660\\CameraWebServer_ESP32S3_OV3660.ino"
static bool connectToWifiNetwork(const char *label, const char *networkSsid, const char *networkPassword, uint32_t timeoutMs);
#line 1182 "C:\\Users\\Julius\\Documents\\Arduino\\CameraWebServer_ESP32S3_OV3660\\CameraWebServer_ESP32S3_OV3660.ino"
bool initCameraHardware();
#line 1313 "C:\\Users\\Julius\\Documents\\Arduino\\CameraWebServer_ESP32S3_OV3660\\CameraWebServer_ESP32S3_OV3660.ino"
void setup();
#line 1462 "C:\\Users\\Julius\\Documents\\Arduino\\CameraWebServer_ESP32S3_OV3660\\CameraWebServer_ESP32S3_OV3660.ino"
void loop();
#line 84 "C:\\Users\\Julius\\Documents\\Arduino\\CameraWebServer_ESP32S3_OV3660\\CameraWebServer_ESP32S3_OV3660.ino"
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

void getRadarGateEnergySnapshot(uint16_t *out, size_t count) {
  if (out == NULL) {
    return;
  }
  size_t copyCount = count < 16 ? count : 16;
  if (radarUartMutex != NULL &&
      xSemaphoreTake(radarUartMutex, pdMS_TO_TICKS(20)) == pdTRUE) {
    memcpy(out, radarGateEnergy, copyCount * sizeof(uint16_t));
    xSemaphoreGive(radarUartMutex);
  } else {
    memset(out, 0, copyCount * sizeof(uint16_t));
  }
}

bool startRadarCalibration(float moveFactor, float stillFactor) {
  if (moveFactor < 0.0f || moveFactor > 5.0f || stillFactor < 0.0f ||
      stillFactor > 5.0f || radarUartMutex == NULL ||
      xSemaphoreTake(radarUartMutex, pdMS_TO_TICKS(50)) != pdTRUE) {
    return false;
  }
  if (radarCalibrationActive) {
    xSemaphoreGive(radarUartMutex);
    return false;
  }
  memset(radarCalibrationPeak, 0, sizeof(radarCalibrationPeak));
  radarCalibrationMoveFactor = moveFactor;
  radarCalibrationStillFactor = stillFactor;
  radarCalibrationSampleCount = 0;
  radarCalibrationStartedAtMs = millis();
  radarCalibrationApplied = false;
  radarCalibrationActive = true;
  xSemaphoreGive(radarUartMutex);
  remoteLogf("[RADAR CAL] started duration=%lums move_factor=%.2f still_factor=%.2f\n",
             (unsigned long)RADAR_CALIBRATION_DURATION_MS, moveFactor,
             stillFactor);
  return true;
}

bool cancelRadarCalibration() {
  if (radarUartMutex == NULL ||
      xSemaphoreTake(radarUartMutex, pdMS_TO_TICKS(50)) != pdTRUE) {
    return false;
  }
  bool wasActive = radarCalibrationActive;
  radarCalibrationActive = false;
  xSemaphoreGive(radarUartMutex);
  if (wasActive) {
    remoteLogln("[RADAR CAL] cancelled");
  }
  return wasActive;
}

void getRadarCalibrationStatus(bool *active, bool *ready, bool *applied,
                               uint32_t *elapsedMs, uint32_t *sampleCount,
                               float *moveFactor, float *stillFactor,
                               uint16_t *peaks, size_t peakCount) {
  const uint32_t now = millis();
  if (radarUartMutex == NULL ||
      xSemaphoreTake(radarUartMutex, pdMS_TO_TICKS(50)) != pdTRUE) {
    if (active) *active = false;
    if (ready) *ready = false;
    if (applied) *applied = false;
    if (elapsedMs) *elapsedMs = 0;
    if (sampleCount) *sampleCount = 0;
    if (moveFactor) *moveFactor = 0.0f;
    if (stillFactor) *stillFactor = 0.0f;
    if (peaks) memset(peaks, 0, peakCount * sizeof(uint16_t));
    return;
  }
  const bool isActive = radarCalibrationActive;
  const uint32_t elapsed =
      isActive ? now - radarCalibrationStartedAtMs : 0;
  if (active) *active = isActive;
  if (ready) *ready = isActive && elapsed >= RADAR_CALIBRATION_DURATION_MS;
  if (applied) *applied = radarCalibrationApplied;
  if (elapsedMs) *elapsedMs = elapsed;
  if (sampleCount) *sampleCount = radarCalibrationSampleCount;
  if (moveFactor) *moveFactor = radarCalibrationMoveFactor;
  if (stillFactor) *stillFactor = radarCalibrationStillFactor;
  if (peaks) {
    const size_t copyCount = peakCount < 16 ? peakCount : 16;
    memcpy(peaks, radarCalibrationPeak, copyCount * sizeof(uint16_t));
  }
  xSemaphoreGive(radarUartMutex);
}

void getRadarRangeSettings(uint8_t *minimumGate, uint8_t *maximumGate,
                           uint8_t *presenceDelay) {
  if (minimumGate) *minimumGate = radarMinimumGate;
  if (maximumGate) *maximumGate = radarMaximumGate;
  if (presenceDelay) *presenceDelay = radarPresenceDelay;
}

void getRadarGateThresholdSettings(uint16_t *trigger, uint16_t *maintain,
                                   size_t count) {
  if (!trigger || !maintain) {
    return;
  }
  const size_t copyCount = count < 16 ? count : 16;
  if (radarUartMutex != NULL &&
      xSemaphoreTake(radarUartMutex, pdMS_TO_TICKS(50)) == pdTRUE) {
    memcpy(trigger, radarTriggerThreshold, copyCount * sizeof(uint16_t));
    memcpy(maintain, radarMaintainThreshold, copyCount * sizeof(uint16_t));
    xSemaphoreGive(radarUartMutex);
  } else {
    memset(trigger, 0, copyCount * sizeof(uint16_t));
    memset(maintain, 0, copyCount * sizeof(uint16_t));
  }
}

static void pulseBuzzerFor(uint32_t durationMs) {
#if !BUZZER_PIN_TEST
  remoteLogf("[BUZZER-TEST] pulse suppressed because buzzer pin disabled duration=%ums\n",
             (unsigned int)durationMs);
  buzzerPulseUntilMs = 0;
  return;
#endif
  if (!buzzerAlarmEnabled) {
    remoteLogf("[BUZZER] alarm pulse suppressed duration=%ums\n",
               (unsigned int)durationMs);
    return;
  }
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
  radarEnergyPresent = false;
  radarEnergyDistanceCm = 0;
  radarEnergyFrameCount = 0;
  if (radarUartMutex != NULL &&
      xSemaphoreTake(radarUartMutex, pdMS_TO_TICKS(10)) == pdTRUE) {
    radarUartLastHex[0] = '\0';
    radarUartLastText[0] = '\0';
    memset(radarGateEnergy, 0, sizeof(radarGateEnergy));
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
#if RADAR_UART_PASSIVE_TEST
  remoteLogln("[RADAR UART] passive test: not sending LD2420 commands");
  return;
#endif

  static const uint8_t enableConfigV2[] = {
      0xFD, 0xFC, 0xFB, 0xFA, 0x04, 0x00, 0xFF,
      0x00, 0x02, 0x00, 0x04, 0x03, 0x02, 0x01};
  static const uint8_t setEnergyMode[] = {
      0xFD, 0xFC, 0xFB, 0xFA, 0x08, 0x00, 0x12, 0x00, 0x00, 0x00,
      0x04, 0x00, 0x00, 0x00, 0x04, 0x03, 0x02, 0x01};
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
  sendRadarUartBytes("disable_config", disableConfig, sizeof(disableConfig));
}

static void sendLd2420GateThresholds(uint8_t gate, uint16_t moveThreshold,
                                      uint16_t stillThreshold) {
  uint8_t command[] = {
      0xFD, 0xFC, 0xFB, 0xFA, 0x14, 0x00, 0x07, 0x00,
      (uint8_t)(0x10 + gate), 0x00, 0x00, 0x00, 0x00, 0x00,
      (uint8_t)(0x20 + gate), 0x00, 0x00, 0x00, 0x00, 0x00,
      0x04, 0x03, 0x02, 0x01};
  command[10] = (uint8_t)(moveThreshold & 0xFF);
  command[11] = (uint8_t)(moveThreshold >> 8);
  command[16] = (uint8_t)(stillThreshold & 0xFF);
  command[17] = (uint8_t)(stillThreshold >> 8);
  sendRadarUartBytes("set_gate_thresholds", command, sizeof(command));
}

bool applyRadarRangeSettings(uint8_t minimumGate, uint8_t maximumGate,
                             uint8_t presenceDelay) {
  if (minimumGate > maximumGate || maximumGate > 15 || presenceDelay > 15) {
    return false;
  }
  static const uint8_t enableConfigV2[] = {
      0xFD, 0xFC, 0xFB, 0xFA, 0x04, 0x00, 0xFF,
      0x00, 0x02, 0x00, 0x04, 0x03, 0x02, 0x01};
  static const uint8_t setEnergyMode[] = {
      0xFD, 0xFC, 0xFB, 0xFA, 0x08, 0x00, 0x12, 0x00, 0x00, 0x00,
      0x04, 0x00, 0x00, 0x00, 0x04, 0x03, 0x02, 0x01};
  static const uint8_t disableConfig[] = {
      0xFD, 0xFC, 0xFB, 0xFA, 0x02, 0x00, 0xFE,
      0x00, 0x04, 0x03, 0x02, 0x01};
  uint8_t command[] = {
      0xFD, 0xFC, 0xFB, 0xFA, 0x14, 0x00, 0x07, 0x00,
      0x00, 0x00, minimumGate, 0x00, 0x00, 0x00,
      0x01, 0x00, maximumGate, 0x00, 0x00, 0x00,
      0x04, 0x00, presenceDelay, 0x00, 0x00, 0x00,
      0x04, 0x03, 0x02, 0x01};

  sendRadarUartBytes("enable_config_v2", enableConfigV2,
                     sizeof(enableConfigV2));
  delay(120);
  sendRadarUartBytes("set_range_timeout", command, sizeof(command));
  delay(120);
  sendRadarUartBytes("set_energy_mode", setEnergyMode, sizeof(setEnergyMode));
  delay(120);
  sendRadarUartBytes("disable_config", disableConfig, sizeof(disableConfig));

  radarMinimumGate = minimumGate;
  radarMaximumGate = maximumGate;
  radarPresenceDelay = presenceDelay;
  settingsPrefs.putUChar("radar_min", minimumGate);
  settingsPrefs.putUChar("radar_max", maximumGate);
  settingsPrefs.putUChar("radar_delay", presenceDelay);
  remoteLogf("[RADAR CFG] min_gate=%u max_gate=%u delay=%u\n", minimumGate,
             maximumGate, presenceDelay);
  return true;
}

bool applyRadarGateThresholdSettings(uint8_t gate, uint16_t trigger,
                                     uint16_t maintain) {
  if (gate > 15 || radarCalibrationActive) {
    return false;
  }
  static const uint8_t enableConfigV2[] = {
      0xFD, 0xFC, 0xFB, 0xFA, 0x04, 0x00, 0xFF,
      0x00, 0x02, 0x00, 0x04, 0x03, 0x02, 0x01};
  static const uint8_t setEnergyMode[] = {
      0xFD, 0xFC, 0xFB, 0xFA, 0x08, 0x00, 0x12, 0x00, 0x00, 0x00,
      0x04, 0x00, 0x00, 0x00, 0x04, 0x03, 0x02, 0x01};
  static const uint8_t disableConfig[] = {
      0xFD, 0xFC, 0xFB, 0xFA, 0x02, 0x00, 0xFE,
      0x00, 0x04, 0x03, 0x02, 0x01};
  sendRadarUartBytes("enable_config_v2", enableConfigV2,
                     sizeof(enableConfigV2));
  delay(120);
  sendLd2420GateThresholds(gate, trigger, maintain);
  delay(120);
  sendRadarUartBytes("set_energy_mode", setEnergyMode, sizeof(setEnergyMode));
  delay(120);
  sendRadarUartBytes("disable_config", disableConfig, sizeof(disableConfig));
  if (radarUartMutex != NULL &&
      xSemaphoreTake(radarUartMutex, pdMS_TO_TICKS(50)) == pdTRUE) {
    radarTriggerThreshold[gate] = trigger;
    radarMaintainThreshold[gate] = maintain;
    xSemaphoreGive(radarUartMutex);
  }
  settingsPrefs.putBytes("radar_trigger", radarTriggerThreshold,
                         sizeof(radarTriggerThreshold));
  settingsPrefs.putBytes("radar_maintain", radarMaintainThreshold,
                         sizeof(radarMaintainThreshold));
  remoteLogf("[RADAR CFG] gate=%u trigger=%u maintain=%u\n", gate, trigger,
             maintain);
  return true;
}

bool applyRadarCalibration() {
  uint16_t peaks[16];
  float moveFactor = 0.0f;
  float stillFactor = 0.0f;
  if (radarUartMutex == NULL ||
      xSemaphoreTake(radarUartMutex, pdMS_TO_TICKS(100)) != pdTRUE) {
    return false;
  }
  const bool ready = radarCalibrationActive &&
      millis() - radarCalibrationStartedAtMs >= RADAR_CALIBRATION_DURATION_MS &&
      radarCalibrationSampleCount > 0;
  if (!ready) {
    xSemaphoreGive(radarUartMutex);
    return false;
  }
  memcpy(peaks, radarCalibrationPeak, sizeof(peaks));
  moveFactor = radarCalibrationMoveFactor;
  stillFactor = radarCalibrationStillFactor;
  radarCalibrationActive = false;
  xSemaphoreGive(radarUartMutex);

  static const uint8_t enableConfigV2[] = {
      0xFD, 0xFC, 0xFB, 0xFA, 0x04, 0x00, 0xFF,
      0x00, 0x02, 0x00, 0x04, 0x03, 0x02, 0x01};
  static const uint8_t setEnergyMode[] = {
      0xFD, 0xFC, 0xFB, 0xFA, 0x08, 0x00, 0x12, 0x00, 0x00, 0x00,
      0x04, 0x00, 0x00, 0x00, 0x04, 0x03, 0x02, 0x01};
  static const uint8_t disableConfig[] = {
      0xFD, 0xFC, 0xFB, 0xFA, 0x02, 0x00, 0xFE,
      0x00, 0x04, 0x03, 0x02, 0x01};

  sendRadarUartBytes("enable_config_v2", enableConfigV2,
                     sizeof(enableConfigV2));
  delay(120);
  for (uint8_t gate = 0; gate < 16; gate++) {
    const uint32_t moveValue = (uint32_t)(peaks[gate] * (2.0f + moveFactor));
    const uint32_t stillValue =
        (uint32_t)(peaks[gate] * (2.0f + stillFactor * 0.5f));
    const uint16_t moveThreshold =
        (uint16_t)(moveValue > 65535 ? 65535 : moveValue);
    const uint16_t stillThreshold =
        (uint16_t)(stillValue > 65535 ? 65535 : stillValue);
    sendLd2420GateThresholds(gate, moveThreshold, stillThreshold);
    if (radarUartMutex != NULL &&
        xSemaphoreTake(radarUartMutex, pdMS_TO_TICKS(20)) == pdTRUE) {
      radarTriggerThreshold[gate] = moveThreshold;
      radarMaintainThreshold[gate] = stillThreshold;
      xSemaphoreGive(radarUartMutex);
    }
    delay(20);
  }
  sendRadarUartBytes("set_energy_mode", setEnergyMode, sizeof(setEnergyMode));
  delay(120);
  sendRadarUartBytes("disable_config", disableConfig, sizeof(disableConfig));

  if (radarUartMutex != NULL &&
      xSemaphoreTake(radarUartMutex, pdMS_TO_TICKS(100)) == pdTRUE) {
    radarCalibrationApplied = true;
    xSemaphoreGive(radarUartMutex);
  }
  settingsPrefs.putBytes("radar_trigger", radarTriggerThreshold,
                         sizeof(radarTriggerThreshold));
  settingsPrefs.putBytes("radar_maintain", radarMaintainThreshold,
                         sizeof(radarMaintainThreshold));
  remoteLogln("[RADAR CAL] thresholds written and stored");
  return true;
}

static bool isLd2420EnergyFrame(const uint8_t *frame, size_t len) {
  return len >= 45 &&
         frame[0] == 0xF4 && frame[1] == 0xF3 &&
         frame[2] == 0xF2 && frame[3] == 0xF1 &&
         frame[41] == 0xF8 && frame[42] == 0xF7 &&
         frame[43] == 0xF6 && frame[44] == 0xF5;
}

static void parseLd2420EnergyFrame(const uint8_t *frame, uint32_t nowMs) {
  uint16_t gates[16];
  for (size_t i = 0; i < 16; i++) {
    size_t offset = 9 + i * 2;
    gates[i] = (uint16_t)frame[offset] | ((uint16_t)frame[offset + 1] << 8);
  }

  bool zoneHit = false;
  uint8_t strongestGate = radarMinimumGate;
  uint16_t strongestEnergy = 0;
  if (radarUartMutex != NULL &&
      xSemaphoreTake(radarUartMutex, pdMS_TO_TICKS(10)) == pdTRUE) {
    memcpy(radarGateEnergy, gates, sizeof(radarGateEnergy));
    const uint8_t firstGate = radarMinimumGate > 15 ? 15 : radarMinimumGate;
    const uint8_t lastGate = radarMaximumGate > 15 ? 15 : radarMaximumGate;
    for (uint8_t gate = firstGate; gate <= lastGate; gate++) {
      const uint16_t threshold = radarZoneActive
          ? radarMaintainThreshold[gate]
          : radarTriggerThreshold[gate];
      if (gates[gate] > strongestEnergy) {
        strongestEnergy = gates[gate];
        strongestGate = gate;
      }
      // Zero means that a threshold is not configured, never "always active".
      if (threshold > 0 && gates[gate] >= threshold) {
        zoneHit = true;
      }
    }
    if (radarCalibrationActive) {
      for (size_t i = 0; i < 16; i++) {
        if (gates[i] > radarCalibrationPeak[i]) {
          radarCalibrationPeak[i] = gates[i];
        }
      }
      radarCalibrationSampleCount++;
    }
    xSemaphoreGive(radarUartMutex);
  }

  if (zoneHit) {
    radarZoneActive = true;
    radarZoneLastDetectionMs = nowMs;
  } else if (radarZoneActive) {
    const uint32_t holdMs = (uint32_t)radarPresenceDelay * 1000U;
    if (holdMs == 0 || nowMs - radarZoneLastDetectionMs > holdMs) {
      radarZoneActive = false;
    }
  }

  radarEnergyPresent = radarZoneActive;
  if (radarZoneActive) {
    const uint16_t sensorDistance =
        (uint16_t)frame[7] | ((uint16_t)frame[8] << 8);
    const uint16_t minDistanceCm = (uint16_t)radarMinimumGate * 70U;
    const uint16_t maxDistanceCm = ((uint16_t)radarMaximumGate + 1U) * 70U;
    radarEnergyDistanceCm =
        sensorDistance >= minDistanceCm && sensorDistance <= maxDistanceCm
            ? sensorDistance
            : (uint16_t)strongestGate * 70U;
  } else {
    radarEnergyDistanceCm = 0;
  }
  radarUartRange = radarEnergyDistanceCm;
  radarUartPresent = radarEnergyPresent;
  radarEnergyFrameCount++;
  radarUartFrameCount++;
  radarUartLastRxMs = nowMs;
}

void readRadarUart() {
  static uint8_t frame[160];
  static size_t frameLen = 0;
  static uint32_t lastByteMs = 0;
  static uint32_t lastRadarUartLogMs = 0;
  bool gotByte = false;
  uint32_t nowMs = millis();

  while (Serial1.available() > 0) {
    int value = Serial1.read();
    if (value < 0) {
      break;
    }
    gotByte = true;
    lastByteMs = nowMs;
    radarUartByteCount++;
    radarUartLastRxMs = nowMs;

    if (frameLen < sizeof(frame)) {
      frame[frameLen++] = (uint8_t)value;
    } else {
      memmove(frame, frame + 1, sizeof(frame) - 1);
      frame[sizeof(frame) - 1] = (uint8_t)value;
    }

    while (frameLen >= 45) {
      size_t start = frameLen;
      for (size_t i = 0; i + 3 < frameLen; i++) {
        if (frame[i] == 0xF4 && frame[i + 1] == 0xF3 &&
            frame[i + 2] == 0xF2 && frame[i + 3] == 0xF1) {
          start = i;
          break;
        }
      }
      if (start == frameLen) {
        break;
      }
      if (start > 0) {
        memmove(frame, frame + start, frameLen - start);
        frameLen -= start;
      }
      if (frameLen < 45) {
        break;
      }
      if (isLd2420EnergyFrame(frame, 45)) {
        parseLd2420EnergyFrame(frame, nowMs);
        memmove(frame, frame + 45, frameLen - 45);
        frameLen -= 45;
      } else {
        break;
      }
    }
  }

  bool radarUartStale =
      radarUartLastRxMs == 0 || nowMs - radarUartLastRxMs >= 10000;
  if (radarUartStale && nowMs - lastRadarUartBaudSwitchMs >= 4000) {
    radarUartBaudIndex =
        (radarUartBaudIndex + 1) %
        (sizeof(RADAR_UART_BAUD_CANDIDATES) /
         sizeof(RADAR_UART_BAUD_CANDIDATES[0]));
    beginRadarUart(RADAR_UART_BAUD_CANDIDATES[radarUartBaudIndex]);
    sendLd2420InitCommands();
  }

  if (frameLen == 0 || (!gotByte && nowMs - lastByteMs < 30)) {
    if (nowMs - lastRadarUartLogMs >= RADAR_UART_LOG_INTERVAL_MS &&
        radarEnergyFrameCount > 0) {
      lastRadarUartLogMs = nowMs;
      uint16_t gates[16];
      getRadarGateEnergySnapshot(gates, 16);
      uint16_t maxEnergy = 0;
      size_t maxGate = 0;
      for (size_t i = 0; i < 16; i++) {
        if (gates[i] > maxEnergy) {
          maxEnergy = gates[i];
          maxGate = i;
        }
      }
      remoteLogf("[RADAR UART] energy frames=%lu present=%u distance=%ucm max_gate=%u max_energy=%u\n",
                 (unsigned long)radarEnergyFrameCount,
                 radarEnergyPresent ? 1 : 0,
                 (unsigned int)radarEnergyDistanceCm,
                 (unsigned int)maxGate,
                 (unsigned int)maxEnergy);
    }
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
  if (nowMs - lastRadarUartLogMs >= RADAR_UART_LOG_INTERVAL_MS) {
    lastRadarUartLogMs = nowMs;
    remoteLogf("[RADAR UART] bytes=%lu frame=%lu text=\"%s\" range=%d hex=%s\n",
               (unsigned long)radarUartByteCount,
               (unsigned long)radarUartFrameCount, text, radarUartRange, hex);
  }
  frameLen = 0;
}

void readAlarmSensors() {
  bool radarNow = false;
  int lightNow = 0;
#if RADAR_PIN_TEST
  radarNow = digitalRead(RADAR_PIN) == (RADAR_ACTIVE_HIGH ? HIGH : LOW);
#endif
#if LIGHT_SENSOR_PIN_TEST
  lightNow = analogRead(LIGHT_SENSOR_PIN);
#endif
  bool daylightNow = lightNow >= LIGHT_DAY_THRESHOLD;
  uint32_t nowMs = millis();

#if RADAR_UART_TEST
  // Fresh energy frames are authoritative. This makes the configured
  // distance gates and their thresholds control the real alarm state.
  if (radarEnergyFrameCount > 0 && radarUartLastRxMs > 0 &&
      nowMs - radarUartLastRxMs < 1500) {
    radarNow = radarEnergyPresent;
  }
#endif

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
      if (localRadarBuzzerEnabled &&
          (lastLocalRadarBuzzerMs == 0 ||
           nowMs - lastLocalRadarBuzzerMs >= DARK_ALARM_COOLDOWN_MS)) {
        lastLocalRadarBuzzerMs = nowMs;
        localRadarBuzzerCount++;
        pulseBuzzerFor(DARK_ALARM_PULSE_MS);
        remoteLogf("[ALARM] local radar buzzer #%lu pulse=%ums\n",
                   (unsigned long)localRadarBuzzerCount,
                   (unsigned int)DARK_ALARM_PULSE_MS);
      }
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

static void sendSimpleHttpJson(WiFiClient &client, const char *body) {
  client.print("HTTP/1.1 200 OK\r\n");
  client.print("Content-Type: application/json\r\n");
  client.print("Access-Control-Allow-Origin: *\r\n");
  client.print("Cache-Control: no-store\r\n");
  client.print("Connection: close\r\n");
  client.print("Content-Length: ");
  client.print(strlen(body));
  client.print("\r\n\r\n");
  client.print(body);
}

static void handleSimpleHttpClient() {
  WiFiClient client = simpleHttpServer.available();
  if (!client) {
    return;
  }

  uint32_t startMs = millis();
  String requestLine;
  while (client.connected() && millis() - startMs < 250) {
    if (client.available()) {
      requestLine = client.readStringUntil('\n');
      break;
    }
    delay(1);
  }

  while (client.available()) {
    client.read();
  }

  char body[768];
  if (requestLine.indexOf("/sensors") >= 0) {
    snprintf(body, sizeof(body),
             "{\"radar_active\":false,\"daylight\":false,\"light_raw\":0,"
             "\"light_day_threshold\":%d,\"radar_motion_count\":0,"
             "\"dark_alarm_count\":0,\"local_radar_buzzer_count\":0,"
             "\"last_motion_ms\":0,\"sensor_age_ms\":0,"
             "\"buzzer_active\":false,\"buzzer_alarm_enabled\":false,"
             "\"local_radar_buzzer_enabled\":false,\"uptime_ms\":%lu,"
             "\"radar_uart_baud\":0,\"radar_uart_bytes\":0,"
             "\"radar_uart_frames\":0,\"radar_energy_frames\":0,"
             "\"radar_energy_present\":false,\"radar_energy_distance_cm\":0,"
             "\"radar_gate_energy\":[],\"radar_uart_present\":false,"
             "\"radar_uart_range\":-1}",
             LIGHT_DAY_THRESHOLD, (unsigned long)millis());
  } else {
    snprintf(body, sizeof(body),
             "{\"wifi_connected\":%u,\"wifi_ssid\":\"%s\","
             "\"wifi_ip\":\"%s\",\"wifi_gateway\":\"%s\","
             "\"wifi_rssi\":%d,\"camera_ready\":false,"
             "\"xclk\":0,\"pixformat\":0,\"framesize\":0,\"quality\":0,"
             "\"brightness\":0,\"contrast\":0,\"saturation\":0,"
             "\"hmirror\":0,\"vflip\":0}",
             WiFi.status() == WL_CONNECTED ? 1 : 0, WiFi.SSID().c_str(),
             WiFi.localIP().toString().c_str(),
             WiFi.gatewayIP().toString().c_str(), WiFi.RSSI());
  }
  sendSimpleHttpJson(client, body);
  client.stop();
}

const char *wifiDisconnectReasonName(uint8_t reason) {
  switch (reason) {
  case 2:
    return "AUTH_EXPIRE";
  case 3:
    return "AUTH_LEAVE";
  case 4:
    return "ASSOC_EXPIRE";
  case 8:
    return "ASSOC_LEAVE";
  case 15:
    return "4WAY_HANDSHAKE_TIMEOUT";
  case 23:
    return "IE_INVALID";
  case 36:
    return "HANDSHAKE_TIMEOUT_OR_AUTH";
  case 200:
    return "BEACON_TIMEOUT";
  case 201:
    return "NO_AP_FOUND";
  case 202:
    return "AUTH_FAIL";
  case 203:
    return "ASSOC_FAIL";
  case 204:
    return "HANDSHAKE_TIMEOUT";
  case 205:
    return "CONNECTION_FAIL";
  case 207:
    return "AP_TSF_RESET";
  case 208:
    return "ROAMING";
  default:
    return "UNKNOWN";
  }
}

void onWiFiEvent(WiFiEvent_t event, WiFiEventInfo_t info) {
  switch (event) {
  case ARDUINO_EVENT_WIFI_STA_CONNECTED:
    remoteLogf("[WIFI] sta_connected ssid=\"%s\" channel=%d\n",
               WiFi.SSID().c_str(), WiFi.channel());
    break;
  case ARDUINO_EVENT_WIFI_STA_DISCONNECTED:
    wifiDisconnectCount++;
    wifiDisconnectedSinceMs = wifiDisconnectedSinceMs == 0 ? millis() : wifiDisconnectedSinceMs;
    remoteLogf("[WIFI] disconnected reason=%u (%s) total=%lu status=%d ssid=\"%s\"\n",
               info.wifi_sta_disconnected.reason,
               wifiDisconnectReasonName(info.wifi_sta_disconnected.reason),
               (unsigned long)wifiDisconnectCount, WiFi.status(),
               WiFi.SSID().c_str());
    break;
  case ARDUINO_EVENT_WIFI_STA_GOT_IP:
    wifiGotIpCount++;
    wifiDisconnectedSinceMs = 0;
    remoteLogf("[WIFI] got_ip total=%lu ip=%s\n",
               (unsigned long)wifiGotIpCount,
               WiFi.localIP().toString().c_str());
    break;
  default:
    break;
  }
}

static bool hasWifiSsid(const char *networkSsid) {
  return networkSsid != NULL && strlen(networkSsid) > 0;
}

static void configureWifiStationRadio() {
  WiFi.mode(WIFI_STA);
  WiFi.setHostname("esp32s3-EED1C8");
  WiFi.setSleep(false);
  WiFi.setAutoReconnect(false);
  WiFi.persistent(false);
  // MJPEG is uplink-heavy. Use the permitted maximum transmit power so ACKs
  // and stream data have the best possible link budget. This does not replace
  // good AP coverage, but avoids artificially limiting the camera radio.
  WiFi.setTxPower(WIFI_POWER_19_5dBm);
  wifi_country_t wifiCountry = {
      .cc = "DE",
      .schan = 1,
      .nchan = 13,
      .policy = WIFI_COUNTRY_POLICY_AUTO,
  };
  esp_wifi_set_country(&wifiCountry);
  esp_wifi_set_ps(WIFI_PS_NONE);
}

static void resetWifiRadioForReconnect(const char *label) {
  remoteLogf("[WIFI] radio reset before profile=%s\n", label);
  WiFi.disconnect(false, false);
  delay(300);
  WiFi.mode(WIFI_OFF);
  delay(900);
  configureWifiStationRadio();
  delay(200);
}

static bool connectToWifiNetwork(const char *label, const char *networkSsid,
                                 const char *networkPassword,
                                 uint32_t timeoutMs) {
  bool isAndroidHotspot = strcmp(label, "android_hotspot") == 0;
  if (!hasWifiSsid(networkSsid)) {
    remoteLogf("[WIFI] skip profile=%s because SSID is empty\n", label);
    return false;
  }

  remoteLogf("[WIFI] profile=%s start ssid=\"%s\" timeout=%lums\n", label,
             networkSsid, (unsigned long)timeoutMs);
  resetWifiRadioForReconnect(label);

  bool targetFound = false;
  int targetChannel = 0;
  int targetRssi = -1000;
  uint8_t targetBssid[6] = {0};
  if (isAndroidHotspot) {
    remoteLogf("[WIFI] profile=%s debug scan for ssid=\"%s\"\n", label,
               networkSsid);
    int networkCount = WiFi.scanNetworks(false, true);
    remoteLogf("[WIFI] profile=%s debug scan complete networks=%d\n", label,
               networkCount);
    for (int i = 0; i < networkCount; i++) {
      if (WiFi.SSID(i) == networkSsid) {
        targetFound = true;
        remoteLogf("[WIFI] hotspot visible bssid=%s rssi=%d channel=%d enc=%d\n",
                   WiFi.BSSIDstr(i).c_str(), WiFi.RSSI(i), WiFi.channel(i),
                   WiFi.encryptionType(i));
        if (WiFi.RSSI(i) > targetRssi) {
          targetRssi = WiFi.RSSI(i);
          targetChannel = WiFi.channel(i);
          memcpy(targetBssid, WiFi.BSSID(i), sizeof(targetBssid));
        }
      }
    }
    if (!targetFound) {
      remoteLogf("[WIFI] hotspot not visible in scan ssid=\"%s\"\n",
                 networkSsid);
    }
    WiFi.scanDelete();
  } else {
    remoteLogf("[WIFI] profile=%s scanning for ssid=\"%s\"\n", label,
               networkSsid);
    int networkCount = WiFi.scanNetworks(false, true);
    remoteLogf("[WIFI] profile=%s scan complete networks=%d\n", label,
               networkCount);
    for (int i = 0; i < networkCount; i++) {
      if (WiFi.SSID(i) == networkSsid) {
        targetFound = true;
        remoteLogf("[WIFI] scan found target ssid=\"%s\" bssid=%s rssi=%d channel=%d enc=%d\n",
                   WiFi.SSID(i).c_str(), WiFi.BSSIDstr(i).c_str(), WiFi.RSSI(i),
                   WiFi.channel(i), WiFi.encryptionType(i));
        if (WiFi.RSSI(i) > targetRssi) {
          targetRssi = WiFi.RSSI(i);
          targetChannel = WiFi.channel(i);
          memcpy(targetBssid, WiFi.BSSID(i), sizeof(targetBssid));
        }
      }
    }
    if (targetFound) {
      char bssidText[18];
      snprintf(bssidText, sizeof(bssidText), "%02X:%02X:%02X:%02X:%02X:%02X",
               targetBssid[0], targetBssid[1], targetBssid[2], targetBssid[3],
               targetBssid[4], targetBssid[5]);
      remoteLogf("[WIFI] selected target ssid=\"%s\" bssid=%s rssi=%d channel=%d\n",
                 networkSsid, bssidText, targetRssi, targetChannel);
    }
    if (!targetFound) {
      remoteLogf("[WIFI] scan did not find target ssid=\"%s\" networks=%d\n",
                 networkSsid, networkCount);
    }
    WiFi.scanDelete();
  }

  uint32_t startMs = millis();
  uint8_t attempt = 0;
  while (WiFi.status() != WL_CONNECTED && millis() - startMs < timeoutMs) {
    attempt++;
    if (attempt > 1) {
      resetWifiRadioForReconnect(label);
    }

    if (isAndroidHotspot && hasWifiSsid(demoStaticIp) &&
        hasWifiSsid(demoGatewayIp)) {
      IPAddress localIp;
      IPAddress gatewayIp;
      IPAddress subnetMask;
      if (localIp.fromString(demoStaticIp) &&
          gatewayIp.fromString(demoGatewayIp) &&
          subnetMask.fromString(demoSubnetMask)) {
        WiFi.config(localIp, gatewayIp, subnetMask, gatewayIp);
        remoteLogf("[WIFI] static demo ip=%s gateway=%s subnet=%s\n",
                   demoStaticIp, demoGatewayIp, demoSubnetMask);
      } else {
        remoteLogln("[WIFI] invalid static demo IP config, using DHCP");
      }
    } else {
      WiFi.config(INADDR_NONE, INADDR_NONE, INADDR_NONE);
      remoteLogf("[WIFI] profile=%s using DHCP\n", label);
    }

    bool usePinnedBssid = targetFound && targetChannel > 0;
    if (isAndroidHotspot) {
      usePinnedBssid = usePinnedBssid && (attempt % 2 == 0);
    }
    if (usePinnedBssid) {
      remoteLogf("[WIFI] profile=%s attempt=%u begin pinned bssid channel=%d\n",
                 label, attempt, targetChannel);
      WiFi.begin(networkSsid, networkPassword, targetChannel, targetBssid);
    } else {
      remoteLogf("[WIFI] profile=%s attempt=%u begin without pinned BSSID\n",
                 label, attempt);
      WiFi.begin(networkSsid, networkPassword);
    }

    uint32_t attemptStartMs = millis();
    uint32_t lastProgressMs = attemptStartMs;
    while (WiFi.status() != WL_CONNECTED &&
           millis() - attemptStartMs < 15000 &&
           millis() - startMs < timeoutMs) {
      delay(500);
      uint32_t nowMs = millis();
      if (nowMs - lastProgressMs >= 2500) {
        lastProgressMs = nowMs;
        remoteLogf("[WIFI] profile=%s attempt=%u waiting status=%d elapsed=%lums total=%lums\n",
                   label, attempt, WiFi.status(),
                   (unsigned long)(nowMs - attemptStartMs),
                   (unsigned long)(nowMs - startMs));
      } else {
        Serial.print(".");
      }
    }
    Serial.println();

    if (WiFi.status() != WL_CONNECTED && millis() - startMs < timeoutMs) {
      remoteLogf("[WIFI] profile=%s attempt=%u stuck status=%d, restarting radio\n",
                 label, attempt, WiFi.status());
    }
  }

  if (WiFi.status() == WL_CONNECTED) {
    strncpy(lastConnectedWifiProfile, label, sizeof(lastConnectedWifiProfile));
    lastConnectedWifiProfile[sizeof(lastConnectedWifiProfile) - 1] = '\0';
    wifiReconnectBackoffMs = 10000;
    nextWifiReconnectAttemptMs = 0;
    lastWifiReconnectAttemptMs = 0;
    remoteLogf("[WIFI] connected profile=%s ssid=\"%s\" ip=%s rssi=%d\n",
               label, WiFi.SSID().c_str(), WiFi.localIP().toString().c_str(),
               WiFi.RSSI());
    return true;
  }

  remoteLogf("[WIFI] profile=%s failed status=%d elapsed=%lums\n", label,
             WiFi.status(), (unsigned long)(millis() - startMs));
  return false;
}

static bool connectToConfiguredWifi(uint32_t managerTimeoutMs = 0) {
  remoteLogf("[WIFI] connect manager start normal_configured=%u hotspot_configured=%u demo_only=%u\n",
             hasWifiSsid(ssid) ? 1 : 0, hasWifiSsid(demoSsid) ? 1 : 0,
             demoWifiOnly ? 1 : 0);
  configureWifiStationRadio();

  uint32_t managerStartMs = millis();
  while (WiFi.status() != WL_CONNECTED) {
    if (managerTimeoutMs > 0 && millis() - managerStartMs >= managerTimeoutMs) {
      remoteLogf("[WIFI] connect manager timeout elapsed=%lums status=%d\n",
                 (unsigned long)(millis() - managerStartMs), WiFi.status());
      return false;
    }

    bool demoIsDifferent =
        hasWifiSsid(demoSsid) && (!hasWifiSsid(ssid) || strcmp(demoSsid, ssid) != 0);

    if (demoWifiOnly) {
      remoteLogln("[WIFI] demoWifiOnly enabled; trying android_hotspot only");
      if (connectToWifiNetwork("android_hotspot", demoSsid, demoPassword,
                               90000)) {
        remoteLogln("[WIFI] android_hotspot connected");
        return true;
      }
      remoteLogln("[WIFI] android_hotspot failed, retrying...");
      delay(2000);
      continue;
    }

    bool preferHotspot = demoIsDifferent &&
                         strcmp(lastConnectedWifiProfile, "android_hotspot") == 0;

    if (preferHotspot) {
      remoteLogln("[WIFI] trying previous android_hotspot first");
      if (connectToWifiNetwork("android_hotspot", demoSsid, demoPassword,
                               30000)) {
        remoteLogln("[WIFI] android_hotspot connected");
        return true;
      }
      remoteLogln("[WIFI] previous android_hotspot failed, trying normal_wifi");
    }

    remoteLogln("[WIFI] trying normal_wifi");
    if (connectToWifiNetwork("normal_wifi", ssid, password, 30000)) {
      remoteLogln("[WIFI] normal_wifi connected");
      return true;
    }

    if (!demoIsDifferent) {
      remoteLogln("[WIFI] android_hotspot skipped: not configured or same SSID as normal_wifi");
    } else {
      remoteLogln("[WIFI] normal_wifi failed; trying android_hotspot");
      if (connectToWifiNetwork("android_hotspot", demoSsid, demoPassword,
                               30000)) {
        remoteLogln("[WIFI] android_hotspot connected");
        return true;
      }
      remoteLogln("[WIFI] android_hotspot failed");
    }
    remoteLogln("[WIFI] no configured network reachable, retrying...");
    delay(2000);
  }
  return true;
}

bool initCameraHardware() {
#if CAMERA_DISABLED_TEST
  remoteLogln("[CAMERA-TEST] camera init disabled; HTTP/logs/radar remain active");
  return false;
#else
  struct CameraProbeConfig {
    const char *name;
    uint32_t xclk;
    framesize_t frameSize;
    camera_grab_mode_t grabMode;
    camera_fb_location_t fbLocation;
    uint8_t quality;
    uint8_t fbCount;
  };

  const bool hasPsram = psramFound();
  remoteLogf("[CAMERA] psram=%u free_heap=%u free_psram=%u\n",
             hasPsram ? 1 : 0, (unsigned int)ESP.getFreeHeap(),
             (unsigned int)ESP.getPsramSize());

  // VGA provides the best useful detail once the WLAN has enough headroom.
  // At a weak RSSI it would turn a live stream into a slow sequence of stale
  // frames, so start directly with QVGA in that case. CAMERA_GRAB_LATEST and
  // two PSRAM buffers deliberately trade dropped frames for low latency.
  const int initialRssi = WiFi.status() == WL_CONNECTED ? WiFi.RSSI() : -127;
  // The measured Android-hotspot link sustains VGA at -73 dBm. Keep a small
  // margin below that real-world result; below -75 dBm QVGA remains the
  // low-latency fallback.
  const bool linkSupportsVga = initialRssi >= -75;
  const CameraProbeConfig probes[] = {
      {"psram_vga_20_latest", 20000000, FRAMESIZE_VGA,
       CAMERA_GRAB_LATEST, CAMERA_FB_IN_PSRAM, 14, 2},
      {"psram_qvga_15_latest", 15000000, FRAMESIZE_QVGA,
       CAMERA_GRAB_LATEST, CAMERA_FB_IN_PSRAM, 18, 2},
      {"psram_qvga_15_empty", 15000000, FRAMESIZE_QVGA,
       CAMERA_GRAB_WHEN_EMPTY, CAMERA_FB_IN_PSRAM, 18, 1},
      {"psram_qvga_20_latest", 20000000, FRAMESIZE_QVGA,
       CAMERA_GRAB_LATEST, CAMERA_FB_IN_PSRAM, 18, 2},
      {"dram_qqvga_10_empty", 10000000, FRAMESIZE_QQVGA,
       CAMERA_GRAB_WHEN_EMPTY, CAMERA_FB_IN_DRAM, 20, 1},
  };

  const size_t firstProbe = linkSupportsVga ? 0 : 1;
  remoteLogf("[CAMERA] link rssi=%d profile=%s\n", initialRssi,
             linkSupportsVga ? "VGA" : "QVGA");

  for (size_t i = firstProbe; i < sizeof(probes) / sizeof(probes[0]); i++) {
    if (probes[i].fbLocation == CAMERA_FB_IN_PSRAM && !hasPsram) {
      continue;
    }

    esp_camera_deinit();
    delay(150);

    camera_config_t config;
    memset(&config, 0, sizeof(config));
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
    config.xclk_freq_hz = probes[i].xclk;
    config.frame_size = probes[i].frameSize;
    config.pixel_format = PIXFORMAT_JPEG;
    config.grab_mode = probes[i].grabMode;
    config.fb_location = probes[i].fbLocation;
    config.jpeg_quality = probes[i].quality;
    config.fb_count = probes[i].fbCount;

    remoteLogf("[CAMERA] probe=%s xclk=%lu frame=%u fb_count=%u loc=%u\n",
               probes[i].name, (unsigned long)probes[i].xclk,
               (unsigned int)probes[i].frameSize,
               (unsigned int)probes[i].fbCount,
               (unsigned int)probes[i].fbLocation);

    esp_err_t err = esp_camera_init(&config);
    if (err != ESP_OK) {
      remoteLogf("[CAMERA] probe=%s init failed err=0x%x\n", probes[i].name,
                 err);
      continue;
    }

    sensor_t *s = esp_camera_sensor_get();
    if (!s) {
      remoteLogf("[CAMERA] probe=%s sensor missing after init\n",
                 probes[i].name);
      continue;
    }
    if (s->id.PID == OV3660_PID) {
      s->set_vflip(s, 0);
      s->set_hmirror(s, 1);
      s->set_brightness(s, 1);
      s->set_saturation(s, -2);
    }

    remoteLogf("[CAMERA] probe=%s init ok pid=0x%x framesize=%u quality=%u\n",
               probes[i].name, s->id.PID, s->status.framesize,
               s->status.quality);
    for (int attempt = 1; attempt <= 5; attempt++) {
      delay(150);
      camera_fb_t *warmup = esp_camera_fb_get();
      if (warmup) {
        remoteLogf("[CAMERA] probe=%s frame ok attempt=%d len=%u format=%u\n",
                   probes[i].name, attempt, (unsigned int)warmup->len,
                   (unsigned int)warmup->format);
        esp_camera_fb_return(warmup);
        return true;
      }
      remoteLogf("[CAMERA] probe=%s fb_get failed attempt=%d heap=%u\n",
                 probes[i].name, attempt, (unsigned int)ESP.getFreeHeap());
    }
  }

  remoteLogln("[CAMERA] all probes failed: init works but no frames available");
  return true;
#endif
}

void setup() {
  Serial.begin(115200);
  Serial.setDebugOutput(false);
  initRemoteLogBuffer();
  Serial.println();
  remoteLogln("[BOOT] ESP32-S3 camera boot");
  remoteLogf("[BOOT] reset_reason=%d heap=%u\n", esp_reset_reason(),
             (unsigned int)ESP.getFreeHeap());
  remoteLogln("[BOOT] USB serial log active at 115200 baud");
  settingsPrefs.begin("vision", false);
  radarMinimumGate = settingsPrefs.getUChar("radar_min", radarMinimumGate);
  radarMaximumGate = settingsPrefs.getUChar("radar_max", radarMaximumGate);
  radarPresenceDelay = settingsPrefs.getUChar("radar_delay", radarPresenceDelay);
  if (settingsPrefs.getBytesLength("radar_trigger") == sizeof(radarTriggerThreshold)) {
    settingsPrefs.getBytes("radar_trigger", radarTriggerThreshold,
                           sizeof(radarTriggerThreshold));
  }
  if (settingsPrefs.getBytesLength("radar_maintain") == sizeof(radarMaintainThreshold)) {
    settingsPrefs.getBytes("radar_maintain", radarMaintainThreshold,
                           sizeof(radarMaintainThreshold));
  }
  localRadarBuzzerEnabled = settingsPrefs.getBool("radar_buzzer", false);
  remoteLogf("[ALARM] local radar buzzer enabled=%u\n",
             localRadarBuzzerEnabled ? 1 : 0);

#if BUZZER_PIN_TEST
  digitalWrite(BUZZER_PIN, BUZZER_OFF_LEVEL);
  pinMode(BUZZER_PIN, OUTPUT);
#else
  remoteLogln("[PERIPHERAL-TEST] buzzer pin disabled");
#endif

#if RADAR_PIN_TEST
  pinMode(RADAR_PIN, RADAR_ACTIVE_HIGH ? INPUT_PULLDOWN : INPUT_PULLUP);
#else
  remoteLogln("[PERIPHERAL-TEST] radar digital pin disabled");
#endif

#if LIGHT_SENSOR_PIN_TEST
  pinMode(LIGHT_SENSOR_PIN, INPUT);
#else
  remoteLogln("[PERIPHERAL-TEST] light sensor pin disabled");
#endif

  // Bring WiFi up before camera/radar init so the ESP remains reachable even
  // if a peripheral blocks or fails during startup.
  WiFi.onEvent(onWiFiEvent);
  connectToConfiguredWifi();
  remoteLogln("WiFi verbunden!");
  esp_wifi_set_ps(WIFI_PS_NONE);
  remoteLogln("WLAN Power Management deaktiviert fuer stabilen Stream");

#if SIMPLE_HTTP_TEST
  simpleHttpServer.begin();
  remoteLogln("[SIMPLE-HTTP-TEST] minimal /status and /sensors server active");
  return;
#endif

#if WIFI_DEBUG_ONLY
  remoteLogln("[WIFI-DEBUG] minimal firmware active: camera/http/radar disabled");
  return;
#endif

  bool cameraOk = initCameraHardware();

  // Start HTTP after the camera had a chance to produce its first frame. This
  // avoids early stream requests racing the camera driver during boot.
  startCameraServer();

  if (!cameraOk) {
    return;
  }

#if RADAR_UART_TEST
  beginRadarUart(RADAR_UART_BAUD);
  sendLd2420InitCommands();
  applyRadarRangeSettings(radarMinimumGate, radarMaximumGate,
                          radarPresenceDelay);
#else
  remoteLogln("[PERIPHERAL-TEST] radar UART disabled");
#endif

#if 0
#if CAMERA_DISABLED_TEST
  remoteLogln("[CAMERA-TEST] camera init disabled; HTTP/logs/radar remain active");
  return;
#endif

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
  config.frame_size = FRAMESIZE_QVGA;
  config.pixel_format = PIXFORMAT_JPEG;
  config.grab_mode = CAMERA_GRAB_LATEST;
  config.fb_location = CAMERA_FB_IN_PSRAM;
  config.jpeg_quality = 18;
  config.fb_count = 2;

  // PSRAM Check (Essentiell für S3-WROOM)
  if (psramFound()) {
    config.jpeg_quality = 18;
    config.fb_count = 2;
    config.frame_size = FRAMESIZE_QVGA;
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
    s->set_vflip(s, 0);
    s->set_hmirror(s, 1);
    s->set_brightness(s, 1);
    s->set_saturation(s, -2);
  }

  Serial.print("Kamera bereit! Öffne: http://");
#endif
  Serial.println(WiFi.localIP());
}

void loop() {
  static uint32_t lastSensorReadLoopMs = 0;
  static uint32_t lastRadarUartReadLoopMs = 0;

  uint32_t nowMs = millis();

  if (WiFi.status() == WL_CONNECTED) {
    wifiDisconnectedSinceMs = 0;
    nextWifiReconnectAttemptMs = 0;
    wifiReconnectBackoffMs = 10000;
  } else {
    if (wifiDisconnectedSinceMs == 0) {
      wifiDisconnectedSinceMs = nowMs;
      nextWifiReconnectAttemptMs = nowMs + 10000;
      remoteLogln("[WIFI] disconnected in loop, scheduling clean reconnect in 10000ms");
    }
    uint32_t disconnectedForMs = nowMs - wifiDisconnectedSinceMs;
    if (nextWifiReconnectAttemptMs != 0 &&
        (int32_t)(nowMs - nextWifiReconnectAttemptMs) >= 0) {
      lastWifiReconnectAttemptMs = nowMs;
      remoteLogf("[WIFI] clean reconnect attempt after %lums disconnected backoff=%lums\n",
                 (unsigned long)disconnectedForMs,
                 (unsigned long)wifiReconnectBackoffMs);
      bool reconnected = connectToConfiguredWifi(120000);
      if (reconnected && WiFi.status() == WL_CONNECTED) {
        wifiDisconnectedSinceMs = 0;
        nextWifiReconnectAttemptMs = 0;
        wifiReconnectBackoffMs = 10000;
      } else {
        uint32_t failedAtMs = millis();
        wifiDisconnectedSinceMs = failedAtMs;
        wifiReconnectBackoffMs =
            wifiReconnectBackoffMs < 60000 ? wifiReconnectBackoffMs * 2 : 60000;
        nextWifiReconnectAttemptMs = failedAtMs + wifiReconnectBackoffMs;
        remoteLogf("[WIFI] reconnect failed, next attempt in %lums\n",
                   (unsigned long)wifiReconnectBackoffMs);
      }
    }
  }

  if (nowMs - lastWifiStatusLogMs >=
#if WIFI_DEBUG_ONLY
      2000
#else
      5000
#endif
  ) {
    lastWifiStatusLogMs = nowMs;
    if (WiFi.status() == WL_CONNECTED) {
      remoteLogf("[WIFI] status ip=%s ssid=\"%s\" bssid=%s rssi=%d channel=%d disc=%lu gotip=%lu heap=%u\n",
                 WiFi.localIP().toString().c_str(), WiFi.SSID().c_str(),
                 WiFi.BSSIDstr().c_str(), WiFi.RSSI(), WiFi.channel(),
                 (unsigned long)wifiDisconnectCount,
                 (unsigned long)wifiGotIpCount,
                 (unsigned int)ESP.getFreeHeap());
    } else {
      remoteLogf("[WIFI] status disconnected status=%d disc=%lu gotip=%lu heap=%u\n",
                 WiFi.status(), (unsigned long)wifiDisconnectCount,
                 (unsigned long)wifiGotIpCount,
                 (unsigned int)ESP.getFreeHeap());
    }
  }

#if WIFI_DEBUG_ONLY
  delay(50);
  return;
#endif

#if SIMPLE_HTTP_TEST
  handleSimpleHttpClient();
  delay(10);
  return;
#endif

#if RADAR_UART_TEST
  if (nowMs - lastRadarUartReadLoopMs >= RADAR_UART_READ_INTERVAL_MS) {
    lastRadarUartReadLoopMs = nowMs;
    readRadarUart();
  }
#endif

#if RADAR_PIN_TEST || LIGHT_SENSOR_PIN_TEST
  if (nowMs - lastSensorReadLoopMs >= SENSOR_READ_INTERVAL_MS) {
    lastSensorReadLoopMs = nowMs;
    readAlarmSensors();
  }
#endif

#if BUZZER_PIN_TEST
  uint32_t pulseUntil = buzzerPulseUntilMs;
  if (pulseUntil != 0 && (int32_t)(millis() - pulseUntil) >= 0) {
    digitalWrite(BUZZER_PIN, BUZZER_OFF_LEVEL);
    buzzerPulseUntilMs = 0;
  }
#endif

  delay(5);
}

