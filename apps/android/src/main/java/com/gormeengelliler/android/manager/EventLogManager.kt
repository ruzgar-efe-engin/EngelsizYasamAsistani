package com.gormeengelliler.android.manager

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Global event log manager - TTS ve STT işlemlerini loglar
 * SetupScreen'deki event log ekranına yazmak için kullanılır
 */
object EventLogManager {
    private val listeners = CopyOnWriteArrayList<(String) -> Unit>()
    private val maxLogEntries = 200
    private val mainHandler = Handler(Looper.getMainLooper())
    
    /**
     * Event log listener ekle (SetupScreen'den çağrılır)
     */
    fun addListener(listener: (String) -> Unit) {
        listeners.add(listener)
    }
    
    /**
     * Event log listener kaldır
     */
    fun removeListener(listener: (String) -> Unit) {
        listeners.remove(listener)
    }
    
    /**
     * Log mesajı gönder (hem logcat'e hem de event log ekranına)
     */
    private fun logMessage(tag: String, message: String, logcatLevel: String = "d") {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val logLine = "[$timestamp] [$tag] $message"
        
        // Logcat'e yaz (her thread'den güvenli)
        when (logcatLevel.lowercase()) {
            "e" -> Log.e(tag, message)
            "w" -> Log.w(tag, message)
            "i" -> Log.i(tag, message)
            else -> Log.d(tag, message)
        }
        
        // Event log ekranına yaz (UI thread'inde)
        mainHandler.post {
            listeners.forEach { listener ->
                try {
                    listener(logLine)
                } catch (e: Exception) {
                    Log.e("EventLogManager", "Listener hatası: ${e.message}", e)
                }
            }
        }
    }
    
    /**
     * TTS log mesajı
     */
    fun logTTS(step: String, details: String = "", isError: Boolean = false) {
        val message = "TTS: $step${if (details.isNotEmpty()) " - $details" else ""}"
        logMessage("TTS", message, if (isError) "e" else "d")
    }
    
    /**
     * STT log mesajı
     */
    fun logSTT(step: String, details: String = "", isError: Boolean = false) {
        val message = "STT: $step${if (details.isNotEmpty()) " - $details" else ""}"
        logMessage("STT", message, if (isError) "e" else "d")
    }
    
    /**
     * Menu log mesajı
     */
    fun logMenu(step: String, details: String = "", isError: Boolean = false) {
        val message = "MENU: $step${if (details.isNotEmpty()) " - $details" else ""}"
        logMessage("MENU", message, if (isError) "e" else "d")
    }
    
    /**
     * Genel log mesajı
     */
    fun log(tag: String, message: String, isError: Boolean = false) {
        logMessage(tag, message, if (isError) "e" else "d")
    }
}

