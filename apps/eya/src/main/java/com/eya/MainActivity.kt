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
import com.eya.handlers.*
import com.eya.utils.SpeechToTextManager
import kotlinx.coroutines.launch

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
    val sttManager = remember { SpeechToTextManager(context) }
    
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
                    sttManager = sttManager,
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
    sttManager: SpeechToTextManager,
    scope: kotlinx.coroutines.CoroutineScope,
    onLog: (String) -> Unit
) {
    when (event.type) {
        EventType.MAIN_ROTATE -> {
            val mainIndex = menuManager.normalize(event.mainIndex, menuManager.getMainMenuCount())
            onMainIndexChanged(mainIndex)
            val name = menuManager.getMainMenuName(mainIndex, language)
            if (name != null) {
                ttsManager.speak(name, language)
                onLog("MAIN_ROTATE -> $name")
            }
        }
        EventType.SUB_ROTATE -> {
            val subIndex = menuManager.normalize(event.subIndex, menuManager.getSubMenuCount(currentMainIndex))
            onSubIndexChanged(subIndex)
            val name = menuManager.getSubMenuName(currentMainIndex, subIndex, language)
            if (name != null) {
                ttsManager.speak(name, language)
                onLog("SUB_ROTATE -> $name")
            }
        }
        EventType.CONFIRM -> {
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
                onLog = onLog
            )
        }
        EventType.AI_PRESS -> {
            val languageCode = if (language == "tr") "tr-TR" else "en-US"
            sttManager.startRecording(languageCode)
            onLog("AI_PRESS -> kayıt başlıyor")
        }
        EventType.AI_RELEASE -> {
            val languageCode = if (language == "tr") "tr-TR" else "en-US"
            scope.launch {
                val transcribedText = sttManager.stopRecordingAndTranscribe(languageCode)
                if (transcribedText != null && transcribedText.isNotEmpty()) {
                    handleAIPressRelease(
                        transcribedText = transcribedText,
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
                } else {
                    val errorMsg = if (language == "tr") {
                        "Ses anlaşılamadı"
                    } else {
                        "Could not understand speech"
                    }
                    ttsManager.speak(errorMsg, language)
                    onLog("AI_RELEASE -> STT başarısız")
                }
            }
        }
        EventType.EVENT_CANCEL -> {
            sttManager.cancelRecording()
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
            // Konum al ve hava durumu göster
            locationHandler.handleCurrentLocation(ttsManager, language) { errorMsg ->
                ttsManager.speak(errorMsg, language)
            }
            // Basit implementasyon: Mock konum kullan
            weatherHandler.handleWeather(subIndex, 41.0082, 28.9784, ttsManager, language) { errorMsg ->
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
                    // Basit implementasyon: Varsayılan acil kişi
                    securityHandler.handleSendEmergencyLocation("112", ttsManager, language)
                    onLog("CONFIRM -> Acil Konum")
                }
                1 -> { // "Güvendeyim" Mesajı
                    securityHandler.handleSendSafeMessage("112", ttsManager, language)
                    onLog("CONFIRM -> Güvendeyim")
                }
                2 -> { // Yardım Çağrısı
                    securityHandler.handleStartHelpCall(ttsManager, language)
                    onLog("CONFIRM -> Yardım Çağrısı")
                }
                3 -> { // Sürekli Konum Paylaş
                    securityHandler.handleShareLocationContinuously("112", ttsManager, language)
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

