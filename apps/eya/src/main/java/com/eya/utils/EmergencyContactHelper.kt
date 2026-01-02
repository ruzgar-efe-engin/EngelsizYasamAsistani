package com.eya.utils

import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

data class EmergencyContact(
    val name: String,
    val phoneNumber: String
)

class EmergencyContactHelper(private val context: Context) {
    
    fun getEmergencyContacts(): List<EmergencyContact> {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) != 
            PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }
        
        val contacts = mutableListOf<EmergencyContact>()
        
        // Sadece favori (yıldızlı) kişileri al - en güvenli yöntem
        // IS_PRIMARY kullanma, çünkü o tüm rehberi döndürür
        try {
            val cursor: Cursor? = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.STARRED
                ),
                "${ContactsContract.CommonDataKinds.Phone.STARRED} = ?",
                arrayOf("1"),
                "${ContactsContract.CommonDataKinds.Phone.LAST_TIME_CONTACTED} DESC LIMIT 5"
            )
            
            cursor?.use {
                while (it.moveToNext() && contacts.size < 5) {
                    val name = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                    val number = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                    if (name.isNotEmpty() && number.isNotEmpty()) {
                        contacts.add(EmergencyContact(name, number))
                    }
                }
            }
        } catch (e: Exception) {
            // Hata durumunda boş liste döndür
            android.util.Log.e("EmergencyContactHelper", "Acil durum kişileri alınırken hata: ${e.message}")
        }
        
        // Eğer favori kişi yoksa, hiçbir şey döndürme (tüm rehbere göndermeyi önle)
        return contacts
    }
    
    fun getFirstEmergencyContact(): EmergencyContact? {
        return getEmergencyContacts().firstOrNull()
    }
}

