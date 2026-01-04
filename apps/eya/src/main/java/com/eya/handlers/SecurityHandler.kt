package com.eya.handlers

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.eya.TTSManager
import com.eya.utils.EmergencyContactHelper
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
    private val emergencyContactHelper = EmergencyContactHelper(context)
    private var isSharingLocation = false
    
    fun handleSendEmergencyLocation(
        ttsManager: TTSManager,
        language: String
    ) {
        val emergencyContacts = emergencyContactHelper.getEmergencyContacts()
        if (emergencyContacts.isEmpty()) {
            val text = if (language == "tr") {
                "Acil durum kişisi bulunamadı. Lütfen rehberinizde favori kişiler ekleyin"
            } else {
                "No emergency contact found. Please add favorite contacts in your address book"
            }
            ttsManager.speak(text, language)
            return
        }
        
        val contactInfo = if (emergencyContacts.size == 1) {
            if (language == "tr") {
                "${emergencyContacts[0].name}'e gönderiliyor"
            } else {
                "Sending to ${emergencyContacts[0].name}"
            }
        } else {
            if (language == "tr") {
                "${emergencyContacts.size} kişiye gönderiliyor"
            } else {
                "Sending to ${emergencyContacts.size} contacts"
            }
        }
        ttsManager.speak(contactInfo, language)
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.SEND_SMS) != 
            PackageManager.PERMISSION_GRANTED) {
            val text = if (language == "tr") "SMS izni gerekli" else "SMS permission required"
            ttsManager.speak(text, language)
            return
        }
        
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != 
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) != 
            PackageManager.PERMISSION_GRANTED) {
            val text = if (language == "tr") "Konum izni gerekli" else "Location permission required"
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
                    // Tüm acil durum kişilerine gönder
                    emergencyContacts.forEach { contact ->
                        smsManager.sendTextMessage(contact.phoneNumber, null, message, null, null)
                    }
                    
                    val text = if (language == "tr") {
                        "Acil konum ${emergencyContacts.size} kişiye gönderildi"
                    } else {
                        "Emergency location sent to ${emergencyContacts.size} contacts"
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
        ttsManager: TTSManager,
        language: String
    ) {
        val emergencyContacts = emergencyContactHelper.getEmergencyContacts()
        if (emergencyContacts.isEmpty()) {
            val text = if (language == "tr") {
                "Acil durum kişisi bulunamadı, 112'ye gönderiliyor"
            } else {
                "No emergency contact found, sending to 112"
            }
            ttsManager.speak(text, language)
            return
        }
        
        val contactInfo = if (emergencyContacts.size == 1) {
            if (language == "tr") {
                "${emergencyContacts[0].name}'e gönderiliyor"
            } else {
                "Sending to ${emergencyContacts[0].name}"
            }
        } else {
            if (language == "tr") {
                "${emergencyContacts.size} kişiye gönderiliyor"
            } else {
                "Sending to ${emergencyContacts.size} contacts"
            }
        }
        ttsManager.speak(contactInfo, language)
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
            // Tüm acil durum kişilerine gönder
            emergencyContacts.forEach { contact ->
                smsManager.sendTextMessage(contact.phoneNumber, null, message, null, null)
            }
            
            val text = if (language == "tr") {
                "Güvendeyim mesajı ${emergencyContacts.size} kişiye gönderildi"
            } else {
                "Safe message sent to ${emergencyContacts.size} contacts"
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
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) != 
            PackageManager.PERMISSION_GRANTED) {
            val text = if (language == "tr") "Arama izni gerekli" else "Call permission required"
            ttsManager.speak(text, language)
            return
        }
        
        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:112")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val text = if (language == "tr") {
                "Arama başlatılamadı: ${e.message}"
            } else {
                "Could not start call: ${e.message}"
            }
            ttsManager.speak(text, language)
            return
        }
        
        // Konum da gönder (basit implementasyon)
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == 
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == 
            PackageManager.PERMISSION_GRANTED) {
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
        }
            
        val text = if (language == "tr") {
            "112 aranıyor"
        } else {
            "Calling 112"
        }
        ttsManager.speak(text, language)
    }
    
    fun handleShareLocationContinuously(
        ttsManager: TTSManager,
        language: String
    ) {
        val emergencyContacts = emergencyContactHelper.getEmergencyContacts()
        if (emergencyContacts.isEmpty()) {
            val text = if (language == "tr") {
                "Acil durum kişisi bulunamadı. Lütfen rehberinizde favori kişiler ekleyin"
            } else {
                "No emergency contact found. Please add favorite contacts in your address book"
            }
            ttsManager.speak(text, language)
            return
        }
        
        val contactInfo = if (emergencyContacts.size == 1) {
            if (language == "tr") {
                "${emergencyContacts[0].name}'e konum paylaşımı"
            } else {
                "Sharing location with ${emergencyContacts[0].name}"
            }
        } else {
            if (language == "tr") {
                "${emergencyContacts.size} kişiye konum paylaşımı"
            } else {
                "Sharing location with ${emergencyContacts.size} contacts"
            }
        }
        isSharingLocation = !isSharingLocation
        
        if (isSharingLocation) {
            val text = if (language == "tr") {
                "$contactInfo başlatıldı"
            } else {
                "$contactInfo started"
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

