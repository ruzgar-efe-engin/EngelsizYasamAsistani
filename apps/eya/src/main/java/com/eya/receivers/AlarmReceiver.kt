package com.eya.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.eya.TTSManager

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmTime = intent.getStringExtra("alarm_time") ?: ""
        val ttsManager = TTSManager(context)
        
        // Alarm çaldığında TTS ile bildirim yap
        val message = "Alarm çaldı, saat $alarmTime"
        ttsManager.speak(message, "tr")
    }
}

