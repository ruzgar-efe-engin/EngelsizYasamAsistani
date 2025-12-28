# Bridge Server Architecture

## Genel Bakış

Bridge server, Wokwi simülasyonundan gelen Serial port verilerini BLE Peripheral olarak mobile app'lere iletir. Bu sayede mobile app'ler hem Wokwi bridge hem fiziksel BLE cihazını aynı kodla destekler.

## Mimari Diyagram

```
┌─────────────────┐
│  Wokwi Device   │
│  (ESP32-S3)     │
│  TRANSPORT_SERIAL│
└────────┬────────┘
         │ Serial Port (115200 baud)
         │ [BLE] MAIN_ROTATE m=1 s=0 ts=12345
         │ veya {"type":0,"mainIndex":1,"subIndex":0,"ts":12345}
         ▼
┌─────────────────┐
│ Bridge Server   │
│ (Node.js)       │
│                 │
│ ┌─────────────┐ │
│ │Serial       │ │
│ │Listener     │ │
│ └──────┬──────┘ │
│        │         │
│ ┌──────▼──────┐ │
│ │Event        │ │
│ │Parser       │ │
│ └──────┬──────┘ │
│        │         │
│ ┌──────▼──────┐ │
│ │BLE          │ │
│ │Peripheral   │ │
│ └──────┬──────┘ │
└────────┼────────┘
         │ BLE (GATT)
         │ Service UUID: 12345678-1234-1234-1234-123456789abc
         │ Characteristic UUID: 12345678-1234-1234-1234-123456789abd
         │ Device Name: GormeEngellilerKumanda
         ▼
┌─────────────────┐
│  Mobile App     │
│  (Android/iOS)  │
│  BLE Central    │
└─────────────────┘
```

## Fiziksel BLE Cihaz Flow

```
┌─────────────────┐
│  Fiziksel Cihaz │
│  (ESP32-S3)     │
│  TRANSPORT_BLE  │
└────────┬────────┘
         │ BLE (GATT)
         │ Service UUID: 12345678-1234-1234-1234-123456789abc
         │ Characteristic UUID: 12345678-1234-1234-1234-123456789abd
         │ Device Name: GormeEngellilerKumanda
         ▼
┌─────────────────┐
│  Mobile App     │
│  (Android/iOS)  │
│  BLE Central    │
└─────────────────┘
```

## Modüler Yapı

### lib/serial-listener.js
- Serial port dinleme
- Auto-reconnect logic
- Event callback'leri

### lib/ble-peripheral.js
- BLE Peripheral emülasyonu
- Advertising management
- Event forwarding

### lib/event-parser.js
- Text format parsing
- JSON format parsing
- Event validation

### config/ble-config.js
- UUID configuration
- Device name configuration
- Format helpers

## Event Flow

1. **Wokwi Device** → Serial port'a event gönderir
2. **Serial Listener** → Event'i dinler ve parse eder
3. **Event Parser** → Event'i JSON formatına çevirir
4. **BLE Peripheral** → Event'i BLE characteristic'e yazar
5. **Mobile App** → BLE notification ile event'i alır

## UUID ve Device Name

Tüm component'ler aynı UUID'leri ve device name'i kullanır:

- **Service UUID**: `12345678-1234-1234-1234-123456789abc`
- **Characteristic UUID**: `12345678-1234-1234-1234-123456789abd`
- **Device Name**: `GormeEngellilerKumanda`

Bu sayede mobile app'ler otomatik olarak her iki cihazı da bulabilir.

## Auto-Reconnect

- Serial port bağlantısı kesilirse otomatik yeniden bağlanır
- Configurable reconnect delay (varsayılan: 3000ms)
- Error recovery logic

## Logging

Winston logger kullanılır:
- Console output (colorized)
- Timestamp'li log formatı
- Environment variable ile log level kontrolü

## Environment Variables

- `SERIAL_PORT`: Serial port path (opsiyonel)
- `BAUD_RATE`: Baud rate (varsayılan: 115200)
- `LOG_LEVEL`: Logging seviyesi (error, warn, info, debug)
- `AUTO_RECONNECT`: Otomatik yeniden bağlanma (true/false)
- `RECONNECT_DELAY`: Yeniden bağlanma gecikmesi (ms)

