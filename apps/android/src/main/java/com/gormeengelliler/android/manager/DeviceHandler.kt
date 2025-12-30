package com.gormeengelliler.android.manager

import android.content.Context
import android.media.AudioManager
import android.os.BatteryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DeviceHandler(
    private val context: Context,
    private val menuManager: MenuManager,
    private val ttsManager: TTSManager
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    
    suspend fun handleSoundSettings(action: String) {
        val language = menuManager.getSelectedLanguage()
        when (action.lowercase()) {
            "aç", "on", "ein" -> {
                audioManager.setStreamMute(AudioManager.STREAM_MUSIC, false)
                val message = when (language) {
                    "tr" -> "Ses açıldı"
                    "en" -> "Sound turned on"
                    "de" -> "Ton eingeschaltet"
                    else -> "Ses açıldı"
                }
                ttsManager.speak(message)
            }
            "kapat", "off", "aus" -> {
                audioManager.setStreamMute(AudioManager.STREAM_MUSIC, true)
                val message = when (language) {
                    "tr" -> "Ses kapatıldı"
                    "en" -> "Sound turned off"
                    "de" -> "Ton ausgeschaltet"
                    else -> "Ses kapatıldı"
                }
                ttsManager.speak(message)
            }
            "kıs", "lower", "leiser" -> {
                val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val newVolume = (currentVolume * 0.7).toInt()
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                val message = when (language) {
                    "tr" -> "Ses kısıldı"
                    "en" -> "Sound lowered"
                    "de" -> "Ton leiser gemacht"
                    else -> "Ses kısıldı"
                }
                ttsManager.speak(message)
            }
        }
    }
    
    suspend fun handleDeviceBatteryLevel() {
        val language = menuManager.getSelectedLanguage()
        // TODO: BLE device'tan pil seviyesi alma
        val message = when (language) {
            "tr" -> "Cihazınızın şarjı %43"
            "en" -> "Your device battery is at 43%"
            "de" -> "Ihr Gerät hat 43% Batterie"
            else -> "Cihazınızın şarjı %43"
        }
        ttsManager.speak(message)
    }
    
    suspend fun handlePhoneBatteryLevel() {
        val language = menuManager.getSelectedLanguage()
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        
        val message = when (language) {
            "tr" -> "Telefonunuzun şarjı %$batteryLevel"
            "en" -> "Your phone battery is at $batteryLevel%"
            "de" -> "Ihr Telefon hat $batteryLevel% Batterie"
            else -> "Telefonunuzun şarjı %$batteryLevel"
        }
        ttsManager.speak(message)
    }
}

