package com.gormeengelliler.android.manager

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.io.FileInputStream
import java.util.Properties

class AIHandler(
    private val context: Context,
    private val menuManager: MenuManager,
    private val ttsManager: TTSManager
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var isListening = false
    private var geminiApiKey: String = ""
    private val client = OkHttpClient()
    private val conversationManager = ConversationManager(context)
    
    init {
        loadGeminiAPIKey()
    }
    
    private fun loadGeminiAPIKey() {
        try {
            val localPropertiesFile = File(context.filesDir.parent, "local.properties")
            if (localPropertiesFile.exists()) {
                val properties = Properties()
                FileInputStream(localPropertiesFile).use { properties.load(it) }
                geminiApiKey = properties.getProperty("GEMINI_API_KEY", "")
                
                if (geminiApiKey.isEmpty()) {
                    geminiApiKey = "AIzaSyBiR3IYsBKQnlZ1G2XvQ8g2fIKIP30GhwI"
                }
            } else {
                geminiApiKey = "AIzaSyBiR3IYsBKQnlZ1G2XvQ8g2fIKIP30GhwI"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            geminiApiKey = "AIzaSyBiR3IYsBKQnlZ1G2XvQ8g2fIKIP30GhwI"
        }
    }
    
    suspend fun handleGeminiChat() {
        val language = menuManager.getSelectedLanguage()
        val message = when (language) {
            "tr" -> "Merhaba Size. Nasıl yardımcı olabilirim?"
            "en" -> "Hello. How can I help you?"
            "de" -> "Hallo. Wie kann ich Ihnen helfen?"
            else -> "Merhaba Size. Nasıl yardımcı olabilirim?"
        }
        ttsManager.speak(message)
        
        isListening = true
    }
    
    suspend fun processUserMessage(userMessage: String): String? {
        if (geminiApiKey.isEmpty()) {
            return null
        }
        
        return try {
            // Conversation context'i al
            val context = conversationManager.getContextForGemini()
            
            // Gemini Chat API'ye gönder
            val requestBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    // Context varsa ekle
                    if (context.isNotEmpty()) {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", context)
                                })
                            })
                        })
                    }
                    // Yeni mesaj
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", userMessage)
                            })
                        })
                    })
                })
            }.toString()
            
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$geminiApiKey")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: return null
                val jsonResponse = JSONObject(responseBody)
                val candidates = jsonResponse.optJSONArray("candidates")
                
                if (candidates != null && candidates.length() > 0) {
                    val candidate = candidates.getJSONObject(0)
                    val content = candidate.optJSONObject("content")
                    if (content != null) {
                        val parts = content.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            val part = parts.getJSONObject(0)
                            val aiResponse = part.optString("text", "")
                            
                            // Conversation'a ekle
                            conversationManager.addMessage(userMessage, aiResponse)
                            
                            return aiResponse
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
    
    suspend fun handleQuickQuestion(questionType: String) {
        val language = menuManager.getSelectedLanguage()
        val question = when (questionType) {
            "neredeyim" -> when (language) {
                "tr" -> "Neredeyim?"
                "en" -> "Where am I?"
                "de" -> "Wo bin ich?"
                else -> "Neredeyim?"
            }
            "kaç_durak" -> when (language) {
                "tr" -> "Kaç durak kaldı?"
                "en" -> "How many stops left?"
                "de" -> "Wie viele Haltestellen noch?"
                else -> "Kaç durak kaldı?"
            }
            "hava_nasıl" -> when (language) {
                "tr" -> "Hava nasıl?"
                "en" -> "How's the weather?"
                "de" -> "Wie ist das Wetter?"
                else -> "Hava nasıl?"
            }
            else -> when (language) {
                "tr" -> "Soru işleniyor..."
                "en" -> "Processing question..."
                "de" -> "Frage wird verarbeitet..."
                else -> "Soru işleniyor..."
            }
        }
        
        val response = processUserMessage(question)
        if (response != null) {
            ttsManager.speak(response, language)
        } else {
            ttsManager.speak(question, language)
        }
    }
    
    fun stopListening() {
        isListening = false
    }
    
    fun isListening(): Boolean {
        return isListening
    }
}

