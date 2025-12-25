-- Cursor Auto Accept Script for Hammerspoon
-- Bu script, Cursor IDE'de Agent modunda otomatik olarak "Accept" butonuna basar
-- Kullanım: Bu dosyayı ~/.hammerspoon/init.lua dosyasına ekleyin veya require edin

local cursorAutoAccept = {}

-- Cursor uygulamasının bundle identifier'ı
local CURSOR_BUNDLE_ID = "com.todesktop.230313mzl4w4u92"

-- Otomatik kabul için kullanılacak tuş kombinasyonu (Cmd+Return)
local ACCEPT_KEY = {{"cmd"}, "return"}

-- Kontrol aralığı (saniye cinsinden)
local CHECK_INTERVAL = 0.5

-- Timer referansı
local timer = nil

-- Cursor'un aktif olup olmadığını kontrol et
local function isCursorActive()
    local app = hs.application.frontmostApplication()
    if app then
        return app:bundleID() == CURSOR_BUNDLE_ID
    end
    return false
end

-- "Accept" butonunu bul ve tıkla
local function acceptChanges()
    if not isCursorActive() then
        return
    end
    
    -- Cmd+Return tuş kombinasyonunu gönder
    hs.eventtap.keyStroke(ACCEPT_KEY[1], ACCEPT_KEY[2])
end

-- Timer'ı başlat
function cursorAutoAccept.start()
    if timer then
        timer:stop()
    end
    
    timer = hs.timer.doEvery(CHECK_INTERVAL, function()
        if isCursorActive() then
            acceptChanges()
        end
    end)
    
    print("✅ Cursor Auto Accept başlatıldı")
end

-- Timer'ı durdur
function cursorAutoAccept.stop()
    if timer then
        timer:stop()
        timer = nil
        print("⏹️  Cursor Auto Accept durduruldu")
    end
end

-- Hotkey tanımla: Cmd+Shift+A ile başlat/durdur
hs.hotkey.bind({"cmd", "shift"}, "A", function()
    if timer then
        cursorAutoAccept.stop()
    else
        cursorAutoAccept.start()
    end
end)

-- İlk başlatma (isteğe bağlı - yorum satırını kaldırarak etkinleştirebilirsiniz)
-- cursorAutoAccept.start()

return cursorAutoAccept

