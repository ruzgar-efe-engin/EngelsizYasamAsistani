package com.gormeengelliler.android.manager

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EmergencyHandler(
    private val context: Context,
    private val menuManager: MenuManager,
    private val ttsManager: TTSManager
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    
    suspend fun handleEmergencyCall() {
        val language = menuManager.getSelectedLanguage()
        val message = when (language) {
            "tr" -> "112 aranıyor..."
            "en" -> "Calling 112..."
            "de" -> "112 wird angerufen..."
            else -> "112 aranıyor..."
        }
        ttsManager.speak(message)
        
        // 112'yi ara
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:112")
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            val errorMessage = when (language) {
                "tr" -> "Arama yapılamadı"
                "en" -> "Could not make call"
                "de" -> "Anruf konnte nicht getätigt werden"
                else -> "Arama yapılamadı"
            }
            ttsManager.speak(errorMessage)
        }
    }
    
    suspend fun handleShareLocation() {
        val language = menuManager.getSelectedLanguage()
        // TODO: Konum paylaşma implementasyonu
        val message = when (language) {
            "tr" -> "Konum paylaşılıyor..."
            "en" -> "Sharing location..."
            "de" -> "Standort wird geteilt..."
            else -> "Konum paylaşılıyor..."
        }
        ttsManager.speak(message)
    }
    
    suspend fun handleSendToEmergencyContact() {
        val language = menuManager.getSelectedLanguage()
        // TODO: Acil kişiye gönderme implementasyonu
        val message = when (language) {
            "tr" -> "Acil kişiye gönderiliyor..."
            "en" -> "Sending to emergency contact..."
            "de" -> "An Notfallkontakt wird gesendet..."
            else -> "Acil kişiye gönderiliyor..."
        }
        ttsManager.speak(message)
    }
    
    suspend fun handleAlarmSound() {
        val language = menuManager.getSelectedLanguage()
        // TODO: Sesli alarm implementasyonu
        val message = when (language) {
            "tr" -> "Sesli alarm çalıyor..."
            "en" -> "Sound alarm is playing..."
            "de" -> "Akustischer Alarm wird abgespielt..."
            else -> "Sesli alarm çalıyor..."
        }
        ttsManager.speak(message)
    }
    
    suspend fun handleAlarmVibration() {
        val language = menuManager.getSelectedLanguage()
        // TODO: Titreşimli alarm implementasyonu
        val message = when (language) {
            "tr" -> "Titreşimli alarm aktif..."
            "en" -> "Vibration alarm is active..."
            "de" -> "Vibrationsalarm ist aktiv..."
            else -> "Titreşimli alarm aktif..."
        }
        ttsManager.speak(message)
    }
}

