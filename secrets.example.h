#pragma once

const char *ssid = "YOUR_WIFI_NAME";
const char *password = "YOUR_WIFI_PASSWORD";

// Presentation backup:
// The ESP32 tries this first. If it is unavailable, it falls back to ssid/password.
const char *demoSsid = "YOUR_ANDROID_HOTSPOT_NAME";
const char *demoPassword = "YOUR_ANDROID_HOTSPOT_PASSWORD";
const bool demoWifiOnly = false;
const char *demoStaticIp = "";
const char *demoGatewayIp = "";
const char *demoSubnetMask = "255.255.255.0";
