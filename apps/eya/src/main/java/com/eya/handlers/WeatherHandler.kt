package com.eya.handlers

import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.core.content.ContextCompat
import com.eya.TTSManager
import com.eya.utils.WeatherClient
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

class WeatherHandler(private val context: Context) {
    private val weatherClient = WeatherClient()
    private val scope = CoroutineScope(Dispatchers.Main)
    private val fusedLocationClient: FusedLocationProviderClient = 
        LocationServices.getFusedLocationProviderClient(context)
    
    fun handleWeather(
        subIndex: Int,
        ttsManager: TTSManager,
        language: String,
        onError: (String) -> Unit
    ) {
        // İzin kontrolü
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != 
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) != 
            PackageManager.PERMISSION_GRANTED) {
            val text = if (language == "tr") "Konum izni gerekli" else "Location permission required"
            ttsManager.speak(text, language)
            return
        }
        val days = when (subIndex) {
            0 -> 1  // Bugün
            1 -> 2  // Yarın
            2 -> 7  // Bu Hafta
            3 -> 14 // Önümüzdeki Hafta
            else -> 1
        }
        
        scope.launch {
            try {
                // Önce konum al
                val location = fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    null
                ).await()
                
                if (location == null) {
                    onError(if (language == "tr") "Konum alınamadı" else "Could not get location")
                    return@launch
                }
                
                // Şehir ve semt bilgisini al
                val geocoder = Geocoder(context, Locale.getDefault())
                val addresses = geocoder.getFromLocation(
                    location.latitude,
                    location.longitude,
                    1
                )
                
                val cityName = addresses?.firstOrNull()?.let { addr ->
                    addr.locality ?: addr.adminArea ?: addr.subAdminArea
                } ?: ""
                val districtName = addresses?.firstOrNull()?.subLocality ?: ""
                
                // Hava durumu al
                val weatherData = weatherClient.getWeather(location.latitude, location.longitude, days)
                if (weatherData != null) {
                    val finalText = if (cityName.isNotEmpty()) {
                        if (language == "tr") {
                            "$cityName${if (districtName.isNotEmpty()) " $districtName" else ""} için $weatherData"
                        } else {
                            "$weatherData for $cityName${if (districtName.isNotEmpty()) ", $districtName" else ""}"
                        }
                    } else {
                        weatherData
                    }
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

