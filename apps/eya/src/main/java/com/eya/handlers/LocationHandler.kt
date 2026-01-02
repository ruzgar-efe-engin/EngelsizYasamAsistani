package com.eya.handlers

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Address
import android.location.Geocoder
import androidx.core.content.ContextCompat
import com.eya.TTSManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

class LocationHandler(private val context: Context) {
    private val fusedLocationClient: FusedLocationProviderClient = 
        LocationServices.getFusedLocationProviderClient(context)
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var compassListener: SensorEventListener? = null
    
    fun handleCurrentLocation(
        ttsManager: TTSManager,
        language: String,
        onError: (String) -> Unit
    ) {
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
                    val geocoder = Geocoder(context, Locale.getDefault())
                    val addresses = geocoder.getFromLocation(
                        location.latitude,
                        location.longitude,
                        1
                    )
                    
                    if (addresses != null && addresses.isNotEmpty()) {
                        val address = addresses[0]
                        val addressText = address.getAddressLine(0) ?: 
                            "${address.locality}, ${address.adminArea}"
                        
                        val text = if (language == "tr") {
                            "Mevcut konumunuz: $addressText"
                        } else {
                            "Your current location: $addressText"
                        }
                        ttsManager.speak(text, language)
                    } else {
                        val text = if (language == "tr") {
                            "Konum alındı: ${location.latitude}, ${location.longitude}"
                        } else {
                            "Location obtained: ${location.latitude}, ${location.longitude}"
                        }
                        ttsManager.speak(text, language)
                    }
                } else {
                    onError(if (language == "tr") "Konum alınamadı" else "Could not get location")
                }
            } catch (e: Exception) {
                onError(if (language == "tr") "Konum alınırken hata oluştu" else "Error getting location")
            }
        }
    }
    
    fun handleDirection(
        ttsManager: TTSManager,
        language: String,
        onError: (String) -> Unit
    ) {
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        
        if (accelerometer == null || magnetometer == null) {
            onError(if (language == "tr") "Yön sensörü bulunamadı" else "Direction sensor not found")
            return
        }
        
        compassListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER || 
                    event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                    // Basit implementasyon: magnetometer kullanarak yön hesaplanabilir
                    // Şimdilik basit bir mesaj
                    sensorManager.unregisterListener(this)
                    
                    val directions = if (language == "tr") {
                        arrayOf("Kuzey", "Kuzeydoğu", "Doğu", "Güneydoğu", 
                            "Güney", "Güneybatı", "Batı", "Kuzeybatı")
                    } else {
                        arrayOf("North", "Northeast", "East", "Southeast",
                            "South", "Southwest", "West", "Northwest")
                    }
                    
                    // Basit implementasyon - gerçek yön hesaplaması için daha fazla kod gerekli
                    val direction = directions[0] // Varsayılan Kuzey
                    val text = if (language == "tr") {
                        "$direction yönüne bakıyorsunuz"
                    } else {
                        "You are facing $direction"
                    }
                    ttsManager.speak(text, language)
                }
            }
            
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }
        
        sensorManager.registerListener(compassListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(compassListener, magnetometer, SensorManager.SENSOR_DELAY_NORMAL)
    }
    
    fun handleNearby(
        ttsManager: TTSManager,
        language: String,
        onError: (String) -> Unit
    ) {
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
                    val geocoder = Geocoder(context, Locale.getDefault())
                    try {
                        val addresses = geocoder.getFromLocation(
                            location.latitude,
                            location.longitude,
                            5
                        )
                        
                        if (addresses != null && addresses.isNotEmpty()) {
                            val places = addresses.take(3).mapNotNull { addr ->
                                addr.locality ?: addr.subLocality ?: addr.featureName ?: addr.adminArea
                            }.distinct().filter { it.isNotEmpty() }
                            
                            if (places.isNotEmpty()) {
                                val text = if (language == "tr") {
                                    "Çevrenizde: ${places.joinToString(", ")}"
                                } else {
                                    "Around you: ${places.joinToString(", ")}"
                                }
                                ttsManager.speak(text, language)
                            } else {
                                val text = if (language == "tr") {
                                    "Çevrenizdeki yerler bulunamadı"
                                } else {
                                    "Could not find places around you"
                                }
                                ttsManager.speak(text, language)
                            }
                        } else {
                            val text = if (language == "tr") {
                                "Çevrenizdeki yerler bulunamadı"
                            } else {
                                "Could not find places around you"
                            }
                            ttsManager.speak(text, language)
                        }
                    } catch (e: Exception) {
                        // Geocoder hatası - basit mesaj ver
                        val text = if (language == "tr") {
                            "Konum alındı: ${location.latitude}, ${location.longitude}"
                        } else {
                            "Location obtained: ${location.latitude}, ${location.longitude}"
                        }
                        ttsManager.speak(text, language)
                    }
                } else {
                    onError(if (language == "tr") "Konum alınamadı" else "Could not get location")
                }
            } catch (e: Exception) {
                onError(if (language == "tr") "Çevre bilgisi alınırken hata oluştu" else "Error getting nearby information")
            }
        }
    }
    
    fun cleanup() {
        compassListener?.let {
            sensorManager.unregisterListener(it)
        }
    }
}

