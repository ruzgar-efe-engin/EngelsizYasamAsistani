package com.eya.handlers

import android.content.Context
import android.content.Intent
import com.eya.TTSManager
import com.eya.utils.GeminiClient
import com.eya.utils.SystemAlarmHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmHandler(private val context: Context) {
    private val geminiClient = GeminiClient()
    private val systemAlarmHelper = SystemAlarmHelper(context)
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

