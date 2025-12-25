/*
 * ============================================================================
 * GÖRME ENGELLİLER PROJESİ - ESP32-S3 CİHAZ FIRMWARE
 * ============================================================================
 * 
 * Bu cihaz bir pozisyon kumandasıdır. Kullanıcı etkileşimlerini (encoder 
 * döndürme, buton basma) event formatında mobile app'e gönderir.
 * 
 * ÖNEMLİ: Bu cihaz hiçbir şeyi yorumlamaz, isimlendirmez, konuşmaz.
 * Sadece pozisyon değişikliklerini ve buton olaylarını bildirir.
 * 
 * Mimari:
 * - Encoder'lar: Tema, Ana Menü, Alt Menü pozisyonlarını takip eder
 * - Butonlar: YES, NO, AI, SubSW - kullanıcı seçimlerini bildirir
 * - Event Transport: Serial (simülasyon) veya BLE (gerçek cihaz)
 * - LED: Fiziksel geri bildirim (kullanıcı aksiyonu anlaşıldığında yanar), gerçek cihazda titreşim motoru olabilir.
 * 
 * ============================================================================
 */

#include <Arduino.h>
#include "EventTransport.h"
#ifdef TRANSPORT_BLE
#include <BLEDevice.h>
#endif

/* ============================================================================
 * TRANSPORT SELECTION (Event Gönderme Yöntemi)
 * ============================================================================
 * 
 * TRANSPORT_SERIAL: Wokwi simülasyonu için Serial port üzerinden log
 * TRANSPORT_BLE: Gerçek cihaz için BLE (Bluetooth Low Energy) ile gönderim, BLE teknolojisi sayesinde enerji tüketimi daha azdır. Cihaz desteklenirse BLE kullanılabilir.
 * 
 * Not: Aynı anda sadece biri aktif olmalı. Diğerini yorum satırı yapın.
 */
#define TRANSPORT_SERIAL  // Wokwi / Simülasyon için
// #define TRANSPORT_BLE   // Gerçek cihaz için

/* ============================================================================
 * PIN DEFINITIONS (Pin Tanımlamaları)
 * ============================================================================
 * 
 * ESP32-S3 Seeed Studio XIAO board'unda kullanılan pin'ler.
 * Her encoder 2 pin kullanır (CLK ve DT), butonlar tek pin.
 * 
 * Encoder Pins:
 * - CLK (Clock): Ana sinyal pini, dönüş yönünü belirlemek için kullanılır
 * - DT (Data): Yön bilgisi pini, CLK ile birlikte okunarak yön tespit edilir
 * 
 * Button Pins:
 * - Pull-up resistor kullanılır (INPUT_PULLUP)
 * - Basılıyken LOW, basılı değilken HIGH okunur
 */
// Theme Encoder (Tema Seçimi) - En üst seviye menü
#define PIN_THEME_CLK D0  // Clock pin
#define PIN_THEME_DT  D1  // Data pin

// Main Menu Encoder (Ana Menü Seçimi) - İkinci seviye menü
#define PIN_MAIN_CLK  D2  // Clock pin
#define PIN_MAIN_DT   D3  // Data pin

// Sub Menu Encoder (Alt Menü Seçimi) - Üçüncü seviye menü
#define PIN_SUB_CLK   D4  // Clock pin
#define PIN_SUB_DT    D5  // Data pin
#define PIN_SUB_SW    D6  // Switch (buton) - encoder üzerindeki basma butonu

// Buttons (Butonlar)
#define PIN_YES       D7  // Evet/Onay butonu
#define PIN_NO        D8  // Hayır/İptal butonu
#define PIN_AI        D9  // AI butonu (basılı tutma için)

// LED (Geri Bildirim)
#define PIN_LED       D10 // LED pin (gelecekte titreşim motoru olabilir)

/* ============================================================================
 * POZISYON DEGISKENLERI (Position Variables)
 * ============================================================================
 * 
 * Bu cihaz sadece pozisyon takibi yapar. Menü içeriğini bilmez.
 * Her pozisyon bir index (sayı) olarak tutulur.
 * 
 * Örnek:
 * - themeIndex = 2 → "3. tema" (0'dan başlar)
 * - mainIndex = 1 → "2. ana menü öğesi"
 * - subIndex = 0 → "1. alt menü öğesi"
 * - themeIndex = 255 → "256. tema" (sınırsız artabilir)
 * - mainIndex = 255 → "256. ana menü öğesi" (sınırsız artabilir)
 * - subIndex = 255 → "256. alt menü öğesi" (sınırsız artabilir),
 * - 255'den büyük değerler mevcut değil, o yüzden başa döner.
 * - 0'dan küçük değerler mevcut değil, o yüzden 0'dan bir öncesine scroll edildiğinde 255 olur.
 * - Mobile app bu kurallara göre menü içeriğini gösterir.
 * 
 * Bu index'ler mobile app'e gönderilir, app menü içeriğini bilir ve TTS ile okur.
 */
int themeIndex = 0;  // Tema pozisyonu (0'dan başlar, sınırsız artabilir)
int mainIndex  = 0;  // Ana menü pozisyonu (0'dan başlar, sınırsız artabilir)
int subIndex   = 0;  // Alt menü pozisyonu (0'dan başlar, sınırsız artabilir)

/* ============================================================================
 * ROTARY ENCODER CLASS (Döner Encoder Sınıfı)
 * ============================================================================
 * 
 * KY-040 tipi rotary encoder'ları okumak için sınıf.
 * Encoder'lar Gray Code kullanır, bu yüzden özel okuma mantığı gerekir.
 * 
 * Çalışma Prensibi:
 * 1. CLK ve DT pinleri sürekli okunur
 * 2. CLK pininde HIGH → LOW geçişi tespit edildiğinde:
 *    - DT = HIGH ise → Saat yönünde dönüş (+1)
 *    - DT = LOW ise → Saat yönü tersine dönüş (-1)
 * 3. Her dönüş için +1 veya -1 döner, dönüş yoksa 0 döner
 * 
 * Polling Yöntemi: loop() içinde sürekli okunur (interrupt kullanılmaz)
 * Bu işlemin güç tüketimi çok düşüktür, encoder'lar sürekli okunur.
 */
class Encoder {
public:
  // Constructor: Encoder pin'lerini ve başlangıç durumlarını ayarlar
  Encoder(uint8_t clk, uint8_t dt)
    : _clk(clk), _dt(dt), _lastClk(HIGH), _lastDt(HIGH) {}

  // begin(): Pin'leri INPUT_PULLUP olarak ayarlar ve başlangıç durumunu okur
  void begin() {
    pinMode(_clk, INPUT_PULLUP);  // CLK pin'ini pull-up ile input yap
    pinMode(_dt, INPUT_PULLUP);   // DT pin'ini pull-up ile input yap
    delay(10);                     // Pin'lerin stabilize olması için bekle
    _lastClk = digitalRead(_clk);  // Başlangıç CLK durumunu kaydet
    _lastDt = digitalRead(_dt);    // Başlangıç DT durumunu kaydet
  }

  // readStep(): Encoder'ın bir adım döndüğünü tespit eder
  // Return: +1 (saat yönü), -1 (ters yön), 0 (dönüş yok)
  int8_t readStep() {
    uint8_t clk = digitalRead(_clk);  // CLK pin'inin şu anki durumu
    uint8_t dt = digitalRead(_dt);    // DT pin'inin şu anki durumu
    
    // CLK pinindeki HIGH → LOW geçişini tespit et (encoder dönüş başlangıcı)
    if (_lastClk == HIGH && clk == LOW) {
      // CLK düştü, DT pininin durumuna göre yön belirle
      if (dt == HIGH) {
        // DT = HIGH → Saat yönünde dönüş
        _lastClk = clk;  // Durumu güncelle
        _lastDt = dt;
        return +1;  // Clockwise (saat yönü)
      } else {
        // DT = LOW → Saat yönü tersine dönüş
        _lastClk = clk;  // Durumu güncelle
        _lastDt = dt;
        return -1;  // Counter-clockwise (ters yön)
      }
    }
    
    // Durum değişikliği yoksa, sadece son durumları güncelle
    _lastClk = clk;
    _lastDt = dt;
    return 0;  // Dönüş yok
  }

private:
  uint8_t _clk, _dt;        // CLK ve DT pin numaraları
  uint8_t _lastClk, _lastDt; // Önceki okuma durumları (edge detection için)
};

// Encoder nesnelerini oluştur (her encoder için bir instance)
Encoder encTheme(PIN_THEME_CLK, PIN_THEME_DT);  // Tema encoder'ı
Encoder encMain (PIN_MAIN_CLK,  PIN_MAIN_DT);  // Ana menü encoder'ı
Encoder encSub  (PIN_SUB_CLK,   PIN_SUB_DT);   // Alt menü encoder'ı

/* ============================================================================
 * EVENT TRANSPORT (Event Taşıma Katmanı)
 * ============================================================================
 * 
 * Event'leri mobile app'e göndermek için kullanılan katman.
 * Simülasyon için Serial, gerçek cihaz için BLE kullanılır.
 * 
 * Event Transport Pattern: Farklı taşıma yöntemlerini aynı interface ile kullanır
 */
#ifdef TRANSPORT_SERIAL
// Simülasyon modu: Serial port üzerinden log (Wokwi için)
SerialEventTransport eventTransport(PIN_LED);
#else
// Gerçek cihaz modu: BLE üzerinden gönderim
BLEEventTransport eventTransport(PIN_LED);
#endif

/* ============================================================================
 * sendEvent() - Event Gönderme Fonksiyonu
 * ============================================================================
 * 
 * Kullanıcı etkileşimlerini event formatına çevirip gönderir.
 * 
 * Parametreler:
 * - type: Event türü (THEME_ROTATE, CONFIRM, AI_PRESS, vb.)
 * - t: themeIndex (tema pozisyonu)
 * - m: mainIndex (ana menü pozisyonu, opsiyonel, varsayılan 0)
 * - s: subIndex (alt menü pozisyonu, opsiyonel, varsayılan 0)
 * 
 * Event yapısı:
 * - type: Hangi olay olduğu (döndürme, buton basma, vb.)
 * - themeIndex: Hangi temada
 * - mainIndex: Hangi ana menü öğesinde
 * - subIndex: Hangi alt menü öğesinde
 * - ts: Timestamp (millis() - olayın zamanı)
 */
void sendEvent(EventType type, uint8_t t, uint8_t m = 0, uint8_t s = 0) {
  Event event;                    // Event yapısı oluştur
  event.type = type;              // Event türünü ayarla
  event.themeIndex = t;           // Tema pozisyonunu ayarla
  event.mainIndex = m;            // Ana menü pozisyonunu ayarla
  event.subIndex = s;            // Alt menü pozisyonunu ayarla
  event.ts = millis();           // Zaman damgası ekle (milisaniye cinsinden)
  
  eventTransport.sendEvent(event); // Event'i gönder (Serial veya BLE)
}


/* ============================================================================
 * SETUP() - Başlangıç Fonksiyonu
 * ============================================================================
 * 
 * Arduino'da program başladığında bir kez çalışır.
 * Pin'leri, encoder'ları ve butonları başlatır.
 */
void setup() {
  // Serial port'u başlat (115200 baud rate - hızlı veri aktarımı)
  Serial.begin(115200);

  // LED pin'ini OUTPUT olarak ayarla ve başlangıçta söndür
  pinMode(PIN_LED, OUTPUT);
  digitalWrite(PIN_LED, LOW);

  // Buton pin'lerini INPUT_PULLUP olarak ayarla
  // Pull-up: Pin'e dahili direnç bağlı, basılı değilken HIGH, basılıyken LOW
  pinMode(PIN_YES, INPUT_PULLUP);    // YES butonu
  pinMode(PIN_NO,  INPUT_PULLUP);   // NO butonu
  pinMode(PIN_AI,  INPUT_PULLUP);   // AI butonu
  pinMode(PIN_SUB_SW, INPUT_PULLUP); // Sub Menu Switch butonu

  // Encoder'ları başlat (pin'leri ayarlar ve başlangıç durumunu okur)
  encTheme.begin();  // Tema encoder'ı
  encMain.begin();  // Ana menü encoder'ı
  encSub.begin();   // Alt menü encoder'ı

  // Buton durumlarını stabilize et (ilk okumalarda yanlış tetiklenmeyi önle)
  delay(100);

  // Başlangıç mesajı (Serial Monitor'de görünür)
  Serial.println("[INIT] Pozisyon takibi aktif");
}

/* ============================================================================
 * GLOBAL BUTON STATE (Global Buton Durum Değişkenleri)
 * ============================================================================
 * 
 * Butonların basılı olup olmadığını takip eden flag'ler.
 * Edge detection için kullanılır: Sadece basıldığında event gönderilir,
 * basılı tutulduğunda tekrar event gönderilmez.
 * 
 * static: Fonksiyon dışında erişilebilir ama global scope'u kirletmez
 */
static bool yesPressed = false;    // YES butonu basılı mı?
static bool noPressed = false;     // NO butonu basılı mı?
static bool aiPressed = false;     // AI butonu basılı mı?
static bool subSwPressed = false;   // Sub Menu Switch basılı mı?
static uint32_t loopCount = 0;     // Loop sayacı (ilk 10 loop'u atlamak için)

// Buton bırakıldıktan sonra bounce'u önlemek için zaman takibi
static uint32_t lastYesReleaseTime = 0;   // YES butonu son bırakılma zamanı
static uint32_t lastNoReleaseTime = 0;    // NO butonu son bırakılma zamanı
static uint32_t lastSubSwReleaseTime = 0; // SubSW butonu son bırakılma zamanı
static const uint32_t BUTTON_RELEASE_DEBOUNCE_MS = 100; // Buton bırakıldıktan sonra 100ms bekle

// Bluetooth kontrolü için buton basılı tutma takibi
static uint32_t yesPressStartTime = 0;    // YES butonu basılmaya başlandığı zaman
static uint32_t noPressStartTime = 0;     // NO butonu basılmaya başlandığı zaman
static bool yesLongPressHandled = false;  // YES uzun basış işlendi mi?
static bool noLongPressHandled = false;   // NO uzun basış işlendi mi?
static const uint32_t LONG_PRESS_MS = 3000; // 3 saniye basılı tutma süresi

/* ============================================================================
 * LOOP() - Ana Döngü Fonksiyonu
 * ============================================================================
 * 
 * Arduino'da setup()'dan sonra sürekli çalışır (milisaniyeler içinde binlerce kez).
 * Bu fonksiyon:
 * 1. Encoder'ları okur ve pozisyon değişikliklerini tespit eder
 * 2. Butonları okur ve basılma olaylarını tespit eder
 * 3. Her değişiklik için event gönderir
 * 
 * ÖNEMLİ: Bu cihaz sadece pozisyon takibi yapar, menü içeriğini bilmez!
 */
void loop() {
  loopCount++;  // Loop sayacını artır
  
  // İlk 10 loop: Buton durumlarını stabilize et
  // Bu, cihaz açıldığında butonların yanlış tetiklenmesini önler
  if (loopCount < 10) {
    // Butonların mevcut durumunu oku ve flag'leri ayarla
    // Eğer buton başlangıçta basılıysa, ilk gerçek basışta tetiklenmez
    yesPressed = (digitalRead(PIN_YES) == LOW);      // LOW = basılı
    noPressed = (digitalRead(PIN_NO) == LOW);
    aiPressed = (digitalRead(PIN_AI) == LOW);
    subSwPressed = (digitalRead(PIN_SUB_SW) == LOW);
    
    // Encoder'ları da oku (başlangıç durumunu kaydetmek için)
    encTheme.readStep();
    encMain.readStep();
    encSub.readStep();
    return;  // İlk 10 loop'ta sadece durumları oku, event gönderme
  }

  // ========================================================================
  // ENCODER OKUMA (Döner Encoder'ları Oku)
  // ========================================================================
  
  // Her encoder'ı oku ve dönüş miktarını al
  // dTheme, dMain, dSub: -1 (ters yön), 0 (dönüş yok), +1 (ileri yön)
  int8_t dTheme = encTheme.readStep();  // Tema encoder'ı
  int8_t dMain  = encMain.readStep();   // Ana menü encoder'ı
  int8_t dSub   = encSub.readStep();    // Alt menü encoder'ı

  // Tema Encoder döndü mü?
  if (dTheme != 0) {
    themeIndex += dTheme;  // Pozisyonu güncelle (+1 veya -1)
    // Tema değiştiğinde alt seviye menüleri sıfırla
    mainIndex = 0;  // Ana menü sıfırla
    subIndex = 0;   // Alt menü sıfırla
    // Event gönder: Tema değişti, yeni tema pozisyonu
    sendEvent(THEME_ROTATE, themeIndex);
  }

  // Ana Menü Encoder döndü mü?
  if (dMain != 0) {
    mainIndex += dMain;  // Pozisyonu güncelle (+1 veya -1)
    // Ana menü değiştiğinde alt menüyü sıfırla
    subIndex = 0;  // Alt menü sıfırla
    // Event gönder: Ana menü değişti, tema ve ana menü pozisyonu
    sendEvent(MAIN_ROTATE, themeIndex, mainIndex);
  }

  // Alt Menü Encoder döndü mü?
  if (dSub != 0) {
    subIndex += dSub;  // Pozisyonu güncelle (+1 veya -1)
    // Event gönder: Alt menü değişti, tüm pozisyonlar
    sendEvent(SUB_ROTATE, themeIndex, mainIndex, subIndex);
  }

  // ========================================================================
  // BUTON OKUMA (Butonları Oku ve Event Gönder)
  // ========================================================================
  
  // YES Button (Evet/Onay Butonu)
  uint8_t yesState = digitalRead(PIN_YES);  // Butonun şu anki durumu
  uint32_t now = millis();  // Şu anki zaman (debounce için)
  
  if (yesState == LOW && !yesPressed) {
    // Buton basıldı (LOW) ve önceki durum basılı değildi
    // Bırakıldıktan sonra yeterli süre geçti mi kontrol et (bounce önleme)
    if (now - lastYesReleaseTime >= BUTTON_RELEASE_DEBOUNCE_MS) {
      yesPressed = true;  // Flag'i güncelle
      yesPressStartTime = now;  // Basılmaya başlandığı zamanı kaydet
      yesLongPressHandled = false;  // Uzun basış henüz işlenmedi
      // Event gönder: Onay butonu basıldı, mevcut pozisyonlarla
      sendEvent(CONFIRM, themeIndex, mainIndex, subIndex);
    }
  } else if (yesState == LOW && yesPressed && !yesLongPressHandled) {
    // Buton hala basılı ve 3 saniye geçti mi kontrol et
    if (now - yesPressStartTime >= LONG_PRESS_MS) {
      yesLongPressHandled = true;  // Uzun basış işlendi
      
      // Bluetooth kontrolü (sadece BLE modunda)
      #ifdef TRANSPORT_BLE
      Serial.println("[BLE] YES butonu 3 saniye basılı tutuldu - Bluetooth kontrolü");
      // Bluetooth açık değilse aç, açıksa advertising'i yeniden başlat
      if (!eventTransport.isBLEEnabled()) {
        Serial.println("[BLE] Bluetooth kapalı, açılıyor...");
        eventTransport.enableBLE();
        Serial.println("[BLE] ✅ YES butonu ile Bluetooth açıldı");
      } else {
        Serial.println("[BLE] Bluetooth zaten açık, advertising yeniden başlatılıyor...");
        // Zaten açıksa advertising'i yeniden başlat (arama moduna geç)
        eventTransport.enableBLE(); // Bu fonksiyon advertising'i de başlatır
        Serial.println("[BLE] ✅ YES butonu ile arama moduna geçildi");
      }
      // Artık LED yanıp sönecek (updateAdvertisingStatus tarafından)
      Serial.println("[BLE] LED 3 kez yanıp söndü, şimdi yanıp sönmeye devam edecek (bağlantı yoksa)");
      #else
      // Serial modunda (Wokwi simülasyonu)
      // Bridge server zaten sürekli BLE advertising yapıyor
      // Wokwi simülasyonundan gelen Serial verileri bridge server'a gidecek
      Serial.println("[SERIAL] YES butonu 3 saniye basılı tutuldu");
      Serial.println("[SERIAL] Bridge server BLE advertising yapıyor, mobile app cihazı bulabilir");
      #endif
      
      // LED feedback: 3 kez yanıp sönsün (her iki modda da çalışır)
      for (int i = 0; i < 3; i++) {
        digitalWrite(PIN_LED, HIGH);
        delay(100);
        digitalWrite(PIN_LED, LOW);
        delay(100);
      }
    }
  } else if (yesState == HIGH && yesPressed) {
    // Buton bırakıldı (HIGH) ve önceki durum basılıydı
    yesPressed = false;  // Flag'i sıfırla
    yesLongPressHandled = false;  // Uzun basış flag'ini sıfırla
    lastYesReleaseTime = now;  // Bırakılma zamanını kaydet (bounce önleme için)
  }

  // NO Button (Hayır/İptal Butonu)
  uint8_t noState = digitalRead(PIN_NO);
  if (noState == LOW && !noPressed) {
    // Buton basıldı
    // Bırakıldıktan sonra yeterli süre geçti mi kontrol et (bounce önleme)
    if (now - lastNoReleaseTime >= BUTTON_RELEASE_DEBOUNCE_MS) {
      noPressed = true;
      noPressStartTime = now;  // Basılmaya başlandığı zamanı kaydet
      noLongPressHandled = false;  // Uzun basış henüz işlenmedi
      // Event gönder: İptal butonu basıldı
      sendEvent(EVENT_CANCEL, themeIndex, mainIndex, subIndex);
    }
  } else if (noState == LOW && noPressed && !noLongPressHandled) {
    // Buton hala basılı ve 3 saniye geçti mi kontrol et
    if (now - noPressStartTime >= LONG_PRESS_MS) {
      noLongPressHandled = true;  // Uzun basış işlendi
      
      // Bluetooth kontrolü (sadece BLE modunda)
      #ifdef TRANSPORT_BLE
      if (eventTransport.isBLEEnabled()) {
        eventTransport.disableBLE();
        Serial.println("[BLE] NO butonu ile Bluetooth kapatıldı");
      }
      #else
      // Serial modunda (Wokwi simülasyonu)
      Serial.println("[SERIAL] NO butonu 3 saniye basılı tutuldu (Bluetooth kontrolü sadece BLE modunda)");
      #endif
      
      // LED feedback: 5 kez yanıp sönsün (kapatma onayı - her iki modda da çalışır)
      for (int i = 0; i < 5; i++) {
        digitalWrite(PIN_LED, HIGH);
        delay(100);
        digitalWrite(PIN_LED, LOW);
        delay(100);
      }
    }
  } else if (noState == HIGH && noPressed) {
    // Buton bırakıldı
    noPressed = false;
    noLongPressHandled = false;  // Uzun basış flag'ini sıfırla
    lastNoReleaseTime = now;  // Bırakılma zamanını kaydet (bounce önleme için)
  }

  // AI Button (AI Butonu - Basılı Tutma Desteği)
  uint8_t aiState = digitalRead(PIN_AI);
  if (aiState == LOW && !aiPressed) {
    // Buton basıldı
    aiPressed = true;
    // Event gönder: AI butonu basıldı
    sendEvent(AI_PRESS, themeIndex, mainIndex, subIndex);
  } else if (aiState == HIGH && aiPressed) {
    // Buton bırakıldı
    aiPressed = false;
    // Event gönder: AI butonu bırakıldı (basılı tutma sona erdi)
    sendEvent(AI_RELEASE, themeIndex, mainIndex, subIndex);
  }

  // Sub Menu Switch (Alt Menü Encoder'ındaki Basma Butonu)
  uint8_t subSwState = digitalRead(PIN_SUB_SW);
  if (subSwState == LOW && !subSwPressed) {
    // Buton basıldı
    // Bırakıldıktan sonra yeterli süre geçti mi kontrol et (bounce önleme)
    if (now - lastSubSwReleaseTime >= BUTTON_RELEASE_DEBOUNCE_MS) {
      subSwPressed = true;
      // Event gönder: Alt menü switch'i basıldı (onay işlemi)
      sendEvent(CONFIRM, themeIndex, mainIndex, subIndex);
    }
  } else if (subSwState == HIGH && subSwPressed) {
    // Buton bırakıldı
    subSwPressed = false;
    lastSubSwReleaseTime = now;  // Bırakılma zamanını kaydet (bounce önleme için)
  }
  
  // Bluetooth durumunu kontrol et ve LED'i yanıp söndür (bağlantı yoksa)
  #ifdef TRANSPORT_BLE
  eventTransport.updateAdvertisingStatus();
  eventTransport.handleConnection();
  #endif
  
  // Loop burada biter ve tekrar baştan başlar (sürekli döngü)
}
