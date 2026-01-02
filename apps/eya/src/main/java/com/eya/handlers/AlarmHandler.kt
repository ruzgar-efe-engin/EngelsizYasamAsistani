package com.eya.handlers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.eya.TTSManager
import com.eya.receivers.AlarmReceiver
import com.eya.utils.GeminiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

class AlarmHandler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val geminiClient = GeminiClient()
    private val scope = CoroutineScope(Dispatchers.Main)
    private val prefs: SharedPreferences = context.getSharedPreferences("EYA_ALARMS", Context.MODE_PRIVATE)
    
    private fun saveAlarms(alarms: List<AlarmData>) {
        val jsonArray = JSONArray()
        alarms.forEach { alarm ->
            val json = JSONObject().apply {
                put("id", alarm.id)
                put("time", alarm.time)
                put("timestamp", alarm.timestamp)
            }
            jsonArray.put(json)
        }
        prefs.edit().putString("alarms", jsonArray.toString()).apply()
    }
    
    private fun loadAlarms(): List<AlarmData> {
        val alarmsJson = prefs.getString("alarms", "[]") ?: "[]"
        val alarms = mutableListOf<AlarmData>()
        try {
            val jsonArray = JSONArray(alarmsJson)
            for (i in 0 until jsonArray.length()) {
                val json = jsonArray.getJSONObject(i)
                alarms.add(
                    AlarmData(
                        id = json.getLong("id"),
                        time = json.getString("time"),
                        timestamp = json.getLong("timestamp")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return alarms
    }
    
    data class AlarmData(
        val id: Long,
        val time: String,
        val timestamp: Long
    )
    
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
                    
                    // Alarm ID'yi daha küçük bir aralıkta tut (1-9999)
                    val existingAlarms = loadAlarms()
                    val maxId = existingAlarms.maxOfOrNull { it.id } ?: 0L
                    val alarmId = if (maxId >= 9999) {
                        // ID'ler dolduysa en küçük boş ID'yi bul
                        val usedIds = existingAlarms.map { it.id }.toSet()
                        (1L..9999L).firstOrNull { it !in usedIds } ?: 1L
                    } else {
                        maxId + 1
                    }
                    
                    val intent = Intent(context, AlarmReceiver::class.java).apply {
                        putExtra("alarm_time", timeString)
                        putExtra("alarm_id", alarmId)
                    }
                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        alarmId.toInt(),
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            calendar.timeInMillis,
                            pendingIntent
                        )
                    } else {
                        alarmManager.setExact(
                            AlarmManager.RTC_WAKEUP,
                            calendar.timeInMillis,
                            pendingIntent
                        )
                    }
                    
                    // Alarm'ı listeye ekle
                    val alarms = loadAlarms().toMutableList()
                    alarms.add(AlarmData(alarmId, timeString, calendar.timeInMillis))
                    saveAlarms(alarms)
                    
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
        val allAlarms = loadAlarms()
        
        // Geçmiş alarmları temizle ve sadece gelecekteki alarmları göster
        val activeAlarms = allAlarms.filter { it.timestamp > System.currentTimeMillis() }
        
        // Eğer aktif alarm yoksa ama listede alarm varsa, geçmiş alarmları temizle
        if (activeAlarms.size != allAlarms.size) {
            saveAlarms(activeAlarms)
        }
        
        if (activeAlarms.isEmpty()) {
            val text = if (language == "tr") {
                "Kurulu alarm yok"
            } else {
                "No alarms set"
            }
            ttsManager.speak(text, language)
            return
        }
        
        val count = activeAlarms.size
        val text = if (language == "tr") {
            "$count alarm kurulu"
        } else {
            "$count alarms set"
        }
        ttsManager.speak(text, language)
        
        // Her alarmı sırayla oku
        activeAlarms.sortedBy { it.timestamp }.forEachIndexed { index, alarm ->
            val alarmText = if (language == "tr") {
                "${index + 1}. alarm saat ${alarm.time}"
            } else {
                "Alarm ${index + 1} at ${alarm.time}"
            }
            ttsManager.speak(alarmText, language)
        }
    }
    
    fun handleCancelAlarms(ttsManager: TTSManager, language: String) {
        val alarms = loadAlarms()
        
        if (alarms.isEmpty()) {
            val text = if (language == "tr") {
                "İptal edilecek alarm yok"
            } else {
                "No alarms to cancel"
            }
            ttsManager.speak(text, language)
            return
        }
        
        // Tüm alarmları iptal et - PendingIntent'i aynı şekilde oluştur
        var cancelledCount = 0
        alarms.forEach { alarm ->
            try {
                val intent = Intent(context, AlarmReceiver::class.java).apply {
                    putExtra("alarm_time", alarm.time)
                    putExtra("alarm_id", alarm.id)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    alarm.id.toInt(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel() // Ekstra güvenlik için
                cancelledCount++
            } catch (e: Exception) {
                android.util.Log.e("AlarmHandler", "Alarm iptal edilirken hata: ${e.message}")
            }
        }
        
        // Listeyi temizle
        saveAlarms(emptyList())
        
        val text = if (language == "tr") {
            "$cancelledCount alarm iptal edildi"
        } else {
            "$cancelledCount alarms cancelled"
        }
        ttsManager.speak(text, language)
    }
}

