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
    // Singleton instance - SetupScreen'den erişilebilmesi için
    companion object {
        @Volatile
        private var instance: DeviceEventService? = null
        
        fun getInstance(): DeviceEventService? = instance
    }
    
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
        
        // Singleton instance'ı set et
        instance = this
        android.util.Log.d("DeviceEventService", "✅ Singleton instance set edildi")
        
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
        
        // Singleton instance'ı temizle
        instance = null
        android.util.Log.d("DeviceEventService", "✅ Singleton instance temizlendi")
        
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
    
    // Public yapıldı - SetupScreen'den çağrılabilmesi için
    fun handleEvent(jsonString: String) {
        val event = DeviceEvent.fromJson(jsonString) ?: return
        
        when (event.type) {
            EventType.MAIN_ROTATE -> {
                android.util.Log.d("DeviceEventService", "🔄 MAIN_ROTATE: mainIndex=${event.mainIndex}")
                com.gormeengelliler.android.manager.EventLogManager.logMenu("MAIN_ROTATE event alındı", "mainIndex=${event.mainIndex}")
                
                // Index normalizasyonu
                val normalizedMainIndex = menuManager.normalizeMainIndex(event.mainIndex)
                com.gormeengelliler.android.manager.EventLogManager.logMenu("MainIndex normalizasyonu", "${event.mainIndex} → $normalizedMainIndex")
                
                // KRİTİK: TTS Interrupt - Yeni event geldiğinde mevcut sesi anında kes
                ttsManager.stop()
                
                // Önceki Handler delay'i iptal et
                currentTTSHandler?.removeCallbacksAndMessages(null)
                
                // Ana menu değiştiğinde alt menu'leri sıfırla (normalize edilmiş index ile)
                if (navigationState.currentMainIndex != normalizedMainIndex) {
                    com.gormeengelliler.android.manager.EventLogManager.logMenu("Ana menu değişti", "${navigationState.currentMainIndex} -> $normalizedMainIndex, alt menu'ler sıfırlanıyor")
                    navigationState.setMainMenu(normalizedMainIndex)
                } else {
                    com.gormeengelliler.android.manager.EventLogManager.logMenu("Ana menu aynı", "normalizedMainIndex=$normalizedMainIndex")
                }
                
                // Scroll geri bildirimi - bip sesi
                playScrollBeep()
                
                // Bip sesinden 100ms sonra aktif dilde menu adı seslendir (normalize edilmiş index ile)
                com.gormeengelliler.android.manager.EventLogManager.logMenu("Menu adı alınıyor", "normalizedMainIndex=$normalizedMainIndex")
                android.util.Log.d("DeviceEventService", "🔍 DEBUG: Menu adı alınıyor, normalizedMainIndex=$normalizedMainIndex")
                
                val mainMenuName = menuManager.getMainMenuName(normalizedMainIndex)
                android.util.Log.d("DeviceEventService", "🔍 DEBUG: getMainMenuName() çağrıldı, sonuç: mainMenuName=${mainMenuName ?: "NULL"}")
                com.gormeengelliler.android.manager.EventLogManager.logMenu("getMainMenuName() sonucu", "mainMenuName=${mainMenuName ?: "NULL"}")
                
                if (mainMenuName != null) {
                    android.util.Log.d("DeviceEventService", "🔍 DEBUG: mainMenuName != null, TTS başlatılacak")
                    val selectedLanguage = menuManager.getSelectedLanguage()
                    android.util.Log.d("DeviceEventService", "📢 Menu adı seslendirilecek: '$mainMenuName' (dil: $selectedLanguage)")
                    com.gormeengelliler.android.manager.EventLogManager.logMenu("✅ Menu adı alındı, TTS başlatılıyor", "text='$mainMenuName', language=$selectedLanguage")
                    
                    android.util.Log.d("DeviceEventService", "🔍 DEBUG: Handler oluşturuluyor...")
                    currentTTSHandler = android.os.Handler(android.os.Looper.getMainLooper())
                    com.gormeengelliler.android.manager.EventLogManager.logMenu("Handler oluşturuldu", "150ms delay başlatıldı, text='$mainMenuName'")
                    
                    android.util.Log.d("DeviceEventService", "🔍 DEBUG: postDelayed çağrılıyor, 150ms delay...")
                    currentTTSHandler?.postDelayed({
                        android.util.Log.d("DeviceEventService", "🔍 DEBUG: Handler delay tamamlandı! Lambda içindeyiz!")
                        android.util.Log.d("DeviceEventService", "⏰ Handler delay tamamlandı, TTS başlatılıyor...")
                        com.gormeengelliler.android.manager.EventLogManager.logMenu("Handler delay tamamlandı", "TTS başlatılıyor: '$mainMenuName', speakAsync() çağrılıyor")
                        
                        android.util.Log.d("DeviceEventService", "🔍 DEBUG: speakAsync() çağrılıyor, mainMenuName='$mainMenuName', selectedLanguage='$selectedLanguage'")
                        ttsManager.speakAsync(mainMenuName, selectedLanguage)
                        android.util.Log.d("DeviceEventService", "🔍 DEBUG: speakAsync() çağrıldı, döndü")
                        com.gormeengelliler.android.manager.EventLogManager.logMenu("✅ speakAsync() çağrıldı", "DeviceEventService'ten döndü, text='$mainMenuName'")
                    }, 150) // 100ms'den 150ms'ye çıkarıldı (bip sesi 50ms, daha güvenli timing)
                    android.util.Log.d("DeviceEventService", "🔍 DEBUG: postDelayed çağrıldı, handler'a eklendi")
                } else {
                    android.util.Log.e("DeviceEventService", "❌ Menu adı alınamadı! normalizedMainIndex=$normalizedMainIndex")
                    com.gormeengelliler.android.manager.EventLogManager.logMenu("❌ Menu adı alınamadı", "normalizedMainIndex=$normalizedMainIndex (orijinal: ${event.mainIndex})", true)
                    // Menu bulunamadığında bile bir mesaj seslendir
                    val selectedLanguage = menuManager.getSelectedLanguage()
                    val errorMessage = when (selectedLanguage) {
                        "tr" -> "Menu bulunamadı"
                        "en" -> "Menu not found"
                        "de" -> "Menü nicht gefunden"
                        else -> "Menu bulunamadı"
                    }
                    currentTTSHandler = android.os.Handler(android.os.Looper.getMainLooper())
                    currentTTSHandler?.postDelayed({
                        ttsManager.speakAsync(errorMessage, selectedLanguage)
                        com.gormeengelliler.android.manager.EventLogManager.logMenu("Hata mesajı TTS ile seslendiriliyor", "text='$errorMessage'")
                    }, 150) // 100ms'den 150ms'ye çıkarıldı
                }
            }
            EventType.SUB_ROTATE -> {
                android.util.Log.d("DeviceEventService", "🔄 SUB_ROTATE: mainIndex=${event.mainIndex}, subIndex=${event.subIndex}")
                com.gormeengelliler.android.manager.EventLogManager.logMenu("SUB_ROTATE event alındı", "mainIndex=${event.mainIndex}, subIndex=${event.subIndex}")
                
                // Index normalizasyonu
                val normalizedMainIndex = menuManager.normalizeMainIndex(event.mainIndex)
                val normalizedSubIndex = menuManager.normalizeSubIndex(normalizedMainIndex, event.subIndex)
                com.gormeengelliler.android.manager.EventLogManager.logMenu("Index normalizasyonu", "mainIndex=${event.mainIndex} → $normalizedMainIndex, subIndex=${event.subIndex} → $normalizedSubIndex")
                
                // KRİTİK: TTS Interrupt - Yeni event geldiğinde mevcut sesi anında kes
                ttsManager.stop()
                
                // Önceki Handler delay'i iptal et
                currentTTSHandler?.removeCallbacksAndMessages(null)
                
                // Navigation state'i güncelle (normalize edilmiş index ile)
                navigationState.currentMainIndex = normalizedMainIndex
                com.gormeengelliler.android.manager.EventLogManager.logMenu("Navigation state güncellendi", "currentMainIndex=$normalizedMainIndex, isAtNestedSubMenu=${navigationState.isAtNestedSubMenu()}")
                
                // Scroll geri bildirimi - bip sesi
                playScrollBeep()
                
                val selectedLanguage = menuManager.getSelectedLanguage()
                
                // Nested sub-menu'de miyiz kontrol et
                if (navigationState.isAtNestedSubMenu()) {
                    // Nested sub-menu scroll - subIndex aslında subSubIndex olarak kullanılıyor
                    val currentSubIndex = navigationState.currentSubIndex ?: 0
                    val normalizedSubSubIndex = menuManager.normalizeSubSubIndex(normalizedMainIndex, currentSubIndex, event.subIndex)
                    com.gormeengelliler.android.manager.EventLogManager.logMenu("Nested sub-menu scroll", "currentSubIndex=$currentSubIndex, subSubIndex=${event.subIndex} → $normalizedSubSubIndex")
                    navigationState.currentSubSubIndex = normalizedSubSubIndex
                    val nestedSubMenuName = menuManager.getSubMenuName(
                        normalizedMainIndex,
                        currentSubIndex,
                        normalizedSubSubIndex
                    )
                    nestedSubMenuName?.let {
                        android.util.Log.d("DeviceEventService", "📢 Nested sub-menu adı seslendirilecek: '$it' (dil: $selectedLanguage)")
                        com.gormeengelliler.android.manager.EventLogManager.logMenu("Nested sub-menu adı alındı, TTS başlatılıyor", "text='$it', language=$selectedLanguage")
                        currentTTSHandler = android.os.Handler(android.os.Looper.getMainLooper())
                        com.gormeengelliler.android.manager.EventLogManager.logMenu("Handler oluşturuldu (nested)", "150ms delay başlatıldı, text='$it'")
                        currentTTSHandler?.postDelayed({
                            android.util.Log.d("DeviceEventService", "⏰ Handler delay tamamlandı, TTS başlatılıyor...")
                            com.gormeengelliler.android.manager.EventLogManager.logMenu("Handler delay tamamlandı (nested)", "TTS başlatılıyor: '$it', speakAsync() çağrılıyor")
                            ttsManager.speakAsync(it, selectedLanguage)
                            com.gormeengelliler.android.manager.EventLogManager.logMenu("speakAsync() çağrıldı (nested)", "DeviceEventService'ten döndü")
                        }, 150) // 100ms'den 150ms'ye çıkarıldı
                    } ?: run {
                        com.gormeengelliler.android.manager.EventLogManager.logMenu("Nested sub-menu adı alınamadı", "normalizedMainIndex=$normalizedMainIndex, currentSubIndex=${navigationState.currentSubIndex}, normalizedSubSubIndex=$normalizedSubSubIndex", true)
                    }
                } else {
                    // Normal sub-menu scroll
                    com.gormeengelliler.android.manager.EventLogManager.logMenu("Normal sub-menu scroll", "normalizedSubIndex=$normalizedSubIndex")
                    navigationState.setSubMenu(normalizedSubIndex)
                    val subMenuName = menuManager.getSubMenuName(
                        normalizedMainIndex,
                        normalizedSubIndex
                    )
                    if (subMenuName != null) {
                        android.util.Log.d("DeviceEventService", "📢 Sub-menu adı seslendirilecek: '$subMenuName' (dil: $selectedLanguage)")
                        com.gormeengelliler.android.manager.EventLogManager.logMenu("✅ Sub-menu adı alındı, TTS başlatılıyor", "text='$subMenuName', language=$selectedLanguage")
                        currentTTSHandler = android.os.Handler(android.os.Looper.getMainLooper())
                        com.gormeengelliler.android.manager.EventLogManager.logMenu("Handler oluşturuldu (sub)", "150ms delay başlatıldı, text='$subMenuName'")
                        currentTTSHandler?.postDelayed({
                            android.util.Log.d("DeviceEventService", "⏰ Handler delay tamamlandı, TTS başlatılıyor...")
                            com.gormeengelliler.android.manager.EventLogManager.logMenu("Handler delay tamamlandı (sub)", "TTS başlatılıyor: '$subMenuName', speakAsync() çağrılıyor")
                            ttsManager.speakAsync(subMenuName, selectedLanguage)
                            com.gormeengelliler.android.manager.EventLogManager.logMenu("✅ speakAsync() çağrıldı (sub)", "DeviceEventService'ten döndü, text='$subMenuName'")
                        }, 150) // 100ms'den 150ms'ye çıkarıldı
                    } else {
                        android.util.Log.e("DeviceEventService", "❌ Sub-menu adı alınamadı! normalizedMainIndex=$normalizedMainIndex, normalizedSubIndex=$normalizedSubIndex")
                        com.gormeengelliler.android.manager.EventLogManager.logMenu("❌ Sub-menu adı alınamadı", "normalizedMainIndex=$normalizedMainIndex, normalizedSubIndex=$normalizedSubIndex (orijinal: mainIndex=${event.mainIndex}, subIndex=${event.subIndex})", true)
                        // Menu bulunamadığında bile bir mesaj seslendir
                        val errorMessage = when (selectedLanguage) {
                            "tr" -> "Menu bulunamadı"
                            "en" -> "Menu not found"
                            "de" -> "Menü nicht gefunden"
                            else -> "Menu bulunamadı"
                        }
                        currentTTSHandler = android.os.Handler(android.os.Looper.getMainLooper())
                        currentTTSHandler?.postDelayed({
                            ttsManager.speakAsync(errorMessage, selectedLanguage)
                            com.gormeengelliler.android.manager.EventLogManager.logMenu("Hata mesajı TTS ile seslendiriliyor", "text='$errorMessage'")
                        }, 150) // 100ms'den 150ms'ye çıkarıldı
                    }
                }
            }
            EventType.CONFIRM -> {
                // Index normalizasyonu
                val normalizedMainIndex = menuManager.normalizeMainIndex(event.mainIndex)
                val normalizedSubIndex = menuManager.normalizeSubIndex(normalizedMainIndex, event.subIndex)
                com.gormeengelliler.android.manager.EventLogManager.logMenu("CONFIRM index normalizasyonu", "mainIndex=${event.mainIndex} → $normalizedMainIndex, subIndex=${event.subIndex} → $normalizedSubIndex")
                
                // KRİTİK: TTS Interrupt - Yeni event geldiğinde mevcut sesi anında kes
                ttsManager.stop()
                
                // Navigation state'e göre işlem yap (normalize edilmiş index ile)
                if (navigationState.isAtNestedSubMenu()) {
                    // Nested sub-menu click
                    val subSubIndex = navigationState.currentSubSubIndex ?: normalizedSubIndex
                    val normalizedSubSubIndex = menuManager.normalizeSubSubIndex(normalizedMainIndex, navigationState.currentSubIndex ?: 0, subSubIndex)
                    confirmHandler.confirm(
                        normalizedMainIndex,
                        navigationState.currentSubIndex ?: 0,
                        normalizedSubSubIndex
                    )
                } else {
                    // Normal sub-menu click - nested sub-menu var mı kontrol et
                    val hasNested = menuManager.hasNestedSubMenus(normalizedMainIndex, normalizedSubIndex)
                    if (hasNested) {
                        // Nested sub-menu'ye geç
                        navigationState.setSubMenu(normalizedSubIndex)
                        navigationState.setNestedSubMenu(0)
                        // İlk nested sub-menu'yu aktif dilde seslendir
                        val firstNestedName = menuManager.getSubMenuName(
                            normalizedMainIndex,
                            normalizedSubIndex,
                            0
                        )
                        firstNestedName?.let {
                            ttsManager.speakAsync(it, menuManager.getSelectedLanguage())
                        }
                    } else {
                        // Normal click - direkt işlemi yap
                        navigationState.setSubMenu(normalizedSubIndex)
                        confirmHandler.confirm(normalizedMainIndex, normalizedSubIndex)
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

