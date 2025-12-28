package com.gormeengelliler.android.service

import android.content.Context

/**
 * TransportType Enum
 * 
 * Mevcut transport tipleri
 */
enum class TransportType {
    BLE,
    WEBSOCKET
}

/**
 * EventTransportProvider
 * 
 * BLE ve WebSocket gibi farklı transport mekanizmalarını yönetir.
 * Provider pattern implementasyonu.
 */
class EventTransportProvider(private val context: Context) {
    private var currentTransport: EventTransport? = null
    private var currentType: TransportType? = null
    
    /**
     * Mevcut provider'ları listele
     * @return Mevcut transport tipleri
     */
    fun getAvailableProviders(): List<TransportType> {
        val providers = mutableListOf<TransportType>()
        
        // BLE her zaman mevcut (gerçek cihaz için)
        providers.add(TransportType.BLE)
        
        // WebSocket her zaman mevcut (simülasyon için)
        providers.add(TransportType.WEBSOCKET)
        
        return providers
    }
    
    /**
     * Belirtilen tip için transport oluştur
     * @param type Transport tipi
     * @return EventTransport instance
     */
    fun createTransport(type: TransportType): EventTransport {
        return when (type) {
            TransportType.BLE -> {
                android.util.Log.d("EventTransportProvider", "📡 BLE transport oluşturuluyor...")
                BLEEventTransport(context)
            }
            TransportType.WEBSOCKET -> {
                android.util.Log.d("EventTransportProvider", "🌐 WebSocket transport oluşturuluyor...")
                WebSocketEventTransport(context)
            }
        }
    }
    
    /**
     * Aktif transport'u al
     * @return Aktif EventTransport veya null
     */
    fun getCurrentTransport(): EventTransport? {
        return currentTransport
    }
    
    /**
     * Aktif transport tipini al
     * @return Aktif TransportType veya null
     */
    fun getCurrentType(): TransportType? {
        return currentType
    }
    
    /**
     * Transport değiştir
     * @param type Yeni transport tipi
     */
    fun switchTransport(type: TransportType) {
        android.util.Log.d("EventTransportProvider", "🔄 Transport değiştiriliyor: ${currentType} -> $type")
        
        // Mevcut transport'u temizle
        currentTransport?.disconnect()
        currentTransport?.stopScan()
        
        // Yeni transport oluştur
        currentTransport = createTransport(type)
        currentType = type
        
        // Callback'leri koru (eğer daha önce set edilmişse)
        // Bu callback'ler SetupScreen veya DeviceEventService tarafından set edilecek
        
        android.util.Log.d("EventTransportProvider", "✅ Transport değiştirildi: $type")
    }
    
    /**
     * İlk transport'u başlat (varsayılan: BLE)
     * @param type Başlatılacak transport tipi (varsayılan: BLE)
     */
    fun initialize(type: TransportType = TransportType.BLE) {
        android.util.Log.d("EventTransportProvider", "🚀 EventTransportProvider başlatılıyor: $type")
        
        if (currentTransport == null) {
            switchTransport(type)
        }
    }
    
    /**
     * Transport'u temizle
     */
    fun cleanup() {
        android.util.Log.d("EventTransportProvider", "🧹 EventTransportProvider temizleniyor...")
        
        currentTransport?.disconnect()
        currentTransport?.stopScan()
        currentTransport = null
        currentType = null
        
        android.util.Log.d("EventTransportProvider", "✅ EventTransportProvider temizlendi")
    }
}

