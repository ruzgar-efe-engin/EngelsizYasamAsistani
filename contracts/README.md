# Event Sözleşmesi

Bu klasör, cihaz ve mobil uygulamalar arasındaki event sözleşmesini içerir.

## Event Modeli

Cihaz, kullanıcı etkileşimlerini (encoder döndürme, buton basma) event formatında telefona gönderir.

### Event Türleri

- `0` - THEME_ROTATE: Tema encoder döndü
- `1` - MAIN_ROTATE: Ana menü encoder döndü
- `2` - SUB_ROTATE: Alt menü encoder döndü
- `3` - CONFIRM: Seçim onaylandı (YES butonu veya SubSW)
- `4` - CANCEL: İptal (NO butonu)
- `5` - AI_PRESS: AI butonu basıldı
- `6` - AI_RELEASE: AI butonu bırakıldı

### Event Formatı

```json
{
  "type": 0,
  "themeIndex": 2,
  "mainIndex": 1,
  "subIndex": 0,
  "ts": 12345
}
```

### Önemli Notlar

- Cihaz **sadece pozisyon (index)** gönderir, metin bilgisi yok
- Telefon event'i alır, pozisyona göre metni bulur ve TTS ile seslendirir
- `ts` (timestamp) alanı debug, debounce ve log korelasyonu için kritik
- `mainIndex` ve `subIndex` bazı event türlerinde opsiyoneldir

## JSON Schema

Detaylı şema: `event-schema.json`

