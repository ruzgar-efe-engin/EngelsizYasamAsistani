package com.gormeengelliler.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
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
    private lateinit var bleManager: BLEManager
    private lateinit var menuManager: MenuManager
    private lateinit var ttsManager: TTSManager
    private lateinit var confirmHandler: ConfirmHandler
    private lateinit var cancelHandler: CancelHandler
    
    private val CHANNEL_ID = "DeviceEventServiceChannel"
    private val NOTIFICATION_ID = 1
    
    inner class LocalBinder : Binder() {
        fun getService(): DeviceEventService = this@DeviceEventService
    }
    
    override fun onCreate() {
        super.onCreate()
        
        createNotificationChannel()
        
        // Managers'ı başlat
        menuManager = MenuManager(this)
        ttsManager = TTSManager(this, menuManager)
        confirmHandler = ConfirmHandler(this, menuManager, ttsManager)
        cancelHandler = CancelHandler(ttsManager, menuManager)
        bleManager = BLEManager(this)
        
        // BLE event listener
        bleManager.onEventReceived = { jsonString ->
            handleEvent(jsonString)
        }
        
        // BLE connection state listener
        bleManager.onConnectionStateChanged = { state ->
            // Connection state değişikliklerini handle et
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        
        // BLE bağlantısını başlat (eğer daha önce bağlanmışsa)
        // Bu kısım Setup UI'dan gelecek
        
        return START_STICKY
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
    
    fun connectToDevice(deviceAddress: String) {
        // Device address'ten device'ı bul ve bağlan
        // Bu kısım Setup UI'dan çağrılacak
    }
    
    fun startBLEScan() {
        bleManager.startScan()
    }
    
    fun stopBLEScan() {
        bleManager.stopScan()
    }
    
    fun connectBLE(device: android.bluetooth.BluetoothDevice) {
        bleManager.connect(device)
    }
    
    private fun handleEvent(jsonString: String) {
        val event = DeviceEvent.fromJson(jsonString) ?: return
        
        // KRİTİK: TTS Interrupt - Yeni event geldiğinde mevcut sesi anında kes
        ttsManager.stop()
        
        when (event.type) {
            EventType.THEME_ROTATE -> {
                val themeName = menuManager.getThemeName(event.themeIndex)
                themeName?.let {
                    ttsManager.speakAsync(it)
                }
            }
            EventType.MAIN_ROTATE -> {
                val mainMenuName = menuManager.getMainMenuName(event.themeIndex, event.mainIndex)
                mainMenuName?.let {
                    ttsManager.speakAsync(it)
                }
            }
            EventType.SUB_ROTATE -> {
                val subMenuName = menuManager.getSubMenuName(
                    event.themeIndex,
                    event.mainIndex,
                    event.subIndex
                )
                subMenuName?.let {
                    ttsManager.speakAsync(it)
                }
            }
            EventType.CONFIRM -> {
                confirmHandler.confirm(event.themeIndex, event.mainIndex, event.subIndex)
            }
            EventType.EVENT_CANCEL -> {
                cancelHandler.handleCancel()
            }
            EventType.AI_PRESS, EventType.AI_RELEASE -> {
                // AI işlemleri - gelecekte implement edilecek
            }
        }
    }
    
    fun getBLEManager(): BLEManager {
        return bleManager
    }
    
    fun getMenuManager(): MenuManager {
        return menuManager
    }
    
    fun getTTSManager(): TTSManager {
        return ttsManager
    }
}

