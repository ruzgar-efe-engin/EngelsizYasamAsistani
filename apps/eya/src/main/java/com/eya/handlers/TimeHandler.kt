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
            formatTimeTurkish(hour, minute)
        } else {
            formatTimeEnglish(hour, minute)
        }
        
        ttsManager.speak(text, language)
    }
    
    private fun formatTimeTurkish(hour: Int, minute: Int): String {
        val hourNames = arrayOf(
            "", "bir", "iki", "üç", "dört", "beş", "altı", "yedi", "sekiz", "dokuz",
            "on", "on bir", "on iki", "on üç", "on dört", "on beş", "on altı",
            "on yedi", "on sekiz", "on dokuz", "yirmi", "yirmi bir", "yirmi iki", "yirmi üç"
        )
        
        val minuteNames = arrayOf(
            "", "bir", "iki", "üç", "dört", "beş", "altı", "yedi", "sekiz", "dokuz",
            "on", "on bir", "on iki", "on üç", "on dört", "on beş", "on altı",
            "on yedi", "on sekiz", "on dokuz", "yirmi", "yirmi bir", "yirmi iki", "yirmi üç",
            "yirmi dört", "yirmi beş", "yirmi altı", "yirmi yedi", "yirmi sekiz", "yirmi dokuz",
            "otuz", "otuz bir", "otuz iki", "otuz üç", "otuz dört", "otuz beş", "otuz altı",
            "otuz yedi", "otuz sekiz", "otuz dokuz", "kırk", "kırk bir", "kırk iki", "kırk üç",
            "kırk dört", "kırk beş", "kırk altı", "kırk yedi", "kırk sekiz", "kırk dokuz",
            "elli", "elli bir", "elli iki", "elli üç", "elli dört", "elli beş", "elli altı",
            "elli yedi", "elli sekiz", "elli dokuz"
        )
        
        val period = when {
            hour < 12 -> "sabah"
            hour < 18 -> "öğleden sonra"
            else -> "akşam"
        }
        
        val hourName = hourNames.getOrNull(hour) ?: hour.toString()
        val minuteName = minuteNames.getOrNull(minute) ?: minute.toString()
        
        return when {
            minute == 0 -> "$period $hourName"
            minute == 30 -> "$period $hourName buçuk"
            minute < 30 -> "$period $hourName $minuteName geçiyor"
            else -> {
                val nextHour = (hour + 1) % 24
                val remainingMinutes = 60 - minute
                val remainingMinuteName = minuteNames.getOrNull(remainingMinutes) ?: remainingMinutes.toString()
                val nextHourName = hourNames.getOrNull(nextHour) ?: nextHour.toString()
                "$period $nextHourName'ye $remainingMinuteName var"
            }
        }
    }
    
    private fun formatTimeEnglish(hour: Int, minute: Int): String {
        val hour12 = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
        val period = if (hour < 12) "AM" else "PM"
        
        return when {
            minute == 0 -> "It's $hour12 o'clock $period"
            minute == 30 -> "It's half past $hour12 $period"
            minute < 30 -> "It's $minute past $hour12 $period"
            else -> {
                val nextHour = if (hour == 23) 0 else hour + 1
                val nextHour12 = if (nextHour == 0) 12 else if (nextHour > 12) nextHour - 12 else nextHour
                val remainingMinutes = 60 - minute
                "It's $remainingMinutes to $nextHour12 $period"
            }
        }
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

