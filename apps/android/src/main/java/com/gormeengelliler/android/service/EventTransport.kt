package com.gormeengelliler.android.service

/**
 * EventTransport Interface
 * 
 * BLE ve WebSocket gibi farklı transport mekanizmalarını
 * birleştiren interface. Provider pattern için kullanılır.
 */
interface EventTransport {
    /**
     * Cihaz taramasını başlat
     */
    fun startScan()
    
    /**
     * Cihaz taramasını durdur
     */
    fun stopScan()
    
    /**
     * Belirtilen cihaza bağlan
     * @param deviceId Cihaz ID'si (BLE için MAC address, WebSocket için server URL)
     */
    fun connect(deviceId: String)
    
    /**
     * Bağlantıyı kes
     */
    fun disconnect()
    
    /**
     * Bağlantı durumunu kontrol et
     * @return true eğer bağlıysa, false değilse
     */
    fun isConnected(): Boolean
    
    /**
     * Bluetooth etkin mi kontrol et (sadece BLE için geçerli)
     * @return true eğer Bluetooth etkinse, false değilse
     */
    fun isBluetoothEnabled(): Boolean = false
    
    // Callbacks
    
    /**
     * Cihaz bulunduğunda çağrılır
     * @param deviceId Cihaz ID'si (BLE için MAC address, WebSocket için server URL)
     */
    var onDeviceFound: ((String) -> Unit)?
    
    /**
     * Event alındığında çağrılır
     * @param jsonString Event JSON string'i
     */
    var onEventReceived: ((String) -> Unit)?
    
    /**
     * Bağlantı durumu değiştiğinde çağrılır
     * @param isConnected true eğer bağlıysa, false değilse
     */
    var onConnectionStateChanged: ((Boolean) -> Unit)?
}

