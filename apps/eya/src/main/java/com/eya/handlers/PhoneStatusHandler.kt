package com.eya.handlers

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import com.eya.TTSManager

class PhoneStatusHandler(private val context: Context) {
    
    fun handleBatteryLevel(ttsManager: TTSManager, language: String) {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            context.registerReceiver(null, filter)
        }
        
        val level = batteryStatus?.let { intent ->
            val batteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val batteryScale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (batteryLevel >= 0 && batteryScale > 0) {
                (batteryLevel * 100 / batteryScale)
            } else {
                null
            }
        }
        
        if (level != null) {
            val text = if (language == "tr") {
                "Şarjınız yüzde $level"
            } else {
                "Battery level is $level percent"
            }
            ttsManager.speak(text, language)
        } else {
            val text = if (language == "tr") {
                "Şarj bilgisi alınamadı"
            } else {
                "Could not get battery information"
            }
            ttsManager.speak(text, language)
        }
    }
    
    fun handleInternetStatus(ttsManager: TTSManager, language: String) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val isConnected = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork
            if (network != null) {
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                capabilities != null && (
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                )
            } else {
                false
            }
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            networkInfo != null && networkInfo.isConnected
        }
        
        val text = if (isConnected) {
            if (language == "tr") {
                "İnternet bağlantısı var"
            } else {
                "Internet connection available"
            }
        } else {
            if (language == "tr") {
                "İnternet bağlantısı yok"
            } else {
                "No internet connection"
            }
        }
        ttsManager.speak(text, language)
    }
    
    fun handleSilentMode(ttsManager: TTSManager, language: String) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val ringerMode = audioManager.ringerMode
        
        val text = when (ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> {
                if (language == "tr") "Telefon sessiz modda" else "Phone is in silent mode"
            }
            AudioManager.RINGER_MODE_VIBRATE -> {
                if (language == "tr") "Telefon titreşim modunda" else "Phone is in vibrate mode"
            }
            else -> {
                if (language == "tr") "Telefon normal modda" else "Phone is in normal mode"
            }
        }
        ttsManager.speak(text, language)
    }
}

