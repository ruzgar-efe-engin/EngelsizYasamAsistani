# Görme Engelliler Projesi - Monorepo

Görme engelli kullanıcıların toplu taşıma ve günlük şehir yaşamında menüler arasında dokunsal ve sesli geri bildirimle gezinebilmesini sağlayan, ekransız, avuç içi / saat formunda bir akıllı kontrol cihazı projesi.

## Proje Yapısı

```
/
├── device/              # ESP32 firmware (PlatformIO)
│   ├── src/
│   ├── platformio.ini
│   └── wokwi.toml
├── apps/
│   ├── ios/            # iOS uygulaması (SwiftUI)
│   ├── android/         # Android uygulaması (Kotlin)
│   └── web/
│       ├── driver-dashboard/  # Şoför dashboard (React + TypeScript)
│       └── admin-panel/       # Admin panel (React + TypeScript)
├── backend/            # Backend API (Node.js + TypeScript + Express)
└── contracts/          # Event sözleşmeleri (JSON şemalar)
```

## Mimari

### Cihaz (Device)
- ESP32-S3 (Seeed Studio XIAO)
- Encoder ve buton okuma
- Event üretimi ve transport abstraction
- LED geri bildirim (Faz-1)
- BLE event gönderimi (gelecekte)

### Mobil Uygulamalar
- iOS (SwiftUI) - CoreBluetooth ile BLE event dinleme
- Android (Kotlin) - BLE foreground service
- Event → pozisyon → metin → TTS
- AI tetikleme (press-to-talk)

### Backend
- Node.js + TypeScript + Express
- AI servisleri
- Kullanıcı / cihaz / hat / durak yönetimi
- Şoför bildirimleri
- Admin API

### Web Uygulamaları
- Şoför Dashboard (React + TypeScript)
- Admin Panel (React + TypeScript)
- Aynı backend API'yi kullanır

## Event Sistemi

Cihaz, kullanıcı etkileşimlerini event formatında gönderir. Detaylar için `contracts/README.md` dosyasına bakın.

## Geliştirme

### Device
```bash
cd device
pio run
```

### Backend
```bash
cd backend
npm install
npm run dev
```

### Web Apps
```bash
cd apps/web/driver-dashboard
npm install
npm run dev
```

## Notlar

- Bu proje monorepo yapısında geliştirilmektedir
- Her katman bağımsız olarak geliştirilebilir
- Event sözleşmesi `contracts/` klasöründe "tek gerçek" olarak tutulur

