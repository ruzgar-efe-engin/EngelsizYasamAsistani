package com.gormeengelliler.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.gormeengelliler.android.MainActivity
import com.gormeengelliler.android.R
import com.gormeengelliler.android.manager.*
import com.gormeengelliler.android.model.DeviceEvent
import com.gormeengelliler.android.model.EventType
import com.gormeengelliler.android.model.MenuNavigationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DeviceEventService : Service() {
    private val binder = LocalBinder()
    private lateinit var transportProvider: EventTransportProvider
    private lateinit var menuManager: MenuManager
    private lateinit var ttsManager: TTSManager
    private lateinit var confirmHandler: ConfirmHandler
    private lateinit var cancelHandler: CancelHandler
    private lateinit var speechToTextManager: SpeechToTextManager
    private lateinit var aiHandler: AIHandler
    private lateinit var navigationState: MenuNavigationState
    private val scope = CoroutineScope(Dispatchers.IO)
    
    // Handler delay iptal mekanizması için
    private var currentTTSHandler: android.os.Handler? = null
    
    private val CHANNEL_ID = "DeviceEventServiceChannel"
    private val NOTIFICATION_ID = 1
    
    // Bip sesi için ToneGenerator - scroll döndürüldüğünde geri bildirim
    private var toneGenerator: ToneGenerator? = null
    
    inner class LocalBinder : Binder() {
        fun getService(): DeviceEventService = this@DeviceEventService
    }
    
    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("DeviceEventService", "\n🚀 ========================================")
        android.util.Log.d("DeviceEventService", "🚀 onCreate() ÇAĞRILDI")
        android.util.Log.d("DeviceEventService", "========================================")
        
        createNotificationChannel()
        
        // Managers'ı başlat
        android.util.Log.d("DeviceEventService", "📦 Managers başlatılıyor...")
        menuManager = MenuManager(this)
        navigationState = MenuNavigationState(menuManager)
        ttsManager = TTSManager(this, menuManager)
        speechToTextManager = SpeechToTextManager(this)
        
        // Handler'ları oluştur
        val weatherHandler = WeatherHandler(this, menuManager, ttsManager)
        val locationHandler = LocationHandler(this, menuManager, ttsManager)
        val emergencyHandler = EmergencyHandler(this, menuManager, ttsManager)
        val contactHandler = ContactHandler(this, menuManager, ttsManager)
        val notesHandler = NotesHandler(this, menuManager, ttsManager)
        val deviceHandler = DeviceHandler(this, menuManager, ttsManager)
        aiHandler = AIHandler(this, menuManager, ttsManager)
        
        confirmHandler = ConfirmHandler(
            this, 
            menuManager, 
            ttsManager,
            weatherHandler,
            locationHandler,
            emergencyHandler,
            contactHandler,
            notesHandler,
            deviceHandler,
            aiHandler
        )
        cancelHandler = CancelHandler(ttsManager, menuManager)
        transportProvider = EventTransportProvider(this).apply {
            initialize(TransportType.BLE) // Varsayılan: BLE
        }
        
        // ToneGenerator başlat - scroll geri bildirimi için
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.MAX_VOLUME)
            android.util.Log.d("DeviceEventService", "✅ ToneGenerator başlatıldı")
        } catch (e: Exception) {
            android.util.Log.e("DeviceEventService", "❌ ToneGenerator başlatılamadı: ${e.message}")
        }
        
        android.util.Log.d("DeviceEventService", "✅ Managers başlatıldı")
        
        // Transport event listener
        transportProvider.getCurrentTransport()?.let { transport ->
            transport.onEventReceived = { jsonString ->
                android.util.Log.d("DeviceEventService", "📥 Transport event alındı: $jsonString")
                handleEvent(jsonString)
            }
            
            // Transport connection state listener
            transport.onConnectionStateChanged = { isConnected ->
                android.util.Log.d("DeviceEventService", "🔄 Transport connection state değişti: $isConnected")
                // Connection state değişikliklerini handle et
            }
        }
        
        android.util.Log.d("DeviceEventService", "✅ onCreate() tamamlandı")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.d("DeviceEventService", "\n🚀 ========================================")
        android.util.Log.d("DeviceEventService", "🚀 onStartCommand() ÇAĞRILDI")
        android.util.Log.d("DeviceEventService", "   flags: $flags, startId: $startId")
        android.util.Log.d("DeviceEventService", "========================================")
        
        startForeground(NOTIFICATION_ID, createNotification())
        android.util.Log.d("DeviceEventService", "✅ Foreground notification başlatıldı")
        
        // Transport bağlantısını başlat (eğer daha önce bağlanmışsa)
        // Bu kısım Setup UI'dan gelecek
        android.util.Log.d("DeviceEventService", "💡 Transport bağlantısı Setup UI'dan başlatılacak")
        
        android.util.Log.d("DeviceEventService", "✅ onStartCommand() tamamlandı - START_STICKY dönüyor")
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        android.util.Log.d("DeviceEventService", "\n🛑 ========================================")
        android.util.Log.d("DeviceEventService", "🛑 onDestroy() ÇAĞRILDI")
        android.util.Log.d("DeviceEventService", "========================================")
        
        // ToneGenerator'ı temizle
        toneGenerator?.release()
        toneGenerator = null
        android.util.Log.d("DeviceEventService", "✅ ToneGenerator temizlendi")
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Engelsiz Yaşam Asistanı Servisi",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Cihaz event'lerini dinleyen arka plan servisi"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Engelsiz Yaşam Asistanı")
            .setContentText("Cihaz bağlantısı aktif")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    fun connectToDevice(deviceId: String) {
        // Device ID'den device'a bağlan
        // Bu kısım Setup UI'dan çağrılacak
        transportProvider.getCurrentTransport()?.connect(deviceId)
    }
    
    fun startScan() {
        transportProvider.getCurrentTransport()?.startScan()
    }
    
    fun stopScan() {
        transportProvider.getCurrentTransport()?.stopScan()
    }
    
    fun connect(deviceId: String) {
        transportProvider.getCurrentTransport()?.connect(deviceId)
    }
    
    private fun handleEvent(jsonString: String) {
        val event = DeviceEvent.fromJson(jsonString) ?: return
        
        when (event.type) {
            EventType.MAIN_ROTATE -> {
                android.util.Log.d("DeviceEventService", "🔄 MAIN_ROTATE: mainIndex=${event.mainIndex}")
                com.gormeengelliler.android.manager.EventLogManager.logMenu("MAIN_ROTATE event alındı", "mainIndex=${event.mainIndex}")
                
                // KRİTİK: TTS Interrupt - Yeni event geldiğinde mevcut sesi anında kes
                ttsManager.stop()
                
                // Önceki Handler delay'i iptal et
                currentTTSHandler?.removeCallbacksAndMessages(null)
                
                // Ana menu değiştiğinde alt menu'leri sıfırla
                if (navigationState.currentMainIndex != event.mainIndex) {
                    com.gormeengelliler.android.manager.EventLogManager.logMenu("Ana menu değişti", "${navigationState.currentMainIndex} -> ${event.mainIndex}, alt menu'ler sıfırlanıyor")
                    navigationState.setMainMenu(event.mainIndex)
                } else {
                    com.gormeengelliler.android.manager.EventLogManager.logMenu("Ana menu aynı", "mainIndex=${event.mainIndex}")
                }
                
                // Scroll geri bildirimi - bip sesi
                playScrollBeep()
                
                // Bip sesinden 100ms sonra aktif dilde menu adı seslendir (bip sesi 50ms, biraz daha bekleyelim)
                com.gormeengelliler.android.manager.EventLogManager.logMenu("Menu adı alınıyor", "mainIndex=${event.mainIndex}")
                val mainMenuName = menuManager.getMainMenuName(event.mainIndex)
                mainMenuName?.let {
                    val selectedLanguage = menuManager.getSelectedLanguage()
                    android.util.Log.d("DeviceEventService", "📢 Menu adı seslendirilecek: '$it' (dil: $selectedLanguage)")
                    com.gormeengelliler.android.manager.EventLogManager.logMenu("Menu adı alındı, TTS başlatılıyor", "text='$it', language=$selectedLanguage")
                    
                    currentTTSHandler = android.os.Handler(android.os.Looper.getMainLooper())
                    com.gormeengelliler.android.manager.EventLogManager.logMenu("Handler oluşturuldu", "100ms delay başlatıldı, text='$it'")
                    currentTTSHandler?.postDelayed({
                        android.util.Log.d("DeviceEventService", "⏰ Handler delay tamamlandı, TTS başlatılıyor...")
                        com.gormeengelliler.android.manager.EventLogManager.logMenu("Handler delay tamamlandı", "TTS başlatılıyor: '$it', speakAsync() çağrılıyor")
                        ttsManager.speakAsync(it, selectedLanguage)
                        com.gormeengelliler.android.manager.EventLogManager.logMenu("speakAsync() çağrıldı", "DeviceEventService'ten döndü")
                    }, 100) // 50ms'den 100ms'ye çıkarıldı
                } ?: run {
                    com.gormeengelliler.android.manager.EventLogManager.logMenu("Menu adı alınamadı", "mainIndex=${event.mainIndex}", true)
                }
            }
            EventType.SUB_ROTATE -> {
                android.util.Log.d("DeviceEventService", "🔄 SUB_ROTATE: mainIndex=${event.mainIndex}, subIndex=${event.subIndex}")
                com.gormeengelliler.android.manager.EventLogManager.logMenu("SUB_ROTATE event alındı", "mainIndex=${event.mainIndex}, subIndex=${event.subIndex}")
                
                // KRİTİK: TTS Interrupt - Yeni event geldiğinde mevcut sesi anında kes
                ttsManager.stop()
                
                // Önceki Handler delay'i iptal et
                currentTTSHandler?.removeCallbacksAndMessages(null)
                
                // Navigation state'i güncelle
                navigationState.currentMainIndex = event.mainIndex
                com.gormeengelliler.android.manager.EventLogManager.logMenu("Navigation state güncellendi", "currentMainIndex=${navigationState.currentMainIndex}, isAtNestedSubMenu=${navigationState.isAtNestedSubMenu()}")
                
                // Scroll geri bildirimi - bip sesi
                playScrollBeep()
                
                val selectedLanguage = menuManager.getSelectedLanguage()
                
                // Nested sub-menu'de miyiz kontrol et
                if (navigationState.isAtNestedSubMenu()) {
                    // Nested sub-menu scroll
                    com.gormeengelliler.android.manager.EventLogManager.logMenu("Nested sub-menu scroll", "currentSubIndex=${navigationState.currentSubIndex}, subSubIndex=${event.subIndex}")
                    navigationState.currentSubSubIndex = event.subIndex
                    val nestedSubMenuName = menuManager.getSubMenuName(
                        event.mainIndex,
                        navigationState.currentSubIndex ?: 0,
                        event.subIndex
                    )
                    nestedSubMenuName?.let {
                        android.util.Log.d("DeviceEventService", "📢 Nested sub-menu adı seslendirilecek: '$it' (dil: $selectedLanguage)")
                        com.gormeengelliler.android.manager.EventLogManager.logMenu("Nested sub-menu adı alındı, TTS başlatılıyor", "text='$it', language=$selectedLanguage")
                        currentTTSHandler = android.os.Handler(android.os.Looper.getMainLooper())
                        com.gormeengelliler.android.manager.EventLogManager.logMenu("Handler oluşturuldu (nested)", "100ms delay başlatıldı, text='$it'")
                        currentTTSHandler?.postDelayed({
                            android.util.Log.d("DeviceEventService", "⏰ Handler delay tamamlandı, TTS başlatılıyor...")
                            com.gormeengelliler.android.manager.EventLogManager.logMenu("Handler delay tamamlandı (nested)", "TTS başlatılıyor: '$it', speakAsync() çağrılıyor")
                            ttsManager.speakAsync(it, selectedLanguage)
                            com.gormeengelliler.android.manager.EventLogManager.logMenu("speakAsync() çağrıldı (nested)", "DeviceEventService'ten döndü")
                        }, 100) // 50ms'den 100ms'ye çıkarıldı
                    } ?: run {
                        com.gormeengelliler.android.manager.EventLogManager.logMenu("Nested sub-menu adı alınamadı", "mainIndex=${event.mainIndex}, subIndex=${navigationState.currentSubIndex}, subSubIndex=${event.subIndex}", true)
                    }
                } else {
                    // Normal sub-menu scroll
                    com.gormeengelliler.android.manager.EventLogManager.logMenu("Normal sub-menu scroll", "subIndex=${event.subIndex}")
                    navigationState.setSubMenu(event.subIndex)
                    val subMenuName = menuManager.getSubMenuName(
                        event.mainIndex,
                        event.subIndex
                    )
                    subMenuName?.let {
                        android.util.Log.d("DeviceEventService", "📢 Sub-menu adı seslendirilecek: '$it' (dil: $selectedLanguage)")
                        com.gormeengelliler.android.manager.EventLogManager.logMenu("Sub-menu adı alındı, TTS başlatılıyor", "text='$it', language=$selectedLanguage")
                        currentTTSHandler = android.os.Handler(android.os.Looper.getMainLooper())
                        com.gormeengelliler.android.manager.EventLogManager.logMenu("Handler oluşturuldu (sub)", "100ms delay başlatıldı, text='$it'")
                        currentTTSHandler?.postDelayed({
                            android.util.Log.d("DeviceEventService", "⏰ Handler delay tamamlandı, TTS başlatılıyor...")
                            com.gormeengelliler.android.manager.EventLogManager.logMenu("Handler delay tamamlandı (sub)", "TTS başlatılıyor: '$it', speakAsync() çağrılıyor")
                            ttsManager.speakAsync(it, selectedLanguage)
                            com.gormeengelliler.android.manager.EventLogManager.logMenu("speakAsync() çağrıldı (sub)", "DeviceEventService'ten döndü")
                        }, 100) // 50ms'den 100ms'ye çıkarıldı
                    } ?: run {
                        com.gormeengelliler.android.manager.EventLogManager.logMenu("Sub-menu adı alınamadı", "mainIndex=${event.mainIndex}, subIndex=${event.subIndex}", true)
                    }
                }
            }
            EventType.CONFIRM -> {
                // KRİTİK: TTS Interrupt - Yeni event geldiğinde mevcut sesi anında kes
                ttsManager.stop()
                
                // Navigation state'e göre işlem yap
                if (navigationState.isAtNestedSubMenu()) {
                    // Nested sub-menu click
                    val subSubIndex = navigationState.currentSubSubIndex ?: event.subIndex
                    confirmHandler.confirm(
                        event.mainIndex,
                        navigationState.currentSubIndex ?: 0,
                        subSubIndex
                    )
                } else {
                    // Normal sub-menu click - nested sub-menu var mı kontrol et
                    val hasNested = menuManager.hasNestedSubMenus(event.mainIndex, event.subIndex)
                    if (hasNested) {
                        // Nested sub-menu'ye geç
                        navigationState.setSubMenu(event.subIndex)
                        navigationState.setNestedSubMenu(0)
                        // İlk nested sub-menu'yu aktif dilde seslendir
                        val firstNestedName = menuManager.getSubMenuName(
                            event.mainIndex,
                            event.subIndex,
                            0
                        )
                        firstNestedName?.let {
                            ttsManager.speakAsync(it, menuManager.getSelectedLanguage())
                        }
                    } else {
                        // Normal click - direkt işlemi yap
                        navigationState.setSubMenu(event.subIndex)
                        confirmHandler.confirm(event.mainIndex, event.subIndex)
                    }
                }
            }
            EventType.EVENT_CANCEL -> {
                // Cancel - bir seviye geri git
                if (navigationState.isAtNestedSubMenu()) {
                    navigationState.currentSubSubIndex = null
                } else if (navigationState.isAtSubMenu()) {
                    navigationState.currentSubIndex = null
                }
                cancelHandler.handleCancel()
            }
            EventType.AI_PRESS -> {
                // AI butonu basıldı - ses kaydı başlat
                android.util.Log.d("DeviceEventService", "🎤 AI_PRESS - Ses kaydı başlatılıyor...")
                scope.launch {
                    val started = speechToTextManager.startRecording()
                    if (started) {
                        android.util.Log.d("DeviceEventService", "✅ Ses kaydı başlatıldı")
                    } else {
                        android.util.Log.e("DeviceEventService", "❌ Ses kaydı başlatılamadı")
                        val errorMessage = when (menuManager.getSelectedLanguage()) {
                            "tr" -> "Ses kaydı başlatılamadı. Lütfen mikrofon iznini kontrol edin."
                            "en" -> "Could not start recording. Please check microphone permission."
                            "de" -> "Aufnahme konnte nicht gestartet werden. Bitte Mikrofonberechtigung überprüfen."
                            else -> "Ses kaydı başlatılamadı."
                        }
                        ttsManager.speakAsync(errorMessage, menuManager.getSelectedLanguage())
                    }
                }
            }
            EventType.AI_RELEASE -> {
                // AI butonu bırakıldı - ses kaydı durdur, transkript al, Gemini'ye gönder
                android.util.Log.d("DeviceEventService", "🎤 AI_RELEASE - Ses kaydı durduruluyor, transkript alınıyor...")
                scope.launch {
                    val transcript = speechToTextManager.stopRecordingAndTranscribe()
                    
                    if (transcript.isNullOrEmpty()) {
                        android.util.Log.e("DeviceEventService", "❌ Transkript alınamadı")
                        val errorMessage = when (menuManager.getSelectedLanguage()) {
                            "tr" -> "Ses anlaşılamadı. Lütfen tekrar deneyin."
                            "en" -> "Could not understand audio. Please try again."
                            "de" -> "Audio konnte nicht verstanden werden. Bitte versuchen Sie es erneut."
                            else -> "Ses anlaşılamadı."
                        }
                        ttsManager.speakAsync(errorMessage, menuManager.getSelectedLanguage())
                        return@launch
                    }
                    
                    android.util.Log.d("DeviceEventService", "📝 Transkript: $transcript")
                    
                    // Gemini'ye gönder
                    val aiResponse = aiHandler.processUserMessage(transcript)
                    
                    if (aiResponse != null) {
                        android.util.Log.d("DeviceEventService", "🤖 AI Yanıt: $aiResponse")
                        // Yanıtı Gemini TTS ile seslendir
                        ttsManager.speakAsync(aiResponse, menuManager.getSelectedLanguage())
                    } else {
                        android.util.Log.e("DeviceEventService", "❌ AI yanıt alınamadı")
                        val errorMessage = when (menuManager.getSelectedLanguage()) {
                            "tr" -> "Yanıt alınamadı. Lütfen tekrar deneyin."
                            "en" -> "Could not get response. Please try again."
                            "de" -> "Antwort konnte nicht erhalten werden. Bitte versuchen Sie es erneut."
                            else -> "Yanıt alınamadı."
                        }
                        ttsManager.speakAsync(errorMessage, menuManager.getSelectedLanguage())
                    }
                }
            }
        }
    }
    
    fun getTransportProvider(): EventTransportProvider {
        return transportProvider
    }
    
    fun getMenuManager(): MenuManager {
        return menuManager
    }
    
    fun getTTSManager(): TTSManager {
        return ttsManager
    }
    
    /**
     * Scroll döndürüldüğünde bip sesi çıkar - geri bildirim için
     */
    private fun playScrollBeep() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 50) // 50ms kısa bip
            android.util.Log.d("DeviceEventService", "🔊 Scroll bip sesi çalındı")
        } catch (e: Exception) {
            android.util.Log.e("DeviceEventService", "❌ Bip sesi çalınamadı: ${e.message}")
        }
    }
}

