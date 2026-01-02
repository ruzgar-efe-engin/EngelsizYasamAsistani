package com.eya.utils

import com.eya.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class GeminiClient {
    private val client = OkHttpClient()
    private val apiKey = BuildConfig.API_KEY
    
    suspend fun generateContent(prompt: String, language: String = "tr"): String? {
        if (apiKey.isEmpty()) {
            return null
        }
        
        return withContext(Dispatchers.IO) {
            try {
                val requestBody = JSONObject().apply {
                    put("contents", org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", org.json.JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", prompt)
                                })
                            })
                        })
                    })
                }.toString()
                
                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=$apiKey")
                    .post(requestBody.toRequestBody("application/json".toMediaType()))
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: return@withContext null
                    val jsonResponse = JSONObject(responseBody)
                    val candidates = jsonResponse.optJSONArray("candidates")
                    
                    if (candidates != null && candidates.length() > 0) {
                        val firstCandidate = candidates.getJSONObject(0)
                        val content = firstCandidate.optJSONObject("content")
                        if (content != null) {
                            val parts = content.optJSONArray("parts")
                            if (parts != null && parts.length() > 0) {
                                val firstPart = parts.getJSONObject(0)
                                return@withContext firstPart.optString("text", "")
                            }
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
    
    suspend fun naturalizeWeatherData(weatherData: String, language: String): String? {
        val prompt = if (language == "tr") {
            "Hava durumu verisini görme engelli bir kullanıcı için kısa, net, doğal bir cümleye dönüştür. Maksimum 2 cümle, çok kısa ve anlaşılır olsun. Veri: $weatherData"
        } else {
            "Convert this weather data into a short, clear, natural sentence for a visually impaired user. Maximum 2 sentences, very brief and understandable. Data: $weatherData"
        }
        return generateContent(prompt, language)
    }
    
    suspend fun parseAlarmTime(userInput: String, language: String): String? {
        val prompt = if (language == "tr") {
            "Kullanıcının söylediği alarm zamanını parse et ve sadece saat:dakika formatında döndür (örn: 14:30). Sadece zamanı döndür, başka bir şey yazma. Kullanıcı girişi: $userInput"
        } else {
            "Parse the alarm time the user said and return only in hour:minute format (e.g., 14:30). Return only the time, write nothing else. User input: $userInput"
        }
        return generateContent(prompt, language)
    }
}

