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

extern volatile uint32_t wifiDisconnectCount;
extern volatile uint32_t wifiGotIpCount;
extern volatile uint32_t buzzerPulseUntilMs;
extern volatile uint32_t sensorLastReadMs;
extern volatile uint32_t radarLastMotionMs;
extern volatile uint32_t radarMotionCount;
extern volatile uint32_t darkAlarmCount;
extern volatile int lightRaw;
extern volatile bool radarActive;
extern volatile bool daylightActive;
extern void remoteLogf(const char *format, ...);
extern void remoteLogln(const char *message);
extern String getRemoteLogSnapshot(bool clearAfterRead);

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
  httpd_resp_set_hdr(req, "Access-Control-Allow-Origin", "*");

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
  fb = esp_camera_fb_get();
#endif
  if (!fb) {
    httpd_resp_send_500(req);
    return ESP_FAIL;
  }
  httpd_resp_set_type(req, "image/jpeg");
  httpd_resp_set_hdr(req, "Access-Control-Allow-Origin", "*");

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
  if (res != ESP_OK)
    return res;

  httpd_resp_set_hdr(req, "Access-Control-Allow-Origin", "*");

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

    vTaskDelay(1);
  }

#if defined(LED_GPIO_NUM)
  isStreaming = false;
  enable_led(false);
#endif
  last_frame = 0;
  last_log = 0;
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
  httpd_resp_set_hdr(req, "Access-Control-Allow-Origin", "*");
  return httpd_resp_send(req, NULL, 0);
}

static esp_err_t status_handler(httpd_req_t *req) {
  static char json_response[1024];
  sensor_t *s = esp_camera_sensor_get();
  char *p = json_response;
  *p++ = '{';
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
  httpd_resp_set_hdr(req, "Access-Control-Allow-Origin", "*");
  return httpd_resp_send(req, json_response, strlen(json_response));
}

static esp_err_t sensors_handler(httpd_req_t *req) {
  uint32_t now = millis();
  uint32_t last_read = sensorLastReadMs;
  uint32_t last_motion = radarLastMotionMs;
  uint32_t buzzer_until = buzzerPulseUntilMs;

  char response[384];
  int response_len = snprintf(
      response, sizeof(response),
      "{\"radar_active\":%s,\"daylight\":%s,\"light_raw\":%d,"
      "\"light_day_threshold\":%d,\"radar_motion_count\":%lu,"
      "\"dark_alarm_count\":%lu,\"last_motion_ms\":%lu,"
      "\"sensor_age_ms\":%lu,\"buzzer_active\":%s,\"uptime_ms\":%lu}",
      radarActive ? "true" : "false", daylightActive ? "true" : "false",
      lightRaw, LIGHT_DAY_THRESHOLD, (unsigned long)radarMotionCount,
      (unsigned long)darkAlarmCount, (unsigned long)last_motion,
      (unsigned long)(now - last_read),
      (buzzer_until != 0 && (int32_t)(now - buzzer_until) < 0) ? "true"
                                                               : "false",
      (unsigned long)now);

  httpd_resp_set_type(req, "application/json");
  httpd_resp_set_hdr(req, "Access-Control-Allow-Origin", "*");
  httpd_resp_set_hdr(req, "Cache-Control", "no-store");
  return httpd_resp_send(req, response, response_len);
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
  httpd_resp_set_hdr(req, "Access-Control-Allow-Origin", "*");
  return httpd_resp_send(req, NULL, 0);
}

static esp_err_t index_handler(httpd_req_t *req) {
  httpd_resp_set_type(req, "text/html");
  httpd_resp_set_hdr(req, "Content-Encoding", "gzip");
  sensor_t *s = esp_camera_sensor_get();
  if (s->id.PID == OV3660_PID)
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
  httpd_resp_set_hdr(req, "Access-Control-Allow-Origin", "*");
  httpd_resp_set_hdr(req, "Cache-Control", "no-store");
  return httpd_resp_send(req, snapshot.c_str(), snapshot.length());
}

static esp_err_t buzzer_handler(httpd_req_t *req) {
  char *buf = NULL;
  size_t buf_len = httpd_req_get_url_query_len(req) + 1;
  char state[16] = "off";
  char duration_param[16] = "0";
  uint32_t duration_ms = 0;

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
    }
    free(buf);
  }

  duration_ms = (uint32_t)atoi(duration_param);

  if (strcmp(state, "on") == 0) {
    digitalWrite(BUZZER_PIN, HIGH);
    buzzerPulseUntilMs = duration_ms > 0 ? millis() + duration_ms : 0;
    remoteLogf("Buzzer ON duration_ms=%lu\n", (unsigned long)duration_ms);
  } else {
    digitalWrite(BUZZER_PIN, LOW);
    buzzerPulseUntilMs = 0;
    remoteLogln("Buzzer OFF");
  }

  char response[96];
  int response_len = snprintf(
      response, sizeof(response), "{\"state\":\"%s\",\"duration_ms\":%lu}",
      strcmp(state, "on") == 0 ? "on" : "off", (unsigned long)duration_ms);

  httpd_resp_set_type(req, "application/json");
  httpd_resp_set_hdr(req, "Access-Control-Allow-Origin", "*");
  return httpd_resp_send(req, response, response_len);
}

void startCameraServer() {
  httpd_config_t config = HTTPD_DEFAULT_CONFIG();
  config.send_wait_timeout = 1;
  config.recv_wait_timeout = 1;
  config.lru_purge_enable = true;
  config.max_open_sockets = 2;
  config.backlog_conn = 1;
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
  httpd_uri_t stream_uri = {.uri = "/stream",
                            .method = HTTP_GET,
                            .handler = stream_handler,
                            .user_ctx = NULL};
  httpd_uri_t buzzer_uri = {.uri = "/buzzer",
                            .method = HTTP_GET,
                            .handler = buzzer_handler,
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
  }

  config.server_port += 1;
  config.ctrl_port += 1;
  if (httpd_start(&stream_httpd, &config) == ESP_OK) {
    httpd_register_uri_handler(stream_httpd, &stream_uri);
  }
}

void setupLedFlash() {
#if defined(LED_GPIO_NUM)
  ledcAttach(LED_GPIO_NUM, 5000, 8);
#endif
}
