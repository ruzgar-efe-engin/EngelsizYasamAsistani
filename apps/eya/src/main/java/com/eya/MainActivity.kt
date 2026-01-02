package com.eya

import android.os.Bundle
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.eya.model.DeviceEvent
import com.eya.model.EventType

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppScreen()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen() {
    val context = LocalContext.current
    val bleManager = remember { BLEEventTransport(context) }
    val ttsManager = remember { TTSManager(context) }
    val menuManager = remember { MenuManager(context) }

    var connected by remember { mutableStateOf(false) }
    var language by remember { mutableStateOf("tr") }
    var selectedVoice by remember { mutableStateOf("tr_female_1") }
    var isScanning by remember { mutableStateOf(false) }
    var countdown by remember { mutableStateOf(0) }
    var eventLogs by remember { mutableStateOf(listOf<String>()) }
    var panel1 by remember { mutableStateOf(true) }
    var panel2 by remember { mutableStateOf(true) }
    var panel3 by remember { mutableStateOf(true) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { Log.d("EYA", "İzin sonucu: $it") }
    )

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
                handleDeviceEvent(event, menuManager, ttsManager, language, onLog = { log ->
                    eventLogs = (eventLogs + log).takeLast(50)
                })
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
        topBar = { TopAppBar(title = { Text("EYA - Basit BLE") }) }
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
                        permissionLauncher.launch(
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
    onLog: (String) -> Unit
) {
    when (event.type) {
        EventType.MAIN_ROTATE -> {
            val name = menuManager.getMainMenuName(event.mainIndex, language)
            if (name != null) {
                ttsManager.speak(name, language)
                onLog("MAIN_ROTATE -> $name")
            }
        }
        EventType.SUB_ROTATE -> {
            val name = menuManager.getSubMenuName(event.mainIndex, event.subIndex, language)
            if (name != null) {
                ttsManager.speak(name, language)
                onLog("SUB_ROTATE -> $name")
            }
        }
        EventType.CONFIRM -> {
            // Basit if/else ile aksiyonlar (çocuklar için okunur)
            onLog("CONFIRM -> aksiyon çalıştır")
            ttsManager.speak("Tamamlandı", language)
        }
        EventType.AI_PRESS -> {
            onLog("AI_PRESS -> kayıt başlıyor")
        }
        EventType.AI_RELEASE -> {
            onLog("AI_RELEASE -> kayıt durdu, STT/LLM/TTS çalışacak (basit placeholder)")
            ttsManager.speak("Yanıt hazırlandı", language)
        }
        EventType.EVENT_CANCEL -> {
            onLog("EVENT_CANCEL -> iptal edildi")
        }
    }
}

