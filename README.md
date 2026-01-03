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

## Gereksinimler

- **ESP32-S3** (Seeed Studio XIAO)
- **Android 8.0+** cihaz
- **PlatformIO** (cihaz geliştirme için)
- **Android Studio** veya **Gradle** (Android geliştirme için)

---

## 📖 KURULUM REHBERİ

Bu rehber, projeyi sıfırdan kurmak isteyenler için hazırlanmıştır. Her adımı sırayla takip edin.

### 1️⃣ Projeyi Git'ten İndirme

**Windows için:**
1. Bilgisayarınızda bir klasör açın (örneğin: `C:\Users\İsminiz\Documents`)
2. Bu klasörde sağ tıklayın ve "Git Bash Here" seçin (Git yüklüyse)
3. Şu komutu yazın:
   ```bash
   git clone https://github.com/RuzgarEfeEngin/EngelsizYasamAsistani.git
   ```
4. Enter'a basın ve proje indirilsin
5. İndirme bitince klasöre girin:
   ```bash
   cd EngelsizYasamAsistani
   ```

**Mac için:**
1. Terminal uygulamasını açın (Spotlight'ta "Terminal" yazıp Enter)
2. İstediğiniz bir klasöre gidin (örneğin):
   ```bash
   cd ~/Documents
   ```
3. Şu komutu yazın:
   ```bash
   git clone https://github.com/RuzgarEfeEngin/EngelsizYasamAsistani.git
   ```
4. Enter'a basın ve proje indirilsin
5. İndirme bitince klasöre girin:
   ```bash
   cd EngelsizYasamAsistani
   ```

---

### 2️⃣ Device (ESP32) Kurulumu

Device, fiziksel cihazın (ESP32-S3) yazılımıdır. Önce gerekli programları kurmalısınız.

#### Windows için Device Kurulumu

**Adım 1: PlatformIO Kurulumu**
1. Tarayıcınızda şu adrese gidin: https://platformio.org/install/cli
2. "Windows" sekmesine tıklayın
3. "Installer" butonuna tıklayıp indirin
4. İndirilen dosyayı çalıştırın ve kurulumu tamamlayın
5. Kurulum bitince bilgisayarınızı yeniden başlatın

**Adım 2: Device'ı Derleme**
1. Git Bash veya PowerShell'i açın
2. Proje klasörüne gidin:
   ```bash
   cd EngelsizYasamAsistani/device
   ```
3. Şu komutu yazın:
   ```bash
   pio run
   ```
4. Birkaç dakika bekleyin, "SUCCESS" yazısını görünce tamamlanmış demektir

**Adım 3: Device'ı Wokwi ile Başlatma (Simülasyon)**
1. Tarayıcınızda https://wokwi.com adresine gidin
2. Hesap oluşturun veya giriş yapın
3. Sol üstteki "New Project" butonuna tıklayın
4. "Import from file" seçeneğini seçin
5. `EngelsizYasamAsistani/device/diagram.json` dosyasını seçin
6. Proje açılacak, yeşil "Start Simulation" butonuna tıklayın
7. Simülasyon başlayacak ve cihaz çalışacak

**Adım 4: Device'ı USB'den Güncelleme (Gerçek Cihaz)**
1. ESP32-S3 cihazınızı USB kablosu ile bilgisayara bağlayın
2. Windows cihazı tanıyacak (ilk seferde sürücü yüklemesi gerekebilir)
3. Git Bash veya PowerShell'de device klasörüne gidin:
   ```bash
   cd EngelsizYasamAsistani/device
   ```
4. Şu komutu yazın:
   ```bash
   pio run -t upload
   ```
5. Birkaç dakika bekleyin, "SUCCESS" yazısını görünce yükleme tamamlanmıştır

#### Mac için Device Kurulumu

**Adım 1: PlatformIO Kurulumu**
1. Terminal'i açın
2. Şu komutu yazın:
   ```bash
   python3 -c "$(curl -fsSL https://raw.githubusercontent.com/platformio/platformio-core-installer/master/get-platformio.py)"
   ```
3. Enter'a basın ve kurulum başlasın
4. Kurulum bitince Terminal'i kapatıp yeniden açın
5. Şu komutu yazıp kurulumu kontrol edin:
   ```bash
   pio --version
   ```
6. Bir versiyon numarası görüyorsanız kurulum başarılıdır

**Adım 2: Device'ı Derleme**
1. Terminal'i açın
2. Proje klasörüne gidin:
   ```bash
   cd ~/Documents/EngelsizYasamAsistani/device
   ```
   (Eğer farklı bir yere indirdiyseniz o yolu yazın)
3. Şu komutu yazın:
   ```bash
   pio run
   ```
4. Birkaç dakika bekleyin, "SUCCESS" yazısını görünce tamamlanmış demektir

**Adım 3: Device'ı Wokwi ile Başlatma (Simülasyon)**
1. Tarayıcınızda https://wokwi.com adresine gidin
2. Hesap oluşturun veya giriş yapın
3. Sol üstteki "New Project" butonuna tıklayın
4. "Import from file" seçeneğini seçin
5. `EngelsizYasamAsistani/device/diagram.json` dosyasını seçin
6. Proje açılacak, yeşil "Start Simulation" butonuna tıklayın
7. Simülasyon başlayacak ve cihaz çalışacak

**Adım 4: Device'ı USB'den Güncelleme (Gerçek Cihaz)**
1. ESP32-S3 cihazınızı USB kablosu ile Mac'inize bağlayın
2. Terminal'de device klasörüne gidin:
   ```bash
   cd ~/Documents/EngelsizYasamAsistani/device
   ```
3. Şu komutu yazın:
   ```bash
   pio run -t upload
   ```
4. Birkaç dakika bekleyin, "SUCCESS" yazısını görünce yükleme tamamlanmıştır

---

### 3️⃣ EYA (Android Uygulaması) Kurulumu

EYA, Android telefonunuza yüklenecek uygulamadır. Önce gerekli programları kurmalısınız.

#### Windows için EYA Kurulumu

**Adım 1: Android Studio Kurulumu**
1. Tarayıcınızda şu adrese gidin: https://developer.android.com/studio
2. "Download Android Studio" butonuna tıklayın
3. İndirilen dosyayı çalıştırın ve kurulumu tamamlayın
4. İlk açılışta "Standard" kurulumu seçin ve "Next" butonlarına tıklayın
5. Android Studio açılınca "More Actions" > "SDK Manager" açın
6. "SDK Platforms" sekmesinde "Android 8.0 (Oreo)" ve üstünü işaretleyin
7. "SDK Tools" sekmesinde "Android SDK Build-Tools", "Android SDK Platform-Tools", "Android SDK Command-line Tools" işaretli olsun
8. "Apply" butonuna tıklayın ve indirme bitene kadar bekleyin

**Adım 2: Java JDK Kurulumu**
1. Android Studio içinde otomatik olarak JDK kurulur, ekstra bir şey yapmanıza gerek yok
2. Eğer sorun yaşarsanız: https://adoptium.net/ adresinden JDK 17 indirip kurun

**Adım 3: EYA'yı Derleme**
1. Android Studio'yu açın
2. "Open" butonuna tıklayın
3. `EngelsizYasamAsistani/apps/eya` klasörünü seçin
4. Android Studio projeyi açacak, birkaç dakika bekleyin (ilk seferde uzun sürebilir)
5. Üst menüden "Build" > "Make Project" seçin
6. Alt kısımdaki "Build" sekmesinde ilerlemeyi görebilirsiniz
7. "BUILD SUCCESSFUL" yazısını görünce derleme tamamlanmıştır

**Adım 4: EYA'yı Başlatma (Telefonda)**
1. Android telefonunuzu USB kablosu ile bilgisayara bağlayın
2. Telefonda "USB ile dosya aktarımı" veya "Dosya aktarımı" modunu seçin
3. Android Studio'da üst menüden "Run" > "Run 'app'" seçin
4. Telefonunuz listede görünecek, seçip "OK" butonuna tıklayın
5. Uygulama telefona yüklenecek ve otomatik açılacak

**Adım 5: EYA'yı USB'den Clean Build ile Güncelleme**
1. Android Studio'da üst menüden "Build" > "Clean Project" seçin
2. Birkaç saniye bekleyin
3. Sonra "Build" > "Rebuild Project" seçin
4. Derleme bitince telefonunuzu USB ile bağlayın
5. "Run" > "Run 'app'" seçin
6. Uygulama güncellenmiş haliyle yüklenecek

#### Mac için EYA Kurulumu

**Adım 1: Android Studio Kurulumu**
1. Tarayıcınızda şu adrese gidin: https://developer.android.com/studio
2. "Download Android Studio" butonuna tıklayın
3. Mac için .dmg dosyası indirilecek
4. İndirilen dosyayı açın ve Android Studio'yu Applications klasörüne sürükleyin
5. Applications klasöründen Android Studio'yu açın
6. İlk açılışta "Standard" kurulumu seçin ve "Next" butonlarına tıklayın
7. Android Studio açılınca "More Actions" > "SDK Manager" açın
8. "SDK Platforms" sekmesinde "Android 8.0 (Oreo)" ve üstünü işaretleyin
9. "SDK Tools" sekmesinde "Android SDK Build-Tools", "Android SDK Platform-Tools", "Android SDK Command-line Tools" işaretli olsun
10. "Apply" butonuna tıklayın ve indirme bitene kadar bekleyin

**Adım 2: Java JDK Kurulumu**
1. Mac'te genellikle Java zaten kuruludur
2. Terminal'de şu komutu yazıp kontrol edin:
   ```bash
   java -version
   ```
3. Eğer hata verirse, Android Studio içinde otomatik olarak JDK kurulur

**Adım 3: EYA'yı Derleme**
1. Android Studio'yu açın
2. "Open" butonuna tıklayın
3. `EngelsizYasamAsistani/apps/eya` klasörünü seçin
4. Android Studio projeyi açacak, birkaç dakika bekleyin (ilk seferde uzun sürebilir)
5. Üst menüden "Build" > "Make Project" seçin
6. Alt kısımdaki "Build" sekmesinde ilerlemeyi görebilirsiniz
7. "BUILD SUCCESSFUL" yazısını görünce derleme tamamlanmıştır

**Adım 4: EYA'yı Başlatma (Telefonda)**
1. Android telefonunuzu USB kablosu ile Mac'inize bağlayın
2. Telefonda "USB ile dosya aktarımı" veya "Dosya aktarımı" modunu seçin
3. Android Studio'da üst menüden "Run" > "Run 'app'" seçin
4. Telefonunuz listede görünecek, seçip "OK" butonuna tıklayın
5. Uygulama telefona yüklenecek ve otomatik açılacak

**Adım 5: EYA'yı USB'den Clean Build ile Güncelleme**
1. Android Studio'da üst menüden "Build" > "Clean Project" seçin
2. Birkaç saniye bekleyin
3. Sonra "Build" > "Rebuild Project" seçin
4. Derleme bitince telefonunuzu USB ile bağlayın
5. "Run" > "Run 'app'" seçin
6. Uygulama güncellenmiş haliyle yüklenecek

---

### 4️⃣ Telefonda Yapılması Gereken Ayarlar

EYA uygulamasının düzgün çalışması için telefonunuzda bazı ayarlar yapmanız gerekir:

**Adım 1: Geliştirici Seçeneklerini Açma**
1. Telefonunuzun "Ayarlar" uygulamasını açın
2. "Telefon Hakkında" veya "Cihaz Bilgisi" bölümüne gidin
3. "Yapı Numarası" veya "Build Number" yazısını bulun
4. Bu yazıya 7 kez arka arkaya dokunun
5. "Geliştirici oldunuz!" gibi bir mesaj göreceksiniz

**Adım 2: USB Hata Ayıklamayı Açma**
1. Ayarlar'a geri dönün
2. "Geliştirici Seçenekleri" veya "Developer Options" bölümünü bulun
3. "USB Hata Ayıklama" veya "USB Debugging" seçeneğini açın
4. Onay penceresinde "Tamam" veya "OK" butonuna tıklayın

**Adım 3: Bilinmeyen Kaynaklardan Uygulama Yükleme**
1. Ayarlar'da "Güvenlik" veya "Security" bölümüne gidin
2. "Bilinmeyen Kaynaklardan Uygulama Yükleme" veya "Install Unknown Apps" seçeneğini açın
3. Android Studio için bu seçeneği açın

**Adım 4: EYA Uygulaması İzinleri**
1. EYA uygulamasını açın
2. Uygulama ilk açıldığında bir izin ekranı göreceksiniz
3. Tüm izinleri vermeniz gerekiyor:
   - **Bluetooth**: Cihazla bağlantı için
   - **Konum**: Bluetooth tarama için (Android gereksinimi)
   - **Mikrofon**: Bas-konuş özelliği için
   - **Kişiler**: Rehberden arama için
   - **Telefon**: Arama yapmak için
   - **SMS**: Acil durum mesajları için
   - **Arama Geçmişi**: Son arayanı aramak için
4. Her izin için "İzin Ver" veya "Allow" butonuna tıklayın
5. Tüm izinler verilmeden uygulama çalışmaz

**Adım 5: Erişilebilirlik Servisi (112 Arama için)**
1. Ayarlar > "Erişilebilirlik" veya "Accessibility" bölümüne gidin
2. "Yüklü Uygulamalar" veya "Installed Apps" seçeneğine tıklayın
3. "EYA" uygulamasını bulun ve açın
4. "EYA Erişilebilirlik Servisi" veya "EYA Accessibility Service" seçeneğini açın
5. Bu, 112 aramalarının otomatik yapılması için gereklidir

**Adım 6: Uygulamayı Arka Planda Çalıştırma İzni**
1. Ayarlar > "Uygulamalar" veya "Apps" bölümüne gidin
2. "EYA" uygulamasını bulun ve açın
3. "Pil" veya "Battery" bölümüne gidin
4. "Kısıtlama Yok" veya "Unrestricted" seçeneğini seçin
5. Bu, uygulamanın arka planda çalışmaya devam etmesini sağlar

**Adım 7: Bildirim İzinleri**
1. Ayarlar > "Uygulamalar" > "EYA" > "Bildirimler" bölümüne gidin
2. Tüm bildirim izinlerini açın
3. Bu, BLE servisinin çalışması için gereklidir

---

## 🎉 TAMAMLANDI!

Artık projeyi kullanmaya hazırsınız! Herhangi bir sorun yaşarsanız, yukarıdaki adımları tekrar kontrol edin.

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

## Notlar

- Cihaz sadece **pozisyon (index)** gönderir, metin bilgisi yok
- Android uygulaması event'i alır, pozisyona göre menü metnini bulur ve TTS ile seslendirir
- BLE iletişimi gerçek cihaz üzerinden çalışır
- Wokwi simülasyonu mevcut ancak iletişim gerçek cihaz üzerinden yapılır
