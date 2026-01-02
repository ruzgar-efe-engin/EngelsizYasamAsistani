package com.eya.utils

import com.eya.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class GeminiTTSClient {
    private val client = OkHttpClient()
    private val apiKey = BuildConfig.API_KEY
    
    suspend fun synthesizeSpeech(
        text: String,
        voiceId: String,
        language: String = "tr"
    ): ByteArray? {
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
                                    put("text", text)
                                })
                            })
                        })
                    })
                    put("generationConfig", JSONObject().apply {
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().apply {
                                put("voiceName", voiceId)
                            })
                        })
                    })
                }.toString()
                
                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-tts:synthesize?key=$apiKey")
                    .post(requestBody.toRequestBody("application/json".toMediaType()))
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val responseBody = response.body?.bytes() ?: return@withContext null
                    // Response'dan audio data'yı parse et
                    val jsonResponse = JSONObject(responseBody.toString(Charsets.UTF_8))
                    val audioContent = jsonResponse.optString("audioContent", "")
                    if (audioContent.isNotEmpty()) {
                        // Base64 decode
                        android.util.Base64.decode(audioContent, android.util.Base64.DEFAULT)
                    } else {
                        null
                    }
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}

