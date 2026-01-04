package com.eya.handlers

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.eya.TTSManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CommunicationHandler(private val context: Context) {
    
    fun handleCall112(ttsManager: TTSManager, language: String) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) != 
            PackageManager.PERMISSION_GRANTED) {
            val text = if (language == "tr") "Arama izni gerekli" else "Call permission required"
            ttsManager.speak(text, language)
            return
        }
        
        try {
            // Wake lock al (ekran kapalıysa aç)
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "EYA::EmergencyCall"
            )
            wakeLock.acquire(10000) // 10 saniye
            
            // Keyguard'ı kaldır (Android 5.0+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                    val activity = context as? android.app.Activity
                    if (activity != null) {
                        keyguardManager.requestDismissKeyguard(activity, null)
                    }
                }
            }
            
            // ACTION_CALL yerine ACTION_DIAL kullan (daha güvenilir)
            // Çünkü ACTION_CALL bazı cihazlarda Google ses servislerini açıyor
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:112")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                       Intent.FLAG_ACTIVITY_CLEAR_TOP or
                       Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            
            context.startActivity(intent)
            
            // Wake lock'ı serbest bırak (10 saniye sonra otomatik)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (wakeLock.isHeld) {
                    wakeLock.release()
                }
            }, 10000)
            // Accessibility Service otomatik olarak "Ara" butonuna tıklayacak
            val text = if (language == "tr") {
                "112 aranıyor. Erişilebilirlik servisi aktifse otomatik arama başlatılacak"
            } else {
                "Calling 112. If accessibility service is active, call will start automatically"
            }
            ttsManager.speak(text, language)
        } catch (e: android.content.ActivityNotFoundException) {
            // ACTION_CALL desteklenmiyorsa ACTION_DIAL kullan
            try {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:112")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                // Accessibility Service otomatik olarak "Ara" butonuna tıklayacak
                val text = if (language == "tr") {
                    "112 numarası tuşlandı. Erişilebilirlik servisi aktifse otomatik arama başlatılacak"
                } else {
                    "112 number dialed. If accessibility service is active, call will start automatically"
                }
                ttsManager.speak(text, language)
            } catch (e2: Exception) {
                val text = if (language == "tr") {
                    "Arama başlatılamadı. Lütfen manuel olarak 112'yi arayın"
                } else {
                    "Could not start call. Please dial 112 manually"
                }
                ttsManager.speak(text, language)
            }
        } catch (e: Exception) {
            // Bazı cihazlarda ACTION_CALL direkt arama yapmak yerine dialer açabilir
            // Accessibility Service otomatik olarak "Ara" butonuna tıklayacak
            val text = if (language == "tr") {
                "112 numarası tuşlandı. Erişilebilirlik servisi aktifse otomatik arama başlatılacak"
            } else {
                "112 number dialed. If accessibility service is active, call will start automatically"
            }
            ttsManager.speak(text, language)
        }
    }
    
    fun handleCallLastCaller(ttsManager: TTSManager, language: String) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALL_LOG) != 
            PackageManager.PERMISSION_GRANTED) {
            val text = if (language == "tr") "Arama geçmişi izni gerekli" else "Call log permission required"
            ttsManager.speak(text, language)
            return
        }
        
        val cursor: Cursor? = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER),
            null,
            null,
            "${CallLog.Calls.DATE} DESC LIMIT 1"
        )
        
        cursor?.use {
            if (it.moveToFirst()) {
                val number = it.getString(it.getColumnIndexOrThrow(CallLog.Calls.NUMBER))
                val intent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:$number")
                }
                
                if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) == 
                    PackageManager.PERMISSION_GRANTED) {
                    context.startActivity(intent)
                    val text = if (language == "tr") "Son arayan aranıyor" else "Calling last caller"
                    ttsManager.speak(text, language)
                } else {
                    val text = if (language == "tr") "Arama izni gerekli" else "Call permission required"
                    ttsManager.speak(text, language)
                }
            } else {
                val text = if (language == "tr") "Arama geçmişi bulunamadı" else "No call history found"
                ttsManager.speak(text, language)
            }
        }
    }
    
    fun handleCallByName(
        transcribedText: String,
        ttsManager: TTSManager,
        language: String
    ) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) != 
            PackageManager.PERMISSION_GRANTED) {
            val text = if (language == "tr") "Rehber izni gerekli" else "Contacts permission required"
            ttsManager.speak(text, language)
            return
        }
        
        val cursor: Cursor? = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$transcribedText%"),
            null
        )
        
        cursor?.use {
            if (it.moveToFirst()) {
                val name = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                val number = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                
                val intent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:$number")
                }
                
                if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) == 
                    PackageManager.PERMISSION_GRANTED) {
                    context.startActivity(intent)
                    val text = if (language == "tr") "$name aranıyor" else "Calling $name"
                    ttsManager.speak(text, language)
                } else {
                    val text = if (language == "tr") "Arama izni gerekli" else "Call permission required"
                    ttsManager.speak(text, language)
                }
            } else {
                val text = if (language == "tr") "Kişi bulunamadı" else "Contact not found"
                ttsManager.speak(text, language)
            }
        }
    }
    
    fun handleCallByNumber(
        transcribedText: String,
        ttsManager: TTSManager,
        language: String
    ) {
        // Basit numara parse etme (sadece rakamları al)
        val number = transcribedText.filter { it.isDigit() }
        
        if (number.isNotEmpty()) {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$number")
            }
            
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) == 
                PackageManager.PERMISSION_GRANTED) {
                context.startActivity(intent)
                val text = if (language == "tr") "Aranıyor" else "Calling"
                ttsManager.speak(text, language)
            } else {
                val text = if (language == "tr") "Arama izni gerekli" else "Call permission required"
                ttsManager.speak(text, language)
            }
        } else {
            val text = if (language == "tr") "Numara anlaşılamadı" else "Could not understand number"
            ttsManager.speak(text, language)
        }
    }
}

