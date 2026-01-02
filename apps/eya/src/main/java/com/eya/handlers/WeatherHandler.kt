package com.eya.handlers

import android.content.Context
import com.eya.TTSManager
import com.eya.utils.GeminiClient
import com.eya.utils.WeatherClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WeatherHandler(private val context: Context) {
    private val weatherClient = WeatherClient()
    private val geminiClient = GeminiClient()
    private val scope = CoroutineScope(Dispatchers.Main)
    
    fun handleWeather(
        subIndex: Int,
        latitude: Double,
        longitude: Double,
        ttsManager: TTSManager,
        language: String,
        onError: (String) -> Unit
    ) {
        val days = when (subIndex) {
            0 -> 1  // Bugün
            1 -> 2  // Yarın
            2 -> 7  // Bu Hafta
            3 -> 14 // Önümüzdeki Hafta
            else -> 1
        }
        
        scope.launch {
            try {
                val weatherData = weatherClient.getWeather(latitude, longitude, days)
                if (weatherData != null) {
                    val naturalized = geminiClient.naturalizeWeatherData(weatherData, language)
                    val finalText = naturalized ?: weatherData
                    ttsManager.speak(finalText, language)
                } else {
                    val errorMsg = if (language == "tr") {
                        "Hava durumu bilgisi alınamadı"
                    } else {
                        "Could not get weather information"
                    }
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = if (language == "tr") {
                    "Hava durumu bilgisi alınırken hata oluştu"
                } else {
                    "Error getting weather information"
                }
                onError(errorMsg)
            }
        }
    }
}

