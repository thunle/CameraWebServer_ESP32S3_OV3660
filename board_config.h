#ifndef BOARD_CONFIG_H
#define BOARD_CONFIG_H

//
// WARNING!!! PSRAM IC required for UXGA resolution and high JPEG quality
//            Ensure ESP32 Wrover Module or other board with PSRAM is selected
//            Partial images will be transmitted if image exceeds buffer size
//
//            You must select partition scheme from the board menu that has at least 3MB APP space.

// ===================
// Select camera model
// ===================
//#define CAMERA_MODEL_WROVER_KIT // Has PSRAM
//#define CAMERA_MODEL_ESP_EYE  // Has PSRAM
#define CAMERA_MODEL_ESP32S3_EYE // Matches the Freenove ESP32-S3 CAM pinout
//#define CAMERA_MODEL_M5STACK_PSRAM // Has PSRAM
//#define CAMERA_MODEL_M5STACK_V2_PSRAM // M5Camera version B Has PSRAM
//#define CAMERA_MODEL_M5STACK_WIDE // Has PSRAM
//#define CAMERA_MODEL_M5STACK_ESP32CAM // No PSRAM
//#define CAMERA_MODEL_M5STACK_UNITCAM // No PSRAM
//#define CAMERA_MODEL_M5STACK_CAMS3_UNIT  // Has PSRAM
//#define CAMERA_MODEL_AI_THINKER // Has PSRAM
//#define CAMERA_MODEL_TTGO_T_JOURNAL // No PSRAM
//#define CAMERA_MODEL_XIAO_ESP32S3 // Has PSRAM
// ** Espressif Internal Boards **
//#define CAMERA_MODEL_ESP32_CAM_BOARD
//#define CAMERA_MODEL_ESP32S2_CAM_BOARD
//#define CAMERA_MODEL_ESP32S3_CAM_LCD
//#define CAMERA_MODEL_DFRobot_FireBeetle2_ESP32S3 // Has PSRAM
//#define CAMERA_MODEL_DFRobot_Romeo_ESP32S3 // Has PSRAM
#include "camera_pins.h"

// ===================
// Alarm hardware
// ===================
#ifndef BUZZER_PIN
#define BUZZER_PIN 14
#endif

#ifndef BUZZER_ACTIVE_HIGH
#define BUZZER_ACTIVE_HIGH 1
#endif

#define BUZZER_ON_LEVEL (BUZZER_ACTIVE_HIGH ? HIGH : LOW)
#define BUZZER_OFF_LEVEL (BUZZER_ACTIVE_HIGH ? LOW : HIGH)

#ifndef RADAR_PIN
#define RADAR_PIN 2
#endif

#ifndef LIGHT_SENSOR_PIN
#define LIGHT_SENSOR_PIN 1
#endif

#ifndef RADAR_ACTIVE_HIGH
#define RADAR_ACTIVE_HIGH 1
#endif

#ifndef RADAR_UART_RX_PIN
#define RADAR_UART_RX_PIN 41
#endif

#ifndef RADAR_UART_TX_PIN
#define RADAR_UART_TX_PIN 40
#endif

#ifndef RADAR_UART_BAUD
#define RADAR_UART_BAUD 115200
#endif

#ifndef LIGHT_DAY_THRESHOLD
#define LIGHT_DAY_THRESHOLD 100
#endif

#ifndef SENSOR_READ_INTERVAL_MS
#define SENSOR_READ_INTERVAL_MS 250
#endif

#ifndef RADAR_UART_READ_INTERVAL_MS
#define RADAR_UART_READ_INTERVAL_MS 50
#endif

#ifndef RADAR_UART_LOG_INTERVAL_MS
#define RADAR_UART_LOG_INTERVAL_MS 2000
#endif

#ifndef DARK_RADAR_ALARM_ENABLED
#define DARK_RADAR_ALARM_ENABLED 0
#endif

#ifndef DARK_ALARM_COOLDOWN_MS
#define DARK_ALARM_COOLDOWN_MS 5000
#endif

#ifndef DARK_ALARM_PULSE_MS
#define DARK_ALARM_PULSE_MS 1500
#endif

#endif  // BOARD_CONFIG_H
