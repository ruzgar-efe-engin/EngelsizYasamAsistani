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
import com.gormeengelliler.android.manager.CancelHandler
import com.gormeengelliler.android.manager.ConfirmHandler
import com.gormeengelliler.android.manager.MenuManager
import com.gormeengelliler.android.manager.TTSManager
import com.gormeengelliler.android.model.DeviceEvent
import com.gormeengelliler.android.model.EventType

class DeviceEventService : Service() {
    private val binder = LocalBinder()
    private lateinit var transportProvider: EventTransportProvider
    private lateinit var menuManager: MenuManager
    private lateinit var ttsManager: TTSManager
    private lateinit var confirmHandler: ConfirmHandler
    private lateinit var cancelHandler: CancelHandler
    
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
        ttsManager = TTSManager(this, menuManager)
        confirmHandler = ConfirmHandler(this, menuManager, ttsManager)
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
        
        // KRİTİK: TTS Interrupt - Yeni event geldiğinde mevcut sesi anında kes
        ttsManager.stop()
        
        when (event.type) {
            EventType.MAIN_ROTATE -> {
                // Scroll geri bildirimi - bip sesi
                playScrollBeep()
                
                val mainMenuName = menuManager.getMainMenuName(event.mainIndex)
                mainMenuName?.let {
                    ttsManager.speakAsync(it)
                }
            }
            EventType.SUB_ROTATE -> {
                // Scroll geri bildirimi - bip sesi
                playScrollBeep()
                
                val subMenuName = menuManager.getSubMenuName(
                    event.mainIndex,
                    event.subIndex
                )
                subMenuName?.let {
                    ttsManager.speakAsync(it)
                }
            }
            EventType.CONFIRM -> {
                confirmHandler.confirm(event.mainIndex, event.subIndex)
            }
            EventType.EVENT_CANCEL -> {
                cancelHandler.handleCancel()
            }
            EventType.AI_PRESS, EventType.AI_RELEASE -> {
                // AI işlemleri - gelecekte implement edilecek
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

