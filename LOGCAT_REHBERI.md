# Logcat Rehberi - Android Log'larına Bakma

## Hızlı Komutlar

### 1. Tüm Log'ları Göster (Filtresiz)
```bash
adb -s R5CX22L8QTR logcat
```

### 2. Sadece Uygulama Log'ları
```bash
adb -s R5CX22L8QTR logcat | grep -E "(Engelsiz|GormeEngelliler|BLEManager|DeviceEventService|SetupScreen)"
```

### 3. BLE Event'leri İzle
```bash
adb -s R5CX22L8QTR logcat | grep -E "(onEventReceived|onCharacteristicChanged|Event alındı|📥|📡)"
```

### 4. BLE Callback'leri İzle
```bash
adb -s R5CX22L8QTR logcat | grep -E "(onConnectionStateChange|onServicesDiscovered|onDescriptorWrite|🔄|📡|📝)"
```

### 5. Scroll ve Bip Sesleri
```bash
adb -s R5CX22L8QTR logcat | grep -E "(ToneGenerator|playScrollBeep|🔊|MAIN_ROTATE|SUB_ROTATE)"
```

### 6. Hata ve Exception'lar
```bash
adb -s R5CX22L8QTR logcat | grep -E "(FATAL|Exception|Error|❌)"
```

### 7. SetupScreen Log'ları
```bash
adb -s R5CX22L8QTR logcat | grep "SetupScreen"
```

### 8. DeviceEventService Log'ları
```bash
adb -s R5CX22L8QTR logcat | grep "DeviceEventService"
```

### 9. BLEManager Log'ları
```bash
adb -s R5CX22L8QTR logcat | grep "BLEManager"
```

### 10. Tüm Event Akışı (Wokwi → Bridge → Android)
```bash
adb -s R5CX22L8QTR logcat | grep -E "(sendEvent|Event alındı|Paket alındı|Wokwi|Bridge|Telefona)"
```

## Logcat Temizleme

```bash
adb -s R5CX22L8QTR logcat -c
```

## Logcat'i Dosyaya Kaydetme

```bash
adb -s R5CX22L8QTR logcat > logcat_output.txt
```

## Android Studio'da Logcat

1. Android Studio'yu aç
2. Alt kısımda "Logcat" tab'ına tıkla
3. Filtre kutusuna şunları yaz:
   - `package:mine` - Sadece uygulama log'ları
   - `tag:BLEManager` - Sadece BLEManager log'ları
   - `tag:SetupScreen` - Sadece SetupScreen log'ları
   - `tag:DeviceEventService` - Sadece DeviceEventService log'ları

## Önemli Log Tag'leri

- `BLEManager` - BLE bağlantı ve event log'ları
- `SetupScreen` - UI ve event log panel log'ları
- `DeviceEventService` - Service lifecycle ve event handling log'ları
- `AndroidRuntime` - Crash ve exception log'ları

## Debug İpuçları

1. **Event gelmiyor mu?**
   ```bash
   adb -s R5CX22L8QTR logcat | grep "onCharacteristicChanged"
   ```

2. **Bip sesi çıkmıyor mu?**
   ```bash
   adb -s R5CX22L8QTR logcat | grep "ToneGenerator\|playScrollBeep"
   ```

3. **Callback'ler tetiklenmiyor mu?**
   ```bash
   adb -s R5CX22L8QTR logcat | grep "CALLBACK TETİKLENDİ\|CALLBACK ÇAĞRILDI"
   ```

4. **Permission sorunları mı?**
   ```bash
   adb -s R5CX22L8QTR logcat | grep "permission\|Permission\|İzin"
   ```

