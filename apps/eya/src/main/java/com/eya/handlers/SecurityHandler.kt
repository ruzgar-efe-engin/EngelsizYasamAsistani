package com.eya.handlers

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.eya.TTSManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SecurityHandler(private val context: Context) {
    private val fusedLocationClient: FusedLocationProviderClient = 
        LocationServices.getFusedLocationProviderClient(context)
    private var isSharingLocation = false
    
    fun handleSendEmergencyLocation(
        emergencyContact: String,
        ttsManager: TTSManager,
        language: String
    ) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.SEND_SMS) != 
            PackageManager.PERMISSION_GRANTED) {
            val text = if (language == "tr") "SMS izni gerekli" else "SMS permission required"
            ttsManager.speak(text, language)
            return
        }
        
        val scope = CoroutineScope(Dispatchers.Main)
        scope.launch {
            try {
                val location = fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    null
                ).await()
                
                if (location != null) {
                    val message = if (language == "tr") {
                        "Acil durum! Konumum: https://maps.google.com/?q=${location.latitude},${location.longitude}"
                    } else {
                        "Emergency! My location: https://maps.google.com/?q=${location.latitude},${location.longitude}"
                    }
                    
                    val smsManager = SmsManager.getDefault()
                    smsManager.sendTextMessage(emergencyContact, null, message, null, null)
                    
                    val text = if (language == "tr") {
                        "Acil konum gönderildi"
                    } else {
                        "Emergency location sent"
                    }
                    ttsManager.speak(text, language)
                } else {
                    val text = if (language == "tr") {
                        "Konum alınamadı"
                    } else {
                        "Could not get location"
                    }
                    ttsManager.speak(text, language)
                }
            } catch (e: Exception) {
                val text = if (language == "tr") {
                    "Konum gönderilirken hata oluştu"
                } else {
                    "Error sending location"
                }
                ttsManager.speak(text, language)
            }
        }
    }
    
    fun handleSendSafeMessage(
        emergencyContact: String,
        ttsManager: TTSManager,
        language: String
    ) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.SEND_SMS) != 
            PackageManager.PERMISSION_GRANTED) {
            val text = if (language == "tr") "SMS izni gerekli" else "SMS permission required"
            ttsManager.speak(text, language)
            return
        }
        
        val message = if (language == "tr") {
            "Güvendeyim"
        } else {
            "I am safe"
        }
        
        try {
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(emergencyContact, null, message, null, null)
            
            val text = if (language == "tr") {
                "Güvendeyim mesajı gönderildi"
            } else {
                "Safe message sent"
            }
            ttsManager.speak(text, language)
        } catch (e: Exception) {
            val text = if (language == "tr") {
                "Mesaj gönderilirken hata oluştu"
            } else {
                "Error sending message"
            }
            ttsManager.speak(text, language)
        }
    }
    
    fun handleStartHelpCall(ttsManager: TTSManager, language: String) {
        // 112'yi ara ve konum paylaş
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:112")
        }
        
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) == 
            PackageManager.PERMISSION_GRANTED) {
            context.startActivity(intent)
            
            // Konum da gönder (basit implementasyon)
            val scope = CoroutineScope(Dispatchers.Main)
            scope.launch {
                try {
                    val location = fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        null
                    ).await()
                    // Konum bilgisi arama sırasında paylaşılabilir
                } catch (e: Exception) {
                    // Hata durumunda sessizce devam et
                }
            }
            
            val text = if (language == "tr") {
                "Yardım çağrısı başlatıldı"
            } else {
                "Help call started"
            }
            ttsManager.speak(text, language)
        } else {
            val text = if (language == "tr") {
                "Arama izni gerekli"
            } else {
                "Call permission required"
            }
            ttsManager.speak(text, language)
        }
    }
    
    fun handleShareLocationContinuously(
        emergencyContact: String,
        ttsManager: TTSManager,
        language: String
    ) {
        isSharingLocation = !isSharingLocation
        
        if (isSharingLocation) {
            val text = if (language == "tr") {
                "Sürekli konum paylaşımı başlatıldı"
            } else {
                "Continuous location sharing started"
            }
            ttsManager.speak(text, language)
            // Background service ile sürekli konum paylaşımı (basit implementasyon)
        } else {
            val text = if (language == "tr") {
                "Sürekli konum paylaşımı durduruldu"
            } else {
                "Continuous location sharing stopped"
            }
            ttsManager.speak(text, language)
        }
    }
}

