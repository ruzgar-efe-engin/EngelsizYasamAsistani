#pragma once

// ===============================
// BOARD SELECTION
// ===============================
#define BOARD_XIAO
// #define BOARD_SUPER_MINI

// ===============================
// XIAO ESP32-S3 PIN MAP
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
// ESP32-S3 SUPER MINI PIN MAP
// ===============================
#ifdef BOARD_SUPER_MINI
  #define PIN_MAIN_CLK  16
  #define PIN_MAIN_DT   15

  #define PIN_SUB_CLK   14
  #define PIN_SUB_DT    13
  #define PIN_SUB_SW    12

  #define PIN_AI        11
  #define PIN_LED       48   // onboard WS2812 var → dikkat
#endif
