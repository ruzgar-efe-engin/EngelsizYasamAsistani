# Engelsiz Yaşam Asistanı (EYA)

Görme engelli kullanıcılar için dokunsal ve sesli geri bildirimle çalışan, ekransız akıllı kontrol cihazı projesi.

## Proje Yapısı

```
/
├── device/              # ESP32-S3 firmware (PlatformIO)
│   ├── src/            # Kaynak kodlar
│   ├── platformio.ini  # PlatformIO konfigürasyonu
│   ├── wokwi.toml      # Wokwi simülasyon konfigürasyonu
│   └── diagram.json    # Wokwi devre şeması
└── apps/
    └── eya/            # Android uygulaması (Kotlin)
        ├── src/        # Kaynak kodlar
        └── build.gradle.kts
```

## Mimari

### Cihaz (Device)
- **ESP32-S3** (Seeed Studio XIAO)
- Encoder ve buton okuma
- BLE üzerinden event gönderimi
- LED geri bildirim

### Android Uygulaması (EYA)
- **Kotlin** ile geliştirilmiş Android uygulaması
- BLE foreground service ile cihazdan event dinleme
- Event → menü pozisyonu → metin → TTS seslendirme
- AI entegrasyonu (Gemini, OpenAI) - press-to-talk
- Sistem servisleri entegrasyonu (alarm, konum, iletişim, vb.)

## BLE İletişimi

Cihaz, kullanıcı etkileşimlerini BLE üzerinden event formatında Android uygulamasına gönderir.

### Event Türleri
- `MAIN_ROTATE` (0) - Ana menü encoder döndü
- `SUB_ROTATE` (1) - Alt menü encoder döndü
- `CONFIRM` (2) - Seçim onaylandı
- `EVENT_CANCEL` (3) - İptal
- `AI_PRESS` (4) - AI butonu basıldı
- `AI_RELEASE` (5) - AI butonu bırakıldı

### Event Formatı
```json
{
  "type": 0,
  "mainIndex": 1,
  "subIndex": 0,
  "ts": 1234567890
}
```

## Geliştirme

### Device (ESP32)
```bash
cd device
pio run          # Derle
pio upload       # Yükle
pio monitor      # Serial monitor
```

### Android (EYA)
```bash
cd apps/eya
./gradlew assembleDebug    # APK derle
./gradlew installDebug     # Telefona yükle
```

## Gereksinimler

- **ESP32-S3** (Seeed Studio XIAO)
- **Android 8.0+** cihaz
- **PlatformIO** (cihaz geliştirme için)
- **Android Studio** veya **Gradle** (Android geliştirme için)

## Notlar

- Cihaz sadece **pozisyon (index)** gönderir, metin bilgisi yok
- Android uygulaması event'i alır, pozisyona göre menü metnini bulur ve TTS ile seslendirir
- BLE iletişimi gerçek cihaz üzerinden çalışır
- Wokwi simülasyonu mevcut ancak iletişim gerçek cihaz üzerinden yapılır
