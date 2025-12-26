package com.gormeengelliler.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.gormeengelliler.android.service.DeviceEventService
import com.gormeengelliler.android.theme.EngelsizYasamAsistaniTheme
import com.gormeengelliler.android.ui.SetupScreen

class MainActivity : AppCompatActivity() {
    private var hasBluetoothPermissions by mutableStateOf(false)
    
    // Permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        hasBluetoothPermissions = allGranted
        
        if (allGranted) {
            // İzinler verildi, foreground service'i başlat
            val serviceIntent = Intent(this, DeviceEventService::class.java)
            startForegroundService(serviceIntent)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // İzin kontrolü
        checkBluetoothPermissions()
        
        setContent {
            EngelsizYasamAsistaniTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (hasBluetoothPermissions) {
                        // İzinler verildi, SetupScreen'i göster
                        SetupScreen(
                            onSetupComplete = {
                                // Setup tamamlandı, uygulamayı arka plana al
                                moveTaskToBack(true)
                            }
                        )
                    } else {
                        // İzin ekranı
                        PermissionRequestScreen(
                            onRequestPermissions = {
                                requestBluetoothPermissions()
                            }
                        )
                    }
                }
            }
        }
    }
    
    private fun checkBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scanPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
            
            val connectPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
            
            hasBluetoothPermissions = scanPermission && connectPermission
        } else {
            // Android 12 öncesi için izin gerekmez
            hasBluetoothPermissions = true
        }
        
        if (hasBluetoothPermissions) {
            // İzinler zaten var, foreground service'i başlat
            val serviceIntent = Intent(this, DeviceEventService::class.java)
            startForegroundService(serviceIntent)
        }
    }
    
    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            )
        } else {
            // Android 12 öncesi için izin gerekmez
            hasBluetoothPermissions = true
            val serviceIntent = Intent(this, DeviceEventService::class.java)
            startForegroundService(serviceIntent)
        }
    }
}

@Composable
fun PermissionRequestScreen(
    onRequestPermissions: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Bluetooth İzinleri Gerekli",
            fontSize = 24.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Engelsiz Yaşam Asistani cihazınızla bağlantı kurabilmek için Bluetooth izinlerine ihtiyaç duyuyor.",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onRequestPermissions,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("İzin Ver", fontSize = 18.sp)
        }
    }
}

