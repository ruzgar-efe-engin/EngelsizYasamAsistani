package com.eya.handlers

import android.content.Context
import android.content.Intent
import com.eya.TTSManager
import com.eya.utils.SystemAlarmHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmHandler(private val context: Context) {
    private val systemAlarmHelper = SystemAlarmHelper(context)
    private val scope = CoroutineScope(Dispatchers.Main)
    
    fun handleSetAlarm(
        transcribedText: String,
        ttsManager: TTSManager,
        language: String
    ) {
        scope.launch {
            try {
                // Basit zaman parsing (örn: "14:30", "14 30", "on dört otuz")
                val timeString = parseAlarmTime(transcribedText, language)
                if (timeString != null && timeString.matches(Regex("\\d{1,2}:\\d{2}"))) {
                    val parts = timeString.split(":")
                    val hour = parts[0].toInt()
                    val minute = parts[1].toInt()
                    
                    // Android'in Clock uygulamasını aç ve sistem alarmı kur
                    try {
                        val clockIntent = Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
                            putExtra(android.provider.AlarmClock.EXTRA_HOUR, hour)
                            putExtra(android.provider.AlarmClock.EXTRA_MINUTES, minute)
                            putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, false) // UI'ı göster
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        
                        // Clock uygulaması yüklü mü kontrol et
                        if (clockIntent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(clockIntent)
                            val text = if (language == "tr") {
                                "Sistem alarmı $timeString için kuruluyor"
                            } else {
                                "Setting system alarm for $timeString"
                            }
                            ttsManager.speak(text, language)
                        } else {
                            val text = if (language == "tr") {
                                "Saat uygulaması bulunamadı"
                            } else {
                                "Clock app not found"
                            }
                            ttsManager.speak(text, language)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("AlarmHandler", "Sistem alarmı kurulamadı: ${e.message}", e)
                        val text = if (language == "tr") {
                            "Alarm kurulurken hata oluştu"
                        } else {
                            "Error setting alarm"
                        }
                        ttsManager.speak(text, language)
                    }
                } else {
                    val text = if (language == "tr") {
                        "Alarm zamanı anlaşılamadı"
                    } else {
                        "Could not understand alarm time"
                    }
                    ttsManager.speak(text, language)
                }
            } catch (e: Exception) {
                android.util.Log.e("AlarmHandler", "Alarm kurma hatası: ${e.message}", e)
                val text = if (language == "tr") {
                    "Alarm kurulurken hata oluştu"
                } else {
                    "Error setting alarm"
                }
                ttsManager.speak(text, language)
            }
        }
    }
    
    /**
     * Basit alarm zamanı parsing (API key gerektirmez)
     */
    private fun parseAlarmTime(userInput: String, language: String): String? {
        // Önce "saat:dakika" formatını dene (örn: "14:30", "14 30")
        val timePattern = Regex("(\\d{1,2})[\\s:](\\d{2})")
        val match = timePattern.find(userInput)
        if (match != null) {
            val hour = match.groupValues[1].toIntOrNull()
            val minute = match.groupValues[2].toIntOrNull()
            if (hour != null && minute != null && hour in 0..23 && minute in 0..59) {
                return String.format("%d:%02d", hour, minute)
            }
        }
        
        // Türkçe sayıları parse et (basit)
        if (language == "tr") {
            val turkishNumbers = mapOf(
                "bir" to 1, "iki" to 2, "üç" to 3, "dört" to 4, "beş" to 5,
                "altı" to 6, "yedi" to 7, "sekiz" to 8, "dokuz" to 9, "on" to 10,
                "on bir" to 11, "on iki" to 12, "on üç" to 13, "on dört" to 14,
                "on beş" to 15, "on altı" to 16, "on yedi" to 17, "on sekiz" to 18,
                "on dokuz" to 19, "yirmi" to 20, "yirmi bir" to 21, "yirmi iki" to 22, "yirmi üç" to 23
            )
            
            for ((text, num) in turkishNumbers) {
                if (userInput.contains(text, ignoreCase = true)) {
                    // Dakika için "otuz", "kırk", "elli" gibi ifadeleri de kontrol et
                    val minutePattern = Regex("(\\d{1,2})")
                    val minuteMatch = minutePattern.find(userInput)
                    val minute = minuteMatch?.value?.toIntOrNull() ?: 0
                    if (num in 0..23 && minute in 0..59) {
                        return String.format("%d:%02d", num, minute)
                    }
                }
            }
        }
        
        return null
    }
    
    fun handleListAlarms(ttsManager: TTSManager, language: String) {
        // Sistem alarmlarını oku - sadece aktif (enabled) olanları
        val allSystemAlarms = systemAlarmHelper.getSystemAlarms()
        val systemAlarms = allSystemAlarms.filter { it.enabled }
        
        android.util.Log.d("AlarmHandler", "Sistem alarmları: toplam=${allSystemAlarms.size}, aktif=${systemAlarms.size}")
        
        if (systemAlarms.isEmpty()) {
            val text = if (language == "tr") {
                "Kurulu alarm yok"
            } else {
                "No alarms set"
            }
            ttsManager.speak(text, language)
            return
        }
        
        // Sistem alarmlarını oku
        val systemCount = systemAlarms.size
        val systemText = if (language == "tr") {
            "Sistemde $systemCount alarm kurulu"
        } else {
            "$systemCount system alarms set"
        }
        ttsManager.speak(systemText, language)
        
        systemAlarms.sortedBy { it.hour * 60 + it.minute }.forEachIndexed { index, alarm ->
            val timeStr = systemAlarmHelper.formatAlarmTime(alarm.hour, alarm.minute)
            val daysStr = systemAlarmHelper.formatAlarmDays(alarm.days, language)
            val labelStr = alarm.label ?: ""
            
            val alarmText = if (language == "tr") {
                "${index + 1}. alarm saat $timeStr"
            } else {
                "Alarm ${index + 1} at $timeStr"
            }
            ttsManager.speak(alarmText, language)
            
            if (labelStr.isNotEmpty()) {
                val labelText = if (language == "tr") {
                    "Etiket: $labelStr"
                } else {
                    "Label: $labelStr"
                }
                ttsManager.speak(labelText, language)
            }
            
            if (alarm.days != 0) {
                val daysText = if (language == "tr") {
                    "Günler: $daysStr"
                } else {
                    "Days: $daysStr"
                }
                ttsManager.speak(daysText, language)
            }
        }
    }
    
    fun handleCancelAlarms(ttsManager: TTSManager, language: String) {
        // Sistem alarmlarını kontrol et
        val systemAlarms = systemAlarmHelper.getSystemAlarms().filter { it.enabled }
        
        if (systemAlarms.isEmpty()) {
            val text = if (language == "tr") {
                "İptal edilecek alarm yok"
            } else {
                "No alarms to cancel"
            }
            ttsManager.speak(text, language)
            return
        }
        
        var cancelledCount = 0
        
        // Sistem alarmlarını devre dışı bırak (kapat)
        systemAlarms.forEach { alarm ->
            if (systemAlarmHelper.setSystemAlarmEnabled(alarm.id, false)) {
                cancelledCount++
                android.util.Log.d("AlarmHandler", "Alarm iptal edildi: id=${alarm.id}, saat=${alarm.hour}:${alarm.minute}")
            } else {
                // Devre dışı bırakma başarısız olursa, silmeyi dene
                if (systemAlarmHelper.deleteSystemAlarm(alarm.id)) {
                    cancelledCount++
                    android.util.Log.d("AlarmHandler", "Alarm silindi: id=${alarm.id}, saat=${alarm.hour}:${alarm.minute}")
                }
            }
        }
        
        val text = if (language == "tr") {
            "$cancelledCount alarm iptal edildi"
        } else {
            "$cancelledCount alarms cancelled"
        }
        ttsManager.speak(text, language)
    }
}

