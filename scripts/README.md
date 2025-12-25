# Cursor Auto Accept Script

Bu klasör, Cursor IDE'de Agent modunda otomatik olarak değişiklikleri kabul etmek için Hammerspoon scriptini içerir.

## Kurulum

### 1. Hammerspoon'u Yükleyin

```bash
brew install --cask hammerspoon
```

### 2. Script'i Hammerspoon'a Ekleyin

İki yöntem var:

#### Yöntem 1: Doğrudan init.lua'ya ekleme

```bash
# ~/.hammerspoon/init.lua dosyasını düzenleyin
nano ~/.hammerspoon/init.lua
```

Dosyanın sonuna şunu ekleyin:

```lua
-- Cursor Auto Accept
require("cursor-auto-accept").start()
```

Ardından script dosyasını kopyalayın:

```bash
cp scripts/cursor-auto-accept.lua ~/.hammerspoon/cursor-auto-accept.lua
```

#### Yöntem 2: Sembolik link (önerilen)

```bash
ln -s "$(pwd)/scripts/cursor-auto-accept.lua" ~/.hammerspoon/cursor-auto-accept.lua
```

Sonra `~/.hammerspoon/init.lua` dosyasına ekleyin:

```lua
require("cursor-auto-accept").start()
```

### 3. Hammerspoon'u Yeniden Yükleyin

Hammerspoon menü çubuğundaki ikona tıklayın ve "Reload Config" seçeneğini seçin.

## Kullanım

- **Otomatik Başlatma**: Script, Hammerspoon yüklendiğinde otomatik olarak başlar (eğer `init.lua`'da `.start()` çağrısı varsa).

- **Manuel Kontrol**: 
  - `Cmd+Shift+A` tuş kombinasyonu ile script'i başlatıp/durdururabilirsiniz.
  - Hammerspoon menü çubuğundan da kontrol edebilirsiniz.

## Ayarlar

Script içindeki ayarları değiştirebilirsiniz:

- `CHECK_INTERVAL`: Kontrol aralığı (varsayılan: 0.5 saniye)
- `ACCEPT_KEY`: Kullanılacak tuş kombinasyonu (varsayılan: Cmd+Return)

## Notlar

⚠️ **Dikkat**: Bu script, Cursor'da Agent modunda yapılan tüm değişiklikleri otomatik olarak kabul eder. Kullanırken dikkatli olun ve değişiklikleri takip edin.

## Alternatif: Cursor Ayarları

Hammerspoon kullanmak istemiyorsanız, Cursor'un kendi ayarlarını kullanabilirsiniz:

1. Cursor > Settings > Agent
2. "Auto-Run" modunu etkinleştirin
3. "Auto-apply Edits" seçeneğini açın

