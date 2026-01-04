#pragma once

// ===============================
// BOARD SELECTION
// ===============================
// Board seçimi platformio.ini'deki build_flags ile yapılıyor
// Manuel #define yapmayın! PlatformIO build_flags otomatik olarak tanımlar:
// -DBOARD_XIAO      -> seeed_xiao_esp32s3 environment için
// -DBOARD_S3_ZERO   -> esp32-s3-zero environment için

// ===============================
//  BOARD_WOKWI_XIAO_ESP32-S3 PIN MAP
// ===============================
#ifdef BOARD_XIAO
  #define PIN_MAIN_CLK  D2
  #define PIN_MAIN_DT   D3

  #define PIN_SUB_CLK   D4
  #define PIN_SUB_DT    D5
  #define PIN_SUB_SW    D6

  #define PIN_AI        D9
  #define PIN_LED       D10
#endif

// ===============================
// ESP32-S3 ZERO PIN MAP
// ===============================
#ifdef BOARD_S3_ZERO
  #define PIN_MAIN_CLK  4
  #define PIN_MAIN_DT   5

  #define PIN_SUB_CLK   6
  #define PIN_SUB_DT    7
  #define PIN_SUB_SW    8

  #define PIN_AI        9
  #define PIN_LED       10
#endif
