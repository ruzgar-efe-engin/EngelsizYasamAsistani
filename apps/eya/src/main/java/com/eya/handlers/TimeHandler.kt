package com.eya.handlers

import android.content.Context
import com.eya.TTSManager
import java.util.Calendar
import java.util.Locale

class TimeHandler(private val context: Context) {
    
    fun handleSaat(ttsManager: TTSManager, language: String) {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        
        val text = if (language == "tr") {
            val minuteText = if (minute == 0) {
                "buçuk"
            } else if (minute < 10) {
                "sıfır $minute"
            } else {
                minute.toString()
            }
            "Saat $hour $minuteText"
        } else {
            val minuteText = if (minute == 0) {
                "o'clock"
            } else if (minute < 10) {
                "oh $minute"
            } else {
                minute.toString()
            }
            "It's $hour $minuteText"
        }
        
        ttsManager.speak(text, language)
    }
    
    fun handleTarih(ttsManager: TTSManager, language: String) {
        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val month = calendar.get(Calendar.MONTH)
        val year = calendar.get(Calendar.YEAR)
        
        val text = if (language == "tr") {
            val dayNames = arrayOf("", "Pazar", "Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi")
            val monthNames = arrayOf("Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
                "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık")
            
            "Bugün ${dayNames[dayOfWeek]} günü, $day ${monthNames[month]}, $year"
        } else {
            val dayNames = arrayOf("", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
            val monthNames = arrayOf("January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December")
            
            "Today is ${dayNames[dayOfWeek]}, ${monthNames[month]} $day, $year"
        }
        
        ttsManager.speak(text, language)
    }
}

