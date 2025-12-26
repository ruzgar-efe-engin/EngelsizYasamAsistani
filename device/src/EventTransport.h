#ifndef EVENT_TRANSPORT_H
#define EVENT_TRANSPORT_H

#include <Arduino.h>
#ifdef TRANSPORT_BLE
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#endif

/* =========================================================
   EVENT MODEL
   ========================================================= */

enum EventType : uint8_t {
  THEME_ROTATE = 0,
  MAIN_ROTATE = 1,
  SUB_ROTATE = 2,
  CONFIRM = 3,
  EVENT_CANCEL = 4,
  AI_PRESS = 5,
  AI_RELEASE = 6
};

struct Event {
  EventType type;
  uint8_t themeIndex;
  uint8_t mainIndex;  // opsiyonel
  uint8_t subIndex;   // opsiyonel
  uint32_t ts;        // millis() - debug, debounce, log korelasyonu için kritik
};

/* =========================================================
   EVENT TRANSPORT INTERFACE
   ========================================================= */

class IEventTransport {
public:
  virtual ~IEventTransport() {}
  virtual void sendEvent(const Event& event) = 0;
};

/* =========================================================
   SERIAL EVENT TRANSPORT (Wokwi / Simülasyon)
   ========================================================= */

class SerialEventTransport : public IEventTransport {
public:
  SerialEventTransport(uint8_t ledPin) : _ledPin(ledPin) {
    pinMode(_ledPin, OUTPUT);
    digitalWrite(_ledPin, LOW);
  }

  void sendEvent(const Event& event) override {
    // LED tetikleme - sendEvent() içinde
    digitalWrite(_ledPin, HIGH);
    delay(30);
    digitalWrite(_ledPin, LOW);

    // Event type string'e çevir
    const char* typeStr = "";
    switch (event.type) {
      case THEME_ROTATE: typeStr = "THEME_ROTATE"; break;
      case MAIN_ROTATE: typeStr = "MAIN_ROTATE"; break;
      case SUB_ROTATE: typeStr = "SUB_ROTATE"; break;
      case CONFIRM: typeStr = "CONFIRM"; break;
      case EVENT_CANCEL: typeStr = "CANCEL"; break;
      case AI_PRESS: typeStr = "AI_PRESS"; break;
      case AI_RELEASE: typeStr = "AI_RELEASE"; break;
    }

    // Kompakt log formatı: [BLE] MAIN_ROTATE m=15 ts=12345
    Serial.print("[BLE] ");
    Serial.print(typeStr);
    
    // mainIndex sadece ilgili event'lerde
    if (event.type == MAIN_ROTATE || event.type == SUB_ROTATE || 
        event.type == CONFIRM || event.type == EVENT_CANCEL || 
        event.type == AI_PRESS || event.type == AI_RELEASE) {
      Serial.print(" m=");
      Serial.print(event.mainIndex);
    }
    
    // subIndex sadece ilgili event'lerde
    if (event.type == SUB_ROTATE || event.type == CONFIRM || 
        event.type == EVENT_CANCEL || event.type == AI_PRESS || 
        event.type == AI_RELEASE) {
      Serial.print(" s=");
      Serial.print(event.subIndex);
    }
    
    Serial.print(" ts=");
    Serial.println(event.ts);
  }

private:
  uint8_t _ledPin;
};

/* =========================================================
   BLE EVENT TRANSPORT (Gerçek Cihaz)
   ========================================================= */

#ifdef TRANSPORT_BLE

// BLE UUID'leri
#define SERVICE_UUID        "12345678-1234-1234-1234-123456789abc"
#define CHARACTERISTIC_UUID "12345678-1234-1234-1234-123456789abd"

class BLEEventTransport : public IEventTransport {
public:
  BLEEventTransport(uint8_t ledPin) : _ledPin(ledPin), _deviceConnected(false), _oldDeviceConnected(false) {
    pinMode(_ledPin, OUTPUT);
    digitalWrite(_ledPin, LOW);
    
    // BLE başlat (constructor'da başlatma, enableBLE() ile kontrol edilebilir)
    // İlk başta kapalı başlat, YES butonu ile açılacak
    // BLEDevice::init("GormeEngellilerKumanda");
    
    Serial.println("[BLE] Bluetooth kapalı başlatıldı. YES butonunu 3 saniye basılı tutarak açabilirsiniz.");
  }

  void sendEvent(const Event& event) override {
    // LED tetikleme
    digitalWrite(_ledPin, HIGH);
    delay(30);
    digitalWrite(_ledPin, LOW);

    // Event'i JSON formatında hazırla
    String json = "{";
    json += "\"type\":" + String(event.type) + ",";
    json += "\"themeIndex\":" + String(event.themeIndex) + ",";
    json += "\"mainIndex\":" + String(event.mainIndex) + ",";
    json += "\"subIndex\":" + String(event.subIndex) + ",";
    json += "\"ts\":" + String(event.ts);
    json += "}";
    
    // BLE'ye gönder (eğer bağlıysa)
    if (_deviceConnected) {
      _pCharacteristic->setValue(json.c_str());
      _pCharacteristic->notify();
    }
    
    // Serial'e de logla (debug için)
    Serial.print("[BLE] ");
    Serial.println(json);
  }
  
  void setDeviceConnected(bool connected) {
    _deviceConnected = connected;
  }
  
  void handleConnection() {
    if (!_deviceConnected && _oldDeviceConnected) {
      // Bağlantı kesildi
      delay(500);
      BLEDevice::startAdvertising();
      Serial.println("[BLE] Bağlantı kesildi, yeniden advertising başlatıldı");
      _oldDeviceConnected = _deviceConnected;
    }
    
    if (_deviceConnected && !_oldDeviceConnected) {
      // Yeni bağlantı
      Serial.println("[BLE] Cihaz bağlandı!");
      _oldDeviceConnected = _deviceConnected;
    }
  }
  
  // Bluetooth'u aç
  void enableBLE() {
    if (!BLEDevice::getInitialized()) {
      Serial.println("[BLE] Bluetooth başlatılıyor...");
      BLEDevice::init("GormeEngellilerKumanda");
      Serial.println("[BLE] Device Name: GormeEngellilerKumanda");
      
      // BLE Server oluştur
      _pServer = BLEDevice::createServer();
      _pServer->setCallbacks(new MyServerCallbacks(this));
      Serial.println("[BLE] BLE Server oluşturuldu");
      
      // BLE Service oluştur
      _pService = _pServer->createService(SERVICE_UUID);
      Serial.print("[BLE] Service UUID: ");
      Serial.println(SERVICE_UUID);
      
      // BLE Characteristic oluştur
      _pCharacteristic = _pService->createCharacteristic(
        CHARACTERISTIC_UUID,
        BLECharacteristic::PROPERTY_READ |
        BLECharacteristic::PROPERTY_WRITE |
        BLECharacteristic::PROPERTY_NOTIFY |
        BLECharacteristic::PROPERTY_INDICATE
      );
      Serial.print("[BLE] Characteristic UUID: ");
      Serial.println(CHARACTERISTIC_UUID);
      
      _pCharacteristic->addDescriptor(new BLE2902());
      _pService->start();
      Serial.println("[BLE] Service başlatıldı");
      Serial.println("[BLE] Bluetooth açıldı");
    } else {
      Serial.println("[BLE] Bluetooth zaten açık, advertising yeniden başlatılıyor");
    }
    
    // Advertising başlat (arama modu - daha sık advertising)
    BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
    pAdvertising->addServiceUUID(SERVICE_UUID);
    pAdvertising->setScanResponse(true);  // Scan response aktif (daha iyi keşif)
    pAdvertising->setMinPreferred(0x06);  // Daha sık advertising
    pAdvertising->setMaxPreferred(0x12);
    BLEDevice::startAdvertising();
    Serial.println("[BLE] Advertising başlatıldı - Arama modu aktif");
    Serial.println("[BLE] LED yanıp sönmeye başlayacak (bağlantı yoksa)");
  }
  
  // Bluetooth'u kapat
  void disableBLE() {
    if (BLEDevice::getInitialized()) {
      BLEDevice::deinit(true);
      Serial.println("[BLE] Bluetooth kapatıldı");
    }
  }
  
  // Bluetooth açık mı kontrol et
  bool isBLEEnabled() {
    return BLEDevice::getInitialized();
  }
  
  // Advertising durumunu kontrol et ve LED'i yanıp söndür (bağlantı yoksa)
  void updateAdvertisingStatus() {
    static uint32_t lastBlinkTime = 0;
    static bool ledState = false;
    uint32_t now = millis();
    
    // Bağlantı yoksa ve BLE açıksa LED'i yanıp söndür
    if (!_deviceConnected && isBLEEnabled()) {
      if (now - lastBlinkTime >= 500) {  // 500ms'de bir yanıp sönsün
        ledState = !ledState;
        digitalWrite(_ledPin, ledState ? HIGH : LOW);
        lastBlinkTime = now;
      }
    } else {
      // Bağlantı varsa veya BLE kapalıysa LED'i söndür
      if (ledState) {
        digitalWrite(_ledPin, LOW);
        ledState = false;
      }
      lastBlinkTime = now;
    }
  }

private:
  uint8_t _ledPin;
  BLEServer* _pServer;
  BLEService* _pService;
  BLECharacteristic* _pCharacteristic;
  bool _deviceConnected;
  bool _oldDeviceConnected;
  
  // BLE Server Callbacks
  class MyServerCallbacks: public BLEServerCallbacks {
    BLEEventTransport* _transport;
  public:
    MyServerCallbacks(BLEEventTransport* transport) : _transport(transport) {}
    
    void onConnect(BLEServer* pServer) {
      _transport->setDeviceConnected(true);
    }
    
    void onDisconnect(BLEServer* pServer) {
      _transport->setDeviceConnected(false);
    }
  };
};

#else

// TRANSPORT_BLE tanımlı değilse stub kullan (Simülasyon için)
class BLEEventTransport : public IEventTransport {
public:
  BLEEventTransport(uint8_t ledPin) : _ledPin(ledPin) {
    pinMode(_ledPin, OUTPUT);
    digitalWrite(_ledPin, LOW);
  }
  
  void enableBLE() {
    // Stub: Simülasyon modunda işlem yok
  }
  
  void disableBLE() {
    // Stub: Simülasyon modunda işlem yok
  }
  
  bool isBLEEnabled() {
    return false;  // Simülasyon modunda BLE kapalı
  }
  
  void updateAdvertisingStatus() {
    // Stub: Simülasyon modunda işlem yok
  }
  
  void handleConnection() {
    // Stub: Simülasyon modunda işlem yok
  }
  
  void setDeviceConnected(bool connected) {
    // Stub: Simülasyon modunda işlem yok
  }

  void sendEvent(const Event& event) override {
    // LED tetikleme
    digitalWrite(_ledPin, HIGH);
    delay(30);
    digitalWrite(_ledPin, LOW);

    // Event type string'e çevir
    const char* typeStr = "";
    switch (event.type) {
      case THEME_ROTATE: typeStr = "THEME_ROTATE"; break;
      case MAIN_ROTATE: typeStr = "MAIN_ROTATE"; break;
      case SUB_ROTATE: typeStr = "SUB_ROTATE"; break;
      case CONFIRM: typeStr = "CONFIRM"; break;
      case EVENT_CANCEL: typeStr = "CANCEL"; break;
      case AI_PRESS: typeStr = "AI_PRESS"; break;
      case AI_RELEASE: typeStr = "AI_RELEASE"; break;
    }

    // Kompakt log formatı: [BLE] MAIN_ROTATE m=15 ts=12345
    Serial.print("[BLE] ");
    Serial.print(typeStr);
    
    // mainIndex sadece ilgili event'lerde
    if (event.type == MAIN_ROTATE || event.type == SUB_ROTATE || 
        event.type == CONFIRM || event.type == EVENT_CANCEL || 
        event.type == AI_PRESS || event.type == AI_RELEASE) {
      Serial.print(" m=");
      Serial.print(event.mainIndex);
    }
    
    // subIndex sadece ilgili event'lerde
    if (event.type == SUB_ROTATE || event.type == CONFIRM || 
        event.type == EVENT_CANCEL || event.type == AI_PRESS || 
        event.type == AI_RELEASE) {
      Serial.print(" s=");
      Serial.print(event.subIndex);
    }
    
    Serial.print(" ts=");
    Serial.println(event.ts);
  }

private:
  uint8_t _ledPin;
};

#endif // TRANSPORT_BLE

#endif // EVENT_TRANSPORT_H

