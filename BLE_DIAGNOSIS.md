# BLE Notify Sorunu Teşhis Rehberi

## Amaç
Android cihaz BLE peripheral'ı görüyor ama bridge → APK yönünde hiç paket gelmiyor. Bu rehber, sorunun macOS/bleno tarafında mı yoksa Android APK tarafında mı olduğunu tespit etmek için adım adım test prosedürü içerir.

## Test 1: nRF Connect ile Notify Testi

### Ön Hazırlık
1. **nRF Connect uygulamasını kur:**
   - Google Play Store'dan "nRF Connect" uygulamasını indir ve kur
   - Alternatif: [nRF Connect - Google Play](https://play.google.com/store/apps/details?id=no.nordicsemi.android.mcp)

2. **Bridge server'ı başlat:**
   ```bash
   cd device/bridge-server
   npm start
   ```
   - Bridge server'ın başarıyla başladığını kontrol et
   - BLE state'in `poweredOn` olduğunu kontrol et

3. **Wokwi'yi başlat:**
   - Wokwi simülasyonunu başlat
   - Serial Monitor'ün açık olduğundan emin ol

### Test Adımları

1. **Pairing mode'u aktif et:**
   - Wokwi'de AI butonuna 5 saniye basılı tut
   - LED'in yanıp sönmeye başladığını kontrol et
   - Bridge server loglarında "Pairing mode aktif" mesajını gör

2. **nRF Connect'te cihazı bul:**
   - nRF Connect uygulamasını aç
   - "Scan" butonuna bas
   - "GormeEngellilerKumanda" veya "Engelsiz Yaşam Asistanı" adlı cihazı bul
   - Cihazın Service UUID'sini kontrol et: `12345678-1234-1234-1234-123456789abc`

3. **Cihaza bağlan:**
   - Cihaza tıkla
   - "Connect" butonuna bas
   - Bağlantı başarılı olduğunu kontrol et

4. **Service ve Characteristic'i bul:**
   - Service listesinde `12345678-1234-1234-1234-123456789abc` UUID'sini bul
   - Service'i genişlet
   - Characteristic'i bul: `12345678-1234-1234-1234-123456789abd`

5. **Notification'ı aktif et:**
   - Characteristic'e tıkla
   - "Enable notifications" veya "Subscribe" butonuna bas
   - nRF Connect'te "Notifications enabled" mesajını gör

6. **Event gönder:**
   - Wokwi'de scroll wheel'i çevir veya butona bas
   - Bridge server loglarında event'in gönderildiğini kontrol et

7. **Notify geliyor mu kontrol et:**
   - nRF Connect'te "Notifications" sekmesine bak
   - Event'ler görünüyor mu kontrol et

### Beklenen Sonuçlar

**Senaryo A: nRF Connect'te notify GELİYOR**
- ✅ Sorun Android APK tarafında
- ✅ Bridge server ve macOS bleno çalışıyor
- 🔧 Çözüm: Android APK'daki `onCharacteristicChanged()` callback'ini düzelt

**Senaryo B: nRF Connect'te notify GELMİYOR**
- ❌ Sorun bridge server / macOS bleno tarafında
- ❌ macOS CoreBluetooth Android'in descriptor yazmasını algılamıyor
- 🔧 Çözüm: Polling-based read workaround'u uygula

## Test 2: Payload Boyutu Testi

### Amaç
MTU/payload boyutu sorununu tespit etmek. Eğer 1 byte bile gelmiyorsa, notify mekanizması tamamen çalışmıyor demektir.

### Test Adımları

1. **Bridge server'ı durdur** (Ctrl+C)

2. **Geçici test kodu ekle:**
   - `device/bridge-server/lib/ble-peripheral.js` dosyasını aç
   - `sendEvent()` metodunu geçici olarak değiştir:
   ```javascript
   sendEvent(event) {
       // Geçici test: 1 byte payload
       const testData = Buffer.from([0x01]);
       if (this.eventCharacteristic) {
           const result = this.eventCharacteristic.updateValue(testData);
           console.log(`✅ Test payload gönderildi: ${result ? 'başarılı' : 'başarısız'}`);
       }
   }
   ```

3. **Bridge server'ı yeniden başlat:**
   ```bash
   npm start
   ```

4. **Android APK'yi aç:**
   - Cihaza bağlan
   - Event log panelini aç

5. **Wokwi'den event gönder:**
   - Scroll wheel'i çevir
   - Bridge server loglarında "Test payload gönderildi" mesajını gör

6. **Android'de kontrol et:**
   - Event log panelinde 1 byte'lık event görünüyor mu?
   - `adb logcat | grep "onCharacteristicChanged"` komutuyla log'ları kontrol et

### Beklenen Sonuçlar

**Senaryo A: 1 byte GELİYOR**
- ✅ Notify mekanizması çalışıyor
- ⚠️ Sorun JSON/MTU boyutu ile ilgili
- 🔧 Çözüm: JSON payload'ını küçült veya MTU negotiation yap

**Senaryo B: 1 byte bile GELMİYOR**
- ❌ Notify mekanizması tamamen çalışmıyor
- ❌ macOS CoreBluetooth sorunu
- 🔧 Çözüm: Polling-based read workaround'u uygula (zorunlu)

## Test 3: Log Analizi

### Bridge Server Log Kontrolü

1. **onSubscribe callback'i tetikleniyor mu?**
   ```bash
   # Bridge server terminalinde şu mesajı ara:
   "🎉 onSubscribe CALLBACK TETİKLENDİ!"
   ```
   - ✅ Görüyorsan: Bridge server subscribe'ı algılıyor
   - ❌ Görmüyorsan: macOS CoreBluetooth Android'in descriptor yazmasını algılamıyor

2. **updateValue() başarılı mı?**
   ```bash
   # Bridge server terminalinde şu mesajı ara:
   "✅ Notification başarıyla gönderildi"
   ```
   - ✅ Görüyorsan: Bridge server notify gönderiyor
   - ❌ Görmüyorsan: `_updateValueCallback` null (subscribe yok)

3. **Read request'ler geliyor mu?**
   ```bash
   # Bridge server terminalinde şu mesajı ara:
   "📖 onReadRequest CALLBACK TETİKLENDİ!"
   ```
   - ✅ Görüyorsan: Android read yapıyor (polling için hazır)
   - ❌ Görmüyorsan: Android read yapmıyor

### Android Log Kontrolü

1. **onDescriptorWrite başarılı mı?**
   ```bash
   adb logcat | grep "onDescriptorWrite"
   ```
   - ✅ "GATT_SUCCESS" görüyorsan: Descriptor yazma başarılı
   - ❌ Hata görüyorsan: Descriptor yazma başarısız

2. **onCharacteristicChanged tetikleniyor mu?**
   ```bash
   adb logcat | grep "onCharacteristicChanged\|NOTIFY GELDI"
   ```
   - ✅ "NOTIFY GELDI" görüyorsan: Notify çalışıyor
   - ❌ Hiç log yok: Notify çalışmıyor (macOS sorunu)

3. **Read request'ler yapılıyor mu?**
   ```bash
   adb logcat | grep "readCharacteristic"
   ```
   - ✅ Read request log'ları görüyorsan: Android read yapıyor
   - ❌ Hiç log yok: Android read yapmıyor

## Sonuç ve Çözüm Önerileri

### Eğer nRF Connect'te bile notify gelmiyorsa:
→ **Kesin çözüm:** Polling-based read workaround'u uygula

### Eğer nRF Connect'te notify geliyorsa ama APK'de gelmiyorsa:
→ **Android APK sorunu:** `onCharacteristicChanged()` callback'ini düzelt

### Eğer 1 byte geliyorsa ama JSON gelmiyorsa:
→ **MTU/JSON sorunu:** Payload boyutunu küçült veya MTU negotiation yap

### Eğer hiçbir şey gelmiyorsa:
→ **macOS CoreBluetooth sorunu:** Polling-based read workaround'u uygula (zorunlu)

## Notlar

- Bu testler macOS Sonoma/Ventura + bleno + Android arasındaki bilinen uyumluluk sorunlarını tespit etmek için tasarlandı
- nRF Connect testi en kritik testtir - eğer nRF Connect'te bile gelmiyorsa, sorun kesinlikle bridge/macOS tarafında
- Payload boyutu testi, MTU sorunlarını tespit etmek için önemlidir
- Log analizi, sorunun tam olarak nerede olduğunu anlamak için kritiktir

