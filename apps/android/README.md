# Engelsiz Yaşam Asistanı - Android

## API Key Kurulumu

Google TTS API key'i kullanmak için aşağıdaki adımları izleyin:

1. **Google Cloud Console'dan API Key Alın**
   - https://console.cloud.google.com/apis/credentials adresine gidin
   - Yeni bir API key oluşturun veya mevcut bir key'i kullanın
   - Cloud Text-to-Speech API'nin etkin olduğundan emin olun

2. **local.properties Dosyası Oluşturun**
   ```bash
   cp local.properties.example local.properties
   ```

3. **API Key'i Ekleyin**
   `local.properties` dosyasını açın ve `your-api-key-here` yerine gerçek API key'inizi yazın:
   ```properties
   GOOGLE_TTS_API_KEY=your-actual-api-key-here
   ```

4. **Güvenlik Notu**
   - `local.properties` dosyası `.gitignore`'da olduğu için GitHub'a commit edilmeyecektir
   - API key'inizi asla GitHub'a yüklemeyin
   - Sadece `local.properties.example` dosyası commit edilir (template olarak)

## Build

```bash
./gradlew build
```

## Build

```bash
./build.sh  # Mac/Linux
build.bat   # Windows
```

## Çalıştırma

### Emulator'de Çalıştırma

```bash
./run.sh    # Mac/Linux
run.bat     # Windows
```

Script otomatik olarak:
- Emulator'ü kontrol eder, yoksa kurar
- APK'yı build eder (yoksa)
- Emulator'ü başlatır
- APK'yı yükler ve çalıştırır

### Wokwi Bridge Server ile Test

1. Wokwi device'ı Serial modda çalıştırın
2. Bridge server'ı başlatın:
   ```bash
   cd device/bridge-server
   npm install
   npm start
   ```
3. Android app'i açın ve cihaz araması yapın
4. Bridge server "GormeEngellilerKumanda" olarak görünecektir
5. Bağlanın ve event'leri test edin

### Fiziksel BLE Cihaz ile Test

1. Fiziksel cihazı BLE modda çalıştırın
2. Android app'i açın ve cihaz araması yapın
3. Fiziksel cihaz "GormeEngellilerKumanda" olarak görünecektir
4. Bağlanın ve event'leri test edin

**Not:** Mobile app hem Wokwi bridge hem fiziksel BLE cihazını aynı kodla destekler. Kod değişikliği gerekmez.

