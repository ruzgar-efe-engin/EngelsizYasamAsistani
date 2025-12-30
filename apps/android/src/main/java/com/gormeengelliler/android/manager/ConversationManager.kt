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
import java.io.FileWriter
import java.util.Properties

data class ConversationSummary(
    val summary: String,
    val lastUpdated: Long,
    val messageCount: Int,
    val recentMessages: List<String> // Son 5 mesaj
)

class ConversationManager(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val SUMMARY_THRESHOLD = 2000 // Karakter limiti
    private val MAX_RECENT_MESSAGES = 5
    private val SUMMARY_FILE = File(context.filesDir, "ai_conversation_summary.json")
    private var geminiApiKey: String = ""
    private val client = OkHttpClient()
    private var currentSummary: ConversationSummary? = null
    private val recentMessages = mutableListOf<String>()
    
    init {
        loadGeminiAPIKey()
        loadSummary()
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
    
    private fun loadSummary() {
        try {
            if (SUMMARY_FILE.exists()) {
                val jsonString = SUMMARY_FILE.readText()
                val json = JSONObject(jsonString)
                val recentMessagesArray = json.optJSONArray("recentMessages")
                val recentMessagesList = if (recentMessagesArray != null) {
                    mutableListOf<String>().apply {
                        for (i in 0 until recentMessagesArray.length()) {
                            add(recentMessagesArray.getString(i))
                        }
                    }
                } else {
                    mutableListOf()
                }
                
                currentSummary = ConversationSummary(
                    summary = json.getString("summary"),
                    lastUpdated = json.getLong("lastUpdated"),
                    messageCount = json.getInt("messageCount"),
                    recentMessages = recentMessagesList
                )
                recentMessages.clear()
                recentMessages.addAll(recentMessagesList)
            } else {
                currentSummary = ConversationSummary(
                    summary = "",
                    lastUpdated = System.currentTimeMillis(),
                    messageCount = 0,
                    recentMessages = emptyList()
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            currentSummary = ConversationSummary(
                summary = "",
                lastUpdated = System.currentTimeMillis(),
                messageCount = 0,
                recentMessages = emptyList()
            )
        }
    }
    
    private fun saveSummary(summary: ConversationSummary) {
        try {
            val json = JSONObject().apply {
                put("summary", summary.summary)
                put("lastUpdated", summary.lastUpdated)
                put("messageCount", summary.messageCount)
                put("recentMessages", JSONArray().apply {
                    summary.recentMessages.forEach { put(it) }
                })
            }
            
            FileWriter(SUMMARY_FILE).use { writer ->
                writer.write(json.toString())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    suspend fun addMessage(userMessage: String, aiResponse: String): ConversationSummary {
        // Yeni mesajları ekle
        recentMessages.add("User: $userMessage")
        recentMessages.add("AI: $aiResponse")
        
        // Son N mesajı tut
        if (recentMessages.size > MAX_RECENT_MESSAGES * 2) {
            recentMessages.removeAt(0)
            recentMessages.removeAt(0)
        }
        
        val current = currentSummary ?: ConversationSummary("", System.currentTimeMillis(), 0, emptyList())
        
        // Özet güncelle
        val updatedSummary = updateSummary(current, userMessage, aiResponse)
        currentSummary = updatedSummary
        
        // Eğer özet threshold'u aştıysa dosyaya yaz
        if (updatedSummary.summary.length >= SUMMARY_THRESHOLD) {
            saveSummary(updatedSummary)
        }
        
        return updatedSummary
    }
    
    private suspend fun updateSummary(
        current: ConversationSummary,
        newUserMessage: String,
        newAiResponse: String
    ): ConversationSummary {
        // Yeni konuşmaları özetle
        val newConversation = "User: $newUserMessage\nAI: $newAiResponse"
        
        // Eğer mevcut özet varsa, yeni konuşmaları özetle ve eski özetle birleştir
        val updatedSummary = if (current.summary.isNotEmpty()) {
            // %50 son konuşmalar + %50 geçmiş özet formatı
            val recentSummary = summarizeText(newConversation)
            val combinedText = "$recentSummary\n\n${current.summary}"
            
            // Özetin özeti (recursive summarization)
            if (combinedText.length > SUMMARY_THRESHOLD) {
                summarizeText(combinedText)
            } else {
                combinedText
            }
        } else {
            // İlk özet
            summarizeText(newConversation)
        }
        
        return ConversationSummary(
            summary = updatedSummary,
            lastUpdated = System.currentTimeMillis(),
            messageCount = current.messageCount + 1,
            recentMessages = recentMessages.takeLast(MAX_RECENT_MESSAGES * 2)
        )
    }
    
    private suspend fun summarizeText(text: String): String {
        if (geminiApiKey.isEmpty()) {
            return text.take(SUMMARY_THRESHOLD) // Fallback: sadece kısalt
        }
        
        return try {
            val requestBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "Aşağıdaki konuşmayı özetle, en önemli noktaları koru. Özet maksimum 1000 karakter olsun:\n\n$text")
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
                val responseBody = response.body?.string() ?: return text.take(SUMMARY_THRESHOLD)
                val jsonResponse = JSONObject(responseBody)
                val candidates = jsonResponse.optJSONArray("candidates")
                
                if (candidates != null && candidates.length() > 0) {
                    val candidate = candidates.getJSONObject(0)
                    val content = candidate.optJSONObject("content")
                    if (content != null) {
                        val parts = content.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            val part = parts.getJSONObject(0)
                            return part.optString("text", text.take(SUMMARY_THRESHOLD))
                        }
                    }
                }
            }
            text.take(SUMMARY_THRESHOLD) // Fallback
        } catch (e: Exception) {
            e.printStackTrace()
            text.take(SUMMARY_THRESHOLD) // Fallback
        }
    }
    
    fun getContextForGemini(): String {
        val current = currentSummary ?: return ""
        
        // Özet + son mesajlar
        val context = if (current.summary.isNotEmpty()) {
            "${current.summary}\n\nSon konuşmalar:\n${recentMessages.takeLast(MAX_RECENT_MESSAGES * 2).joinToString("\n")}"
        } else {
            recentMessages.takeLast(MAX_RECENT_MESSAGES * 2).joinToString("\n")
        }
        
        return context
    }
    
    fun clearConversation() {
        currentSummary = ConversationSummary(
            summary = "",
            lastUpdated = System.currentTimeMillis(),
            messageCount = 0,
            recentMessages = emptyList()
        )
        recentMessages.clear()
        SUMMARY_FILE.delete()
    }
}

