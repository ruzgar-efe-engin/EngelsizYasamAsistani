package com.gormeengelliler.android.manager

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TimerManager(
    private val context: Context,
    private val menuManager: MenuManager,
    private val ttsManager: TTSManager
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var activeTimers = mutableListOf<kotlinx.coroutines.Job>()
    
    suspend fun setTimer(minutes: Int) {
        val language = menuManager.getSelectedLanguage()
        
        val message = when (language) {
            "tr" -> "$minutes dk sonra hatırlatılacak"
            "en" -> "You will be reminded in $minutes minutes."
            "de" -> "Sie werden in $minutes Minuten erinnert."
            else -> "$minutes dk sonra hatırlatılacak"
        }
        ttsManager.speak(message)
        
        // Timer'ı başlat
        val timerJob = scope.launch {
            delay(minutes * 60 * 1000L) // milliseconds
            
            val reminderMessage = when (language) {
                "tr" -> "Hatırlatma: $minutes dakika geçti"
                "en" -> "Reminder: $minutes minutes have passed"
                "de" -> "Erinnerung: $minutes Minuten sind vergangen"
                else -> "Hatırlatma: $minutes dakika geçti"
            }
            ttsManager.speak(reminderMessage)
        }
        
        activeTimers.add(timerJob)
    }
    
    fun cancelAllTimers() {
        activeTimers.forEach { it.cancel() }
        activeTimers.clear()
    }
}

