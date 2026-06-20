#line 1 "C:\\Users\\Julius\\Documents\\Arduino\\CameraWebServer_ESP32S3_OV3660\\app_httpd.cpp"
// Copyright 2015-2016 Espressif Systems (Shanghai) PTE LTD
#include "Arduino.h"
#include "board_config.h"
#include "camera_index.h"
#include "esp32-hal-ledc.h"
#include "esp_camera.h"
#include "esp_heap_caps.h"
#include "esp_http_server.h"
#include "esp_timer.h"
#include "esp_wifi.h"
#include "fb_gfx.h"
#include "img_converters.h"
#include "sdkconfig.h"
#include <WiFi.h>
#include <Preferences.h>

#if defined(ARDUINO_ARCH_ESP32) && defined(CONFIG_ARDUHAL_ESP_LOG)
#include "esp32-hal-log.h"
#endif

// LED FLASH setup
#if defined(LED_GPIO_NUM)
#define CONFIG_LED_MAX_INTENSITY 255
int led_duty = 0;
bool isStreaming = false;
#endif

// Buzzer setup
#ifndef BUZZER_PIN
#define BUZZER_PIN 2 // Default Pin for Buzzer - can be changed
#endif

typedef struct {
  httpd_req_t *req;
  size_t len;
} jpg_chunking_t;

#define PART_BOUNDARY "123456789000000000000987654321"
static const char *_STREAM_CONTENT_TYPE =
    "multipart/x-mixed-replace;boundary=" PART_BOUNDARY;
static const char *_STREAM_BOUNDARY = "\r\n--" PART_BOUNDARY "\r\n";
static const char *_STREAM_PART =
    "Content-Type: image/jpeg\r\nContent-Length: %u\r\nX-Timestamp: "
    "%d.%06d\r\nX-RSSI: %d\r\n\r\n";

httpd_handle_t stream_httpd = NULL;
httpd_handle_t camera_httpd = NULL;
static volatile bool streamClientActive = false;
static volatile uint32_t streamRestartCount = 0;

static void startStreamServer();

static void set_close_headers(httpd_req_t *req) {
  httpd_resp_set_hdr(req, "Access-Control-Allow-Origin", "*");
  httpd_resp_set_hdr(req, "Connection", "close");
}

static void set_stream_headers(httpd_req_t *req) {
  httpd_resp_set_hdr(req, "Access-Control-Allow-Origin", "*");
  httpd_resp_set_hdr(req, "Cache-Control", "no-store, no-cache, must-revalidate");
  httpd_resp_set_hdr(req, "Pragma", "no-cache");
}

extern volatile uint32_t wifiDisconnectCount;
extern volatile uint32_t wifiGotIpCount;
extern volatile uint32_t buzzerPulseUntilMs;
extern volatile bool buzzerAlarmEnabled;
extern volatile bool localRadarBuzzerEnabled;
extern Preferences settingsPrefs;
extern volatile uint32_t sensorLastReadMs;
extern volatile uint32_t radarLastMotionMs;
extern volatile uint32_t radarMotionCount;
extern volatile uint32_t darkAlarmCount;
extern volatile uint32_t localRadarBuzzerCount;
extern volatile uint32_t radarUartByteCount;
extern volatile uint32_t radarUartLastRxMs;
extern volatile uint32_t radarUartFrameCount;
extern volatile uint32_t radarUartCurrentBaud;
extern volatile uint32_t radarEnergyFrameCount;
extern volatile int radarUartRange;
extern volatile uint16_t radarEnergyDistanceCm;
extern volatile int lightRaw;
extern volatile bool radarActive;
extern volatile bool daylightActive;
extern volatile bool radarUartPresent;
extern volatile bool radarEnergyPresent;
extern void remoteLogf(const char *format, ...);
extern void remoteLogln(const char *message);
extern String getRemoteLogSnapshot(bool clearAfterRead);
extern String getRadarUartLastHexSnapshot();
extern String getRadarUartLastTextSnapshot();
extern void getRadarGateEnergySnapshot(uint16_t *out, size_t count);
extern bool startRadarCalibration(float moveFactor, float stillFactor);
extern bool cancelRadarCalibration();
extern bool applyRadarCalibration();
extern bool applyRadarRangeSettings(uint8_t minimumGate, uint8_t maximumGate,
                                    uint8_t presenceDelay);
extern bool applyRadarGateThresholdSettings(uint8_t gate, uint16_t trigger,
                                            uint16_t maintain);
extern void getRadarCalibrationStatus(bool *active, bool *ready, bool *applied,
                                      uint32_t *elapsedMs, uint32_t *sampleCount,
                                      float *moveFactor, float *stillFactor,
                                      uint16_t *peaks, size_t peakCount);
extern void getRadarRangeSettings(uint8_t *minimumGate, uint8_t *maximumGate,
                                  uint8_t *presenceDelay);
extern void getRadarGateThresholdSettings(uint16_t *trigger, uint16_t *maintain,
                                          size_t count);
extern bool initCameraHardware();

typedef struct {
  size_t size;
  size_t index;
  size_t count;
  int sum;
  int *values;
} ra_filter_t;

static ra_filter_t ra_filter;

static ra_filter_t *ra_filter_init(ra_filter_t *filter, size_t sample_size) {
  memset(filter, 0, sizeof(ra_filter_t));
  filter->values = (int *)malloc(sample_size * sizeof(int));
  if (!filter->values)
    return NULL;
  memset(filter->values, 0, sample_size * sizeof(int));
  filter->size = sample_size;
  return filter;
}

// FPS Filter: Jetzt immer aktiv, unabhängig vom Log-Level
static int ra_filter_run(ra_filter_t *filter, int value) {
  if (!filter->values)
    return value;
  filter->sum -= filter->values[filter->index];
  filter->values[filter->index] = value;
  filter->sum += filter->values[filter->index];
  filter->index++;
  filter->index = filter->index % filter->size;
  if (filter->count < filter->size)
    filter->count++;
  return filter->sum / filter->count;
}

#if defined(LED_GPIO_NUM)
void enable_led(bool en) {
  int duty = en ? led_duty : 0;
  if (en && isStreaming && (led_duty > CONFIG_LED_MAX_INTENSITY))
    duty = CONFIG_LED_MAX_INTENSITY;
  ledcWrite(LED_GPIO_NUM, duty);
}
#endif

// BMP Handler
static esp_err_t bmp_handler(httpd_req_t *req) {
  camera_fb_t *fb = esp_camera_fb_get();
  if (!fb) {
    httpd_resp_send_500(req);
    return ESP_FAIL;
  }
  httpd_resp_set_type(req, "image/x-windows-bmp");
  httpd_resp_set_hdr(req, "Content-Disposition",
                     "inline; filename=capture.bmp");
  set_close_headers(req);

  uint8_t *buf = NULL;
  size_t buf_len = 0;
  bool converted = frame2bmp(fb, &buf, &buf_len);
  esp_camera_fb_return(fb);
  if (!converted)
    return ESP_FAIL;
  esp_err_t res = httpd_resp_send(req, (const char *)buf, buf_len);
  free(buf);
  return res;
}

static size_t jpg_encode_stream(void *arg, size_t index, const void *data,
                                size_t len) {
  jpg_chunking_t *j = (jpg_chunking_t *)arg;
  if (!index)
    j->len = 0;
  if (httpd_resp_send_chunk(j->req, (const char *)data, len) != ESP_OK)
    return 0;
  j->len += len;
  return len;
}

// Capture Handler (Einzelbild)
static esp_err_t capture_handler(httpd_req_t *req) {
  camera_fb_t *fb = NULL;
#if defined(LED_GPIO_NUM)
  enable_led(true);
  vTaskDelay(150 / portTICK_PERIOD_MS);
  fb = esp_camera_fb_get();
  enable_led(false);
#else
  // A camera frame can be in flight just after boot or while a stream client
  // releases its buffer. A short retry avoids surfacing that as HTTP 500.
  for (uint8_t attempt = 0; attempt < 3 && !fb; attempt++) {
    fb = esp_camera_fb_get();
    if (!fb) {
      vTaskDelay(pdMS_TO_TICKS(20));
    }
  }
#endif
  if (!fb) {
    remoteLogf("[CAPTURE] fb_get failed heap=%u psram=%u stream_active=%u\n",
               (unsigned int)ESP.getFreeHeap(),
               (unsigned int)heap_caps_get_free_size(MALLOC_CAP_SPIRAM),
               streamClientActive ? 1 : 0);
    httpd_resp_send_500(req);
    return ESP_FAIL;
  }
  httpd_resp_set_type(req, "image/jpeg");
  set_close_headers(req);

  esp_err_t res = ESP_OK;
  if (fb->format == PIXFORMAT_JPEG) {
    res = httpd_resp_send(req, (const char *)fb->buf, fb->len);
  } else {
    jpg_chunking_t jchunk = {req, 0};
    res = frame2jpg_cb(fb, 80, jpg_encode_stream, &jchunk) ? ESP_OK : ESP_FAIL;
    httpd_resp_send_chunk(req, NULL, 0);
  }
  esp_camera_fb_return(fb);
  return res;
}

// DER OPTIMIERTE STREAM HANDLER
static esp_err_t stream_handler(httpd_req_t *req) {
  if (streamClientActive) {
    httpd_resp_set_status(req, "503 Busy");
    httpd_resp_set_type(req, "text/plain");
    set_close_headers(req);
    return httpd_resp_sendstr(req, "stream already active");
  }
  streamClientActive = true;
  remoteLogln("[STREAM] client connected");

  camera_fb_t *fb = NULL;
  struct timeval _timestamp;
  esp_err_t res = ESP_OK;
  size_t _jpg_buf_len = 0;
  uint8_t *_jpg_buf = NULL;
  // char *part_buf[128]; // Moved to inner scope with larger size
  static int64_t last_frame = 0;
  static int64_t last_log = 0;
  static uint32_t fb_fail_count = 0;
  static uint32_t send_fail_count = 0;
  int current_rssi = 0; // Declare here to use in both stream and serial

  if (!last_frame)
    last_frame = esp_timer_get_time();

  res = httpd_resp_set_type(req, _STREAM_CONTENT_TYPE);
  if (res != ESP_OK) {
    streamClientActive = false;
    return res;
  }

  // This response remains open for the lifetime of the MJPEG stream. Do not
  // advertise Connection: close here; some clients treat it as a short-lived
  // response and reconnect unnecessarily.
  set_stream_headers(req);

#if defined(LED_GPIO_NUM)
  isStreaming = true;
#endif

  while (true) {
    uint32_t fb_get_ms = 0;
    uint32_t send_ms = 0;
    bool send_attempted = false;
    int64_t fb_start = esp_timer_get_time();
    _jpg_buf_len = 0;

    // Fetch RSSI at start of loop
    wifi_ap_record_t wifi_info;
    if (esp_wifi_sta_get_ap_info(&wifi_info) == ESP_OK) {
      current_rssi = (int)wifi_info.rssi; // Cast int8_t to int properly
    }

    fb = esp_camera_fb_get();
    fb_get_ms = (uint32_t)((esp_timer_get_time() - fb_start) / 1000);
    if (!fb) {
      fb_fail_count++;
      remoteLogf("[STREAM] fb_get failed count=%lu ms=%u heap=%u psram=%u\n",
                 (unsigned long)fb_fail_count, (unsigned int)fb_get_ms,
                 (unsigned int)ESP.getFreeHeap(),
                 (unsigned int)heap_caps_get_free_size(MALLOC_CAP_SPIRAM));
      res = ESP_FAIL;
  } else {
    _timestamp.tv_sec = fb->timestamp.tv_sec;
    _timestamp.tv_usec = fb->timestamp.tv_usec;
    if (fb->format != PIXFORMAT_JPEG) {
        bool jpeg_converted = frame2jpg(fb, 80, &_jpg_buf, &_jpg_buf_len);
        esp_camera_fb_return(fb);
        fb = NULL;
      if (!jpeg_converted)
        res = ESP_FAIL;
    } else {
      _jpg_buf_len = fb->len;
      // JPEG frames already live in PSRAM. Keep this buffer until the HTTP
      // chunks are sent; copying it per frame costs both FPS and heap churn.
      _jpg_buf = fb->buf;
    }
    }

    int64_t send_start = esp_timer_get_time();
    if (res == ESP_OK) {
      send_attempted = true;
      res = httpd_resp_send_chunk(req, _STREAM_BOUNDARY,
                                  strlen(_STREAM_BOUNDARY));
    }
    if (res == ESP_OK) {
      // Use larger buffer to ensure no truncation
      char part_buf[256];
      size_t hlen = snprintf((char *)part_buf, 256, _STREAM_PART, _jpg_buf_len,
                             (int)_timestamp.tv_sec, (int)_timestamp.tv_usec,
                             current_rssi);
      res = httpd_resp_send_chunk(req, (const char *)part_buf, hlen);
    }
    if (res == ESP_OK)
      res = httpd_resp_send_chunk(req, (const char *)_jpg_buf, _jpg_buf_len);
    if (send_attempted)
      send_ms = (uint32_t)((esp_timer_get_time() - send_start) / 1000);

    if (fb) {
      esp_camera_fb_return(fb);
      fb = NULL;
      _jpg_buf = NULL;
    } else if (_jpg_buf) {
      // frame2jpg() allocated this only for a non-JPEG camera format.
      free(_jpg_buf);
      _jpg_buf = NULL;
    }

    if (res != ESP_OK) {
      if (send_attempted)
        send_fail_count++;
      break;
    }

    // --- FPS BERECHNUNG & AUSGABE ---
    int64_t fr_end = esp_timer_get_time();
    int64_t frame_time = (fr_end - last_frame) / 1000;
    last_frame = fr_end;

    uint32_t avg_frame_time = ra_filter_run(&ra_filter, frame_time);

    // Serielle Ausgabe pro Frame bremst lange Streams stark aus.
    if ((fr_end - last_log) >= 1000000) {
      uint32_t free_heap = ESP.getFreeHeap();
      uint32_t free_psram =
          (uint32_t)heap_caps_get_free_size(MALLOC_CAP_SPIRAM);
      last_log = fr_end;
      remoteLogf(
          "STAT t=%lu size=%uB frame=%ums avg=%ums fb=%ums send=%ums fps=%.1f "
          "rssi=%d heap=%u psram=%u disc=%lu gotip=%lu fb_fail=%lu send_fail=%lu\n",
          millis(), (uint32_t)_jpg_buf_len, (uint32_t)frame_time,
          avg_frame_time, fb_get_ms, send_ms,
          avg_frame_time > 0 ? (1000.0f / avg_frame_time) : 0.0f, current_rssi,
          free_heap, free_psram, (unsigned long)wifiDisconnectCount,
          (unsigned long)wifiGotIpCount, (unsigned long)fb_fail_count,
          (unsigned long)send_fail_count);
    }

    if (frame_time > 500 || fb_get_ms > 250 || send_ms > 250) {
      remoteLogf(
          "SPIKE t=%lu size=%uB frame=%ums fb=%ums send=%ums rssi=%d heap=%u "
          "psram=%u\n",
          millis(), (uint32_t)_jpg_buf_len, (uint32_t)frame_time, fb_get_ms,
          send_ms, current_rssi, ESP.getFreeHeap(),
          (uint32_t)heap_caps_get_free_size(MALLOC_CAP_SPIRAM));
    }

    // Yield to Wi-Fi and the camera driver without imposing an 8 FPS cap.
    vTaskDelay(pdMS_TO_TICKS(10));
  }

#if defined(LED_GPIO_NUM)
  isStreaming = false;
  enable_led(false);
#endif
  last_frame = 0;
  last_log = 0;
  streamClientActive = false;
  remoteLogf("[STREAM] client disconnected res=%d send_fail=%lu fb_fail=%lu\n",
             (int)res, (unsigned long)send_fail_count,
             (unsigned long)fb_fail_count);
  return res;
}

// CMD, STATUS, REG & INDEX Handler bleiben weitgehend gleich...
static esp_err_t parse_get(httpd_req_t *req, char **obuf) {
  char *buf = NULL;
  size_t buf_len = httpd_req_get_url_query_len(req) + 1;
  if (buf_len > 1) {
    buf = (char *)malloc(buf_len);
    if (!buf) {
      httpd_resp_send_500(req);
      return ESP_FAIL;
    }
    if (httpd_req_get_url_query_str(req, buf, buf_len) == ESP_OK) {
      *obuf = buf;
      return ESP_OK;
    }
    free(buf);
  }
  httpd_resp_send_404(req);
  return ESP_FAIL;
}

static esp_err_t cmd_handler(httpd_req_t *req) {
  char *buf = NULL;
  char variable[32];
  char value[32];
  if (parse_get(req, &buf) != ESP_OK)
    return ESP_FAIL;
  if (httpd_query_key_value(buf, "var", variable, sizeof(variable)) != ESP_OK ||
      httpd_query_key_value(buf, "val", value, sizeof(value)) != ESP_OK) {
    free(buf);
    httpd_resp_send_404(req);
    return ESP_FAIL;
  }
  free(buf);
  int val = atoi(value);
  sensor_t *s = esp_camera_sensor_get();
  if (!s) {
    httpd_resp_set_hdr(req, "Access-Control-Allow-Origin", "*");
    return httpd_resp_send(req, NULL, 0);
  }
  int res = 0;

  if (!strcmp(variable, "framesize")) {
    if (s->pixformat == PIXFORMAT_JPEG)
      res = s->set_framesize(s, (framesize_t)val);
  } else if (!strcmp(variable, "quality"))
    res = s->set_quality(s, val);
  else if (!strcmp(variable, "contrast"))
    res = s->set_contrast(s, val);
  else if (!strcmp(variable, "brightness"))
    res = s->set_brightness(s, val);
  else if (!strcmp(variable, "saturation"))
    res = s->set_saturation(s, val);
  else if (!strcmp(variable, "awb"))
    res = s->set_whitebal(s, val);
  else if (!strcmp(variable, "agc"))
    res = s->set_gain_ctrl(s, val);
  else if (!strcmp(variable, "aec"))
    res = s->set_exposure_ctrl(s, val);
  else if (!strcmp(variable, "hmirror"))
    res = s->set_hmirror(s, val);
  else if (!strcmp(variable, "vflip"))
    res = s->set_vflip(s, val);
#if defined(LED_GPIO_NUM)
  else if (!strcmp(variable, "led_intensity")) {
    led_duty = val;
    if (isStreaming)
      enable_led(true);
  }
#endif
  else
    res = -1;

  if (res < 0)
    return httpd_resp_send_500(req);
  set_close_headers(req);
  return httpd_resp_send(req, NULL, 0);
}

static esp_err_t status_handler(httpd_req_t *req) {
  static char json_response[1024];
  sensor_t *s = esp_camera_sensor_get();
  String wifiSsid = WiFi.SSID();
  String localIp = WiFi.localIP().toString();
  String gatewayIp = WiFi.gatewayIP().toString();
  char *p = json_response;
  *p++ = '{';
  p += sprintf(p, "\"wifi_connected\":%u,", WiFi.status() == WL_CONNECTED ? 1 : 0);
  p += sprintf(p, "\"wifi_ssid\":\"%s\",", wifiSsid.c_str());
  p += sprintf(p, "\"wifi_ip\":\"%s\",", localIp.c_str());
  p += sprintf(p, "\"wifi_gateway\":\"%s\",", gatewayIp.c_str());
  p += sprintf(p, "\"wifi_rssi\":%d,", WiFi.RSSI());
  p += sprintf(p, "\"stream_active\":%s,", streamClientActive ? "true" : "false");
  p += sprintf(p, "\"stream_restarts\":%lu,", (unsigned long)streamRestartCount);
  p += sprintf(p, "\"camera_ready\":%s,", s ? "true" : "false");
  if (!s) {
    p += sprintf(p, "\"xclk\":0,");
    p += sprintf(p, "\"pixformat\":0,");
    p += sprintf(p, "\"framesize\":0,");
    p += sprintf(p, "\"quality\":0,");
    p += sprintf(p, "\"brightness\":0,");
    p += sprintf(p, "\"contrast\":0,");
    p += sprintf(p, "\"saturation\":0,");
    p += sprintf(p, "\"hmirror\":0,");
    p += sprintf(p, "\"vflip\":0");
#if defined(LED_GPIO_NUM)
    p += sprintf(p, ",\"led_intensity\":%u", led_duty);
#endif
    *p++ = '}';
    *p++ = 0;
    httpd_resp_set_type(req, "application/json");
    set_close_headers(req);
    return httpd_resp_send(req, json_response, strlen(json_response));
  }
  p += sprintf(p, "\"xclk\":%u,", s->xclk_freq_hz / 1000000);
  p += sprintf(p, "\"pixformat\":%u,", s->pixformat);
  p += sprintf(p, "\"framesize\":%u,", s->status.framesize);
  p += sprintf(p, "\"quality\":%u,", s->status.quality);
  p += sprintf(p, "\"brightness\":%d,", s->status.brightness);
  p += sprintf(p, "\"contrast\":%d,", s->status.contrast);
  p += sprintf(p, "\"saturation\":%d,", s->status.saturation);
  p += sprintf(p, "\"hmirror\":%u,", s->status.hmirror);
  p += sprintf(p, "\"vflip\":%u", s->status.vflip);
#if defined(LED_GPIO_NUM)
  p += sprintf(p, ",\"led_intensity\":%u", led_duty);
#endif
  *p++ = '}';
  *p++ = 0;
  httpd_resp_set_type(req, "application/json");
    set_close_headers(req);
  return httpd_resp_send(req, json_response, strlen(json_response));
}

static esp_err_t sensors_handler(httpd_req_t *req) {
  uint32_t now = millis();

#if !(RADAR_PIN_TEST || LIGHT_SENSOR_PIN_TEST || RADAR_UART_TEST || BUZZER_PIN_TEST)
  static char response[640];
  int response_len = snprintf(
      response, sizeof(response),
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
      "\"radar_uart_range\":-1,\"radar_uart_last_rx_ms\":0,"
      "\"radar_uart_age_ms\":0,\"radar_uart_last_text\":\"\","
      "\"radar_uart_last_hex\":\"\"}",
      LIGHT_DAY_THRESHOLD, (unsigned long)now);
  if (response_len < 0) {
    return httpd_resp_send_500(req);
  }
  if ((size_t)response_len >= sizeof(response)) {
    response_len = sizeof(response) - 1;
  }
  httpd_resp_set_type(req, "application/json");
  set_close_headers(req);
  httpd_resp_set_hdr(req, "Cache-Control", "no-store");
  return httpd_resp_send(req, response, response_len);
#else
  uint32_t last_read = sensorLastReadMs;
  uint32_t last_motion = radarLastMotionMs;
  uint32_t radar_uart_last_rx = radarUartLastRxMs;
  uint32_t buzzer_until = buzzerPulseUntilMs;
  String radar_uart_hex = getRadarUartLastHexSnapshot();
  String radar_uart_text = getRadarUartLastTextSnapshot();
  uint16_t gate_energy[16];
  getRadarGateEnergySnapshot(gate_energy, 16);
  bool calibration_active = false;
  bool calibration_ready = false;
  bool calibration_applied = false;
  uint32_t calibration_elapsed_ms = 0;
  uint32_t calibration_samples = 0;
  float calibration_move_factor = 0.0f;
  float calibration_still_factor = 0.0f;
  uint16_t calibration_peaks[16];
  getRadarCalibrationStatus(
      &calibration_active, &calibration_ready, &calibration_applied,
      &calibration_elapsed_ms, &calibration_samples, &calibration_move_factor,
      &calibration_still_factor, calibration_peaks, 16);
  uint8_t radar_minimum_gate = 0;
  uint8_t radar_maximum_gate = 15;
  uint8_t radar_presence_delay = 0;
  getRadarRangeSettings(&radar_minimum_gate, &radar_maximum_gate,
                        &radar_presence_delay);
  uint16_t radar_trigger_threshold[16];
  uint16_t radar_maintain_threshold[16];
  getRadarGateThresholdSettings(radar_trigger_threshold, radar_maintain_threshold,
                                16);

  static char gate_json[160];
  char *g = gate_json;
  size_t remaining = sizeof(gate_json);
  int written = snprintf(g, remaining, "[");
  g += written;
  remaining -= written;
  for (size_t i = 0; i < 16 && remaining > 1; i++) {
    written = snprintf(g, remaining, "%s%u", i == 0 ? "" : ",",
                       (unsigned int)gate_energy[i]);
    if (written < 0 || (size_t)written >= remaining) {
      break;
    }
    g += written;
    remaining -= written;
  }
  snprintf(g, remaining, "]");

  static char calibration_peaks_json[160];
  char *cp = calibration_peaks_json;
  remaining = sizeof(calibration_peaks_json);
  written = snprintf(cp, remaining, "[");
  cp += written;
  remaining -= written;
  for (size_t i = 0; i < 16 && remaining > 1; i++) {
    written = snprintf(cp, remaining, "%s%u", i == 0 ? "" : ",",
                       (unsigned int)calibration_peaks[i]);
    if (written < 0 || (size_t)written >= remaining) {
      break;
    }
    cp += written;
    remaining -= written;
  }
  snprintf(cp, remaining, "]");

  static char trigger_threshold_json[160];
  static char maintain_threshold_json[160];
  char *tt = trigger_threshold_json;
  char *mt = maintain_threshold_json;
  size_t trigger_remaining = sizeof(trigger_threshold_json);
  size_t maintain_remaining = sizeof(maintain_threshold_json);
  int trigger_written = snprintf(tt, trigger_remaining, "[");
  int maintain_written = snprintf(mt, maintain_remaining, "[");
  tt += trigger_written;
  mt += maintain_written;
  trigger_remaining -= trigger_written;
  maintain_remaining -= maintain_written;
  for (size_t i = 0; i < 16 && trigger_remaining > 1 && maintain_remaining > 1; i++) {
    trigger_written = snprintf(tt, trigger_remaining, "%s%u", i == 0 ? "" : ",",
                               (unsigned int)radar_trigger_threshold[i]);
    maintain_written = snprintf(mt, maintain_remaining, "%s%u", i == 0 ? "" : ",",
                                (unsigned int)radar_maintain_threshold[i]);
    if (trigger_written < 0 || maintain_written < 0 ||
        (size_t)trigger_written >= trigger_remaining ||
        (size_t)maintain_written >= maintain_remaining) {
      break;
    }
    tt += trigger_written;
    mt += maintain_written;
    trigger_remaining -= trigger_written;
    maintain_remaining -= maintain_written;
  }
  snprintf(tt, trigger_remaining, "]");
  snprintf(mt, maintain_remaining, "]");

  static char response[3072];
  int response_len = snprintf(
      response, sizeof(response),
      "{\"radar_active\":%s,\"daylight\":%s,\"light_raw\":%d,"
      "\"light_day_threshold\":%d,\"radar_motion_count\":%lu,"
      "\"dark_alarm_count\":%lu,\"local_radar_buzzer_count\":%lu,"
      "\"last_motion_ms\":%lu,"
      "\"sensor_age_ms\":%lu,\"buzzer_active\":%s,"
      "\"buzzer_alarm_enabled\":%s,\"local_radar_buzzer_enabled\":%s,"
      "\"uptime_ms\":%lu,"
      "\"radar_uart_baud\":%lu,\"radar_uart_bytes\":%lu,\"radar_uart_frames\":%lu,"
      "\"radar_energy_frames\":%lu,\"radar_energy_present\":%s,"
      "\"radar_energy_distance_cm\":%u,\"radar_gate_energy\":%s,"
      "\"radar_uart_present\":%s,\"radar_uart_range\":%d,"
      "\"radar_uart_last_rx_ms\":%lu,\"radar_uart_age_ms\":%lu,"
      "\"radar_uart_last_text\":\"%s\",\"radar_uart_last_hex\":\"%s\","
      "\"radar_calibration_active\":%s,\"radar_calibration_ready\":%s,"
      "\"radar_calibration_applied\":%s,\"radar_calibration_elapsed_ms\":%lu,"
      "\"radar_calibration_samples\":%lu,\"radar_calibration_move_factor\":%.2f,"
      "\"radar_calibration_still_factor\":%.2f,\"radar_calibration_peaks\":%s,"
      "\"radar_minimum_gate\":%u,\"radar_maximum_gate\":%u,"
      "\"radar_presence_delay\":%u,\"radar_trigger_threshold\":%s,"
      "\"radar_maintain_threshold\":%s}",
      radarActive ? "true" : "false", daylightActive ? "true" : "false",
      lightRaw, LIGHT_DAY_THRESHOLD, (unsigned long)radarMotionCount,
      (unsigned long)darkAlarmCount, (unsigned long)localRadarBuzzerCount,
      (unsigned long)last_motion,
      (unsigned long)(now - last_read),
      (buzzer_until != 0 && (int32_t)(now - buzzer_until) < 0) ? "true"
                                                               : "false",
      buzzerAlarmEnabled ? "true" : "false",
      localRadarBuzzerEnabled ? "true" : "false",
      (unsigned long)now, (unsigned long)radarUartCurrentBaud,
      (unsigned long)radarUartByteCount,
      (unsigned long)radarUartFrameCount,
      (unsigned long)radarEnergyFrameCount,
      radarEnergyPresent ? "true" : "false",
      (unsigned int)radarEnergyDistanceCm,
      gate_json,
      radarUartPresent ? "true" : "false", radarUartRange,
      (unsigned long)radar_uart_last_rx,
      radar_uart_last_rx == 0 ? 0 : (unsigned long)(now - radar_uart_last_rx),
      radar_uart_text.c_str(),
      radar_uart_hex.c_str(), calibration_active ? "true" : "false",
      calibration_ready ? "true" : "false",
      calibration_applied ? "true" : "false",
      (unsigned long)calibration_elapsed_ms,
      (unsigned long)calibration_samples, calibration_move_factor,
      calibration_still_factor, calibration_peaks_json, radar_minimum_gate,
      radar_maximum_gate, radar_presence_delay, trigger_threshold_json,
      maintain_threshold_json);
  if (response_len < 0) {
    return httpd_resp_send_500(req);
  }
  if ((size_t)response_len >= sizeof(response)) {
    response_len = sizeof(response) - 1;
  }

  httpd_resp_set_type(req, "application/json");
  set_close_headers(req);
  httpd_resp_set_hdr(req, "Cache-Control", "no-store");
  return httpd_resp_send(req, response, response_len);
#endif
}

static esp_err_t radar_calibration_handler(httpd_req_t *req) {
  char *buf = NULL;
  char action[16] = "";
  if (parse_get(req, &buf) != ESP_OK) {
    return ESP_FAIL;
  }
  if (httpd_query_key_value(buf, "action", action, sizeof(action)) != ESP_OK) {
    free(buf);
    httpd_resp_send_404(req);
    return ESP_FAIL;
  }

  bool ok = false;
  if (!strcmp(action, "start")) {
    char move[16] = "0.5";
    char still[16] = "0.5";
    httpd_query_key_value(buf, "move_factor", move, sizeof(move));
    httpd_query_key_value(buf, "still_factor", still, sizeof(still));
    ok = startRadarCalibration(strtof(move, NULL), strtof(still, NULL));
  } else if (!strcmp(action, "cancel")) {
    ok = cancelRadarCalibration();
  } else if (!strcmp(action, "apply")) {
    ok = applyRadarCalibration();
  }
  free(buf);

  if (!ok) {
    httpd_resp_set_status(req, "409 Conflict");
    httpd_resp_set_type(req, "application/json");
    set_close_headers(req);
    return httpd_resp_sendstr(req, "{\"ok\":false}");
  }
  httpd_resp_set_type(req, "application/json");
  set_close_headers(req);
  return httpd_resp_sendstr(req, "{\"ok\":true}");
}

static esp_err_t radar_range_handler(httpd_req_t *req) {
  char *buf = NULL;
  char min_gate[8];
  char max_gate[8];
  char delay[8];
  if (parse_get(req, &buf) != ESP_OK) {
    return ESP_FAIL;
  }
  const bool valid =
      httpd_query_key_value(buf, "min_gate", min_gate, sizeof(min_gate)) == ESP_OK &&
      httpd_query_key_value(buf, "max_gate", max_gate, sizeof(max_gate)) == ESP_OK &&
      httpd_query_key_value(buf, "delay", delay, sizeof(delay)) == ESP_OK;
  if (!valid) {
    free(buf);
    httpd_resp_send_404(req);
    return ESP_FAIL;
  }
  const bool ok = applyRadarRangeSettings((uint8_t)atoi(min_gate),
                                          (uint8_t)atoi(max_gate),
                                          (uint8_t)atoi(delay));
  free(buf);
  httpd_resp_set_type(req, "application/json");
  set_close_headers(req);
  if (!ok) {
    httpd_resp_set_status(req, "409 Conflict");
    return httpd_resp_sendstr(req, "{\"ok\":false}");
  }
  return httpd_resp_sendstr(req, "{\"ok\":true}");
}

static esp_err_t radar_threshold_handler(httpd_req_t *req) {
  char *buf = NULL;
  char gate[8];
  char trigger[12];
  char maintain[12];
  if (parse_get(req, &buf) != ESP_OK) {
    return ESP_FAIL;
  }
  const bool valid =
      httpd_query_key_value(buf, "gate", gate, sizeof(gate)) == ESP_OK &&
      httpd_query_key_value(buf, "trigger", trigger, sizeof(trigger)) == ESP_OK &&
      httpd_query_key_value(buf, "maintain", maintain, sizeof(maintain)) == ESP_OK;
  if (!valid) {
    free(buf);
    httpd_resp_send_404(req);
    return ESP_FAIL;
  }
  const long trigger_value = strtol(trigger, NULL, 10);
  const long maintain_value = strtol(maintain, NULL, 10);
  const bool valid_values = trigger_value >= 0 && trigger_value <= 65535 &&
      maintain_value >= 0 && maintain_value <= 65535;
  const bool ok = valid_values && applyRadarGateThresholdSettings(
      (uint8_t)atoi(gate), (uint16_t)trigger_value, (uint16_t)maintain_value);
  free(buf);
  httpd_resp_set_type(req, "application/json");
  set_close_headers(req);
  if (!ok) {
    httpd_resp_set_status(req, "409 Conflict");
    return httpd_resp_sendstr(req, "{\"ok\":false}");
  }
  return httpd_resp_sendstr(req, "{\"ok\":true}");
}

static esp_err_t xclk_handler(httpd_req_t *req) {
  char *buf = NULL;
  char xclk_value[32];

  if (parse_get(req, &buf) != ESP_OK)
    return ESP_FAIL;
  if (httpd_query_key_value(buf, "xclk", xclk_value, sizeof(xclk_value)) !=
      ESP_OK) {
    free(buf);
    httpd_resp_send_404(req);
    return ESP_FAIL;
  }
  free(buf);

  sensor_t *s = esp_camera_sensor_get();
  int xclk = atoi(xclk_value);
  int res = s->set_xclk(s, LEDC_TIMER_0, xclk);

  if (res < 0)
    return httpd_resp_send_500(req);
  set_close_headers(req);
  return httpd_resp_send(req, NULL, 0);
}

static esp_err_t index_handler(httpd_req_t *req) {
  httpd_resp_set_type(req, "text/html");
  httpd_resp_set_hdr(req, "Content-Encoding", "gzip");
  sensor_t *s = esp_camera_sensor_get();
  if (s && s->id.PID == OV3660_PID)
    return httpd_resp_send(req, (const char *)index_ov3660_html_gz,
                           index_ov3660_html_gz_len);
  return httpd_resp_send(req, (const char *)index_ov2640_html_gz,
                         index_ov2640_html_gz_len);
}

static esp_err_t logs_handler(httpd_req_t *req) {
  char *buf = NULL;
  bool clear_after_read = false;
  size_t buf_len = httpd_req_get_url_query_len(req) + 1;

  if (buf_len > 1) {
    buf = (char *)malloc(buf_len);
    if (!buf) {
      httpd_resp_send_500(req);
      return ESP_FAIL;
    }
    if (httpd_req_get_url_query_str(req, buf, buf_len) == ESP_OK) {
      char clear_value[8];
      if (httpd_query_key_value(buf, "clear", clear_value,
                                sizeof(clear_value)) == ESP_OK) {
        clear_after_read = strcmp(clear_value, "1") == 0;
      }
    }
    free(buf);
  }

  String snapshot = getRemoteLogSnapshot(clear_after_read);
  if (snapshot.isEmpty()) {
    snapshot = "No logs yet.\n";
  }

  httpd_resp_set_type(req, "text/plain; charset=utf-8");
  set_close_headers(req);
  httpd_resp_set_hdr(req, "Cache-Control", "no-store");
  return httpd_resp_send(req, snapshot.c_str(), snapshot.length());
}

static esp_err_t buzzer_handler(httpd_req_t *req) {
  char *buf = NULL;
  size_t buf_len = httpd_req_get_url_query_len(req) + 1;
  char state[16] = "off";
  char duration_param[16] = "0";
  char alarm_enabled_param[16] = "";
  char radar_buzzer_param[16] = "";
  char force_param[8] = "0";
  uint32_t duration_ms = 0;
  bool has_alarm_enabled = false;
  bool has_radar_buzzer = false;

  if (buf_len > 1) {
    buf = (char *)malloc(buf_len);
    if (!buf) {
      httpd_resp_send_500(req);
      return ESP_FAIL;
    }
    if (httpd_req_get_url_query_str(req, buf, buf_len) == ESP_OK) {
      httpd_query_key_value(buf, "state", state, sizeof(state));
      httpd_query_key_value(buf, "duration_ms", duration_param,
                            sizeof(duration_param));
      has_alarm_enabled =
          httpd_query_key_value(buf, "alarm_enabled", alarm_enabled_param,
                                sizeof(alarm_enabled_param)) == ESP_OK;
      has_radar_buzzer =
          httpd_query_key_value(buf, "radar_buzzer", radar_buzzer_param,
                                sizeof(radar_buzzer_param)) == ESP_OK;
      httpd_query_key_value(buf, "force", force_param, sizeof(force_param));
    }
    free(buf);
  }

  duration_ms = (uint32_t)atoi(duration_param);

  if (has_radar_buzzer) {
    localRadarBuzzerEnabled = strcmp(radar_buzzer_param, "1") == 0 ||
                              strcmp(radar_buzzer_param, "true") == 0 ||
                              strcmp(radar_buzzer_param, "on") == 0;
    settingsPrefs.putBool("radar_buzzer", localRadarBuzzerEnabled);
    remoteLogf("[ALARM] local radar buzzer enabled=%u\n",
               localRadarBuzzerEnabled ? 1 : 0);
  }

  if (has_alarm_enabled) {
    buzzerAlarmEnabled = strcmp(alarm_enabled_param, "1") == 0 ||
                         strcmp(alarm_enabled_param, "true") == 0 ||
                         strcmp(alarm_enabled_param, "on") == 0;
    if (!buzzerAlarmEnabled) {
      digitalWrite(BUZZER_PIN, BUZZER_OFF_LEVEL);
      buzzerPulseUntilMs = 0;
    }
    remoteLogf("Buzzer alarm_enabled=%u\n", buzzerAlarmEnabled ? 1 : 0);
  } else if (strcmp(state, "on") == 0) {
    bool forced = strcmp(force_param, "1") == 0 ||
                  strcmp(force_param, "true") == 0 ||
                  strcmp(force_param, "on") == 0;
    if (duration_ms > 0 && !buzzerAlarmEnabled && !forced) {
      digitalWrite(BUZZER_PIN, BUZZER_OFF_LEVEL);
      buzzerPulseUntilMs = 0;
      remoteLogf("Buzzer alarm pulse suppressed duration_ms=%lu\n",
                 (unsigned long)duration_ms);
    } else {
    digitalWrite(BUZZER_PIN, BUZZER_ON_LEVEL);
    buzzerPulseUntilMs = duration_ms > 0 ? millis() + duration_ms : 0;
    remoteLogf("Buzzer ON duration_ms=%lu\n", (unsigned long)duration_ms);
    }
  } else {
    digitalWrite(BUZZER_PIN, BUZZER_OFF_LEVEL);
    buzzerPulseUntilMs = 0;
    remoteLogln("Buzzer OFF");
  }

  char response[160];
  int response_len = snprintf(response, sizeof(response),
                              "{\"state\":\"%s\",\"duration_ms\":%lu,"
                              "\"alarm_enabled\":%s,"
                              "\"local_radar_buzzer_enabled\":%s}",
                              strcmp(state, "on") == 0 ? "on" : "off",
                              (unsigned long)duration_ms,
                              buzzerAlarmEnabled ? "true" : "false",
                              localRadarBuzzerEnabled ? "true" : "false");

  httpd_resp_set_type(req, "application/json");
  set_close_headers(req);
  return httpd_resp_send(req, response, response_len);
}

static void startStreamServer() {
#if !CAMERA_DISABLED_TEST
  if (stream_httpd != NULL) {
    return;
  }

  httpd_config_t config = HTTPD_DEFAULT_CONFIG();
  config.stack_size = 8192;
  // A one-second send timeout tears down otherwise healthy streams whenever
  // 2.4 GHz Wi-Fi briefly retransmits. CAMERA_GRAB_LATEST prevents this from
  // building a stale-frame backlog while the socket catches up.
  config.send_wait_timeout = 5;
  config.recv_wait_timeout = 1;
  config.lru_purge_enable = true;
  config.max_open_sockets = 3;
  config.backlog_conn = 1;
  config.max_uri_handlers = 4;
  config.server_port = 81;
  config.ctrl_port = 32769;

  httpd_uri_t stream_uri = {.uri = "/stream",
                            .method = HTTP_GET,
                            .handler = stream_handler,
                            .user_ctx = NULL};

  if (httpd_start(&stream_httpd, &config) == ESP_OK) {
    httpd_register_uri_handler(stream_httpd, &stream_uri);
    remoteLogln("[STREAM] server started port=81");
  } else {
    stream_httpd = NULL;
    remoteLogln("[STREAM] server start failed");
  }
#else
  remoteLogln("[CAMERA-TEST] stream server disabled with camera disabled");
#endif
}

static esp_err_t stream_reset_handler(httpd_req_t *req) {
  httpd_handle_t old_stream = stream_httpd;
  stream_httpd = NULL;
  streamClientActive = false;
  streamRestartCount++;

  if (old_stream != NULL) {
    remoteLogf("[STREAM] reset requested count=%lu\n",
               (unsigned long)streamRestartCount);
    httpd_stop(old_stream);
  } else {
    remoteLogf("[STREAM] reset requested count=%lu old_server=none\n",
               (unsigned long)streamRestartCount);
  }
  startStreamServer();

  char response[96];
  int response_len = snprintf(response, sizeof(response),
                              "{\"stream_reset\":true,\"count\":%lu}",
                              (unsigned long)streamRestartCount);
  httpd_resp_set_type(req, "application/json");
  set_close_headers(req);
  return httpd_resp_send(req, response, response_len);
}

static esp_err_t camera_reset_handler(httpd_req_t *req) {
  httpd_handle_t old_stream = stream_httpd;
  stream_httpd = NULL;
  streamClientActive = false;
  streamRestartCount++;

  if (old_stream != NULL) {
    remoteLogf("[CAMERA] reset stopping stream count=%lu\n",
               (unsigned long)streamRestartCount);
    httpd_stop(old_stream);
  }

  bool ok = initCameraHardware();
  startStreamServer();

  char response[96];
  int response_len = snprintf(response, sizeof(response),
                              "{\"camera_reset\":%s,\"stream_restarts\":%lu}",
                              ok ? "true" : "false",
                              (unsigned long)streamRestartCount);
  httpd_resp_set_type(req, "application/json");
  set_close_headers(req);
  return httpd_resp_send(req, response, response_len);
}

void startCameraServer() {
  httpd_config_t config = HTTPD_DEFAULT_CONFIG();
  config.stack_size = 8192;
  config.send_wait_timeout = 1;
  config.recv_wait_timeout = 1;
  config.lru_purge_enable = true;
  config.max_open_sockets = 7;
  config.backlog_conn = 2;
  config.max_uri_handlers = 18;

  httpd_uri_t index_uri = {.uri = "/",
                           .method = HTTP_GET,
                           .handler = index_handler,
                           .user_ctx = NULL};
  httpd_uri_t status_uri = {.uri = "/status",
                            .method = HTTP_GET,
                            .handler = status_handler,
                            .user_ctx = NULL};
  httpd_uri_t xclk_uri = {.uri = "/xclk",
                          .method = HTTP_GET,
                          .handler = xclk_handler,
                          .user_ctx = NULL};
  httpd_uri_t cmd_uri = {.uri = "/control",
                         .method = HTTP_GET,
                         .handler = cmd_handler,
                         .user_ctx = NULL};
  httpd_uri_t capture_uri = {.uri = "/capture",
                             .method = HTTP_GET,
                             .handler = capture_handler,
                             .user_ctx = NULL};
  httpd_uri_t logs_uri = {.uri = "/logs",
                          .method = HTTP_GET,
                          .handler = logs_handler,
                          .user_ctx = NULL};
  httpd_uri_t sensors_uri = {.uri = "/sensors",
                             .method = HTTP_GET,
                             .handler = sensors_handler,
                             .user_ctx = NULL};
  httpd_uri_t buzzer_uri = {.uri = "/buzzer",
                            .method = HTTP_GET,
                             .handler = buzzer_handler,
                             .user_ctx = NULL};
  httpd_uri_t radar_calibration_uri = {.uri = "/radar/calibration",
                                       .method = HTTP_GET,
                                       .handler = radar_calibration_handler,
                                       .user_ctx = NULL};
  httpd_uri_t radar_range_uri = {.uri = "/radar/range",
                                 .method = HTTP_GET,
                                 .handler = radar_range_handler,
                                 .user_ctx = NULL};
  httpd_uri_t radar_threshold_uri = {.uri = "/radar/threshold",
                                     .method = HTTP_GET,
                                     .handler = radar_threshold_handler,
                                     .user_ctx = NULL};
  httpd_uri_t stream_reset_uri = {.uri = "/stream_reset",
                                  .method = HTTP_GET,
                                  .handler = stream_reset_handler,
                                  .user_ctx = NULL};
  httpd_uri_t camera_reset_uri = {.uri = "/camera_reset",
                                  .method = HTTP_GET,
                                  .handler = camera_reset_handler,
                                  .user_ctx = NULL};

  ra_filter_init(&ra_filter, 20);

  if (httpd_start(&camera_httpd, &config) == ESP_OK) {
    httpd_register_uri_handler(camera_httpd, &index_uri);
    httpd_register_uri_handler(camera_httpd, &cmd_uri);
    httpd_register_uri_handler(camera_httpd, &status_uri);
    httpd_register_uri_handler(camera_httpd, &xclk_uri);
    httpd_register_uri_handler(camera_httpd, &capture_uri);
    httpd_register_uri_handler(camera_httpd, &logs_uri);
    httpd_register_uri_handler(camera_httpd, &sensors_uri);
    httpd_register_uri_handler(camera_httpd, &buzzer_uri);
    httpd_register_uri_handler(camera_httpd, &radar_calibration_uri);
    httpd_register_uri_handler(camera_httpd, &radar_range_uri);
    httpd_register_uri_handler(camera_httpd, &radar_threshold_uri);
    httpd_register_uri_handler(camera_httpd, &stream_reset_uri);
    httpd_register_uri_handler(camera_httpd, &camera_reset_uri);
  }

  startStreamServer();
}

void setupLedFlash() {
#if defined(LED_GPIO_NUM)
  ledcAttach(LED_GPIO_NUM, 5000, 8);
#endif
}
