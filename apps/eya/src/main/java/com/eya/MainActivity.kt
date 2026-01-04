package com.eya

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.eya.model.DeviceEvent
import com.eya.model.EventType
import com.eya.handlers.*
import com.eya.utils.VoiceCommandManager
import kotlinx.coroutines.launch
import android.content.pm.PackageManager

class MainActivity : ComponentActivity() {
    private var broadcastReceiver: BroadcastReceiver? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Kilit ekranında da çalışabilmek için
        window.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
        
        // Keyguard'ı kaldır (Android 5.0+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        }
        
        // BroadcastReceiver'ı kaydet
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.eya.BLE_EVENT") {
                    val eventData = intent.getStringExtra("event_data") ?: return
                    Log.d("MainActivity", "BroadcastReceiver'dan event alındı: $eventData")
                    // Event'i AppScreen'e iletmek için bir callback kullanacağız
                    // Şimdilik sadece log - AppScreen kendi BroadcastReceiver'ını kullanacak
                }
            }
        }
        
        try {
            val filter = IntentFilter("com.eya.BLE_EVENT")
            registerReceiver(broadcastReceiver, filter)
        } catch (e: Exception) {
            Log.e("MainActivity", "BroadcastReceiver kayıt hatası: ${e.message}")
        }
        
        // BLE event'i intent'ten al (kilit ekranından geldiyse)
        val bleEvent = intent.getStringExtra("ble_event")
        if (bleEvent != null) {
            Log.d("MainActivity", "BLE event intent'ten alındı (onCreate): $bleEvent")
            // Broadcast gönder - AppScreen dinleyecek
            val broadcastIntent = Intent("com.eya.BLE_EVENT").apply {
                putExtra("event_data", bleEvent)
            }
            sendBroadcast(broadcastIntent)
        }
        
        setContent {
            AppScreen()
        }
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent) // Yeni intent'i set et
        // Yeni intent geldiğinde (kilit ekranından)
        val bleEvent = intent?.getStringExtra("ble_event")
        if (bleEvent != null) {
            Log.d("MainActivity", "Yeni BLE event alındı (kilit ekranından): $bleEvent")
            // Broadcast gönder - AppScreen dinleyecek
            val broadcastIntent = Intent("com.eya.BLE_EVENT").apply {
                putExtra("event_data", bleEvent)
            }
            sendBroadcast(broadcastIntent)
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Ekran açıldığında keyguard'ı kaldır
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // BroadcastReceiver'ı kaldır
        try {
            broadcastReceiver?.let {
                unregisterReceiver(it)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "BroadcastReceiver kaldırma hatası: ${e.message}")
        }
    }
}

// İzin bilgisi data class
data class PermissionInfo(
    val permission: String,
    val name: String,
    val reason: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("EYA_PREFS", Context.MODE_PRIVATE) }
    
    val requiredPermissionsInfo = remember {
        mutableListOf<PermissionInfo>().apply {
            add(PermissionInfo(
                android.Manifest.permission.RECORD_AUDIO,
                "Mikrofon",
                "Bas-Konuş özelliği için gerekli"
            ))
            add(PermissionInfo(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                "Konum (Hassas)",
                "Konum bilgisi ve hava durumu için gerekli"
            ))
            add(PermissionInfo(
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                "Konum (Yaklaşık)",
                "Konum bilgisi için gerekli"
            ))
            add(PermissionInfo(
                android.Manifest.permission.READ_CONTACTS,
                "Kişiler",
                "Rehberden arama için gerekli"
            ))
            add(PermissionInfo(
                android.Manifest.permission.CALL_PHONE,
                "Telefon Arama",
                "112 ve kişi aramaları için gerekli"
            ))
            add(PermissionInfo(
                android.Manifest.permission.SEND_SMS,
                "SMS Gönderme",
                "Acil durum mesajları için gerekli"
            ))
            add(PermissionInfo(
                android.Manifest.permission.READ_CALL_LOG,
                "Arama Geçmişi",
                "Son arayanı arama için gerekli"
            ))
        }
    }
    
    val requiredPermissions = remember {
        requiredPermissionsInfo.map { it.permission }.toTypedArray()
    }
    
    // İzin durumlarını takip et
    val permissionStates = remember { mutableStateMapOf<String, Boolean>() }
    val allPermissionsGranted = remember { 
        derivedStateOf { 
            requiredPermissionsInfo.all { permissionStates[it.permission] == true }
        }
    }
    
    // İzin durumlarını kontrol et ve sürekli güncelle
    LaunchedEffect(Unit) {
        while (true) {
            requiredPermissionsInfo.forEach { info ->
                permissionStates[info.permission] = ContextCompat.checkSelfPermission(
                    context,
                    info.permission
                ) == PackageManager.PERMISSION_GRANTED
            }
            kotlinx.coroutines.delay(500) // Her 500ms'de bir kontrol et
        }
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            // İzin durumlarını güncelle
            permissions.forEach { (permission, granted) ->
                permissionStates[permission] = granted
            }
            prefs.edit().putBoolean("permissions_requested", true).apply()
            
            if (allPermissionsGranted.value) {
                Log.d("EYA", "Tüm izinler verildi")
            } else {
                Log.w("EYA", "Bazı izinler eksik")
            }
        }
    )
    
    // İlk açılışta izin kontrolü
    LaunchedEffect(Unit) {
        if (!prefs.getBoolean("permissions_requested", false) || !allPermissionsGranted.value) {
            // İlk açılışta izin iste
            if (!prefs.getBoolean("permissions_requested", false)) {
                permissionLauncher.launch(requiredPermissions)
            }
        }
    }
    
    // İzinler verilmeden uygulama devam etmesin
    if (!allPermissionsGranted.value) {
        PermissionRequestScreen(
            onRequestPermissions = {
                permissionLauncher.launch(requiredPermissions)
            },
            permissionInfo = requiredPermissionsInfo,
            permissionStates = permissionStates,
            language = "tr"
        )
        return
    }
    
    val bleManager = remember { BLEEventTransport(context) }
    val ttsManager = remember { TTSManager(context) }
    val menuManager = remember { MenuManager(context) }
    var sttTranscribedText by remember { mutableStateOf<String?>(null) }
    var sttState by remember { mutableStateOf<String>("STOPPED") }
    
    val voiceCommandManager = remember { 
        VoiceCommandManager(
            context = context,
            onCommand = { text ->
                // STT sonucu geldiğinde
                sttTranscribedText = text
            },
            onError = { error ->
                sttState = "ERROR($error)"
            }
        )
    }
    
    // Handler'lar
    val timeHandler = remember { TimeHandler(context) }
    val weatherHandler = remember { WeatherHandler(context) }
    val locationHandler = remember { LocationHandler(context) }
    val communicationHandler = remember { CommunicationHandler(context) }
    val alarmHandler = remember { AlarmHandler(context) }
    val youtubeHandler = remember { YouTubeHandler(context) }
    val radioHandler = remember { RadioHandler() }
    val phoneStatusHandler = remember { PhoneStatusHandler(context) }
    val securityHandler = remember { SecurityHandler(context) }
    val aiHandler = remember { AIHandler() }
    val voiceHandler = remember { VoiceHandler(context) }
    
    // Menu navigation state
    var currentMainIndex by remember { mutableStateOf(0) }
    var currentSubIndex by remember { mutableStateOf(0) }

    var connected by remember { mutableStateOf(false) }
    var language by remember { mutableStateOf("tr") }
    var selectedVoice by remember { mutableStateOf("tr_female_1") }
    var isScanning by remember { mutableStateOf(false) }
    var countdown by remember { mutableStateOf(0) }
    var eventLogs by remember { mutableStateOf(listOf<String>()) }
    var panel1 by remember { mutableStateOf(true) }
    var panel2 by remember { mutableStateOf(true) }
    var panel3 by remember { mutableStateOf(true) }
    
    val scope = rememberCoroutineScope()
    
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { Log.d("EYA", "Bluetooth izin sonucu: $it") }
    )

    // Not: BroadcastReceiver Activity seviyesinde kaydedildi (MainActivity.onCreate)
    // Kilit ekranından gelen event'ler zaten BLE callback'leri üzerinden işleniyor

    // BLE callback: gelen event, bağlantı ve log
    LaunchedEffect(Unit) {
        bleManager.onDeviceFound = { deviceId ->
            eventLogs = (eventLogs + "Cihaz bulundu: $deviceId, bağlanıyor...").takeLast(50)
            // Android projesindeki gibi: onDeviceFound geldiğinde connect() çağır
            bleManager.connect(deviceId)
        }
        bleManager.onEventReceived = { json ->
            val event = DeviceEvent.fromJson(json)
            if (event != null) {
                handleDeviceEvent(
                    event = event,
                    menuManager = menuManager,
                    ttsManager = ttsManager,
                    language = language,
                    currentMainIndex = currentMainIndex,
                    currentSubIndex = currentSubIndex,
                    onMainIndexChanged = { currentMainIndex = it },
                    onSubIndexChanged = { currentSubIndex = it },
                    timeHandler = timeHandler,
                    weatherHandler = weatherHandler,
                    locationHandler = locationHandler,
                    communicationHandler = communicationHandler,
                    alarmHandler = alarmHandler,
                    youtubeHandler = youtubeHandler,
                    radioHandler = radioHandler,
                    phoneStatusHandler = phoneStatusHandler,
                    securityHandler = securityHandler,
                    aiHandler = aiHandler,
                    voiceHandler = voiceHandler,
                    voiceCommandManager = voiceCommandManager,
                    sttTranscribedText = sttTranscribedText,
                    onSttText = { sttTranscribedText = it },
                    onSttState = { sttState = it },
                    scope = scope,
                    onLog = { log ->
                        eventLogs = (eventLogs + log).takeLast(50)
                    }
                )
            }
        }
        bleManager.onConnectionChanged = {
            connected = it
            val log = if (it) "Bağlandı" else "Bağlantı koptu"
            eventLogs = (eventLogs + log).takeLast(50)
            if (it) {
                isScanning = false
                countdown = 0
            }
        }
        bleManager.onLog = { msg ->
            eventLogs = (eventLogs + msg).takeLast(50)
        }
    }

    // Geri sayım: tarama sırasında 30 sn sayaç, bitince taramayı durdur
    LaunchedEffect(isScanning, countdown) {
        if (isScanning && countdown > 0) {
            kotlinx.coroutines.delay(1000)
            countdown -= 1
            if (countdown == 0) {
                bleManager.stopScan()
                isScanning = false
                eventLogs = (eventLogs + "Tarama süresi doldu").takeLast(50)
            }
        }
    }

    val voices = remember { ttsManager.getVoices() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Engelsiz Yaşam Asistanı") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Panel 1: Hoşgeldiniz + BLE
            Panel(
                title = "1) Hoşgeldiniz & BLE",
                expanded = panel1,
                onToggle = { panel1 = !panel1 }
            ) {
                Text("AI butonuna 5 sn basın (cihaz pairing moduna geçer). Bağlantı: Sadece gerçek BLE.")
                Spacer(Modifier.height(8.dp))
                Text("Bağlantı durumu: ${if (connected) "Bağlı" else "Bağlı değil"}")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        bluetoothPermissionLauncher.launch(
                            arrayOf(
                                android.Manifest.permission.BLUETOOTH_SCAN,
                                android.Manifest.permission.BLUETOOTH_CONNECT
                            )
                        )
                        isScanning = true
                        countdown = 30
                        bleManager.startScan()
                        eventLogs = (eventLogs + "Tarama başladı (BLE)").takeLast(50)
                    }, enabled = !isScanning) { Text("CİHAZ ARA (BLE)") }
                    Button(onClick = {
                        isScanning = false
                        countdown = 0
                        bleManager.stopScan()
                        eventLogs = (eventLogs + "Tarama durduruldu").takeLast(50)
                    }, enabled = isScanning) { Text("VAZGEÇ") }
                }
                if (isScanning) {
                    Spacer(Modifier.height(8.dp))
                    Text("Eşleşme geri sayımı: $countdown sn")
                }
                Spacer(Modifier.height(8.dp))
            }

            // Panel 2: Dil + Ses seçimi
            Panel(
                title = "2) Dil & Ses",
                expanded = panel2,
                onToggle = { panel2 = !panel2 }
            ) {
                Text("Dil seçin:")
                Row {
                    RadioButton(
                        selected = language == "tr",
                        onClick = { language = "tr" }
                    )
                    Text("Türkçe", modifier = Modifier.clickable { language = "tr" })
                    Spacer(Modifier.width(12.dp))
                    RadioButton(
                        selected = language == "en",
                        onClick = { language = "en" }
                    )
                    Text("English", modifier = Modifier.clickable { language = "en" })
                }
                Spacer(Modifier.height(8.dp))
                Text("Ses seçin:")
                VoiceDropdown(
                    voices = voices,
                    selectedId = selectedVoice,
                    onSelected = { id -> selectedVoice = id }
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    ttsManager.setVoice(selectedVoice)
                    ttsManager.speak("Merhaba, ses seçiminiz yapıldı.", language)
                }) { Text("Sesi Dene") }
            }

            // Panel 3: Event log
            Panel(
                title = "3) Event Log",
                expanded = panel3,
                onToggle = { panel3 = !panel3 }
            ) {
                LazyColumn(modifier = Modifier.height(200.dp)) {
                    items(eventLogs) { item ->
                        Text("• $item")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("Bas-Konuş (AI) butonu (basılı tut → konuş, bırak → STT/LLM/TTS)")
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { /* basılı değilken noop */ },
                enabled = false // sadece görsel; gerçek kayıt eklenmedi
            ) { Text("Basılı tut (demo)") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionRequestScreen(
    onRequestPermissions: () -> Unit,
    permissionInfo: List<PermissionInfo>,
    permissionStates: MutableMap<String, Boolean>,
    language: String
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Engelsiz Yaşam Asistanı") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (language == "tr") {
                    "Uygulamanın çalışması için gerekli izinler"
                } else {
                    "Required permissions for the app"
                },
                style = MaterialTheme.typography.headlineSmall
            )
            
            Text(
                text = if (language == "tr") {
                    "Lütfen tüm izinleri verin. Tüm izinler verilmeden uygulama kullanılamaz."
                } else {
                    "Please grant all permissions. The app cannot be used without all permissions."
                },
                style = MaterialTheme.typography.bodyMedium
            )
            
            Divider()
            
            // İzin checklist
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(permissionInfo.size) { index ->
                    val info = permissionInfo[index]
                    val isGranted = permissionStates[info.permission] == true
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Checkbox
                        Checkbox(
                            checked = isGranted,
                            onCheckedChange = null,
                            enabled = false
                        )
                        
                        // İzin bilgisi
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = info.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (isGranted) FontWeight.Normal else FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = info.reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        // Durum ikonu
                        Icon(
                            imageVector = if (isGranted) {
                                Icons.Default.CheckCircle
                            } else {
                                Icons.Default.Close
                            },
                            contentDescription = null,
                            tint = if (isGranted) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }
                }
            }
            
            Divider()
            
            // İzin iste butonu
            Button(
                onClick = onRequestPermissions,
                modifier = Modifier.fillMaxWidth(),
                enabled = !permissionStates.values.all { it }
            ) {
                Text(if (language == "tr") "İzinleri İste" else "Request Permissions")
            }
        }
    }
}

private fun checkAllPermissions(context: Context): Boolean {
    val permissions = listOf(
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
        android.Manifest.permission.READ_CONTACTS,
        android.Manifest.permission.CALL_PHONE,
        android.Manifest.permission.SEND_SMS,
        android.Manifest.permission.READ_CALL_LOG
    )
    
    return permissions.all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}

@Composable
fun Panel(title: String, expanded: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { onToggle() }
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ArrowDropDown else Icons.Default.KeyboardArrowRight,
                    contentDescription = null
                )
                Spacer(Modifier.width(4.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                content()
            }
        }
    }
}

@Composable
fun VoiceDropdown(voices: List<TTSManager.VoiceInfo>, selectedId: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val current = voices.firstOrNull { it.id == selectedId }?.name ?: "Seçilmedi"
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(current)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            voices.forEach { voice ->
                DropdownMenuItem(
                    text = { Text(voice.name) },
                    onClick = {
                        onSelected(voice.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun handleDeviceEvent(
    event: DeviceEvent,
    menuManager: MenuManager,
    ttsManager: TTSManager,
    language: String,
    currentMainIndex: Int,
    currentSubIndex: Int,
    onMainIndexChanged: (Int) -> Unit,
    onSubIndexChanged: (Int) -> Unit,
    timeHandler: TimeHandler,
    weatherHandler: WeatherHandler,
    locationHandler: LocationHandler,
    communicationHandler: CommunicationHandler,
    alarmHandler: AlarmHandler,
    youtubeHandler: YouTubeHandler,
    radioHandler: RadioHandler,
    phoneStatusHandler: PhoneStatusHandler,
    securityHandler: SecurityHandler,
    aiHandler: AIHandler,
    voiceHandler: VoiceHandler,
    voiceCommandManager: VoiceCommandManager,
    sttTranscribedText: String?,
    onSttText: (String) -> Unit,
    onSttState: (String) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
    onLog: (String) -> Unit
) {
    when (event.type) {
        EventType.MAIN_ROTATE -> {
            // Radyo açıksa kapat
            if (radioHandler.isPlaying()) {
                radioHandler.stopRadio()
            }
            val mainIndex = menuManager.normalize(event.mainIndex, menuManager.getMainMenuCount())
            onMainIndexChanged(mainIndex)
            val name = menuManager.getMainMenuName(mainIndex, language)
            if (name != null) {
                ttsManager.speak(name, language)
                onLog("MAIN_ROTATE -> $name")
            }
        }
        EventType.SUB_ROTATE -> {
            // Radyo açıksa kapat
            if (radioHandler.isPlaying()) {
                radioHandler.stopRadio()
            }
            val subIndex = menuManager.normalize(event.subIndex, menuManager.getSubMenuCount(currentMainIndex))
            onSubIndexChanged(subIndex)
            
            // Seslendirme menüsü için özel işlem
            if (currentMainIndex == 11) {
                voiceHandler.handleVoiceRotate(subIndex, language)
                onLog("SUB_ROTATE -> Voice $subIndex")
            } else {
                val name = menuManager.getSubMenuName(currentMainIndex, subIndex, language)
                if (name != null) {
                    ttsManager.speak(name, language)
                    onLog("SUB_ROTATE -> $name")
                }
            }
        }
        EventType.CONFIRM -> {
            // Radyo açıksa kapat
            if (radioHandler.isPlaying()) {
                radioHandler.stopRadio()
            }
            handleMenuConfirm(
                mainIndex = currentMainIndex,
                subIndex = currentSubIndex,
                menuManager = menuManager,
                ttsManager = ttsManager,
                language = language,
                timeHandler = timeHandler,
                weatherHandler = weatherHandler,
                locationHandler = locationHandler,
                communicationHandler = communicationHandler,
                alarmHandler = alarmHandler,
                youtubeHandler = youtubeHandler,
                radioHandler = radioHandler,
                phoneStatusHandler = phoneStatusHandler,
                securityHandler = securityHandler,
                voiceHandler = voiceHandler,
                onLog = onLog
            )
        }
        EventType.AI_PRESS -> {
            // Radyo açıksa kapat
            if (radioHandler.isPlaying()) {
                radioHandler.stopRadio()
            }
            
            // Android SpeechRecognizer başlat
            if (voiceCommandManager.isListening()) {
                onLog("AI_PRESS -> STT zaten çalışıyor")
                return
            }
            
            val languageCode = if (language == "tr") "tr-TR" else "en-US"
            val startMsg = if (language == "tr") {
                "Dinliyorum"
            } else {
                "Listening"
            }
            ttsManager.speak(startMsg, language)
            onLog("AI_PRESS -> Android SpeechRecognizer başlatılıyor")
            
            onSttState("LISTENING")
            voiceCommandManager.startListening(languageCode)
        }
        EventType.AI_RELEASE -> {
            // Android SpeechRecognizer durdur
            if (!voiceCommandManager.isListening()) {
                onLog("AI_RELEASE -> STT çalışmıyor")
                return
            }
            
            // stopListening() çağır - bu onResults'ı tetikleyecek
            voiceCommandManager.stop()
            onSttState("STOPPED")
            
            scope.launch {
                val stopMsg = if (language == "tr") {
                    "İşleniyor"
                } else {
                    "Processing"
                }
                ttsManager.speak(stopMsg, language)
                
                // onResults callback'i async geldiği için biraz bekle
                // Maksimum 3 saniye bekle
                var waited = 0
                var transcribedText: String? = null
                while (waited < 3000 && transcribedText == null) {
                    kotlinx.coroutines.delay(100)
                    waited += 100
                    transcribedText = sttTranscribedText
                }
                
                // Eğer hala yoksa, pending result'ı kontrol et
                if (transcribedText == null || transcribedText.isBlank()) {
                    transcribedText = voiceCommandManager.getPendingResult()
                    if (transcribedText != null) {
                        onSttText(transcribedText)
                    }
                }
                
                // Temizle
                voiceCommandManager.cleanup()
                
                if (transcribedText != null && transcribedText.isNotEmpty() && transcribedText.isNotBlank()) {
                    onLog("AI_RELEASE -> STT başarılı: '$transcribedText'")
                    handleAIPressRelease(
                        transcribedText = transcribedText.trim(),
                        mainIndex = currentMainIndex,
                        subIndex = currentSubIndex,
                        menuManager = menuManager,
                        ttsManager = ttsManager,
                        language = language,
                        communicationHandler = communicationHandler,
                        alarmHandler = alarmHandler,
                        youtubeHandler = youtubeHandler,
                        aiHandler = aiHandler,
                        onLog = onLog
                    )
                    // Transkripti temizle
                    onSttText("")
                } else {
                    val errorMsg = if (language == "tr") {
                        "Ses anlaşılamadı. Daha yüksek sesle ve net konuşun, lütfen tekrar deneyin"
                    } else {
                        "Could not understand speech. Speak louder and clearer, please try again"
                    }
                    ttsManager.speak(errorMsg, language)
                    onLog("AI_RELEASE -> STT başarısız veya boş")
                }
            }
        }
        EventType.EVENT_CANCEL -> {
            voiceCommandManager.cleanup()
            onSttState("STOPPED")
            onSttText("")
            onLog("EVENT_CANCEL -> iptal edildi")
            val text = if (language == "tr") "İptal edildi" else "Cancelled"
            ttsManager.speak(text, language)
        }
    }
}

private fun handleMenuConfirm(
    mainIndex: Int,
    subIndex: Int,
    menuManager: MenuManager,
    ttsManager: TTSManager,
    language: String,
    timeHandler: TimeHandler,
    weatherHandler: WeatherHandler,
    locationHandler: LocationHandler,
    communicationHandler: CommunicationHandler,
    alarmHandler: AlarmHandler,
    youtubeHandler: YouTubeHandler,
    radioHandler: RadioHandler,
    phoneStatusHandler: PhoneStatusHandler,
    securityHandler: SecurityHandler,
    voiceHandler: VoiceHandler,
    onLog: (String) -> Unit
) {
    when (mainIndex) {
        0 -> { // Zaman
            when (subIndex) {
                0 -> { // Saat
                    timeHandler.handleSaat(ttsManager, language)
                    onLog("CONFIRM -> Saat")
                }
                1 -> { // Tarih
                    timeHandler.handleTarih(ttsManager, language)
                    onLog("CONFIRM -> Tarih")
                }
            }
        }
        1 -> { // Hava Durumu
            weatherHandler.handleWeather(subIndex, ttsManager, language) { errorMsg ->
                ttsManager.speak(errorMsg, language)
            }
            onLog("CONFIRM -> Hava Durumu")
        }
        2 -> { // Konum
            when (subIndex) {
                0 -> { // Mevcut Konum
                    locationHandler.handleCurrentLocation(ttsManager, language) { errorMsg ->
                        ttsManager.speak(errorMsg, language)
                    }
                    onLog("CONFIRM -> Mevcut Konum")
                }
                1 -> { // Hangi Yöne Bakıyorum
                    locationHandler.handleDirection(ttsManager, language) { errorMsg ->
                        ttsManager.speak(errorMsg, language)
                    }
                    onLog("CONFIRM -> Yön")
                }
                2 -> { // Çevremde Ne Var
                    locationHandler.handleNearby(ttsManager, language) { errorMsg ->
                        ttsManager.speak(errorMsg, language)
                    }
                    onLog("CONFIRM -> Çevre")
                }
            }
        }
        3 -> { // İletişim
            when (subIndex) {
                0 -> { // 112'yi Ara
                    communicationHandler.handleCall112(ttsManager, language)
                    onLog("CONFIRM -> 112 Ara")
                }
                1 -> { // Son Arayanı Ara
                    communicationHandler.handleCallLastCaller(ttsManager, language)
                    onLog("CONFIRM -> Son Arayan")
                }
                2, 3 -> { // Bas-Konuş özellikleri - AI_RELEASE'de işlenecek
                    val text = if (language == "tr") {
                        "Lütfen butona basılı tutarak konuşun"
                    } else {
                        "Please hold the button and speak"
                    }
                    ttsManager.speak(text, language)
                    onLog("CONFIRM -> Bas-Konuş beklemede")
                }
            }
        }
        4 -> { // Alarm
            when (subIndex) {
                0 -> { // Alarm Kur (Bas-Konuş) - AI_RELEASE'de işlenecek
                    val text = if (language == "tr") {
                        "Lütfen butona basılı tutarak alarm zamanını söyleyin"
                    } else {
                        "Please hold the button and say the alarm time"
                    }
                    ttsManager.speak(text, language)
                    onLog("CONFIRM -> Alarm Kur beklemede")
                }
                1 -> { // Alarmları Say
                    alarmHandler.handleListAlarms(ttsManager, language)
                    onLog("CONFIRM -> Alarmları Say")
                }
                2 -> { // Alarm İptal Et
                    alarmHandler.handleCancelAlarms(ttsManager, language)
                    onLog("CONFIRM -> Alarm İptal")
                }
            }
        }
        5 -> { // YouTube
            when (subIndex) {
                0 -> { // Bas-Konuş Ara ve Oynat - AI_RELEASE'de işlenecek
                    val text = if (language == "tr") {
                        "Lütfen butona basılı tutarak arama yapın"
                    } else {
                        "Please hold the button and search"
                    }
                    ttsManager.speak(text, language)
                    onLog("CONFIRM -> YouTube Bas-Konuş beklemede")
                }
            }
        }
        6 -> { // YouTube Music
            when (subIndex) {
                0 -> { // Bas-Konuş Ara ve Oynat - AI_RELEASE'de işlenecek
                    val text = if (language == "tr") {
                        "Lütfen butona basılı tutarak arama yapın"
                    } else {
                        "Please hold the button and search"
                    }
                    ttsManager.speak(text, language)
                    onLog("CONFIRM -> YouTube Music Bas-Konuş beklemede")
                }
                1 -> { // Rasgele Rock Müzik
                    youtubeHandler.handleRandomRock(ttsManager, language)
                    onLog("CONFIRM -> Rock Müzik")
                }
                2 -> { // Rasgele Klasik Müzik
                    youtubeHandler.handleRandomClassical(ttsManager, language)
                    onLog("CONFIRM -> Klasik Müzik")
                }
            }
        }
        7 -> { // Radyo
            radioHandler.playRadio(subIndex, ttsManager, language) { errorMsg ->
                ttsManager.speak(errorMsg, language)
            }
            onLog("CONFIRM -> Radyo $subIndex")
        }
        8 -> { // Telefon Durumu
            when (subIndex) {
                0 -> { // Şarj Kaç
                    phoneStatusHandler.handleBatteryLevel(ttsManager, language)
                    onLog("CONFIRM -> Şarj")
                }
                1 -> { // İnternet Var mı
                    phoneStatusHandler.handleInternetStatus(ttsManager, language)
                    onLog("CONFIRM -> İnternet")
                }
                2 -> { // Sessizde mi
                    phoneStatusHandler.handleSilentMode(ttsManager, language)
                    onLog("CONFIRM -> Sessiz Mod")
                }
            }
        }
        9 -> { // Güvenlik
            when (subIndex) {
                0 -> { // Acil Konum Gönder
                    securityHandler.handleSendEmergencyLocation(ttsManager, language)
                    onLog("CONFIRM -> Acil Konum")
                }
                1 -> { // "Güvendeyim" Mesajı
                    securityHandler.handleSendSafeMessage(ttsManager, language)
                    onLog("CONFIRM -> Güvendeyim")
                }
                2 -> { // Yardım Çağrısı
                    securityHandler.handleStartHelpCall(ttsManager, language)
                    onLog("CONFIRM -> Yardım Çağrısı")
                }
                3 -> { // Sürekli Konum Paylaş
                    securityHandler.handleShareLocationContinuously(ttsManager, language)
                    onLog("CONFIRM -> Sürekli Konum")
                }
            }
        }
        10 -> { // Yapay Zeka
            when (subIndex) {
                0, 1 -> { // Bas-Konuş - AI_RELEASE'de işlenecek
                    val text = if (language == "tr") {
                        "Lütfen butona basılı tutarak sorunuzu sorun"
                    } else {
                        "Please hold the button and ask your question"
                    }
                    ttsManager.speak(text, language)
                    onLog("CONFIRM -> AI Bas-Konuş beklemede")
                }
            }
        }
        11 -> { // Seslendirme
            voiceHandler.handleVoiceSelection(subIndex, language)
            onLog("CONFIRM -> Ses Seçildi: $subIndex")
        }
    }
}

private fun handleAIPressRelease(
    transcribedText: String,
    mainIndex: Int,
    subIndex: Int,
    menuManager: MenuManager,
    ttsManager: TTSManager,
    language: String,
    communicationHandler: CommunicationHandler,
    alarmHandler: AlarmHandler,
    youtubeHandler: YouTubeHandler,
    aiHandler: AIHandler,
    onLog: (String) -> Unit
) {
    when (mainIndex) {
        3 -> { // İletişim
            when (subIndex) {
                2 -> { // Bas-Konuş Rehberden İsim
                    communicationHandler.handleCallByName(transcribedText, ttsManager, language)
                    onLog("AI_RELEASE -> Rehberden Ara: $transcribedText")
                }
                3 -> { // Bas-Konuş Numara
                    communicationHandler.handleCallByNumber(transcribedText, ttsManager, language)
                    onLog("AI_RELEASE -> Numara Ara: $transcribedText")
                }
            }
        }
        4 -> { // Alarm
            when (subIndex) {
                0 -> { // Alarm Kur
                    alarmHandler.handleSetAlarm(transcribedText, ttsManager, language)
                    onLog("AI_RELEASE -> Alarm Kur: $transcribedText")
                }
            }
        }
        5 -> { // YouTube
            when (subIndex) {
                0 -> { // Bas-Konuş Ara ve Oynat
                    youtubeHandler.handleSearchAndPlay(transcribedText, false, ttsManager, language)
                    onLog("AI_RELEASE -> YouTube Ara: $transcribedText")
                }
            }
        }
        6 -> { // YouTube Music
            when (subIndex) {
                0 -> { // Bas-Konuş Ara ve Oynat
                    youtubeHandler.handleSearchAndPlay(transcribedText, true, ttsManager, language)
                    onLog("AI_RELEASE -> YouTube Music Ara: $transcribedText")
                }
            }
        }
        10 -> { // Yapay Zeka
            when (subIndex) {
                0 -> { // Gemini ile Bas-Konuş
                    aiHandler.handleGeminiChat(transcribedText, ttsManager, language)
                    onLog("AI_RELEASE -> Gemini: $transcribedText")
                }
                1 -> { // ChatGPT ile Bas-Konuş
                    aiHandler.handleChatGPTChat(transcribedText, ttsManager, language)
                    onLog("AI_RELEASE -> ChatGPT: $transcribedText")
                }
            }
        }
    }
}

