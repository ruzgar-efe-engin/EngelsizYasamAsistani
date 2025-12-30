package com.gormeengelliler.android.ui

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.gormeengelliler.android.manager.MenuManager
import com.gormeengelliler.android.manager.EventLogManager
import com.gormeengelliler.android.model.DeviceEvent
import com.gormeengelliler.android.service.EventTransportProvider
import com.gormeengelliler.android.service.TransportType
import com.gormeengelliler.android.service.EventTransport
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class ConnectionStatus {
    NOT_CONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit
) {
    android.util.Log.d("SetupScreen", "\n🚀🚀🚀 ========================================")
    android.util.Log.d("SetupScreen", "🚀 SETUPSCREEN COMPOSE EDİLİYOR!")
    android.util.Log.d("SetupScreen", "========================================")
    
    val context = LocalContext.current
    // Application context kullan - BLE callback'ler Activity lifecycle'dan bağımsız olmalı
    val applicationContext = remember { context.applicationContext }
    val scope = rememberCoroutineScope()
    val menuManager = remember { MenuManager(context) }
    
    // State management
    var selectedLanguage by remember { mutableStateOf(menuManager.getSelectedLanguage()) }
    var isScanning by remember { mutableStateOf(false) }
    var scannedDevices by remember { mutableStateOf<List<String>>(emptyList()) } // deviceId listesi (MAC address veya WebSocket URL)
    var connectionStatus by remember { mutableStateOf<ConnectionStatus>(ConnectionStatus.NOT_CONNECTED) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedDeviceId by remember { mutableStateOf<String?>(null) }
    var isPairingMode by remember { mutableStateOf(false) }
    var pairingTimeRemaining by remember { mutableStateOf(30) }
    
    // Transport provider state
    var selectedTransportType by remember { mutableStateOf<TransportType>(TransportType.BLE) }
    val transportProvider = remember { 
        android.util.Log.d("SetupScreen", "🔧 EventTransportProvider başlatılıyor")
        EventTransportProvider(applicationContext).apply {
            initialize(TransportType.BLE) // Varsayılan: BLE
        }
    }
    var currentTransport by remember { mutableStateOf<EventTransport?>(transportProvider.getCurrentTransport()) }
    
    // Event log state
    var eventLogs by remember { mutableStateOf<List<String>>(emptyList()) }
    val maxLogEntries = 200 // TTS/STT logları için artırıldı
    
    // EventLogManager listener - TTS ve STT loglarını event log'a ekle
    DisposableEffect(Unit) {
        val listener: (String) -> Unit = { logLine ->
            eventLogs = (eventLogs + logLine).takeLast(maxLogEntries)
        }
        EventLogManager.addListener(listener)
        onDispose {
            EventLogManager.removeListener(listener)
        }
    }
    
    // Collapse panel states
    var welcomeExpanded by remember { mutableStateOf(true) }
    var languageExpanded by remember { mutableStateOf(false) }
    var devicePairingExpanded by remember { mutableStateOf(false) }
    var transportSelectionExpanded by remember { mutableStateOf(false) }
    var websocketConfigExpanded by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    
    // WebSocket configuration state
    val prefs = remember { applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    var wsServerIp by remember { 
        mutableStateOf(prefs.getString("websocket_server_ip", "192.168.1.119") ?: "192.168.1.119")
    }
    var wsServerPort by remember { 
        mutableStateOf(prefs.getInt("websocket_server_port", 8080).toString())
    }
    
    // WiFi connection check
    fun isWifiConnected(): Boolean {
        val connectivityManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            networkInfo?.type == ConnectivityManager.TYPE_WIFI && networkInfo.isConnected
        }
    }
    
    var isWifiConnectedState by remember { mutableStateOf(isWifiConnected()) }
    
    // WiFi durumunu periyodik kontrol et (WebSocket seçildiğinde)
    LaunchedEffect(selectedTransportType) {
        if (selectedTransportType == TransportType.WEBSOCKET) {
            while (selectedTransportType == TransportType.WEBSOCKET) {
                isWifiConnectedState = isWifiConnected()
                delay(2000) // 2 saniyede bir kontrol et
            }
        }
    }
    
    // Bip sesi için ToneGenerator - scroll döndürüldüğünde geri bildirim
    val toneGenerator = remember {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.MAX_VOLUME)
            android.util.Log.d("SetupScreen", "✅ ToneGenerator başlatıldı")
            tg
        } catch (e: Exception) {
            android.util.Log.e("SetupScreen", "❌ ToneGenerator başlatılamadı: ${e.message}")
            null
        }
    }
    
    // Cleanup on dispose
    DisposableEffect(toneGenerator) {
        onDispose {
            toneGenerator?.release()
            android.util.Log.d("SetupScreen", "✅ ToneGenerator temizlendi")
        }
    }
    
    // Event formatı dönüşümü - JSON event'i [BLE] formatına çevir
    fun formatEventLog(event: DeviceEvent): String {
        android.util.Log.d("SetupScreen", "   📝 formatEventLog() içinde:")
        android.util.Log.d("SetupScreen", "      Event type: ${event.type.value}")
        android.util.Log.d("SetupScreen", "      Event mainIndex: ${event.mainIndex}")
        android.util.Log.d("SetupScreen", "      Event subIndex: ${event.subIndex}")
        
        // Log formatı: sendEvent çağrıldı: type=X m=Y s=Z - Paket alındı
        val logParts = mutableListOf<String>()
        logParts.add("sendEvent çağrıldı: type=${event.type.value}")
        
        // mainIndex sadece ilgili event'lerde
        if (event.type == com.gormeengelliler.android.model.EventType.MAIN_ROTATE || 
            event.type == com.gormeengelliler.android.model.EventType.SUB_ROTATE || 
            event.type == com.gormeengelliler.android.model.EventType.CONFIRM || 
            event.type == com.gormeengelliler.android.model.EventType.EVENT_CANCEL || 
            event.type == com.gormeengelliler.android.model.EventType.AI_PRESS || 
            event.type == com.gormeengelliler.android.model.EventType.AI_RELEASE) {
            logParts.add("m=${event.mainIndex}")
        }
        
        // subIndex sadece ilgili event'lerde
        if (event.type == com.gormeengelliler.android.model.EventType.SUB_ROTATE || 
            event.type == com.gormeengelliler.android.model.EventType.CONFIRM || 
            event.type == com.gormeengelliler.android.model.EventType.EVENT_CANCEL || 
            event.type == com.gormeengelliler.android.model.EventType.AI_PRESS || 
            event.type == com.gormeengelliler.android.model.EventType.AI_RELEASE) {
            logParts.add("s=${event.subIndex}")
        }
        
        logParts.add("- Paket alındı")
        val result = logParts.joinToString(" ")
        android.util.Log.d("SetupScreen", "   ✅ formatEventLog() tamamlandı: $result")
        return result
    }
    
    // Transport callbacks
    LaunchedEffect(currentTransport) {
        android.util.Log.d("SetupScreen", "\n🔧 ========================================")
        android.util.Log.d("SetupScreen", "🔧 TRANSPORT CALLBACKS AYARLANIYOR")
        android.util.Log.d("SetupScreen", "========================================")
        
        val transport = currentTransport ?: return@LaunchedEffect
        
        transport.onDeviceFound = { deviceId ->
            android.util.Log.d("SetupScreen", "\n📡 ========================================")
            android.util.Log.d("SetupScreen", "📡 onDeviceFound CALLBACK TETİKLENDİ")
            android.util.Log.d("SetupScreen", "   Device ID: $deviceId")
            android.util.Log.d("SetupScreen", "   selectedTransportType: $selectedTransportType")
            android.util.Log.d("SetupScreen", "========================================")
            
            // WebSocket için: Pairing mode mesajı geldi, artık CONNECTED yapabiliriz
            if (selectedTransportType == TransportType.WEBSOCKET && connectionStatus == ConnectionStatus.CONNECTING) {
                android.util.Log.d("SetupScreen", "   WebSocket - Pairing mode mesajı geldi, CONNECTED yapılıyor")
                connectionStatus = ConnectionStatus.CONNECTED
                menuManager.setSelectedLanguage(selectedLanguage)
                
                val connectionLogs = listOf(
                    "[WebSocket] Bağlantı başarılı!",
                    "[WebSocket] Server: $deviceId"
                )
                android.util.Log.d("SetupScreen", "📝 Event log'a bağlantı bilgileri yazılıyor...")
                eventLogs = connectionLogs
                android.util.Log.d("SetupScreen", "✅ Event log güncellendi (${eventLogs.size} satır)")
                
                devicePairingExpanded = false // Cihaz eşleme panelini gizle
                android.util.Log.d("SetupScreen", "📦 Cihaz eşleme paneli gizlendi")
                
                scope.launch {
                    snackbarHostState.showSnackbar("Cihaz başarıyla bağlandı!")
                }
            }
            
            val oldSize = scannedDevices.size
            android.util.Log.d("SetupScreen", "   Mevcut listedeki cihaz sayısı: $oldSize")
            
            if (!scannedDevices.contains(deviceId)) {
                android.util.Log.d("SetupScreen", "✅ Yeni cihaz tespit edildi, listeye ekleniyor...")
                scannedDevices = scannedDevices + deviceId
                val newSize = scannedDevices.size
                android.util.Log.d("SetupScreen", "   scannedDevices.size: $oldSize -> $newSize")
                android.util.Log.d("SetupScreen", "   Auto-connect LaunchedEffect tetiklenecek (dependency: scannedDevices)")
                android.util.Log.d("SetupScreen", "   Auto-connect koşulları kontrol edilecek:")
                android.util.Log.d("SetupScreen", "     - isPairingMode: $isPairingMode")
                android.util.Log.d("SetupScreen", "     - scannedDevices.isNotEmpty(): ${scannedDevices.isNotEmpty()}")
                android.util.Log.d("SetupScreen", "     - connectionStatus != CONNECTED: ${connectionStatus != ConnectionStatus.CONNECTED}")
            } else {
                android.util.Log.d("SetupScreen", "ℹ️  Cihaz zaten listede, tekrar eklenmiyor")
            }
        }
        
        // Event log callback - Transport'tan gelen event'leri log formatına çevir
        transport.onEventReceived = eventReceived@{ jsonString ->
            val callbackTimestamp = System.currentTimeMillis()
            android.util.Log.d("SetupScreen", "\n📥 ========================================")
            android.util.Log.d("SetupScreen", "📥 onEventReceived CALLBACK ÇAĞRILDI")
            android.util.Log.d("SetupScreen", "========================================")
            android.util.Log.d("SetupScreen", "   Timestamp: $callbackTimestamp")
            android.util.Log.d("SetupScreen", "   Thread: ${Thread.currentThread().name}")
            android.util.Log.d("SetupScreen", "   Raw JSON: $jsonString")
            android.util.Log.d("SetupScreen", "   JSON uzunluğu: ${jsonString.length} karakter")
            android.util.Log.d("SetupScreen", "   eventLogs önceki boyutu: ${eventLogs.size}")
            android.util.Log.d("SetupScreen", "========================================")
            
            // Error event kontrolü (WebSocket hataları için)
            if (jsonString.contains("\"type\":\"error\"")) {
                try {
                    val errorJson = org.json.JSONObject(jsonString)
                    val errorMessage = errorJson.optString("message", "Bilinmeyen hata")
                    android.util.Log.e("SetupScreen", "❌ Error event alındı: $errorMessage")
                    scope.launch {
                        snackbarHostState.showSnackbar(errorMessage)
                    }
                    // Error event'i log'a ekle
                    eventLogs = (eventLogs + "[WebSocket] ERROR: $errorMessage").takeLast(maxLogEntries)
                    return@eventReceived
                } catch (e: Exception) {
                    android.util.Log.e("SetupScreen", "❌ Error event parse hatası: ${e.message}")
                }
            }
            
            try {
                
                android.util.Log.d("SetupScreen", "   📝 DeviceEvent.fromJson() çağrılıyor...")
                val event = DeviceEvent.fromJson(jsonString)
                if (event != null) {
                    android.util.Log.d("SetupScreen", "   ✅ Event parse başarılı:")
                    android.util.Log.d("SetupScreen", "      type=${event.type.value}, m=${event.mainIndex}, s=${event.subIndex}")
                    
                    android.util.Log.d("SetupScreen", "   📝 formatEventLog() çağrılıyor...")
                    val logLine = formatEventLog(event)
                    android.util.Log.d("SetupScreen", "   ✅ formatEventLog() tamamlandı:")
                    android.util.Log.d("SetupScreen", "      Log satırı: $logLine")
                    android.util.Log.d("SetupScreen", "📝 Event log paneline YAZILIYOR...")
                    
                    // Scroll event'lerinde bip sesi çal
                    if (event.type == com.gormeengelliler.android.model.EventType.MAIN_ROTATE || 
                        event.type == com.gormeengelliler.android.model.EventType.SUB_ROTATE) {
                        try {
                            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 50) // 50ms kısa bip
                            android.util.Log.d("SetupScreen", "🔊 Scroll bip sesi çalındı")
                        } catch (e: Exception) {
                            android.util.Log.e("SetupScreen", "❌ Bip sesi çalınamadı: ${e.message}")
                        }
                    }
                    
                    // Anında event log paneline ekle
                    android.util.Log.d("SetupScreen", "   📝 eventLogs state güncelleniyor...")
                    val previousSize = eventLogs.size
                    eventLogs = (eventLogs + logLine).takeLast(maxLogEntries)
                    val newSize = eventLogs.size
                    android.util.Log.d("SetupScreen", "   ✅ Event log paneline yazıldı:")
                    android.util.Log.d("SetupScreen", "      Önceki boyut: $previousSize")
                    android.util.Log.d("SetupScreen", "      Yeni boyut: $newSize")
                    android.util.Log.d("SetupScreen", "      Son log satırı: ${eventLogs.lastOrNull()}")
                    android.util.Log.d("SetupScreen", "   💡 UI'da event log görünmeli!")
                } else {
                    android.util.Log.e("SetupScreen", "   ❌ Event parse EDİLEMEDİ: $jsonString")
                    android.util.Log.e("SetupScreen", "   DeviceEvent.fromJson null döndü")
                    android.util.Log.e("SetupScreen", "   💡 JSON formatı hatalı olabilir")
                    // Parse edilemese bile raw JSON'u göster
                    val previousSize = eventLogs.size
                    eventLogs = (eventLogs + "[BLE] RAW: $jsonString").takeLast(maxLogEntries)
                    android.util.Log.d("SetupScreen", "   ✅ Raw JSON event log'a eklendi (${eventLogs.size} satır)")
                }
            } catch (e: Exception) {
                android.util.Log.e("SetupScreen", "   ❌ Event işleme HATASI:")
                android.util.Log.e("SetupScreen", "   Exception: ${e.message}")
                android.util.Log.e("SetupScreen", "   Exception tipi: ${e.javaClass.simpleName}")
                android.util.Log.e("SetupScreen", "   Stack: ${e.stackTrace.take(3).joinToString("\n")}")
                val previousSize = eventLogs.size
                eventLogs = (eventLogs + "[BLE] ERROR: ${e.message} - $jsonString").takeLast(maxLogEntries)
                android.util.Log.d("SetupScreen", "   ✅ Error mesajı event log'a eklendi (${eventLogs.size} satır)")
            }
            android.util.Log.d("SetupScreen", "========================================\n")
        }
        
        transport.onConnectionStateChanged = { isConnected ->
            android.util.Log.d("SetupScreen", "\n🔄 ========================================")
            android.util.Log.d("SetupScreen", "🔄 onConnectionStateChanged CALLBACK ÇAĞRILDI")
            android.util.Log.d("SetupScreen", "   isConnected: $isConnected")
            android.util.Log.d("SetupScreen", "   selectedTransportType: $selectedTransportType")
            android.util.Log.d("SetupScreen", "========================================")
            
            if (isConnected) {
                android.util.Log.d("SetupScreen", "✅ CONNECTED alındı")
                
                // WebSocket için: Pairing mode mesajı gelene kadar CONNECTING olarak tut
                if (selectedTransportType == TransportType.WEBSOCKET) {
                    android.util.Log.d("SetupScreen", "   WebSocket modu - pairing mode bekleniyor")
                    if (connectionStatus != ConnectionStatus.CONNECTED) {
                        android.util.Log.d("SetupScreen", "   connectionStatus: CONNECTING olarak set ediliyor (pairing mode bekleniyor)")
                        connectionStatus = ConnectionStatus.CONNECTING
                    }
                    // CONNECTED durumu sadece pairing mode mesajı geldiğinde (onDeviceFound) yapılacak
                } else {
                    // BLE için: Direkt CONNECTED yap
                    android.util.Log.d("SetupScreen", "   BLE modu - connectionStatus: CONNECTED olarak set ediliyor")
                    connectionStatus = ConnectionStatus.CONNECTED
                    menuManager.setSelectedLanguage(selectedLanguage)
                    
                    // Bağlantı başarılı olduğunda bilgileri log'a ekle
                    val transportType = transportProvider.getCurrentType()
                    val connectionLogs = when (transportType) {
                        TransportType.BLE -> listOf(
                            "[BLE] Bağlantı başarılı!",
                            "[BLE] Service UUID: 12345678-1234-1234-1234-123456789abc",
                            "[BLE] Characteristic UUID: 12345678-1234-1234-1234-123456789abd"
                        )
                        else -> listOf("[Transport] Bağlantı başarılı!")
                    }
                    android.util.Log.d("SetupScreen", "📝 Event log'a bağlantı bilgileri yazılıyor...")
                    eventLogs = connectionLogs
                    android.util.Log.d("SetupScreen", "✅ Event log güncellendi (${eventLogs.size} satır)")
                    
                    devicePairingExpanded = false // Cihaz eşleme panelini gizle
                    android.util.Log.d("SetupScreen", "📦 Cihaz eşleme paneli gizlendi")
                    
                    scope.launch {
                        snackbarHostState.showSnackbar("Cihaz başarıyla bağlandı!")
                    }
                    android.util.Log.d("SetupScreen", "💡 NOT: Setup tamamlandı ama arka plana alınmadı (event log akışı için)")
                }
            } else {
                android.util.Log.d("SetupScreen", "⚠️  DISCONNECTED alındı")
                if (connectionStatus == ConnectionStatus.CONNECTING) {
                    android.util.Log.d("SetupScreen", "   CONNECTING'den DISCONNECTED'a geçiş → ERROR")
                    connectionStatus = ConnectionStatus.ERROR
                    errorMessage = "Bağlantı başarısız oldu"
                    scope.launch {
                        snackbarHostState.showSnackbar("Bağlantı başarısız oldu. Bridge server'ın çalıştığından ve IP adresinin doğru olduğundan emin olun.")
                    }
                } else {
                    android.util.Log.d("SetupScreen", "   connectionStatus: NOT_CONNECTED olarak set ediliyor")
                    connectionStatus = ConnectionStatus.NOT_CONNECTED
                }
            }
        }
    }
    
    // Pairing mode countdown
    LaunchedEffect(isPairingMode) {
        if (isPairingMode) {
            pairingTimeRemaining = 30
            while (isPairingMode && pairingTimeRemaining > 0) {
                delay(1000)
                pairingTimeRemaining--
                if (pairingTimeRemaining <= 0) {
                    isPairingMode = false
                    isScanning = false
                    currentTransport?.stopScan()
                }
            }
        }
    }
    
    // Auto-connect when device found (pairing mode veya normal tarama)
    LaunchedEffect(scannedDevices, isPairingMode, connectionStatus) {
        val timestamp = System.currentTimeMillis()
        android.util.Log.d("SetupScreen", "\n🔍 ========================================")
        android.util.Log.d("SetupScreen", "🔍 Auto-connect LaunchedEffect TETİKLENDİ")
        android.util.Log.d("SetupScreen", "   Timestamp: $timestamp")
        android.util.Log.d("SetupScreen", "   Thread: ${Thread.currentThread().name}")
        android.util.Log.d("SetupScreen", "   (Dependency değişikliği: scannedDevices, isPairingMode veya connectionStatus)")
        android.util.Log.d("SetupScreen", "========================================")
        android.util.Log.d("SetupScreen", "📊 Dependency değerleri:")
        android.util.Log.d("SetupScreen", "   isPairingMode: $isPairingMode")
        android.util.Log.d("SetupScreen", "   scannedDevices.size: ${scannedDevices.size}")
        android.util.Log.d("SetupScreen", "   scannedDevices.isEmpty(): ${scannedDevices.isEmpty()}")
        android.util.Log.d("SetupScreen", "   connectionStatus: $connectionStatus")
        android.util.Log.d("SetupScreen", "   connectionStatus == CONNECTED: ${connectionStatus == ConnectionStatus.CONNECTED}")
        android.util.Log.d("SetupScreen", "   connectionStatus == CONNECTING: ${connectionStatus == ConnectionStatus.CONNECTING}")
        
        // Koşulları kontrol et - daha esnek: pairing mode aktifse veya cihaz bulunduysa bağlan
        val hasDevices = scannedDevices.isNotEmpty()
        val notConnected = connectionStatus != ConnectionStatus.CONNECTED && connectionStatus != ConnectionStatus.CONNECTING
        val shouldAutoConnect = (isPairingMode || hasDevices) && notConnected
        
        android.util.Log.d("SetupScreen", "   Koşul analizi:")
        android.util.Log.d("SetupScreen", "     - hasDevices (scannedDevices.isNotEmpty()): $hasDevices")
        android.util.Log.d("SetupScreen", "     - notConnected (!CONNECTED && !CONNECTING): $notConnected")
        android.util.Log.d("SetupScreen", "     - shouldAutoConnect ((isPairingMode || hasDevices) && notConnected): $shouldAutoConnect")
        
        if (shouldAutoConnect) {
            scannedDevices.firstOrNull()?.let { deviceId ->
                android.util.Log.d("SetupScreen", "\n✅ ========================================")
                android.util.Log.d("SetupScreen", "✅ AUTO-CONNECT BAŞLATILIYOR")
                android.util.Log.d("SetupScreen", "========================================")
                android.util.Log.d("SetupScreen", "   Device ID: $deviceId")
                android.util.Log.d("SetupScreen", "   Timestamp: $timestamp")
                
                // State güncellemeleri
                android.util.Log.d("SetupScreen", "   State güncellemeleri:")
                android.util.Log.d("SetupScreen", "     - connectionStatus: $connectionStatus -> CONNECTING")
                connectionStatus = ConnectionStatus.CONNECTING
                
                android.util.Log.d("SetupScreen", "     - isPairingMode: $isPairingMode -> false")
                isPairingMode = false
                
                android.util.Log.d("SetupScreen", "     - isScanning: $isScanning -> false")
                isScanning = false
                
                // Taramayı durdur
                android.util.Log.d("SetupScreen", "   📡 Transport taraması durduruluyor...")
                currentTransport?.stopScan()
                
                // Bağlantıyı başlat
                android.util.Log.d("SetupScreen", "   🔌 transport.connect() çağrılıyor...")
                try {
                    currentTransport?.connect(deviceId)
                    android.util.Log.d("SetupScreen", "   ✅ transport.connect() çağrıldı (başarılı)")
                    android.util.Log.d("SetupScreen", "   💡 onConnectionStateChanged callback'i bekleniyor...")
                    selectedDeviceId = deviceId
                } catch (e: Exception) {
                    android.util.Log.e("SetupScreen", "   ❌ transport.connect() HATASI:")
                    android.util.Log.e("SetupScreen", "      Exception: ${e.message}")
                    android.util.Log.e("SetupScreen", "      Stack: ${e.stackTrace.take(5).joinToString("\n")}")
                    connectionStatus = ConnectionStatus.ERROR
                    errorMessage = "Bağlantı hatası: ${e.message}"
                }
                android.util.Log.d("SetupScreen", "========================================\n")
            } ?: run {
                android.util.Log.e("SetupScreen", "❌ scannedDevices.firstOrNull() null döndü (bu olmamalı)")
                android.util.Log.e("SetupScreen", "   scannedDevices.size: ${scannedDevices.size}")
            }
        } else {
            android.util.Log.d("SetupScreen", "⚠️  Auto-connect koşulları sağlanmadı:")
            android.util.Log.d("SetupScreen", "   isPairingMode: $isPairingMode")
            android.util.Log.d("SetupScreen", "   hasDevices: $hasDevices")
            android.util.Log.d("SetupScreen", "   notConnected: $notConnected")
            android.util.Log.d("SetupScreen", "   shouldAutoConnect: $shouldAutoConnect")
        }
    }
    
    // Permission check
    val hasBluetoothPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Engelsiz Yaşam Asistanı") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Hoşgeldiniz Paneli
            ExpandableCard(
                title = "Hoş Geldiniz",
                icon = Icons.Default.Info,
                expanded = welcomeExpanded,
                onExpandedChange = { welcomeExpanded = it }
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Bilgi ikonu",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Engelsiz Yaşam Asistanı",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Cihazınızı telefonunuzla bağlamak için aşağıdaki adımları takip edin. Önce dil seçiminizi yapın, sonra cihaz eşleme işlemini başlatın.",
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                }
            }
            
            // 2. Transport Seçimi Paneli
            ExpandableCard(
                title = "Bağlantı Tipi",
                icon = Icons.Default.Settings,
                expanded = transportSelectionExpanded,
                onExpandedChange = { transportSelectionExpanded = it }
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val transports = listOf(
                        TransportType.BLE to "BLE (Gerçek Cihaz)",
                        TransportType.WEBSOCKET to "WebSocket (Simülasyon)"
                    )
                    
                    transports.forEach { (type, name) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    selectedTransportType = type
                                    transportProvider.switchTransport(type)
                                    // Transport değişti, currentTransport'u güncelle
                                    currentTransport = transportProvider.getCurrentTransport()
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedTransportType == type,
                                onClick = { 
                                    selectedTransportType = type
                                    transportProvider.switchTransport(type)
                                    // Transport değişti, currentTransport'u güncelle
                                    currentTransport = transportProvider.getCurrentTransport()
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = name,
                                fontSize = 16.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            
            // 2.5. WebSocket Konfigürasyon Paneli (sadece WebSocket seçildiğinde görünür)
            if (selectedTransportType == TransportType.WEBSOCKET) {
                ExpandableCard(
                    title = "WebSocket Ayarları",
                    icon = Icons.Default.Settings,
                    expanded = websocketConfigExpanded,
                    onExpandedChange = { websocketConfigExpanded = it }
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // WiFi bağlantı uyarısı
                        if (!isWifiConnectedState) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color(0xFFFF5252).copy(alpha = 0.15f),
                                                Color(0xFFFF5252).copy(alpha = 0.08f)
                                            )
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = Color(0xFFFF5252).copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(12.dp)
                                    ),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.Transparent
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Uyarı",
                                        tint = Color(0xFFFF5252)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "WiFi Bağlantısı Gerekli",
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFF5252)
                                        )
                                        Text(
                                            text = "WebSocket bağlantısı için Mac'iniz ve Android cihazınız aynı WiFi ağına bağlı olmalıdır.",
                                            fontSize = 12.sp,
                                            color = Color(0xFFFF5252).copy(alpha = 0.9f)
                                        )
                                    }
                                }
                            }
                        } else {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color(0xFF4CAF50).copy(alpha = 0.15f),
                                                Color(0xFF4CAF50).copy(alpha = 0.08f)
                                            )
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = Color(0xFF4CAF50).copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(12.dp)
                                    ),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.Transparent
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Bağlı",
                                        tint = Color(0xFF4CAF50)
                                    )
                                    Text(
                                        text = "WiFi bağlantısı aktif",
                                        color = Color(0xFF4CAF50)
                                    )
                                }
                            }
                        }
                        
                        // IP adresi girişi
                        OutlinedTextField(
                            value = wsServerIp,
                            onValueChange = { newIp ->
                                wsServerIp = newIp
                                prefs.edit().putString("websocket_server_ip", newIp).apply()
                                // Transport'u yeniden başlat (eğer bağlıysa)
                                if (currentTransport?.isConnected() == true) {
                                    currentTransport?.disconnect()
                                    currentTransport?.startScan()
                                }
                            },
                            label = { Text("Bridge Server IP Adresi") },
                            placeholder = { Text("192.168.1.100") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.White.copy(alpha = 0.6f),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                focusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                focusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            ),
                            supportingText = {
                                Text(
                                    text = "Bilgisayarınızın yerel IP adresini girin (Ayarlar > Network'ten öğrenebilirsiniz)",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        )
                        
                        // Port girişi
                        OutlinedTextField(
                            value = wsServerPort,
                            onValueChange = { newPort ->
                                val portInt = newPort.toIntOrNull() ?: 8080
                                if (portInt in 1..65535) {
                                    wsServerPort = newPort
                                    prefs.edit().putInt("websocket_server_port", portInt).apply()
                                    // Transport'u yeniden başlat (eğer bağlıysa)
                                    if (currentTransport?.isConnected() == true) {
                                        currentTransport?.disconnect()
                                        currentTransport?.startScan()
                                    }
                                }
                            },
                            label = { Text("Port") },
                            placeholder = { Text("8080") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.White.copy(alpha = 0.6f),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                focusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                focusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            ),
                            supportingText = {
                                Text(
                                    text = "Bridge server'ın WebSocket portu (varsayılan: 8080)",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        )
                        
                        // Bilgi kartı
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.1f),
                                            Color.White.copy(alpha = 0.05f)
                                        )
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = Color.White.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.Transparent
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "ℹ️ Bilgi",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                                )
                                Text(
                                    text = "• Mac: Terminal'de 'ifconfig | grep inet' veya 'ipconfig getifaddr en0'",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = "• Windows: CMD'de 'ipconfig' veya PowerShell'de 'Get-NetIPAddress'",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = "• Linux: Terminal'de 'ip addr show' veya 'hostname -I'",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = "• Genellikle '192.168.x.x' veya '10.0.x.x' formatındadır",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = "• Bridge server'ın WebSocket modunda çalıştığından emin olun",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
            
            // 3. Dil Seçimi Paneli
            ExpandableCard(
                title = "Seslendirme Dili",
                icon = Icons.Default.Settings,
                expanded = languageExpanded,
                onExpandedChange = { languageExpanded = it }
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val languages = listOf("tr" to "Türkçe", "en" to "English", "de" to "Deutsch")
                    
                    languages.forEach { (code, name) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    val oldLanguage = selectedLanguage
                                    selectedLanguage = code
                                    if (oldLanguage != code) {
                                        EventLogManager.logMenu("Dil değişikliği (SetupScreen)", "$oldLanguage -> $code")
                                        menuManager.setSelectedLanguage(code)
                                        // Eğer cihaz bağlıysa, mevcut menu'yu yeni dilde seslendir
                                        if (connectionStatus == ConnectionStatus.CONNECTED) {
                                            EventLogManager.logMenu("Cihaz bağlı, mevcut menu yeni dilde seslendirilebilir", "mainIndex ve subIndex kontrol edilmeli")
                                        }
                                    }
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedLanguage == code,
                                onClick = { 
                                    val oldLanguage = selectedLanguage
                                    selectedLanguage = code
                                    if (oldLanguage != code) {
                                        EventLogManager.logMenu("Dil değişikliği (SetupScreen)", "$oldLanguage -> $code")
                                        menuManager.setSelectedLanguage(code)
                                        // Eğer cihaz bağlıysa, mevcut menu'yu yeni dilde seslendir
                                        if (connectionStatus == ConnectionStatus.CONNECTED) {
                                            EventLogManager.logMenu("Cihaz bağlı, mevcut menu yeni dilde seslendirilebilir", "mainIndex ve subIndex kontrol edilmeli")
                                        }
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = name,
                                fontSize = 16.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            
            // 4. Cihaz Eşleme Paneli veya Event Log Paneli
            if (connectionStatus == ConnectionStatus.CONNECTED) {
                // Bağlantı başarılı - Event Log göster
                ExpandableCard(
                    title = "Event Log",
                    icon = Icons.Default.Info,
                    expanded = true,
                    onExpandedChange = { }
                ) {
                    EventLogView(eventLogs = eventLogs)
                }
            } else {
                // Bağlantı yok - Cihaz Eşleme göster
                ExpandableCard(
                    title = "Cihaz Eşleme",
                    icon = Icons.Default.Settings,
                    expanded = devicePairingExpanded,
                    onExpandedChange = { devicePairingExpanded = it }
                ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Talimat - Gri arka plan kaldırıldı, sadece border ve glassmorphism
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Talimat ikonu",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Cihazınızda AI butonuna 5 saniye basılı tutun",
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    // Butonlar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                android.util.Log.d("SetupScreen", "\n🔘 ========================================")
                                android.util.Log.d("SetupScreen", "🔘 Cihaz Ara butonuna BASILDI")
                                android.util.Log.d("SetupScreen", "========================================")
                                
                                val transport = currentTransport ?: run {
                                    android.util.Log.e("SetupScreen", "❌ Transport bulunamadı!")
                                    errorMessage = "Transport başlatılamadı"
                                    return@Button
                                }
                                
                                // Transport tipine göre kontroller
                                when (selectedTransportType) {
                                    TransportType.BLE -> {
                                        // BLE kontrolleri
                                        android.util.Log.d("SetupScreen", "📋 BLE kontrolleri yapılıyor...")
                                        if (!hasBluetoothPermissions) {
                                            android.util.Log.e("SetupScreen", "❌ Bluetooth izinleri yok!")
                                            errorMessage = "Bluetooth izinleri gerekli"
                                            return@Button
                                        }
                                        android.util.Log.d("SetupScreen", "✅ Bluetooth izinleri mevcut")
                                        
                                        if (!transport.isBluetoothEnabled()) {
                                            android.util.Log.e("SetupScreen", "❌ Bluetooth açık değil!")
                                            errorMessage = "Bluetooth açık değil"
                                            return@Button
                                        }
                                        android.util.Log.d("SetupScreen", "✅ Bluetooth açık")
                                    }
                                    TransportType.WEBSOCKET -> {
                                        // WebSocket kontrolleri
                                        android.util.Log.d("SetupScreen", "📋 WebSocket kontrolleri yapılıyor...")
                                        if (!isWifiConnectedState) {
                                            android.util.Log.e("SetupScreen", "❌ WiFi bağlantısı yok!")
                                            errorMessage = "WiFi bağlantısı gerekli"
                                            return@Button
                                        }
                                        android.util.Log.d("SetupScreen", "✅ WiFi bağlantısı aktif")
                                        
                                        // IP ve port kontrolü - UI state'ini kullan (varsayılan değer dahil)
                                        val ip = wsServerIp.ifEmpty { 
                                            prefs.getString("websocket_server_ip", "192.168.1.119") ?: "192.168.1.119"
                                        }
                                        if (ip.isEmpty() || ip == "localhost") {
                                            android.util.Log.e("SetupScreen", "❌ IP adresi girilmemiş!")
                                            errorMessage = "Lütfen bridge server IP adresini girin"
                                            return@Button
                                        }
                                        android.util.Log.d("SetupScreen", "✅ IP adresi girilmiş: $ip")
                                    }
                                }
                                
                                // Ortak işlemler
                                android.util.Log.d("SetupScreen", "🔄 State değişiklikleri yapılıyor...")
                                android.util.Log.d("SetupScreen", "   isPairingMode: $isPairingMode -> true")
                                isPairingMode = true
                                
                                android.util.Log.d("SetupScreen", "   pairingTimeRemaining: $pairingTimeRemaining -> 30")
                                pairingTimeRemaining = 30
                                
                                android.util.Log.d("SetupScreen", "   scannedDevices temizleniyor (${scannedDevices.size} -> 0)")
                                scannedDevices = emptyList()
                                
                                android.util.Log.d("SetupScreen", "   isScanning: $isScanning -> true")
                                isScanning = true
                                
                                android.util.Log.d("SetupScreen", "📡 Transport tarama başlatılıyor...")
                                transport.startScan()
                                android.util.Log.d("SetupScreen", "✅ Transport tarama başlatıldı")
                            },
                            enabled = !isScanning && connectionStatus != ConnectionStatus.CONNECTED,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Taranıyor...")
                            } else {
                                Icon(Icons.Default.Search, contentDescription = "Ara ikonu")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Cihaz Ara")
                            }
                        }
                        
                        OutlinedButton(
                            onClick = {
                                currentTransport?.stopScan()
                                isScanning = false
                                isPairingMode = false
                            },
                            enabled = isScanning,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Vazgeç ikonu")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Vazgeç")
                        }
                    }
                    
                    // Pairing mode UI - Gri arka plan kaldırıldı
                    if (isPairingMode) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(64.dp),
                                strokeWidth = 4.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Eşleştirme Modu Aktif",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${pairingTimeRemaining} saniye kaldı",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    
                    // Bulunan cihazlar listesi
                    if (scannedDevices.isNotEmpty()) {
                        Text(
                            text = "Bulunan Cihazlar (${scannedDevices.size})",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        
                        scannedDevices.forEach { deviceId ->
                            DeviceCard(
                                deviceId = deviceId,
                                transportType = selectedTransportType,
                                isSelected = selectedDeviceId == deviceId,
                                onClick = {
                                    android.util.Log.d("SetupScreen", "\n🖱️  ========================================")
                                    android.util.Log.d("SetupScreen", "🖱️  Cihaz kartına TIKLANDI (Manuel bağlantı)")
                                    android.util.Log.d("SetupScreen", "========================================")
                                    android.util.Log.d("SetupScreen", "   Device ID: $deviceId")
                                    android.util.Log.d("SetupScreen", "   Mevcut connectionStatus: $connectionStatus")
                                    android.util.Log.d("SetupScreen", "   connectionStatus == CONNECTED: ${connectionStatus == ConnectionStatus.CONNECTED}")
                                    
                                    selectedDeviceId = deviceId
                                    android.util.Log.d("SetupScreen", "   selectedDeviceId set edildi")
                                    
                                    if (connectionStatus != ConnectionStatus.CONNECTED) {
                                        android.util.Log.d("SetupScreen", "✅ Manuel bağlantı koşulları sağlandı")
                                        android.util.Log.d("SetupScreen", "   connectionStatus: $connectionStatus -> CONNECTING")
                                        connectionStatus = ConnectionStatus.CONNECTING
                                        android.util.Log.d("SetupScreen", "   transport.connect() çağrılıyor...")
                                        currentTransport?.connect(deviceId)
                                        android.util.Log.d("SetupScreen", "✅ transport.connect() çağrıldı")
                                    } else {
                                        android.util.Log.d("SetupScreen", "⚠️  Zaten bağlı (CONNECTED), yeni bağlantı başlatılmadı")
                                        android.util.Log.d("SetupScreen", "   Mevcut connectionStatus: $connectionStatus")
                                    }
                                }
                            )
                        }
                    }
                    
                    // Bağlantı durumu
                    when (connectionStatus) {
                        ConnectionStatus.CONNECTING -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Bağlanıyor...", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        ConnectionStatus.CONNECTED -> {
                            // Gri arka plan kaldırıldı
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Bağlantı başarılı ikonu",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Bağlandı!",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        ConnectionStatus.ERROR -> {
                            // Gri arka plan kaldırıldı
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Hata ikonu",
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = errorMessage ?: "Bağlantı hatası!",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        else -> {}
                    }
                }
                }
            }
        }
    }
    
    // Error message handling
    LaunchedEffect(errorMessage) {
        errorMessage?.let { message ->
            scope.launch {
                snackbarHostState.showSnackbar(message)
                errorMessage = null
            }
        }
    }
}

// Glassmorphism Card Component
@Composable
fun ExpandableCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.25f),
                        Color.White.copy(alpha = 0.15f)
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.3f),
                shape = RoundedCornerShape(20.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandedChange(!expanded) }
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Kapat" else "Aç",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Divider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = Color.White.copy(alpha = 0.2f)
                )
                content()
            }
        }
    }
}

// Terminal benzeri Event Log View
@Composable
fun EventLogView(eventLogs: List<String>) {
    val scrollState = rememberScrollState()
    
    // Yeni log geldiğinde en alta scroll yap
    LaunchedEffect(eventLogs.size) {
        if (eventLogs.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.8f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Event log ikonu",
                    tint = Color.Green,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "BLE Event Log",
                    color = Color.Green,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Divider(color = Color.Gray.copy(alpha = 0.3f))
            
            // Log content
            if (eventLogs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Event bekleniyor...",
                        color = Color.Gray,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    eventLogs.forEach { logLine ->
                        Text(
                            text = logLine,
                            color = Color.Green,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceCard(
    deviceId: String,
    transportType: TransportType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    // Device ID'den isim oluştur
    val deviceName = when (transportType) {
        TransportType.BLE -> {
            // MAC address formatından isim çıkar (ilk 6 karakter genelde manufacturer)
            "BLE Cihaz"
        }
        TransportType.WEBSOCKET -> {
            if (deviceId.startsWith("ws://") || deviceId.startsWith("wss://")) {
                "WebSocket Server"
            } else {
                "Bridge Server"
            }
        }
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .then(
                if (isSelected) {
                    Modifier
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                )
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(12.dp)
                        )
                } else {
                    Modifier
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.08f),
                                    Color.White.copy(alpha = 0.04f)
                                )
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp)
                        )
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Cihaz ikonu",
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = deviceName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = deviceId,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Seçili cihaz işareti",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
