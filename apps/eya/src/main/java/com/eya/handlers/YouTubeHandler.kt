package com.eya.handlers

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.eya.TTSManager

class YouTubeHandler(private val context: Context) {
    
    fun handleSearchAndPlay(
        transcribedText: String,
        isYouTubeMusic: Boolean,
        ttsManager: TTSManager,
        language: String
    ) {
        val packageName = if (isYouTubeMusic) {
            "com.google.android.apps.youtube.music"
        } else {
            "com.google.android.youtube"
        }
        
        val packageManager = context.packageManager
        
        // YouTube URL ile arama yap (uygulama kontrolü yapmadan direkt aç)
        val searchUrl = if (isYouTubeMusic) {
            "https://music.youtube.com/search?q=${Uri.encode(transcribedText)}"
        } else {
            "https://www.youtube.com/results?search_query=${Uri.encode(transcribedText)}"
        }
        
        // Önce package ile açmayı dene
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(searchUrl)
            setPackage(packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        
        try {
            // Package ile açmayı dene
            if (intent.resolveActivity(packageManager) != null) {
                context.startActivity(intent)
                val text = if (language == "tr") {
                    "Aranıyor ve oynatılıyor"
                } else {
                    "Searching and playing"
                }
                ttsManager.speak(text, language)
            } else {
                // Package yoksa, package olmadan aç (sistem uygun uygulamayı seçer)
                val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl))
                fallbackIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                if (fallbackIntent.resolveActivity(packageManager) != null) {
                    context.startActivity(fallbackIntent)
                    val text = if (language == "tr") {
                        "Aranıyor ve oynatılıyor"
                    } else {
                        "Searching and playing"
                    }
                    ttsManager.speak(text, language)
                } else {
                    val text = if (language == "tr") {
                        "YouTube açılamadı"
                    } else {
                        "Could not open YouTube"
                    }
                    ttsManager.speak(text, language)
                }
            }
        } catch (e: Exception) {
            val text = if (language == "tr") {
                "YouTube açılamadı"
            } else {
                "Could not open YouTube"
            }
            ttsManager.speak(text, language)
        }
    }
    
    fun handleRandomRock(ttsManager: TTSManager, language: String) {
        handleSearchAndPlay("rock music", true, ttsManager, language)
    }
    
    fun handleRandomClassical(ttsManager: TTSManager, language: String) {
        handleSearchAndPlay("classical music", true, ttsManager, language)
    }
}

