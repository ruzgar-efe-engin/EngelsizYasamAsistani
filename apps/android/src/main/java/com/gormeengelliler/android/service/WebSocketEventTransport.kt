package com.gormeengelliler.android.service

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * WebSocketEventTransport
 * 
 * WebSocket üzerinden bridge server'a bağlanır ve event'leri alır.
 * Simülasyon için kullanılır (macOS CoreBluetooth sorunlarını bypass eder).
 */
class WebSocketEventTransport(private val context: Context) : EventTransport {
    companion object {
        // SharedPreferences keys
        private const val PREFS_NAME = "app_prefs"
        private const val PREF_WS_IP = "websocket_server_ip"
        private const val PREF_WS_PORT = "websocket_server_port"
        
        // Default values
        private const val DEFAULT_IP = "192.168.1.119"
        private const val DEFAULT_PORT = 8080
    }
    
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    
    private var webSocket: WebSocket? = null
    private var wsUrl: String = ""
    private var isScanning = false
    private var isPairingModeActive = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * SharedPreferences'ten WebSocket URL'ini oluştur
     */
    private fun getWebSocketUrl(): String {
        val ip = prefs.getString(PREF_WS_IP, DEFAULT_IP) ?: DEFAULT_IP
        val port = prefs.getInt(PREF_WS_PORT, DEFAULT_PORT)
        val url = "ws://$ip:$port"
        android.util.Log.d("WebSocketEventTransport", "📡 WebSocket URL: $url")
        return url
    }
    
    // EventTransport interface callbacks
    override var onDeviceFound: ((String) -> Unit)? = null
    override var onEventReceived: ((String) -> Unit)? = null
    override var onConnectionStateChanged: ((Boolean) -> Unit)? = null
    
    private val webSocketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            android.util.Log.d("WebSocketEventTransport", "✅ WebSocket bağlantısı açıldı")
            mainHandler.post {
                onConnectionStateChanged?.invoke(true)
                // Pairing mode aktifse cihaz bulundu olarak bildir
                // Pairing mode mesajı geldiğinde onDeviceFound çağrılacak
            }
        }
        
        override fun onMessage(webSocket: WebSocket, text: String) {
            android.util.Log.d("WebSocketEventTransport", "📥 WebSocket mesajı alındı: $text")
            
            try {
                // Mesaj tipini kontrol et
                val json = JSONObject(text)
                val messageType = json.optString("type", "")
                
                when (messageType) {
                    "event" -> {
                        // Event mesajı
                        val eventData = json.optString("data", "")
                        if (eventData.isNotEmpty()) {
                            android.util.Log.d("WebSocketEventTransport", "✅ Event alındı: $eventData")
                            mainHandler.post {
                                onEventReceived?.invoke(eventData)
                            }
                        }
                    }
                    "pairing_mode" -> {
                        // Pairing mode durumu
                        val isActive = json.optBoolean("active", false)
                        isPairingModeActive = isActive
                        android.util.Log.d("WebSocketEventTransport", "🔵 Pairing mode: $isActive")
                        if (isActive) {
                            // Pairing mode aktif - cihaz bulundu olarak bildir
                            val deviceId = wsUrl.ifEmpty { getWebSocketUrl() }
                            android.util.Log.d("WebSocketEventTransport", "📡 Pairing mode aktif - cihaz bulundu: $deviceId")
                            mainHandler.post {
                                onDeviceFound?.invoke(deviceId)
                            }
                        }
                    }
                    "device_found" -> {
                        // Cihaz bulundu (simülasyon için)
                        val deviceId = json.optString("deviceId", "")
                        if (deviceId.isNotEmpty()) {
                            android.util.Log.d("WebSocketEventTransport", "📡 Cihaz bulundu: $deviceId")
                            mainHandler.post {
                                onDeviceFound?.invoke(deviceId)
                            }
                        }
                    }
                    else -> {
                        // Bilinmeyen mesaj tipi - direkt event olarak kabul et
                        android.util.Log.d("WebSocketEventTransport", "📥 Bilinmeyen mesaj tipi, event olarak işleniyor: $text")
                        mainHandler.post {
                            onEventReceived?.invoke(text)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("WebSocketEventTransport", "❌ Mesaj parse hatası: ${e.message}")
                // Parse hatası olsa bile mesajı event olarak gönder
                mainHandler.post {
                    onEventReceived?.invoke(text)
                }
            }
        }
        
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            android.util.Log.d("WebSocketEventTransport", "⚠️ WebSocket kapanıyor: code=$code, reason=$reason")
        }
        
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            android.util.Log.d("WebSocketEventTransport", "🔌 WebSocket kapandı: code=$code, reason=$reason")
            mainHandler.post {
                onConnectionStateChanged?.invoke(false)
            }
        }
        
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            val errorMsg = t.message ?: "Bilinmeyen hata"
            android.util.Log.e("WebSocketEventTransport", "❌ WebSocket hatası: $errorMsg")
            android.util.Log.e("WebSocketEventTransport", "   Throwable tipi: ${t.javaClass.simpleName}")
            if (response != null) {
                android.util.Log.e("WebSocketEventTransport", "   Response code: ${response.code}")
                android.util.Log.e("WebSocketEventTransport", "   Response message: ${response.message}")
            }
            mainHandler.post {
                onConnectionStateChanged?.invoke(false)
                // Hata mesajını event olarak gönder (SetupScreen'de handle edilecek)
                // Not: EventTransport interface'inde error callback yok, bu yüzden event olarak gönderiyoruz
                android.util.Log.e("WebSocketEventTransport", "   📤 Hata mesajı onEventReceived'a gönderiliyor")
                onEventReceived?.invoke("{\"type\":\"error\",\"message\":\"WebSocket bağlantı hatası: $errorMsg\"}")
            }
        }
    }
    
    override fun startScan() {
        android.util.Log.d("WebSocketEventTransport", "🔍 WebSocket tarama başlatılıyor (pairing mode bekleniyor)...")
        
        if (isScanning) {
            android.util.Log.d("WebSocketEventTransport", "⚠️  Tarama zaten aktif")
            return
        }
        
        isScanning = true
        
        // Pairing mode bekleniyor, direkt bağlan
        // Bridge server'dan pairing_mode mesajı geldiğinde onDeviceFound çağrılacak
        wsUrl = getWebSocketUrl()
        android.util.Log.d("WebSocketEventTransport", "📡 Bridge server'a bağlanılıyor (pairing mode bekleniyor): $wsUrl")
        connect(wsUrl)
    }
    
    override fun stopScan() {
        android.util.Log.d("WebSocketEventTransport", "🛑 WebSocket tarama durduruluyor...")
        isScanning = false
        // WebSocket tarama yok, sadece flag'i güncelle
    }
    
    override fun connect(deviceId: String) {
        android.util.Log.d("WebSocketEventTransport", "🔌 WebSocket bağlantısı başlatılıyor: $deviceId")
        
        // Mevcut bağlantıyı kapat
        disconnect()
        
        // URL'yi güncelle (eğer farklı bir URL verilmişse)
        if (deviceId.startsWith("ws://") || deviceId.startsWith("wss://")) {
            wsUrl = deviceId
        } else {
            // MAC address gibi bir ID verilmişse, SharedPreferences'ten URL'i al
            wsUrl = getWebSocketUrl()
        }
        
        val request = Request.Builder()
            .url(wsUrl)
            .build()
        
        try {
            webSocket = client.newWebSocket(request, webSocketListener)
            android.util.Log.d("WebSocketEventTransport", "✅ WebSocket bağlantı isteği gönderildi: $wsUrl")
        } catch (e: Exception) {
            android.util.Log.e("WebSocketEventTransport", "❌ WebSocket bağlantı hatası: ${e.message}")
            mainHandler.post {
                onConnectionStateChanged?.invoke(false)
            }
        }
    }
    
    override fun disconnect() {
        android.util.Log.d("WebSocketEventTransport", "🔌 WebSocket bağlantısı kesiliyor...")
        
        webSocket?.close(1000, "Normal closure")
        webSocket = null
        
        android.util.Log.d("WebSocketEventTransport", "✅ WebSocket bağlantısı kesildi")
    }
    
    override fun isConnected(): Boolean {
        return webSocket != null
    }
    
    override fun isBluetoothEnabled(): Boolean {
        // WebSocket için Bluetooth gerekmez
        return false
    }
}

