package com.gormeengelliler.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.gormeengelliler.android.service.DeviceEventService
import com.gormeengelliler.android.theme.EngelsizYasamAsistaniTheme
import com.gormeengelliler.android.ui.SetupScreen

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Foreground service'i başlat
        val serviceIntent = Intent(this, DeviceEventService::class.java)
        startForegroundService(serviceIntent)
        
        setContent {
            EngelsizYasamAsistaniTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background
                ) {
                    SetupScreen(
                        onSetupComplete = {
                            // Setup tamamlandı, uygulamayı arka plana al
                            moveTaskToBack(true)
                        }
                    )
                }
            }
        }
    }
}

