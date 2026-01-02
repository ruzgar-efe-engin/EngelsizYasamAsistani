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
        
        // Uygulamanın kurulu olup olmadığını kontrol et
        val packageManager = context.packageManager
        try {
            packageManager.getPackageInfo(packageName, 0)
            
            // Intent ile uygulamayı aç ve arama yap
            val intent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage(packageName)
                putExtra("query", transcribedText)
            }
            
            // Eğer ACTION_SEARCH çalışmazsa, web URL ile dene
            if (intent.resolveActivity(packageManager) == null) {
                val webIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(transcribedText)}")
                    setPackage(packageName)
                }
                context.startActivity(webIntent)
            } else {
                context.startActivity(intent)
            }
            
            val text = if (language == "tr") {
                "Aranıyor ve oynatılıyor"
            } else {
                "Searching and playing"
            }
            ttsManager.speak(text, language)
        } catch (e: PackageManager.NameNotFoundException) {
            val text = if (language == "tr") {
                "YouTube uygulaması bulunamadı, lütfen yükleyin"
            } else {
                "YouTube app not found, please install it"
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

