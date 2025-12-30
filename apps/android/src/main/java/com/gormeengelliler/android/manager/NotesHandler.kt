package com.gormeengelliler.android.manager

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class NotesHandler(
    private val context: Context,
    private val menuManager: MenuManager,
    private val ttsManager: TTSManager
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val prefs: SharedPreferences = 
        context.getSharedPreferences("Notes", Context.MODE_PRIVATE)
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    
    suspend fun handleNewVoiceNote() {
        val language = menuManager.getSelectedLanguage()
        val message = when (language) {
            "tr" -> "Sesli not alınıyor..."
            "en" -> "Recording voice note..."
            "de" -> "Sprachnotiz wird aufgenommen..."
            else -> "Sesli not alınıyor..."
        }
        ttsManager.speak(message)
        
        // TODO: MediaRecorder ile ses kaydı başlat
        startRecording()
    }
    
    suspend fun handleMyNotes() {
        val language = menuManager.getSelectedLanguage()
        // TODO: SharedPreferences'den notlar listesini al
        val message = when (language) {
            "tr" -> "Not 1"
            "en" -> "Note 1"
            "de" -> "Notiz 1"
            else -> "Not 1"
        }
        ttsManager.speak(message)
    }
    
    suspend fun handlePlayNote(noteIndex: Int) {
        val language = menuManager.getSelectedLanguage()
        // TODO: Ses dosyasını oynat
        val message = when (language) {
            "tr" -> "Not ${noteIndex + 1} okunuyor"
            "en" -> "Reading note ${noteIndex + 1}"
            "de" -> "Notiz ${noteIndex + 1} wird gelesen"
            else -> "Not ${noteIndex + 1} okunuyor"
        }
        ttsManager.speak(message)
    }
    
    private fun startRecording() {
        try {
            val voiceNotesDir = File(context.filesDir, "voice_notes")
            if (!voiceNotesDir.exists()) {
                voiceNotesDir.mkdirs()
            }
            
            val timestamp = System.currentTimeMillis()
            val file = File(voiceNotesDir, "note_$timestamp.wav")
            
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            
            isRecording = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

