package com.eya.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.eya.BLEEventTransport
import com.eya.MainActivity
import com.eya.model.DeviceEvent

class BLEForegroundService : Service() {
    private var bleManager: BLEEventTransport? = null
    private var onEventCallback: ((String) -> Unit)? = null
    
    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "EYA_BLE_SERVICE"
        
        fun start(context: Context) {
            val intent = Intent(context, BLEForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stop(context: Context) {
            val intent = Intent(context, BLEForegroundService::class.java)
            context.stopService(intent)
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        // BLE Manager'ı başlat
        bleManager = BLEEventTransport(this)
        
        // Event callback'lerini ayarla
        bleManager?.onDeviceFound = { deviceId ->
            Log.d("BLEForegroundService", "Cihaz bulundu: $deviceId")
            // MainActivity'ye event gönder
            sendEventToActivity("device_found:$deviceId")
        }
        
        bleManager?.onEventReceived = { json ->
            Log.d("BLEForegroundService", "Event alındı: $json")
            sendEventToActivity(json)
        }
        
        bleManager?.onConnectionChanged = { connected ->
            Log.d("BLEForegroundService", "Bağlantı durumu: $connected")
            sendEventToActivity("connection_changed:$connected")
        }
        
        bleManager?.onLog = { log ->
            Log.d("BLEForegroundService", log)
        }
        
        // Otomatik tarama başlat
        try {
            bleManager?.startScan()
        } catch (e: Exception) {
            Log.e("BLEForegroundService", "Tarama başlatılamadı: ${e.message}")
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY // Servis kapanırsa otomatik yeniden başlat
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        bleManager?.disconnect()
        bleManager?.stopScan()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "EYA BLE Servisi",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Görme engelliler için BLE cihaz bağlantısı"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("EYA Aktif")
            .setContentText("BLE cihaz bağlantısı dinleniyor")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
    
    private fun sendEventToActivity(data: String) {
        // Broadcast gönder - BLEEventReceiver dinleyecek
        val intent = Intent("com.eya.BLE_EVENT").apply {
            putExtra("event_data", data)
        }
        sendBroadcast(intent)
        
        // Ayrıca MainActivity'yi başlat (kilit ekranında da çalışması için)
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                   Intent.FLAG_ACTIVITY_CLEAR_TOP or
                   Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("ble_event", data)
        }
        startActivity(activityIntent)
    }
}

