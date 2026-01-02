package com.eya.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.eya.MainActivity

class BLEEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.eya.BLE_EVENT") {
            val eventData = intent.getStringExtra("event_data") ?: return
            
            Log.d("BLEEventReceiver", "Event alındı (kilit ekranında): $eventData")
            
            // Wake lock al - ekran kapalıysa aç
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "EYA::BLEEvent"
            )
            wakeLock.acquire(5000) // 5 saniye
            
            // MainActivity'yi başlat (kilit ekranında da)
            val activityIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                       Intent.FLAG_ACTIVITY_CLEAR_TOP or
                       Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("ble_event", eventData)
            }
            context.startActivity(activityIntent)
            
            // Wake lock'ı serbest bırak
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (wakeLock.isHeld) {
                    wakeLock.release()
                }
            }, 5000)
        }
    }
}

