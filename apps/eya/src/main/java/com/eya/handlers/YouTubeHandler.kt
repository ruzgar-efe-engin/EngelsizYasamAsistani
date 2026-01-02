package com.eya.handlers

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import com.eya.TTSManager

class YouTubeHandler(private val context: Context) {
    
    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        } catch (e: Exception) {
            Log.e("YouTubeHandler", "Package kontrolü hatası: ${e.message}")
            false
        }
    }
    
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
        
        // Önce uygulamanın yüklü olup olmadığını kontrol et
        val isInstalled = isPackageInstalled(packageName)
        Log.d("YouTubeHandler", "Package kontrolü: $packageName -> $isInstalled")
        
        if (!isInstalled) {
            val text = if (language == "tr") {
                if (isYouTubeMusic) {
                    "YouTube Music uygulaması bulunamadı, lütfen yükleyin"
                } else {
                    "YouTube uygulaması bulunamadı, lütfen yükleyin"
                }
            } else {
                if (isYouTubeMusic) {
                    "YouTube Music app not found, please install"
                } else {
                    "YouTube app not found, please install"
                }
            }
            ttsManager.speak(text, language)
            return
        }
        
        try {
            // YouTube'un kendi intent action'ını kullan
            val intent = if (isYouTubeMusic) {
                // YouTube Music için
                Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://music.youtube.com/search?q=${Uri.encode(transcribedText)}")
                    setPackage(packageName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            } else {
                // YouTube için - önce SEARCH action'ını dene
                Intent("com.google.android.youtube.intent.action.SEARCH").apply {
                    putExtra("query", transcribedText)
                    setPackage(packageName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }
            
            // Intent'i çözümle
            val resolvedActivity = intent.resolveActivity(packageManager)
            Log.d("YouTubeHandler", "Intent çözümlendi: $resolvedActivity")
            
            if (resolvedActivity != null) {
                context.startActivity(intent)
                // YouTube Music için otomatik oynatma için bir flag gönder
                // (AccessibilityService bunu yakalayacak)
                if (isYouTubeMusic) {
                    // YouTube Music açıldı, AccessibilityService otomatik play yapacak
                    android.util.Log.d("YouTubeHandler", "YouTube Music açıldı, otomatik oynatma bekleniyor...")
                }
                val text = if (language == "tr") {
                    "Aranıyor ve oynatılıyor"
                } else {
                    "Searching and playing"
                }
                ttsManager.speak(text, language)
            } else {
                // SEARCH action çalışmazsa, URL ile dene
                val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = if (isYouTubeMusic) {
                        Uri.parse("https://music.youtube.com/search?q=${Uri.encode(transcribedText)}")
                    } else {
                        Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(transcribedText)}")
                    }
                    setPackage(packageName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                
                if (fallbackIntent.resolveActivity(packageManager) != null) {
                    context.startActivity(fallbackIntent)
                    // YouTube Music için otomatik oynatma
                    if (isYouTubeMusic) {
                        android.util.Log.d("YouTubeHandler", "YouTube Music açıldı (fallback), otomatik oynatma bekleniyor...")
                    }
                    val text = if (language == "tr") {
                        "Aranıyor ve oynatılıyor"
                    } else {
                        "Searching and playing"
                    }
                    ttsManager.speak(text, language)
                } else {
                    // Son çare: uygulamayı direkt aç
                    val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                    if (launchIntent != null) {
                        launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        context.startActivity(launchIntent)
                        val text = if (language == "tr") {
                            "Uygulama açılıyor"
                        } else {
                            "Opening app"
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
            }
        } catch (e: SecurityException) {
            Log.e("YouTubeHandler", "Güvenlik hatası: ${e.message}", e)
            val text = if (language == "tr") {
                "YouTube açılırken izin hatası oluştu"
            } else {
                "Permission error opening YouTube"
            }
            ttsManager.speak(text, language)
        } catch (e: Exception) {
            Log.e("YouTubeHandler", "YouTube açılırken hata: ${e.message}", e)
            val text = if (language == "tr") {
                "YouTube açılamadı: ${e.message}"
            } else {
                "Could not open YouTube: ${e.message}"
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

