package com.eya.utils

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Settings
import android.util.Log
import java.util.Calendar

data class SystemAlarm(
    val id: Long,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean,
    val label: String?,
    val days: Int // Bitmask for days of week
)

class SystemAlarmHelper(private val context: Context) {
    
    companion object {
        // Android Clock app ContentProvider URI'leri (farklı cihazlarda farklı olabilir)
        private val CLOCK_ALARM_URI = Uri.parse("content://com.android.deskclock/alarms")
        private val CLOCK_INSTANCE_URI = Uri.parse("content://com.android.deskclock/instances")
        
        // Alternatif URI'ler (farklı cihazlar için)
        private val ALTERNATIVE_URIS = listOf(
            Uri.parse("content://com.google.android.deskclock/alarms"),
            Uri.parse("content://com.samsung.android.app.clockpackage/alarms"),
            Uri.parse("content://com.miui.clock/alarms")
        )
    }
    
    /**
     * Sistem alarmlarını okur (Clock uygulamasından)
     */
    fun getSystemAlarms(): List<SystemAlarm> {
        val alarms = mutableListOf<SystemAlarm>()
        
        // Önce standart URI'yi dene
        try {
            val foundAlarms = readAlarmsFromUri(CLOCK_ALARM_URI)
            if (foundAlarms.isNotEmpty()) {
                alarms.addAll(foundAlarms)
                Log.d("SystemAlarmHelper", "Standart URI'den ${foundAlarms.size} alarm bulundu")
                return alarms
            }
        } catch (e: Exception) {
            Log.d("SystemAlarmHelper", "Standart URI çalışmadı: ${e.message}")
        }
        
        // Eğer alarm bulunamadıysa alternatif URI'leri dene
        for (uri in ALTERNATIVE_URIS) {
            try {
                val foundAlarms = readAlarmsFromUri(uri)
                if (foundAlarms.isNotEmpty()) {
                    alarms.addAll(foundAlarms)
                    Log.d("SystemAlarmHelper", "Alternatif URI'den ${foundAlarms.size} alarm bulundu: $uri")
                    break // İlk çalışan URI'yi kullan
                }
            } catch (e: Exception) {
                Log.d("SystemAlarmHelper", "Alternatif URI çalışmadı ($uri): ${e.message}")
            }
        }
        
        Log.d("SystemAlarmHelper", "Toplam ${alarms.size} sistem alarmı bulundu")
        return alarms
    }
    
    private fun readAlarmsFromUri(uri: Uri): List<SystemAlarm> {
        val alarms = mutableListOf<SystemAlarm>()
        
        try {
            val cursor: Cursor? = context.contentResolver.query(
                uri,
                null,
                null,
                null,
                null
            )
            
            cursor?.use {
                // Önce tüm kolon isimlerini logla (debug için)
                val columnNames = it.columnNames
                Log.d("SystemAlarmHelper", "URI: $uri, Kolonlar: ${columnNames.joinToString(", ")}")
                
                val idColumn = it.getColumnIndex("_id")
                val hourColumn = it.getColumnIndex("hour")
                val minuteColumn = it.getColumnIndex("minutes")
                val enabledColumn = it.getColumnIndex("enabled")
                val labelColumn = it.getColumnIndex("label")
                val daysColumn = it.getColumnIndex("days")
                
                // Alternatif kolon isimleri (farklı cihazlarda farklı olabilir)
                val altIdColumn = if (idColumn < 0) it.getColumnIndex("id") else -1
                val altMinuteColumn = if (minuteColumn < 0) it.getColumnIndex("minute") else -1
                
                Log.d("SystemAlarmHelper", "Kolon indeksleri: id=$idColumn/$altIdColumn, hour=$hourColumn, minute=$minuteColumn/$altMinuteColumn, enabled=$enabledColumn, label=$labelColumn, days=$daysColumn")
                
                while (it.moveToNext()) {
                    try {
                        val id = when {
                            idColumn >= 0 -> it.getLong(idColumn)
                            altIdColumn >= 0 -> it.getLong(altIdColumn)
                            else -> -1L
                        }
                        
                        val hour = if (hourColumn >= 0) it.getInt(hourColumn) else 0
                        
                        val minute = when {
                            minuteColumn >= 0 -> it.getInt(minuteColumn)
                            altMinuteColumn >= 0 -> it.getInt(altMinuteColumn)
                            else -> 0
                        }
                        
                        val enabled = if (enabledColumn >= 0) {
                            val enabledValue = it.getInt(enabledColumn)
                            enabledValue == 1
                        } else {
                            false
                        }
                        
                        val label = if (labelColumn >= 0) {
                            val labelValue = it.getString(labelColumn)
                            if (labelValue.isNullOrEmpty()) null else labelValue
                        } else {
                            null
                        }
                        
                        val days = if (daysColumn >= 0) it.getInt(daysColumn) else 0
                        
                        if (id >= 0) {
                            alarms.add(SystemAlarm(id, hour, minute, enabled, label, days))
                            Log.d("SystemAlarmHelper", "Alarm bulundu: id=$id, hour=$hour, minute=$minute, enabled=$enabled, label=$label, days=$days")
                        }
                    } catch (e: Exception) {
                        Log.e("SystemAlarmHelper", "Alarm okunurken hata: ${e.message}", e)
                    }
                }
            } ?: run {
                Log.w("SystemAlarmHelper", "Cursor null döndü URI için: $uri")
            }
        } catch (e: SecurityException) {
            Log.e("SystemAlarmHelper", "İzin hatası: ${e.message}")
        } catch (e: Exception) {
            Log.e("SystemAlarmHelper", "URI okunurken hata: ${e.message}", e)
        }
        
        return alarms
    }
    
    /**
     * Sistem alarmını açar (Clock uygulamasını alarm ekranına yönlendirir)
     */
    fun openSystemAlarmApp() {
        try {
            // Clock uygulamasını aç
            val intent = context.packageManager.getLaunchIntentForPackage("com.android.deskclock")
            if (intent != null) {
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                // Alternatif Clock uygulamalarını dene
                val alternativePackages = listOf(
                    "com.google.android.deskclock",
                    "com.samsung.android.app.clockpackage",
                    "com.miui.clock"
                )
                
                for (packageName in alternativePackages) {
                    val altIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                    if (altIntent != null) {
                        altIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(altIntent)
                        return
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SystemAlarmHelper", "Clock uygulaması açılamadı: ${e.message}")
        }
    }
    
    /**
     * Sistem alarmını siler (Clock uygulamasından)
     */
    fun deleteSystemAlarm(alarmId: Long): Boolean {
        try {
            val uri = ContentUris.withAppendedId(CLOCK_ALARM_URI, alarmId)
            val deleted = context.contentResolver.delete(uri, null, null)
            return deleted > 0
        } catch (e: Exception) {
            Log.e("SystemAlarmHelper", "Alarm silinirken hata: ${e.message}")
            return false
        }
    }
    
    /**
     * Sistem alarmını etkinleştir/devre dışı bırak
     */
    fun setSystemAlarmEnabled(alarmId: Long, enabled: Boolean): Boolean {
        try {
            val uri = ContentUris.withAppendedId(CLOCK_ALARM_URI, alarmId)
            val values = android.content.ContentValues().apply {
                put("enabled", if (enabled) 1 else 0)
            }
            val updated = context.contentResolver.update(uri, values, null, null)
            return updated > 0
        } catch (e: Exception) {
            Log.e("SystemAlarmHelper", "Alarm güncellenirken hata: ${e.message}")
            return false
        }
    }
    
    /**
     * Alarm zamanını formatla (örn: "14:30")
     */
    fun formatAlarmTime(hour: Int, minute: Int): String {
        return String.format("%02d:%02d", hour, minute)
    }
    
    /**
     * Haftanın günlerini formatla (bitmask'ten string'e)
     */
    fun formatAlarmDays(days: Int, language: String = "tr"): String {
        if (days == 0) {
            return if (language == "tr") "Bir kez" else "Once"
        }
        
        val dayNames = if (language == "tr") {
            listOf("Pazar", "Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi")
        } else {
            listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        }
        
        val selectedDays = mutableListOf<String>()
        for (i in 0..6) {
            if ((days and (1 shl i)) != 0) {
                selectedDays.add(dayNames[i])
            }
        }
        
        return if (selectedDays.isEmpty()) {
            if (language == "tr") "Bir kez" else "Once"
        } else {
            selectedDays.joinToString(", ")
        }
    }
}

