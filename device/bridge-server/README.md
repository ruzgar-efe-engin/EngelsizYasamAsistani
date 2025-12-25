# Wokwi BLE Bridge Server

Bu server, Wokwi simülasyonundan gelen Serial port verilerini dinler ve bunları BLE Peripheral olarak mobile app'lere iletir.

## Konum

Bridge server artık `device/bridge-server/` altında bulunmaktadır. Bu konum device geliştirme sürecinin bir parçası olduğu için device/ klasörü altına taşınmıştır.

## Kurulum

```bash
cd device/bridge-server
npm install
```

## Gereksinimler

- Node.js 18+ (LTS önerilir)
- Bluetooth adaptör (BLE desteği)
- Wokwi device Serial port bağlantısı

## Kullanım

### Otomatik Başlatma (Önerilen)

**Mac/Linux:**
```bash
./start.sh
```

**Windows:**
```batch
start.bat
```

Script otomatik olarak:
- Node.js ve npm kontrolü yapar
- Dependencies yükler (gerekirse)
- .env dosyası kontrolü yapar
- Bridge server'ı başlatır

### Manuel Başlatma

**Otomatik Port Bulma:**
```bash
npm start
```

Server otomatik olarak USB serial port'unu bulmaya çalışır.

### Manuel Port Belirtme

```bash
SERIAL_PORT=/dev/tty.usbserial-XXXX npm start
```

Windows için:
```bash
set SERIAL_PORT=COM3
npm start
```

### Environment Variables

`.env` dosyası oluşturarak yapılandırma yapabilirsiniz:

```bash
cp .env.example .env
```

`.env` dosyasında ayarlanabilir değişkenler:
- `SERIAL_PORT`: Serial port path (opsiyonel, otomatik bulunur)
- `BAUD_RATE`: Baud rate (varsayılan: 115200)
- `LOG_LEVEL`: Logging seviyesi (error, warn, info, debug)
- `AUTO_RECONNECT`: Otomatik yeniden bağlanma (true/false)
- `RECONNECT_DELAY`: Yeniden bağlanma gecikmesi (ms)

### Development Mode

```bash
npm run dev  # nodemon ile otomatik restart
```

## Mimari

### Flow

```
Wokwi Device (Serial) -> Bridge Server -> BLE Peripheral -> Mobile App
```

1. Wokwi device Serial port üzerinden event'leri gönderir
2. Bridge server Serial port'u dinler ve event'leri parse eder
3. Bridge server BLE Peripheral olarak mobile app'lere event'leri iletir
4. Mobile app BLE Central olarak bridge server'a bağlanır ve event'leri alır

### Modüler Yapı

```
device/bridge-server/
├── index.js              # Ana entry point
├── lib/
│   ├── serial-listener.js    # Serial port dinleme
│   ├── ble-peripheral.js     # BLE Peripheral emülasyonu
│   └── event-parser.js       # Event parsing
├── config/
│   └── ble-config.js         # BLE configuration (UUID, device name)
└── package.json
```

## BLE Configuration

### UUID'ler

- **Service UUID**: `12345678-1234-1234-1234-123456789abc`
- **Characteristic UUID**: `12345678-1234-1234-1234-123456789abd`
- **Device Name**: `GormeEngellilerKumanda`

Bu değerler device'daki `EventTransport.h` ve mobile app'lerdeki BLEManager'larla aynıdır. Mobile app'ler hem Wokwi bridge hem fiziksel BLE cihazını aynı kodla destekler.

## Event Format

Bridge server iki formatı destekler:

1. **Text Format**: `[BLE] THEME_ROTATE t=2 m=1 s=0 ts=12345`
2. **JSON Format**: `{"type":0,"themeIndex":2,"mainIndex":1,"subIndex":0,"ts":12345}`

## Best Practices

### Error Handling

- Tüm async işlemler try-catch ile korunur
- Serial port hatalarında otomatik yeniden bağlanma
- BLE state değişikliklerinde otomatik recovery

### Logging

Winston logger kullanılır:
- Console output (colorized)
- Timestamp'li log formatı
- Environment variable ile log level kontrolü

### Auto-Reconnect

- Serial port bağlantısı kesilirse otomatik yeniden bağlanır
- Configurable reconnect delay
- Graceful error handling

## Troubleshooting

### Serial Port Bulunamıyor

- USB kablosunun bağlı olduğundan emin olun
- Wokwi device'ın Serial port'unun açık olduğundan emin olun
- Manuel port belirtmeyi deneyin: `SERIAL_PORT=/dev/tty.usbserial-XXXX npm start`
- Port listesini görmek için server'ı çalıştırın (hata mesajında gösterilir)

### BLE Advertising Başlamıyor

- Bluetooth'un açık olduğundan emin olun
- Root/sudo yetkisi gerekebilir (Linux)
- `bleno` kütüphanesi için sistem gereksinimlerini kontrol edin
- macOS'ta Bluetooth izinleri gerekebilir

### Event'ler Gönderilmiyor

- Mobile app'in bridge server'a bağlandığından emin olun
- Serial port'tan veri geldiğini kontrol edin (console log'ları)
- BLE bağlantısının aktif olduğunu kontrol edin
- Log level'ı `debug` yaparak daha fazla bilgi alın: `LOG_LEVEL=debug npm start`

### Mobile App Cihazı Bulamıyor

- Bridge server'ın çalıştığından emin olun
- Device name'in "GormeEngellilerKumanda" olduğunu kontrol edin
- UUID'lerin doğru olduğunu kontrol edin
- Bluetooth'un her iki cihazda da açık olduğundan emin olun

## Mobile App Entegrasyonu

Mobile app'ler (Android ve iOS) hem Wokwi bridge hem fiziksel BLE cihazını aynı kodla destekler:

- Aynı UUID'ler kullanılır (SERVICE_UUID, CHARACTERISTIC_UUID)
- Device name kontrolü yapılır ("GormeEngellilerKumanda")
- Kod değişikliği gerekmez, sadece bridge server configuration yeterlidir

## Development

### Scripts

- `npm start`: Production mode'da başlat
- `npm run dev`: Development mode (nodemon ile auto-restart)
- `npm test`: Test script'leri (gelecekte eklenecek)
- `npm run build`: Build script (şu an gerekli değil)

### Code Structure

- **Modüler yapı**: Her component ayrı modül (lib/)
- **Configuration**: Merkezi config dosyası (config/ble-config.js)
- **Environment variables**: .env dosyası ile yapılandırma
- **Logging**: Winston ile structured logging

## License

MIT
