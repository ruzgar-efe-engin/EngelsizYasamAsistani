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
 * - Encoder'lar: Ana Menü, Alt Menü pozisyonlarını takip eder
 * - Butonlar: AI, SubSW - kullanıcı seçimlerini bildirir
 * - Event Transport: Serial (simülasyon) veya BLE (gerçek cihaz)
 * - LED: Fiziksel geri bildirim (kullanıcı aksiyonu anlaşıldığında yanar), gerçek cihazda titreşim motoru olabilir.
 * 
 * ============================================================================
 */

#include <Arduino.h>

/* ============================================================================
 * TRANSPORT SELECTION (Event Gönderme Yöntemi)
 * ============================================================================
 * 
 * TRANSPORT_SERIAL: Wokwi simülasyonu için Serial port üzerinden log
 * TRANSPORT_BLE: Gerçek cihaz için BLE (Bluetooth Low Energy) ile gönderim, BLE teknolojisi sayesinde enerji tüketimi daha azdır. Cihaz desteklenirse BLE kullanılabilir.
 * 
 * Not: Aynı anda sadece biri aktif olmalı. Diğerini yorum satırı yapın.
 * ÖNEMLİ: TRANSPORT_BLE tanımı EventTransport.h include edilmeden ÖNCE olmalı!
 */
/// #define TRANSPORT_SERIAL  // Wokwi / Simülasyon için
#define TRANSPORT_BLE   // Gerçek cihaz için

#include "EventTransport.h"
#include "pin.h"

#ifdef TRANSPORT_BLE
#include <BLEDevice.h>
#endif



/* ============================================================================
 * POZISYON DEGISKENLERI (Position Variables)
 * ============================================================================
 * 
 * Bu cihaz sadece pozisyon takibi yapar. Menü içeriğini bilmez.
 * Her pozisyon bir index (sayı) olarak tutulur.
 * 
 * Örnek:
 * - mainIndex = 1 → "2. ana menü öğesi"
 * - subIndex = 0 → "1. alt menü öğesi"
 * - mainIndex = 255 → "256. ana menü öğesi" (sınırsız artabilir)
 * - subIndex = 255 → "256. alt menü öğesi" (sınırsız artabilir),
 * - 255'den büyük değerler mevcut değil, o yüzden başa döner.
 * - 0'dan küçük değerler mevcut değil, o yüzden 0'dan bir öncesine scroll edildiğinde 255 olur.
 * - Mobile app bu kurallara göre menü içeriğini gösterir.
 * 
 * Bu index'ler mobile app'e gönderilir, app menü içeriğini bilir ve TTS ile okur.
 */
 
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
    : _clk(clk), _dt(dt), _lastClk(HIGH), _lastDt(HIGH), _lastReadTime(0) {}

  // begin(): Pin'leri INPUT_PULLUP olarak ayarlar ve başlangıç durumunu okur
  void begin() {
    pinMode(_clk, INPUT_PULLUP);  // CLK pin'ini pull-up ile input yap
    pinMode(_dt, INPUT_PULLUP);   // DT pin'ini pull-up ile input yap
    delay(10);                     // Pin'lerin stabilize olması için bekle
    _lastClk = digitalRead(_clk);  // Başlangıç CLK durumunu kaydet
    _lastDt = digitalRead(_dt);    // Başlangıç DT durumunu kaydet
    _lastReadTime = millis();      // Başlangıç zamanını kaydet
  }

  // readStep(): Encoder'ın bir adım döndüğünü tespit eder
  // Return: +1 (saat yönü), -1 (ters yön), 0 (dönüş yok)
  int8_t readStep() {
    uint32_t now = millis();
    
    // Debounce: Çok hızlı ardışık okumaları filtrele
    if (now - _lastReadTime < ENCODER_DEBOUNCE_MS) {
      // Henüz debounce süresi geçmedi, sadece durumları güncelle
      uint8_t clk = digitalRead(_clk);
      uint8_t dt = digitalRead(_dt);
      _lastClk = clk;
      _lastDt = dt;
      return 0;  // Dönüş yok
    }
    
    uint8_t clk = digitalRead(_clk);  // CLK pin'inin şu anki durumu
    uint8_t dt = digitalRead(_dt);    // DT pin'inin şu anki durumu
    
    // CLK pinindeki HIGH → LOW geçişini tespit et (encoder dönüş başlangıcı)
    if (_lastClk == HIGH && clk == LOW) {
      // CLK düştü, DT pininin durumuna göre yön belirle
      if (dt == HIGH) {
        // DT = HIGH → Saat yönünde dönüş
        _lastClk = clk;  // Durumu güncelle
        _lastDt = dt;
        _lastReadTime = now;  // Okuma zamanını güncelle
        return +1;  // Clockwise (saat yönü)
      } else {
        // DT = LOW → Saat yönü tersine dönüş
        _lastClk = clk;  // Durumu güncelle
        _lastDt = dt;
        _lastReadTime = now;  // Okuma zamanını güncelle
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
  uint32_t _lastReadTime;   // Son okuma zamanı (debounce için)
  static const uint32_t ENCODER_DEBOUNCE_MS = 50;  // Encoder debounce süresi (50ms)
};

// Encoder nesnelerini oluştur (her encoder için bir instance)
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
 * - type: Event türü (MAIN_ROTATE, SUB_ROTATE, CONFIRM, AI_PRESS, AI_RELEASE)
 * - m: mainIndex (ana menü pozisyonu, opsiyonel, varsayılan 0)
 * - s: subIndex (alt menü pozisyonu, opsiyonel, varsayılan 0)
 * 
 * Event yapısı:
 * - type: Hangi olay olduğu (döndürme, buton basma, vb.)
 * - mainIndex: Hangi ana menü öğesinde
 * - subIndex: Hangi alt menü öğesinde
 * - ts: Timestamp (millis() - olayın zamanı)
 */
void sendEvent(EventType type, uint8_t m = 0, uint8_t s = 0) {
  Event event;                    // Event yapısı oluştur
  event.type = type;              // Event türünü ayarla
  event.mainIndex = m;            // Ana menü pozisyonunu ayarla
  event.subIndex = s;            // Alt menü pozisyonunu ayarla
  event.ts = millis();           // Zaman damgası ekle (milisaniye cinsinden)
  
  // Event gönderim log'u
  Serial.print("[DEBUG] sendEvent çağrıldı: type=");
  Serial.print(type);
  Serial.print(" m=");
  Serial.print(m);
  Serial.print(" s=");
  Serial.print(s);
  Serial.print(" ts=");
  Serial.println(event.ts);
  
  eventTransport.sendEvent(event); // Event'i gönder (Serial veya BLE)
  
  Serial.println("[DEBUG] sendEvent tamamlandı");
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
  pinMode(PIN_AI,  INPUT_PULLUP);   // AI butonu
  pinMode(PIN_SUB_SW, INPUT_PULLUP); // Sub Menu Switch butonu

  // Encoder'ları başlat (pin'leri ayarlar ve başlangıç durumunu okur)
  encMain.begin();  // Ana menü encoder'ı
  encSub.begin();   // Alt menü encoder'ı

  // Buton durumlarını stabilize et (ilk okumalarda yanlış tetiklenmeyi önle)
  delay(100);

  // Başlangıç mesajı (Serial Monitor'de görünür)
  Serial.println("[INIT] Pozisyon takibi aktif");
  
  // Cihaz açıldığında otomatik olarak 15 saniye pairing mode başlat
  Serial.println("[INIT] Otomatik pairing mode başlatılıyor (15 saniye)...");
  eventTransport.enablePairingMode();
  Serial.println("[INIT] Pairing mode başlatıldı");
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
static bool aiPressed = false;     // AI butonu basılı mı?
static bool subSwPressed = false;   // Sub Menu Switch basılı mı?
static uint32_t loopCount = 0;     // Loop sayacı (ilk 10 loop'u atlamak için)

// Buton bırakıldıktan sonra bounce'u önlemek için zaman takibi
static uint32_t lastAiReleaseTime = 0;      // AI butonu son bırakılma zamanı
static uint32_t lastSubSwReleaseTime = 0;   // SubSW butonu son bırakılma zamanı
static const uint32_t BUTTON_RELEASE_DEBOUNCE_MS = 100; // Buton bırakıldıktan sonra 100ms bekle

// AI butonu artık sadece bas-konuş için kullanılıyor
// Pairing mode cihaz açılışında otomatik başlatılıyor

// Encoder event gönderimi için rate limiting
static uint32_t lastMainRotateTime = 0;     // Son ana menü rotate event zamanı
static uint32_t lastSubRotateTime = 0;      // Son alt menü rotate event zamanı
static const uint32_t EVENT_RATE_LIMIT_MS = 100; // Minimum event gönderim aralığı (100ms)

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
    // Eğer buton başlangıçta basılıysa, aiPressed = false yap
    // Böylece buton bırakıldığında (HIGH) ilk gerçek basışta tetiklenir
    uint8_t aiStateInit = digitalRead(PIN_AI);
    aiPressed = false;  // Her zaman false ile başla
    lastAiReleaseTime = 0;  // Sıfırla
    
    subSwPressed = (digitalRead(PIN_SUB_SW) == LOW);
    
    // Encoder'ları da oku (başlangıç durumunu kaydetmek için)
    encMain.readStep();
    encSub.readStep();
    return;  // İlk 10 loop'ta sadece durumları oku, event gönderme
  }

  // ========================================================================
  // ENCODER OKUMA (Döner Encoder'ları Oku)
  // ========================================================================
  
  // Her encoder'ı oku ve dönüş miktarını al
  // dMain, dSub: -1 (ters yön), 0 (dönüş yok), +1 (ileri yön)
  int8_t dMain  = encMain.readStep();   // Ana menü encoder'ı
  int8_t dSub   = encSub.readStep();    // Alt menü encoder'ı

  // Ana Menü Encoder döndü mü?
  if (dMain != 0) {
    uint32_t now = millis();
    // Rate limiting: Çok hızlı event gönderimini önle
    if (now - lastMainRotateTime >= EVENT_RATE_LIMIT_MS) {
      mainIndex += dMain;  // Pozisyonu güncelle (+1 veya -1)
      // Ana menü değiştiğinde alt menüyü sıfırla
      subIndex = 0;  // Alt menü sıfırla
      // Event gönder: Ana menü değişti
      sendEvent(MAIN_ROTATE, mainIndex);
      lastMainRotateTime = now;  // Son event zamanını güncelle
    }
  }

  // Alt Menü Encoder döndü mü?
  if (dSub != 0) {
    uint32_t now = millis();
    // Rate limiting: Çok hızlı event gönderimini önle
    if (now - lastSubRotateTime >= EVENT_RATE_LIMIT_MS) {
      subIndex += dSub;  // Pozisyonu güncelle (+1 veya -1)
      // Event gönder: Alt menü değişti
      sendEvent(SUB_ROTATE, mainIndex, subIndex);
      lastSubRotateTime = now;  // Son event zamanını güncelle
    }
  }

  // ========================================================================
  // BUTON OKUMA (Butonları Oku ve Event Gönder)
  // ========================================================================
  
  // AI Button (AI Butonu - Sadece Bas-Konuş İçin)
  uint8_t aiState = digitalRead(PIN_AI);
  
  if (aiState == LOW && !aiPressed) {
    // Buton basıldı
    // Bırakıldıktan sonra yeterli süre geçti mi kontrol et (bounce önleme)
    uint32_t now = millis();
    if (lastAiReleaseTime == 0 || (now - lastAiReleaseTime >= BUTTON_RELEASE_DEBOUNCE_MS)) {
      aiPressed = true;
      // Event gönder: AI butonu basıldı (bas-konuş başladı)
      sendEvent(AI_PRESS, mainIndex, subIndex);
    }
  } else if (aiState == HIGH && aiPressed) {
    // Buton bırakıldı
    aiPressed = false;
    uint32_t now = millis();
    lastAiReleaseTime = now;  // Bırakılma zamanını kaydet (bounce önleme için)
    // Event gönder: AI butonu bırakıldı (bas-konuş sona erdi)
    sendEvent(AI_RELEASE, mainIndex, subIndex);
  }

  // Sub Menu Switch (Alt Menü Encoder'ındaki Basma Butonu)
  uint8_t subSwState = digitalRead(PIN_SUB_SW);
  if (subSwState == LOW && !subSwPressed) {
    // Buton basıldı
    // Bırakıldıktan sonra yeterli süre geçti mi kontrol et (bounce önleme)
    uint32_t now = millis();
    if (now - lastSubSwReleaseTime >= BUTTON_RELEASE_DEBOUNCE_MS) {
      subSwPressed = true;
      // Event gönder: Alt menü switch'i basıldı (onay işlemi)
      sendEvent(CONFIRM, mainIndex, subIndex);
    }
  } else if (subSwState == HIGH && subSwPressed) {
    // Buton bırakıldı
    subSwPressed = false;
    uint32_t now = millis();
    lastSubSwReleaseTime = now;  // Bırakılma zamanını kaydet (bounce önleme için)
  }
  
  // Bluetooth durumunu kontrol et ve LED'i yanıp söndür (bağlantı yoksa)
  eventTransport.updateAdvertisingStatus();
  #ifdef TRANSPORT_BLE
  eventTransport.handleConnection();
  #endif
  
  // Loop burada biter ve tekrar baştan başlar (sürekli döngü)
}
