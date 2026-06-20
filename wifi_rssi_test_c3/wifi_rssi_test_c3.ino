#include <WiFi.h>
#include <esp_wifi.h>
#include "../secrets.h"

static uint32_t lastScanMs = 0;

static void printBssid(const uint8_t *bssid) {
  for (int i = 0; i < 6; i++) {
    if (i > 0) {
      Serial.print(":");
    }
    if (bssid[i] < 16) {
      Serial.print("0");
    }
    Serial.print(bssid[i], HEX);
  }
}

static void scanAndConnect() {
  Serial.println();
  Serial.printf("[TEST] scanning for ssid=\"%s\"\n", ssid);

  WiFi.disconnect(true, true);
  delay(300);
  WiFi.mode(WIFI_STA);
  WiFi.setSleep(false);
  WiFi.setTxPower(WIFI_POWER_19_5dBm);

  wifi_country_t wifiCountry = {
      .cc = "DE",
      .schan = 1,
      .nchan = 13,
      .policy = WIFI_COUNTRY_POLICY_AUTO,
  };
  esp_wifi_set_country(&wifiCountry);

  int count = WiFi.scanNetworks(false, true);
  Serial.printf("[TEST] scan networks=%d\n", count);

  bool found = false;
  int bestRssi = -1000;
  int bestChannel = 0;
  uint8_t bestBssid[6] = {0};

  for (int i = 0; i < count; i++) {
    Serial.printf("[SCAN] ssid=\"%s\" bssid=%s rssi=%d channel=%d enc=%d\n",
                  WiFi.SSID(i).c_str(), WiFi.BSSIDstr(i).c_str(), WiFi.RSSI(i),
                  WiFi.channel(i), WiFi.encryptionType(i));
    if (WiFi.SSID(i) == ssid && WiFi.RSSI(i) > bestRssi) {
      found = true;
      bestRssi = WiFi.RSSI(i);
      bestChannel = WiFi.channel(i);
      memcpy(bestBssid, WiFi.BSSID(i), sizeof(bestBssid));
    }
  }

  if (!found) {
    Serial.printf("[TEST] target ssid not found: \"%s\"\n", ssid);
    WiFi.scanDelete();
    return;
  }

  Serial.print("[TEST] selected bssid=");
  printBssid(bestBssid);
  Serial.printf(" rssi=%d channel=%d\n", bestRssi, bestChannel);
  WiFi.scanDelete();

  WiFi.begin(ssid, password, bestChannel, bestBssid);
  uint32_t startMs = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - startMs < 20000) {
    delay(500);
    Serial.print(".");
  }
  Serial.println();

  if (WiFi.status() == WL_CONNECTED) {
    Serial.printf("[TEST] connected ip=%s ssid=\"%s\" rssi=%d\n",
                  WiFi.localIP().toString().c_str(), WiFi.SSID().c_str(),
                  WiFi.RSSI());
  } else {
    Serial.printf("[TEST] connect failed status=%d\n", WiFi.status());
  }
}

void setup() {
  Serial.begin(115200);
  delay(1500);
  Serial.println();
  Serial.println("[TEST] ESP32-C3 WiFi RSSI test");
  scanAndConnect();
  lastScanMs = millis();
}

void loop() {
  if (WiFi.status() == WL_CONNECTED) {
    Serial.printf("[TEST] live rssi=%d ip=%s\n", WiFi.RSSI(),
                  WiFi.localIP().toString().c_str());
  } else {
    Serial.printf("[TEST] disconnected status=%d\n", WiFi.status());
  }

  if (millis() - lastScanMs >= 30000) {
    lastScanMs = millis();
    scanAndConnect();
  }
  delay(5000);
}
