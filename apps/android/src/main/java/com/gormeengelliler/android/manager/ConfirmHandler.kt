package com.gormeengelliler.android.manager

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConfirmHandler(
    private val context: Context,
    private val menuManager: MenuManager,
    private val ttsManager: TTSManager
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val apiClient = APIClient(context)
    
    // Generic confirm method - KRİTİK
    fun confirm(themeIndex: Int, mainIndex: Int, subIndex: Int) {
        scope.launch {
            // Mevcut TTS'i anında kes
            ttsManager.stop()
            
            // İşlem başlarken "yapılıyor" mesajı TTS
            val processingMessage = getProcessingMessage(themeIndex, mainIndex, subIndex)
            ttsManager.speak(processingMessage)
            
            // İşlem tipine göre routing
            when {
                // Tema 1 (Saat & Zaman) - Saat Bilgisi
                themeIndex == 1 && mainIndex == 0 && subIndex == 0 -> {
                    handleTimeRequest()
                }
                // Tema 1 (Saat & Zaman) - Tarih Bilgisi
                themeIndex == 1 && mainIndex == 0 && subIndex == 1 -> {
                    handleDateRequest()
                }
                // Tema 0 (Ulaşım) - Şoföre Bildir
                themeIndex == 0 && mainIndex == 2 && subIndex == 3 -> {
                    handleDriverNotification()
                }
                // Tema 2 (Hava Durumu) - Bugün Sıcaklık
                themeIndex == 2 && mainIndex == 0 && subIndex == 0 -> {
                    handleWeatherRequest(day = "today", type = "temperature")
                }
                // Tema 2 (Hava Durumu) - Bugün Hava Durumu
                themeIndex == 2 && mainIndex == 0 && subIndex == 1 -> {
                    handleWeatherRequest(day = "today", type = "condition")
                }
                // Tema 2 (Hava Durumu) - Yarın Sıcaklık
                themeIndex == 2 && mainIndex == 1 && subIndex == 0 -> {
                    handleWeatherRequest(day = "tomorrow", type = "temperature")
                }
                // Tema 2 (Hava Durumu) - Yarın Hava Durumu
                themeIndex == 2 && mainIndex == 1 && subIndex == 1 -> {
                    handleWeatherRequest(day = "tomorrow", type = "condition")
                }
                // Diğer işlemler için genel handler
                else -> {
                    handleGenericAction(themeIndex, mainIndex, subIndex)
                }
            }
        }
    }
    
    private fun getProcessingMessage(themeIndex: Int, mainIndex: Int, subIndex: Int): String {
        val language = menuManager.getSelectedLanguage()
        return when (language) {
            "tr" -> "İşlem yapılıyor..."
            "en" -> "Processing..."
            "de" -> "Wird verarbeitet..."
            else -> "İşlem yapılıyor..."
        }
    }
    
    private suspend fun handleTimeRequest() {
        val language = menuManager.getSelectedLanguage()
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val time = timeFormat.format(Date())
        
        val message = when (language) {
            "tr" -> "Saat $time"
            "en" -> "The time is $time"
            "de" -> "Es ist $time Uhr"
            else -> "Saat $time"
        }
        
        ttsManager.speak(message)
    }
    
    private suspend fun handleDateRequest() {
        val language = menuManager.getSelectedLanguage()
        val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
        val date = dateFormat.format(Date())
        
        val message = when (language) {
            "tr" -> "Tarih $date"
            "en" -> "Today is $date"
            "de" -> "Heute ist der $date"
            else -> "Tarih $date"
        }
        
        ttsManager.speak(message)
    }
    
    private suspend fun handleDriverNotification() {
        val language = menuManager.getSelectedLanguage()
        
        // REST API çağrısı
        val result = apiClient.notifyDriver()
        
        val message = if (result.success) {
            when (language) {
                "tr" -> "Şoföre bilgi verildi"
                "en" -> "Driver has been notified"
                "de" -> "Fahrer wurde benachrichtigt"
                else -> "Şoföre bilgi verildi"
            }
        } else {
            when (language) {
                "tr" -> "Bağlantı hatası, lütfen tekrar deneyin"
                "en" -> "Connection error, please try again"
                "de" -> "Verbindungsfehler, bitte versuchen Sie es erneut"
                else -> "Bağlantı hatası, lütfen tekrar deneyin"
            }
        }
        
        ttsManager.speak(message)
    }
    
    private suspend fun handleWeatherRequest(day: String, type: String) {
        val language = menuManager.getSelectedLanguage()
        
        // REST API çağrısı
        val result = apiClient.getWeather(day, type)
        
        val message = if (result.success) {
            result.message ?: when (language) {
                "tr" -> "Hava durumu bilgisi alınamadı"
                "en" -> "Weather information could not be retrieved"
                "de" -> "Wetterinformationen konnten nicht abgerufen werden"
                else -> "Hava durumu bilgisi alınamadı"
            }
        } else {
            when (language) {
                "tr" -> "Hava durumu bilgisi alınamadı"
                "en" -> "Weather information could not be retrieved"
                "de" -> "Wetterinformationen konnten nicht abgerufen werden"
                else -> "Hava durumu bilgisi alınamadı"
            }
        }
        
        ttsManager.speak(message)
    }
    
    private suspend fun handleGenericAction(themeIndex: Int, mainIndex: Int, subIndex: Int) {
        val language = menuManager.getSelectedLanguage()
        val message = when (language) {
            "tr" -> "Bu özellik yakında eklenecek"
            "en" -> "This feature will be added soon"
            "de" -> "Diese Funktion wird bald hinzugefügt"
            else -> "Bu özellik yakında eklenecek"
        }
        
        ttsManager.speak(message)
    }
}

// API Client for REST API calls
class APIClient(private val context: Context) {
    private val client = OkHttpClient()
    private val baseUrl = "https://api.example.com" // TODO: Gerçek API URL'i
    
    suspend fun notifyDriver(): APIResult {
        return try {
            val request = Request.Builder()
                .url("$baseUrl/api/driver/notify")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = client.newCall(request).execute()
            APIResult(success = response.isSuccessful)
        } catch (e: Exception) {
            APIResult(success = false)
        }
    }
    
    suspend fun getWeather(day: String, type: String): APIResult {
        return try {
            val request = Request.Builder()
                .url("$baseUrl/api/weather/$day?type=$type")
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (response.isSuccessful && responseBody != null) {
                // JSON parse ve mesaj oluştur
                val json = org.json.JSONObject(responseBody)
                val message = json.optString("message", "")
                APIResult(success = true, message = message)
            } else {
                APIResult(success = false)
            }
        } catch (e: Exception) {
            APIResult(success = false)
        }
    }
}

data class APIResult(
    val success: Boolean,
    val message: String? = null
)

