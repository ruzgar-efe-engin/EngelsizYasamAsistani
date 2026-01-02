package com.eya.handlers

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.eya.TTSManager
import com.eya.utils.SpeechToTextManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CommunicationHandler(private val context: Context) {
    private val sttManager = SpeechToTextManager(context)
    
    fun handleCall112(ttsManager: TTSManager, language: String) {
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:112")
        }
        
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) == 
            PackageManager.PERMISSION_GRANTED) {
            context.startActivity(intent)
            val text = if (language == "tr") "112 aranıyor" else "Calling 112"
            ttsManager.speak(text, language)
        } else {
            val text = if (language == "tr") "Arama izni gerekli" else "Call permission required"
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

