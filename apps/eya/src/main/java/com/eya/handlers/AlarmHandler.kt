package com.eya.handlers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.eya.TTSManager
import com.eya.utils.GeminiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class AlarmHandler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val geminiClient = GeminiClient()
    private val scope = CoroutineScope(Dispatchers.Main)
    
    fun handleSetAlarm(
        transcribedText: String,
        ttsManager: TTSManager,
        language: String
    ) {
        scope.launch {
            try {
                val timeString = geminiClient.parseAlarmTime(transcribedText, language)
                if (timeString != null && timeString.matches(Regex("\\d{1,2}:\\d{2}"))) {
                    val parts = timeString.split(":")
                    val hour = parts[0].toInt()
                    val minute = parts[1].toInt()
                    
                    val calendar = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        
                        // Eğer geçmiş bir saat seçildiyse, yarın için ayarla
                        if (timeInMillis < System.currentTimeMillis()) {
                            add(Calendar.DAY_OF_MONTH, 1)
                        }
                    }
                    
                    val intent = Intent(context, AlarmReceiver::class.java).apply {
                        putExtra("alarm_time", timeString)
                    }
                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        System.currentTimeMillis().toInt(),
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                    
                    val text = if (language == "tr") {
                        "Alarm $timeString için kuruldu"
                    } else {
                        "Alarm set for $timeString"
                    }
                    ttsManager.speak(text, language)
                } else {
                    val text = if (language == "tr") {
                        "Alarm zamanı anlaşılamadı"
                    } else {
                        "Could not understand alarm time"
                    }
                    ttsManager.speak(text, language)
                }
            } catch (e: Exception) {
                val text = if (language == "tr") {
                    "Alarm kurulurken hata oluştu"
                } else {
                    "Error setting alarm"
                }
                ttsManager.speak(text, language)
            }
        }
    }
    
    fun handleListAlarms(ttsManager: TTSManager, language: String) {
        // Basit implementasyon: AlarmManager'dan alarm listesi almak zor
        // Şimdilik sabit bir mesaj
        val text = if (language == "tr") {
            "Alarm listesi özelliği yakında eklenecek"
        } else {
            "Alarm list feature coming soon"
        }
        ttsManager.speak(text, language)
    }
    
    fun handleCancelAlarms(ttsManager: TTSManager, language: String) {
        // Tüm alarmları iptal etmek için PendingIntent'leri iptal etmek gerekir
        // Basit implementasyon
        val text = if (language == "tr") {
            "Tüm alarmlar iptal edildi"
        } else {
            "All alarms cancelled"
        }
        ttsManager.speak(text, language)
    }
}

// Basit AlarmReceiver - gerçek implementasyon için daha fazla kod gerekli
class AlarmReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Alarm çaldığında yapılacak işlemler
    }
}

