package com.gormeengelliler.android.ui

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.gormeengelliler.android.manager.MenuManager
import com.gormeengelliler.android.service.BLEManager
import kotlinx.coroutines.launch

enum class ConnectionStatus {
    NOT_CONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val menuManager = remember { MenuManager(context) }
    
    // State management
    var selectedLanguage by remember { mutableStateOf(menuManager.getSelectedLanguage()) }
    var isScanning by remember { mutableStateOf(false) }
    var scannedDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var connectionStatus by remember { mutableStateOf<ConnectionStatus>(ConnectionStatus.NOT_CONNECTED) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedDevice by remember { mutableStateOf<BluetoothDevice?>(null) }
    
    val bleManager = remember { BLEManager(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    
    // BLE Manager callbacks
    LaunchedEffect(bleManager) {
        bleManager.onDeviceFound = { device ->
            if (!scannedDevices.any { it.address == device.address }) {
                scannedDevices = scannedDevices + device
            }
        }
        
        bleManager.onConnectionStateChanged = { state ->
            when (state) {
                android.bluetooth.BluetoothProfile.STATE_CONNECTED -> {
                    connectionStatus = ConnectionStatus.CONNECTED
                    menuManager.setSelectedLanguage(selectedLanguage)
                    scope.launch {
                        snackbarHostState.showSnackbar("Cihaz başarıyla bağlandı!")
                    }
                    // Setup tamamlandı, service başlatılacak
                    onSetupComplete()
                }
                android.bluetooth.BluetoothProfile.STATE_DISCONNECTED -> {
                    if (connectionStatus == ConnectionStatus.CONNECTING) {
                        connectionStatus = ConnectionStatus.ERROR
                        errorMessage = "Bağlantı başarısız oldu"
                    } else {
                        connectionStatus = ConnectionStatus.NOT_CONNECTED
                    }
                }
                android.bluetooth.BluetoothProfile.STATE_CONNECTING -> {
                    connectionStatus = ConnectionStatus.CONNECTING
                }
            }
        }
    }
    
    // Permission check
    val hasBluetoothPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Engelsiz Yaşam Asistanı") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ScrollView ekle
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Hoşgeldin mesajı
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(300)) + slideInVertically(initialOffsetY = { -it }),
                    modifier = Modifier.fillMaxWidth()
                ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Bilgi ikonu",
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Hoş Geldiniz",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Engelsiz Yaşam Asistanı cihazınızı telefonunuzla bağlamak için aşağıdaki adımları takip edin.",
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                }
                
                // Dil seçimi
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(400, delayMillis = 100)) + slideInVertically(initialOffsetY = { -it }),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Seslendirme Dili",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            
                            val languages = listOf("tr" to "Türkçe", "en" to "English", "de" to "Deutsch")
                            
                            languages.forEach { (code, name) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedLanguage = code }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedLanguage == code,
                                        onClick = { selectedLanguage = code },
                                        modifier = Modifier.semantics {
                                            contentDescription = "$name dil seçeneği"
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = name,
                                        fontSize = 16.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
                }
                
                // Cihaz arama bölümü
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                    Text(
                        text = "Cihaz Bağlantısı",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Cihaz Ara butonu
                        Button(
                            onClick = {
                                if (!hasBluetoothPermissions) {
                                    errorMessage = "Bluetooth izinleri gerekli"
                                    return@Button
                                }
                                
                                if (!bleManager.isBluetoothEnabled()) {
                                    errorMessage = "Bluetooth açık değil"
                                    return@Button
                                }
                                
                                scannedDevices = emptyList()
                                isScanning = true
                                bleManager.startScan()
                                
                                // 30 saniye sonra otomatik durdur
                                scope.launch {
                                    kotlinx.coroutines.delay(30000)
                                    if (isScanning) {
                                        bleManager.stopScan()
                                        isScanning = false
                                    }
                                }
                            },
                            enabled = !isScanning && connectionStatus != ConnectionStatus.CONNECTED,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Taranıyor...")
                            } else {
                                Icon(Icons.Default.Search, contentDescription = "Ara ikonu")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Cihaz Ara")
                            }
                        }
                        
                        // Aramayı Durdur butonu
                        OutlinedButton(
                            onClick = {
                                bleManager.stopScan()
                                isScanning = false
                            },
                            enabled = isScanning,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Durdur ikonu")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Durdur")
                        }
                    }
                    
                    // Yeniden Ara butonu
                    if (!isScanning && scannedDevices.isEmpty() && connectionStatus == ConnectionStatus.NOT_CONNECTED) {
                        TextButton(
                            onClick = {
                                scannedDevices = emptyList()
                                isScanning = true
                                bleManager.startScan()
                            }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Yenile ikonu")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Yeniden Ara")
                        }
                    }
                }
                }
                
                // Bulunan cihazlar listesi header
                if (scannedDevices.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Bulunan Cihazlar (${scannedDevices.size})",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    scannedDevices.forEach { device ->
                        DeviceCard(
                            device = device,
                            isSelected = selectedDevice?.address == device.address,
                            onClick = {
                                selectedDevice = device
                            }
                        )
                    }
                }
                
                // Empty state - sadece cihaz listesi için
                if (scannedDevices.isEmpty() && !isScanning && connectionStatus == ConnectionStatus.NOT_CONNECTED) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Bluetooth arama ikonu",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Henüz cihaz bulunamadı",
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Yukarıdaki 'Cihaz Ara' butonuna basarak aramayı başlatın",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
                
                // Eşleştirme butonu
                if (selectedDevice != null && connectionStatus != ConnectionStatus.CONNECTED) {
                    Button(
                            onClick = {
                                selectedDevice?.let { device ->
                                    connectionStatus = ConnectionStatus.CONNECTING
                                    bleManager.connect(device)
                                }
                            },
                            enabled = connectionStatus != ConnectionStatus.CONNECTING,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (connectionStatus == ConnectionStatus.CONNECTING) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Bağlanıyor...")
                            } else {
                                Icon(Icons.Default.Check, contentDescription = "Bağlantı ikonu")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Eşleştir")
                            }
                        }
                    }
                }
                
                // Bağlantı durumu göstergesi
                if (connectionStatus != ConnectionStatus.NOT_CONNECTED) {
                    when (connectionStatus) {
                ConnectionStatus.CONNECTING -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Bağlanıyor...", color = MaterialTheme.colorScheme.primary)
                    }
                }
                ConnectionStatus.CONNECTED -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Bağlantı başarılı ikonu",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Bağlandı!",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                ConnectionStatus.ERROR -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Hata ikonu",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = errorMessage ?: "Bağlantı hatası!",
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                else -> {
                    Spacer(modifier = Modifier.height(0.dp))
                }
            }
            
            // Error message handling
            LaunchedEffect(errorMessage) {
                errorMessage?.let { message ->
                    scope.launch {
                        snackbarHostState.showSnackbar(message)
                        errorMessage = null
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceCard(
    device: BluetoothDevice,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(
                2.dp,
                MaterialTheme.colorScheme.primary
            )
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Bluetooth cihaz ikonu",
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name ?: "Bilinmeyen Cihaz",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = device.address,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Seçili cihaz işareti",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
