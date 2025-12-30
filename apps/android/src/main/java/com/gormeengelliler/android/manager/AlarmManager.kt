package com.gormeengelliler.android.manager

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class AlarmManagerHelper(
    private val context: Context,
    private val menuManager: MenuManager,
    private val ttsManager: TTSManager
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    
    suspend fun setAlarm(hour: Int, minute: Int = 0) {
        val language = menuManager.getSelectedLanguage()
        
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            
            // Eğer geçmiş bir saat seçildiyse, yarın için ayarla
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("alarm_hour", hour)
            putExtra("alarm_minute", minute)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            hour * 100 + minute, // Unique request code
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )
        
        val message = when (language) {
            "tr" -> "Sabah $hour'de uyandırılacaksınız."
            "en" -> "You will be woken up at $hour AM."
            "de" -> "Sie werden um $hour Uhr morgens geweckt."
            else -> "Sabah $hour'de uyandırılacaksınız."
        }
        ttsManager.speak(message)
    }
    
    fun cancelAllAlarms() {
        // Tüm alarmları iptal et
        // TODO: Kayıtlı tüm alarm'ları iptal et
    }
}

// AlarmReceiver - BroadcastReceiver olarak implement edilmeli
class AlarmReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Alarm çaldığında yapılacak işlemler
        // TTS ile uyandırma mesajı
    }
}

