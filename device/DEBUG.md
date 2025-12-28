# Wokwi Debug Kılavuzu

## Debug Yapılandırması

Bu proje Wokwi simülatörü ile debug için yapılandırılmıştır.

## Gereksinimler

- PlatformIO kurulu olmalı
- Wokwi simülatörü çalışıyor olmalı
- GDB server port 3333'te dinliyor olmalı

## Debug Adımları

### 1. Wokwi'de Debug Modunu Başlat

1. Wokwi simülatöründe **"Start with Debugger"** seçeneğini kullanın
2. Wokwi GDB server'ı port 3333'te başlatır
3. Simülatör başlar ve debugger'ı bekler

### 2. Cursor/VS Code'da Debug Başlat

1. `main.cpp` veya diğer dosyalarda breakpoint koyun (satır numarasının soluna tıklayın)
2. `F5` tuşuna basın veya Debug panelinden **"Wokwi GDB Debug (ESP32-S3)"** seçin
3. Debugger Wokwi'nin GDB server'ına bağlanır

### 3. Debug Özellikleri

- **Breakpoint'ler**: Kod satırlarında durur
- **Step Over (F10)**: Bir sonraki satıra geçer
- **Step Into (F11)**: Fonksiyon içine girer
- **Step Out (Shift+F11)**: Fonksiyondan çıkar
- **Continue (F5)**: Bir sonraki breakpoint'e kadar devam eder
- **Variables**: Değişkenleri inceleyebilirsiniz
- **Call Stack**: Fonksiyon çağrı yığınını görebilirsiniz

## Sorun Giderme

### Breakpoint'ler Çalışmıyor

1. **Debug build yapıldığından emin olun:**
   ```bash
   cd device
   pio run
   ```

2. **GDB server'ın çalıştığından emin olun:**
   - Wokwi'de "Start with Debugger" kullanın
   - Port 3333'ün açık olduğunu kontrol edin

3. **ELF dosyasının güncel olduğundan emin olun:**
   - `device/.pio/build/seeed_xiao_esp32s3/firmware.elf` dosyası mevcut olmalı

### GDB Bağlanamıyor

1. Wokwi simülatörünün çalıştığından emin olun
2. `wokwi.toml` dosyasında `gdbServerPort = 3333` olduğunu kontrol edin
3. Firewall'ın port 3333'ü engellemediğinden emin olun

### Debug Bilgileri Eksik

1. `platformio.ini` dosyasında `-g` ve `-O0` flag'lerinin olduğundan emin olun
2. Projeyi yeniden derleyin:
   ```bash
   cd device
   pio run
   ```

## Alternatif: Web GDB

Wokwi'nin web tabanlı GDB debugger'ını da kullanabilirsiniz:

1. Wokwi simülatöründe `F1` tuşuna basın
2. "GDB" yazın ve **"Start Web GDB Session"** seçin
3. Tarayıcıda GDB konsolu açılır
4. GDB komutlarını kullanarak debug yapabilirsiniz

## Örnek GDB Komutları

```gdb
# Breakpoint koy
break main.cpp:282

# Değişken değerini göster
print mainIndex

# Adım adım ilerle
step
next

# Devam et
continue
```

## Notlar

- Debug modunda optimizasyon kapalıdır (`-O0`), bu yüzden kod daha yavaş çalışabilir
- Production build için `-O0` flag'ini kaldırın ve `-Os` kullanın
- Wokwi simülatörü sadece Serial modunda çalışır (TRANSPORT_SERIAL)

