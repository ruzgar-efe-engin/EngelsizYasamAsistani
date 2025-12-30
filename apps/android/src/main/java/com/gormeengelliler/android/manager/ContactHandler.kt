package com.gormeengelliler.android.manager

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ContactHandler(
    private val context: Context,
    private val menuManager: MenuManager,
    private val ttsManager: TTSManager
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    
    suspend fun handleSavedContacts() {
        val language = menuManager.getSelectedLanguage()
        // TODO: ContactsContract API ile kişi listesi alma
        val message = when (language) {
            "tr" -> "Kişi 1"
            "en" -> "Contact 1"
            "de" -> "Kontakt 1"
            else -> "Kişi 1"
        }
        ttsManager.speak(message)
    }
    
    suspend fun handleCallContact(contactIndex: Int) {
        val language = menuManager.getSelectedLanguage()
        // TODO: ContactsContract API ile telefon numarası alma ve arama
        val message = when (language) {
            "tr" -> "Kişi ${contactIndex + 1} aranıyor"
            "en" -> "Calling contact ${contactIndex + 1}"
            "de" -> "Kontakt ${contactIndex + 1} wird angerufen"
            else -> "Kişi ${contactIndex + 1} aranıyor"
        }
        ttsManager.speak(message)
        
        // TODO: Gerçek telefon numarası ile arama yap
        // val intent = Intent(Intent.ACTION_CALL).apply {
        //     data = Uri.parse("tel:$phoneNumber")
        // }
        // context.startActivity(intent)
    }
    
    suspend fun handleRecentCalls() {
        val language = menuManager.getSelectedLanguage()
        // TODO: CallLog API ile son arananlar listesi
        val message = when (language) {
            "tr" -> "Son Arama 1, Son Arama 2"
            "en" -> "Last Call 1, Last Call 2"
            "de" -> "Letzter Anruf 1, Letzter Anruf 2"
            else -> "Son Arama 1, Son Arama 2"
        }
        ttsManager.speak(message)
    }
}

