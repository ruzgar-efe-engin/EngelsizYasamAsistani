package com.gormeengelliler.android.ui

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.gormeengelliler.android.manager.MenuManager
import com.gormeengelliler.android.model.DeviceEvent
import com.gormeengelliler.android.service.BLEManager
import kotlinx.coroutines.delay
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
    var isPairingMode by remember { mutableStateOf(false) }
    var pairingTimeRemaining by remember { mutableStateOf(30) }
    
    // Event log state
    var eventLogs by remember { mutableStateOf<List<String>>(emptyList()) }
    val maxLogEntries = 100
    
    // Collapse panel states
    var welcomeExpanded by remember { mutableStateOf(true) }
    var languageExpanded by remember { mutableStateOf(false) }
    var devicePairingExpanded by remember { mutableStateOf(false) }
    
    val bleManager = remember { BLEManager(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Event formatı dönüşümü - JSON event'i [BLE] formatına çevir
    fun formatEventLog(event: DeviceEvent): String {
        // Log formatı: sendEvent çağrıldı: type=X m=Y s=Z - Paket alındı
        val logParts = mutableListOf<String>()
        logParts.add("sendEvent çağrıldı: type=${event.type.value}")
        
        // mainIndex sadece ilgili event'lerde
        if (event.type == com.gormeengelliler.android.model.EventType.MAIN_ROTATE || 
            event.type == com.gormeengelliler.android.model.EventType.SUB_ROTATE || 
            event.type == com.gormeengelliler.android.model.EventType.CONFIRM || 
            event.type == com.gormeengelliler.android.model.EventType.EVENT_CANCEL || 
            event.type == com.gormeengelliler.android.model.EventType.AI_PRESS || 
            event.type == com.gormeengelliler.android.model.EventType.AI_RELEASE) {
            logParts.add("m=${event.mainIndex}")
        }
        
        // subIndex sadece ilgili event'lerde
        if (event.type == com.gormeengelliler.android.model.EventType.SUB_ROTATE || 
            event.type == com.gormeengelliler.android.model.EventType.CONFIRM || 
            event.type == com.gormeengelliler.android.model.EventType.EVENT_CANCEL || 
            event.type == com.gormeengelliler.android.model.EventType.AI_PRESS || 
            event.type == com.gormeengelliler.android.model.EventType.AI_RELEASE) {
            logParts.add("s=${event.subIndex}")
        }
        
        logParts.add("- Paket alındı")
        return logParts.joinToString(" ")
    }
    
    // BLE Manager callbacks
    LaunchedEffect(bleManager) {
        bleManager.onDeviceFound = { device ->
            if (!scannedDevices.any { it.address == device.address }) {
                scannedDevices = scannedDevices + device
            }
        }
        
        // Event log callback - BLE'den gelen event'leri log formatına çevir
        // Wokwi terminal formatı: [BLE] EVENT_TYPE m=X s=Y ts=Z
        bleManager.onEventReceived = { jsonString ->
            android.util.Log.d("SetupScreen", "📥 Event alındı (BLE): $jsonString")
            try {
                val event = DeviceEvent.fromJson(jsonString)
                if (event != null) {
                    val logLine = formatEventLog(event)
                    android.util.Log.d("SetupScreen", "✅ Event parse edildi: $logLine")
                    // Anında event log paneline ekle (Wokwi terminal formatında)
                    eventLogs = (eventLogs + logLine).takeLast(maxLogEntries)
                } else {
                    android.util.Log.e("SetupScreen", "❌ Event parse edilemedi: $jsonString")
                    // Parse edilemese bile raw JSON'u göster
                    eventLogs = (eventLogs + "[BLE] RAW: $jsonString").takeLast(maxLogEntries)
                }
            } catch (e: Exception) {
                android.util.Log.e("SetupScreen", "❌ Event işleme hatası: ${e.message}")
                eventLogs = (eventLogs + "[BLE] ERROR: ${e.message} - $jsonString").takeLast(maxLogEntries)
            }
        }
        
        bleManager.onConnectionStateChanged = { state ->
            when (state) {
                android.bluetooth.BluetoothProfile.STATE_CONNECTED -> {
                    connectionStatus = ConnectionStatus.CONNECTED
                    menuManager.setSelectedLanguage(selectedLanguage)
                    
                    // Bağlantı başarılı olduğunda UUID bilgilerini log'a ekle
                    val uuidLogs = listOf(
                        "[BLE] Bağlantı başarılı!",
                        "[BLE] Service UUID: 12345678-1234-1234-1234-123456789abc",
                        "[BLE] Characteristic UUID: 12345678-1234-1234-1234-123456789abd"
                    )
                    eventLogs = uuidLogs
                    devicePairingExpanded = false // Cihaz eşleme panelini gizle
                    
                    scope.launch {
                        snackbarHostState.showSnackbar("Cihaz başarıyla bağlandı!")
                    }
                    // NOT: Setup tamamlandı diye uygulamayı arka plana alma.
                    // Bu ekranda event log'u gerçek zamanlı göstermek istiyoruz; arka plana alınırsa
                    // ekran kapanır/abonelik düşebilir ve log akışı kaybolur.
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
    
    // Pairing mode countdown
    LaunchedEffect(isPairingMode) {
        if (isPairingMode) {
            pairingTimeRemaining = 30
            while (isPairingMode && pairingTimeRemaining > 0) {
                delay(1000)
                pairingTimeRemaining--
                if (pairingTimeRemaining <= 0) {
                    isPairingMode = false
                    isScanning = false
                    bleManager.stopScan()
                }
            }
        }
    }
    
    // Auto-connect when device found in pairing mode
    LaunchedEffect(scannedDevices, isPairingMode) {
        if (isPairingMode && scannedDevices.isNotEmpty() && connectionStatus != ConnectionStatus.CONNECTED) {
            scannedDevices.firstOrNull()?.let { device ->
                connectionStatus = ConnectionStatus.CONNECTING
                bleManager.connect(device)
                isPairingMode = false
                isScanning = false
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Hoşgeldiniz Paneli
            ExpandableCard(
                title = "Hoş Geldiniz",
                icon = Icons.Default.Info,
                expanded = welcomeExpanded,
                onExpandedChange = { welcomeExpanded = it }
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Bilgi ikonu",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Engelsiz Yaşam Asistanı",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Cihazınızı telefonunuzla bağlamak için aşağıdaki adımları takip edin. Önce dil seçiminizi yapın, sonra cihaz eşleme işlemini başlatın.",
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                }
            }
            
            // 2. Dil Seçimi Paneli
            ExpandableCard(
                title = "Seslendirme Dili",
                icon = Icons.Default.Settings,
                expanded = languageExpanded,
                onExpandedChange = { languageExpanded = it }
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val languages = listOf("tr" to "Türkçe", "en" to "English", "de" to "Deutsch")
                    
                    languages.forEach { (code, name) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedLanguage = code }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedLanguage == code,
                                onClick = { selectedLanguage = code }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = name,
                                fontSize = 16.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            
            // 3. Cihaz Eşleme Paneli veya Event Log Paneli
            if (connectionStatus == ConnectionStatus.CONNECTED) {
                // Bağlantı başarılı - Event Log göster
                ExpandableCard(
                    title = "Event Log",
                    icon = Icons.Default.Info,
                    expanded = true,
                    onExpandedChange = { }
                ) {
                    EventLogView(eventLogs = eventLogs)
                }
            } else {
                // Bağlantı yok - Cihaz Eşleme göster
                ExpandableCard(
                    title = "Cihaz Eşleme",
                    icon = Icons.Default.Settings,
                    expanded = devicePairingExpanded,
                    onExpandedChange = { devicePairingExpanded = it }
                ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Talimat - Gri arka plan kaldırıldı, sadece border ve glassmorphism
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Talimat ikonu",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Cihazınızda AI butonuna 5 saniye basılı tutun",
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    // Butonlar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
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
                                
                                isPairingMode = true
                                pairingTimeRemaining = 30
                                scannedDevices = emptyList()
                                isScanning = true
                                bleManager.startScan()
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
                        
                        OutlinedButton(
                            onClick = {
                                bleManager.stopScan()
                                isScanning = false
                                isPairingMode = false
                            },
                            enabled = isScanning,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Vazgeç ikonu")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Vazgeç")
                        }
                    }
                    
                    // Pairing mode UI - Gri arka plan kaldırıldı
                    if (isPairingMode) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(64.dp),
                                strokeWidth = 4.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Eşleştirme Modu Aktif",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${pairingTimeRemaining} saniye kaldı",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    
                    // Bulunan cihazlar listesi
                    if (scannedDevices.isNotEmpty()) {
                        Text(
                            text = "Bulunan Cihazlar (${scannedDevices.size})",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        
                        scannedDevices.forEach { device ->
                            DeviceCard(
                                device = device,
                                isSelected = selectedDevice?.address == device.address,
                                onClick = {
                                    selectedDevice = device
                                    if (connectionStatus != ConnectionStatus.CONNECTED) {
                                        connectionStatus = ConnectionStatus.CONNECTING
                                        bleManager.connect(device)
                                    }
                                }
                            )
                        }
                    }
                    
                    // Bağlantı durumu
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
                            // Gri arka plan kaldırıldı
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
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
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        ConnectionStatus.ERROR -> {
                            // Gri arka plan kaldırıldı
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
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
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        else -> {}
                    }
                }
                }
            }
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

// Glassmorphism Card Component
@Composable
fun ExpandableCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.25f),
                        Color.White.copy(alpha = 0.15f)
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.3f),
                shape = RoundedCornerShape(20.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandedChange(!expanded) }
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Kapat" else "Aç",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Divider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = Color.White.copy(alpha = 0.2f)
                )
                content()
            }
        }
    }
}

// Terminal benzeri Event Log View
@Composable
fun EventLogView(eventLogs: List<String>) {
    val scrollState = rememberScrollState()
    
    // Yeni log geldiğinde en alta scroll yap
    LaunchedEffect(eventLogs.size) {
        if (eventLogs.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.8f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Event log ikonu",
                    tint = Color.Green,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "BLE Event Log",
                    color = Color.Green,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Divider(color = Color.Gray.copy(alpha = 0.3f))
            
            // Log content
            if (eventLogs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Event bekleniyor...",
                        color = Color.Gray,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    eventLogs.forEach { logLine ->
                        Text(
                            text = logLine,
                            color = Color.Green,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
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
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(
                2.dp,
                MaterialTheme.colorScheme.primary
            )
        } else null,
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 8.dp else 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
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
