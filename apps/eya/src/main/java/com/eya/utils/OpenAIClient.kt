package com.eya.utils

import com.eya.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class OpenAIClient {
    private val client = OkHttpClient()
    private val apiKey = BuildConfig.OPENAI_API_KEY
    
    private val conversationHistory = mutableListOf<JSONObject>()
    private val maxHistorySize = 10
    
    suspend fun chat(userMessage: String, language: String = "tr"): String? {
        if (apiKey.isEmpty()) {
            return null
        }
        
        return withContext(Dispatchers.IO) {
            try {
                // Conversation history'ye ekle
                conversationHistory.add(JSONObject().apply {
                    put("role", "user")
                    put("content", userMessage)
                })
                
                // History'yi sınırla
                if (conversationHistory.size > maxHistorySize) {
                    conversationHistory.removeAt(0)
                }
                
                val messages = conversationHistory.map { it }
                
                val requestBody = JSONObject().apply {
                    put("model", "gpt-3.5-turbo")
                    put("messages", org.json.JSONArray(messages))
                    put("max_tokens", 150)
                }.toString()
                
                val request = Request.Builder()
                    .url("https://api.openai.com/v1/chat/completions")
                    .header("Authorization", "Bearer $apiKey")
                    .post(requestBody.toRequestBody("application/json".toMediaType()))
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: return@withContext null
                    val jsonResponse = JSONObject(responseBody)
                    val choices = jsonResponse.optJSONArray("choices")
                    
                    if (choices != null && choices.length() > 0) {
                        val firstChoice = choices.getJSONObject(0)
                        val message = firstChoice.optJSONObject("message")
                        if (message != null) {
                            val content = message.optString("content", "")
                            
                            // Assistant yanıtını history'ye ekle
                            conversationHistory.add(JSONObject().apply {
                                put("role", "assistant")
                                put("content", content)
                            })
                            
                            return@withContext content
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
    
    fun clearHistory() {
        conversationHistory.clear()
    }
}

