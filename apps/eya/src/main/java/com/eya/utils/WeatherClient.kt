package com.eya.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class WeatherClient {
    private val client = OkHttpClient()
    // OpenWeatherMap API key - kullanıcı local.properties'e ekleyebilir veya ücretsiz key kullanabilir
    // Şimdilik basit bir implementasyon, gerçek API key gerekebilir
    private val apiKey = "" // Kullanıcı local.properties'e ekleyebilir
    
    suspend fun getWeather(latitude: Double, longitude: Double, days: Int = 1): String? {
        return withContext(Dispatchers.IO) {
            try {
                // Basit implementasyon: OpenWeatherMap API kullanılabilir
                // Şimdilik mock data döndürüyoruz, gerçek implementasyon için API key gerekli
                if (apiKey.isEmpty()) {
                    // Mock data
                    return@withContext when (days) {
                        1 -> "Bugün hava 15 derece, parçalı bulutlu"
                        2 -> "Yarın hava 18 derece, güneşli"
                        7 -> "Bu hafta hava genellikle 15-20 derece arası, parçalı bulutlu"
                        14 -> "Önümüzdeki hafta hava 16-22 derece arası, güneşli ve parçalı bulutlu"
                        else -> "Hava durumu bilgisi alınamadı"
                    }
                }
                
                // Gerçek API çağrısı (OpenWeatherMap)
                val url = "https://api.openweathermap.org/data/2.5/forecast?lat=$latitude&lon=$longitude&appid=$apiKey&units=metric&lang=tr"
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: return@withContext null
                    val jsonResponse = JSONObject(responseBody)
                    val list = jsonResponse.optJSONArray("list")
                    
                    if (list != null && list.length() > 0) {
                        val firstItem = list.getJSONObject(0)
                        val main = firstItem.optJSONObject("main")
                        val weather = firstItem.optJSONArray("weather")?.getJSONObject(0)
                        
                        if (main != null && weather != null) {
                            val temp = main.optDouble("temp", 0.0)
                            val description = weather.optString("description", "")
                            return@withContext "Hava $temp derece, $description"
                        }
                    }
                }
                null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}

