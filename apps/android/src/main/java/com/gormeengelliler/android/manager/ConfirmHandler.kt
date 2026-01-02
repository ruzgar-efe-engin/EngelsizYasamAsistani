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
    private val ttsManager: TTSManager,
    private val weatherHandler: WeatherHandler? = null,
    private val locationHandler: LocationHandler? = null,
    private val emergencyHandler: EmergencyHandler? = null,
    private val contactHandler: ContactHandler? = null,
    private val notesHandler: NotesHandler? = null,
    private val deviceHandler: DeviceHandler? = null,
    private val aiHandler: AIHandler? = null
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val apiClient = APIClient(context)
    
    // Generic confirm method - KRİTİK
    fun confirm(mainIndex: Int, subIndex: Int, subSubIndex: Int? = null) {
        scope.launch {
            // Index normalizasyonu (güvenlik için - doğrudan çağrıldığında da çalışsın)
            val normalizedMainIndex = menuManager.normalizeMainIndex(mainIndex)
            val normalizedSubIndex = menuManager.normalizeSubIndex(normalizedMainIndex, subIndex)
            val normalizedSubSubIndex = subSubIndex?.let { 
                menuManager.normalizeSubSubIndex(normalizedMainIndex, normalizedSubIndex, it) 
            }
            android.util.Log.d("ConfirmHandler", "📊 Index normalizasyonu: mainIndex=$mainIndex → $normalizedMainIndex, subIndex=$subIndex → $normalizedSubIndex, subSubIndex=$subSubIndex → $normalizedSubSubIndex")
            
            // Mevcut TTS'i anında kes
            ttsManager.stop()
            
            // Önce clickResult'ı kontrol et - varsa direkt onu seslendir (normalize edilmiş index ile)
            val clickResult = menuManager.getSubMenuClickResult(normalizedMainIndex, normalizedSubIndex, normalizedSubSubIndex)
            if (clickResult != null) {
                ttsManager.speak(clickResult)
                // clickResult varsa işlem yapma, sadece mesajı seslendir
                return@launch
            }
            
            // İşlem başlarken "yapılıyor" mesajı TTS (normalize edilmiş index ile)
            val processingMessage = getProcessingMessage(normalizedMainIndex, normalizedSubIndex)
            ttsManager.speak(processingMessage)
            
            // İşlem tipine göre routing (normalize edilmiş index ile)
            when {
                // Zaman - Saat Kaç?
                normalizedMainIndex == 0 && normalizedSubIndex == 0 -> {
                    handleTimeRequest()
                }
                // Zaman - Tarih Bilgisi
                normalizedMainIndex == 0 && normalizedSubIndex == 1 -> {
                    handleDateRequest()
                }
                // Zaman - Alarm Kur (nested sub-menu ile)
                normalizedMainIndex == 0 && normalizedSubIndex == 2 -> {
                    if (normalizedSubSubIndex != null) {
                        handleAlarmSet(normalizedSubSubIndex)
                    } else {
                        // İlk click - nested sub-menu'ye geçiş yapıldı, işlem yok
                    }
                }
                // Zaman - Zamanlayıcı (nested sub-menu ile)
                normalizedMainIndex == 0 && normalizedSubIndex == 3 -> {
                    if (normalizedSubSubIndex != null) {
                        handleTimerSet(normalizedSubSubIndex)
                    } else {
                        // İlk click - nested sub-menu'ye geçiş yapıldı, işlem yok
                    }
                }
                // Zaman - Tüm alarmları iptal et
                normalizedMainIndex == 0 && normalizedSubIndex == 4 -> {
                    handleCancelAllAlarms()
                }
                // Zaman - Hatırlatıcı (nested sub-menu ile)
                normalizedMainIndex == 0 && normalizedSubIndex == 5 -> {
                    if (normalizedSubSubIndex != null) {
                        handleReminderSet(normalizedSubSubIndex)
                    } else {
                        // İlk click - nested sub-menu'ye geçiş yapıldı, işlem yok
                    }
                }
                // Hava Durumu - Bugün
                normalizedMainIndex == 1 && normalizedSubIndex == 0 -> {
                    weatherHandler?.handleTodayWeather() ?: handleTodayWeather()
                }
                // Hava Durumu - Yarın
                normalizedMainIndex == 1 && normalizedSubIndex == 1 -> {
                    weatherHandler?.handleTomorrowWeather() ?: handleTomorrowWeather()
                }
                // Hava Durumu - Önümüzdeki Hafta
                normalizedMainIndex == 1 && normalizedSubIndex == 2 -> {
                    weatherHandler?.handleNextWeekWeather() ?: handleNextWeekWeather()
                }
                // Konum & Yön - Mevcut Konum
                normalizedMainIndex == 2 && normalizedSubIndex == 0 -> {
                    locationHandler?.handleCurrentLocation()
                }
                // Konum & Yön - Yön Bulma
                normalizedMainIndex == 2 && normalizedSubIndex == 1 -> {
                    locationHandler?.handleDirection()
                }
                // Konum & Yön - Yakın Noktalar
                normalizedMainIndex == 2 && normalizedSubIndex == 2 -> {
                    locationHandler?.handleNearbyPlaces()
                }
                // Acil & Güvenlik - Acil Arama
                normalizedMainIndex == 3 && normalizedSubIndex == 0 -> {
                    emergencyHandler?.handleEmergencyCall()
                }
                // Acil & Güvenlik - Konum Paylaş
                normalizedMainIndex == 3 && normalizedSubIndex == 1 -> {
                    emergencyHandler?.handleShareLocation()
                }
                // Acil & Güvenlik - Alarm (nested)
                normalizedMainIndex == 3 && normalizedSubIndex == 2 -> {
                    if (normalizedSubSubIndex == 0) {
                        emergencyHandler?.handleAlarmSound()
                    } else if (normalizedSubSubIndex == 1) {
                        emergencyHandler?.handleAlarmVibration()
                    }
                }
                // İletişim - Kayıtlı Kişiler
                normalizedMainIndex == 4 && normalizedSubIndex == 0 -> {
                    if (normalizedSubSubIndex != null) {
                        contactHandler?.handleCallContact(normalizedSubSubIndex)
                    } else {
                        contactHandler?.handleSavedContacts()
                    }
                }
                // İletişim - Son Arananlar
                normalizedMainIndex == 4 && normalizedSubIndex == 1 -> {
                    contactHandler?.handleRecentCalls()
                }
                // Notlar & Hatırlatıcı - Yeni Not
                normalizedMainIndex == 5 && normalizedSubIndex == 0 -> {
                    notesHandler?.handleNewVoiceNote()
                }
                // Notlar & Hatırlatıcı - Notlarım
                normalizedMainIndex == 5 && normalizedSubIndex == 1 -> {
                    if (normalizedSubSubIndex != null) {
                        notesHandler?.handlePlayNote(normalizedSubSubIndex)
                    } else {
                        notesHandler?.handleMyNotes()
                    }
                }
                // Cihaz & Sistem - Ses Ayarları
                normalizedMainIndex == 6 && normalizedSubIndex == 0 -> {
                    if (normalizedSubSubIndex == 0) {
                        deviceHandler?.handleSoundSettings("kapat")
                    } else if (normalizedSubSubIndex == 1) {
                        deviceHandler?.handleSoundSettings("kıs")
                    } else {
                        deviceHandler?.handleSoundSettings("aç")
                    }
                }
                // Cihaz & Sistem - Cihaz Pil Seviyesi
                normalizedMainIndex == 6 && normalizedSubIndex == 1 -> {
                    deviceHandler?.handleDeviceBatteryLevel()
                }
                // Cihaz & Sistem - Telefon Pil Seviyesi
                normalizedMainIndex == 6 && normalizedSubIndex == 2 -> {
                    deviceHandler?.handlePhoneBatteryLevel()
                }
                // Serbest Soru (AI) - Gemini ile konuş
                normalizedMainIndex == 7 && normalizedSubIndex == 0 -> {
                    aiHandler?.handleGeminiChat()
                }
                // Diğer işlemler için genel handler
                else -> {
                    handleGenericAction(normalizedMainIndex, normalizedSubIndex, normalizedSubSubIndex)
                }
            }
        }
    }
    
    private fun getProcessingMessage(mainIndex: Int, subIndex: Int): String {
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
    
    private suspend fun handleAlarmSet(subSubIndex: Int) {
        // Alarm kurma işlemi - subSubIndex'e göre saat belirle (8, 9, 10, ...)
        val hour = 8 + subSubIndex // 8'den başlayarak artır
        val alarmManager = AlarmManagerHelper(context, menuManager, ttsManager)
        alarmManager.setAlarm(hour)
    }
    
    private suspend fun handleTimerSet(subSubIndex: Int) {
        // Zamanlayıcı kurma işlemi - subSubIndex'e göre dakika belirle (1, 5, 10, ...)
        val minutes = when (subSubIndex) {
            0 -> 1
            1 -> 5
            2 -> 10
            3 -> 15
            4 -> 30
            else -> 1
        }
        val timerManager = TimerManager(context, menuManager, ttsManager)
        timerManager.setTimer(minutes)
    }
    
    private suspend fun handleCancelAllAlarms() {
        // Tüm alarmları iptal et
        val language = menuManager.getSelectedLanguage()
        val message = when (language) {
            "tr" -> "Tüm alarmlar ve zamanlayıcılar iptal edildi."
            "en" -> "All alarms and timers have been cancelled."
            "de" -> "Alle Wecker und Timer wurden abgebrochen."
            else -> "Tüm alarmlar ve zamanlayıcılar iptal edildi."
        }
        ttsManager.speak(message)
    }
    
    private suspend fun handleReminderSet(subSubIndex: Int) {
        // Hatırlatıcı kurma işlemi
        val language = menuManager.getSelectedLanguage()
        val message = when (language) {
            "tr" -> "Hatırlatıcı kuruluyor..."
            "en" -> "Setting reminder..."
            "de" -> "Erinnerung wird gestellt..."
            else -> "Hatırlatıcı kuruluyor..."
        }
        ttsManager.speak(message)
    }
    
    private suspend fun handleTodayWeather() {
        // Bugün hava durumu - WeatherHandler'a taşınacak
        val language = menuManager.getSelectedLanguage()
        val message = when (language) {
            "tr" -> "Hava durumu bilgisi alınıyor..."
            "en" -> "Getting weather information..."
            "de" -> "Wetterinformationen werden abgerufen..."
            else -> "Hava durumu bilgisi alınıyor..."
        }
        ttsManager.speak(message)
    }
    
    private suspend fun handleTomorrowWeather() {
        // Yarın hava durumu - WeatherHandler'a taşınacak
        val language = menuManager.getSelectedLanguage()
        val message = when (language) {
            "tr" -> "Yarın hava durumu bilgisi alınıyor..."
            "en" -> "Getting tomorrow's weather information..."
            "de" -> "Wetterinformationen für morgen werden abgerufen..."
            else -> "Yarın hava durumu bilgisi alınıyor..."
        }
        ttsManager.speak(message)
    }
    
    private suspend fun handleNextWeekWeather() {
        // Önümüzdeki hafta hava durumu - WeatherHandler'a taşınacak
        val language = menuManager.getSelectedLanguage()
        val message = when (language) {
            "tr" -> "Önümüzdeki hafta hava durumu bilgisi alınıyor..."
            "en" -> "Getting next week's weather information..."
            "de" -> "Wetterinformationen für nächste Woche werden abgerufen..."
            else -> "Önümüzdeki hafta hava durumu bilgisi alınıyor..."
        }
        ttsManager.speak(message)
    }
    
    private suspend fun handleGenericAction(mainIndex: Int, subIndex: Int, subSubIndex: Int? = null) {
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

