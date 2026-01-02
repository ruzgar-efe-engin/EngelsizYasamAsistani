package com.eya

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import java.util.UUID

class BLEEventTransport(private val context: Context)  {
    companion object {
        // BLE UUID'leri - device'daki EventTransport.h ile aynı
        val SERVICE_UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc")
        val CHARACTERISTIC_UUID = UUID.fromString("12345678-1234-1234-1234-123456789abd")
        
        // Device name - device kodunda "GormeEngellilerKumanda" olarak geçiyor
        // Ama plan dosyasında "Engelsiz Yaşam Asistanı" veya mevcut isim olarak belirtilmiş
        // Her iki durumu da kontrol edeceğiz
        val DEVICE_NAMES = listOf("GormeEngellilerKumanda", "Engelsiz Yaşam Asistanı", "EngelsizYasamAsistani")
    }
    
    private val bluetoothManager: BluetoothManager = 
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var bluetoothGatt: BluetoothGatt? = null
    
    private var scanCallback: ScanCallback? = null
    private var isScanning = false

    // Notification/subscription hazır mı?
    private var isNotificationReady: Boolean = false
    
    // Descriptor yazma retry kontrolü
    private var descriptorWriteRetryCount = 0
    private val MAX_DESCRIPTOR_WRITE_RETRIES = 3
    private val DESCRIPTOR_WRITE_RETRY_DELAY_MS = 1000L
    private var descriptorWriteTimeoutHandler: Handler? = null
    private val DESCRIPTOR_WRITE_TIMEOUT_MS = 5000L // 5 saniye timeout
    
    // onSubscribe tetiklenmediyse descriptor'ı tekrar yazma kontrolü
    private var subscribeVerificationHandler: Handler? = null
    private val SUBSCRIBE_VERIFICATION_TIMEOUT_MS = 3000L // 3 saniye - event gelmezse tekrar dene
    private var lastEventReceivedTime: Long = 0
    
    // Connection timeout ve retry mekanizması
    private var connectionTimeoutHandler: Handler? = null
    private val CONNECTION_TIMEOUT_MS = 5000L // 5 saniye timeout
    private var connectionRetryCount = 0
    private val MAX_CONNECTION_RETRIES = 3
    private var lastConnectionAttemptTime: Long = 0
    private var lastConnectionDevice: BluetoothDevice? = null
    
    // Main thread Handler - callback'lerin main thread'de çalışmasını garanti et
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Polling-based read mekanizması (notify çalışmazsa kullanılacak)
    private var eventPollingHandler: Handler? = null
    private var isPollingActive = false
    private val POLLING_INTERVAL_MS = 50L // 50ms'de bir read yap (optimize edildi - daha hızlı event alımı)
    private var lastReadValue: ByteArray? = null // Son okunan değer (duplicate event'leri engellemek için)
    
    // Paket birleştirme mekanizması (BLE paket bölünmesi için)
    private var packetBuffer: StringBuilder = StringBuilder()
    private val PACKET_BUFFER_TIMEOUT_MS = 100L // 100ms içinde tamamlanmazsa buffer'ı temizle
    private var packetBufferTimeoutHandler: Handler? = null
    
    // Duplicate event kontrolü (aynı komutu tekrar göndermeyi önlemek için)
    private var lastSentEventType: String? = null
    private var lastSentEventMainIndex: Int = -1
    private var lastSentEventSubIndex: Int = -1
    private var lastSentEventTime: Long = 0
    private val DUPLICATE_COMMAND_THRESHOLD_MS = 2000L // 2 saniye içinde aynı komut tekrar gelirse duplicate say
    
    // Callbacks
    var onDeviceFound: ((String) -> Unit)? = null
    var onEventReceived: ((String) -> Unit)? = null
    var onConnectionChanged: ((Boolean) -> Unit)? = null
    var onLog: ((String) -> Unit)? = null
    
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
    
    fun startScan() {
        onLog?.invoke("🔍 BLE tarama başlatılıyor...")
        
        if (!isBluetoothEnabled()) {
            onLog?.invoke("❌ Bluetooth etkin değil, tarama başlatılamıyor")
            return
        }
        
        if (isScanning) {
            onLog?.invoke("⚠️  Tarama zaten aktif, durduruluyor...")
            stopScan()
        }
        
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        
        if (bluetoothLeScanner == null) {
            onLog?.invoke("❌ BluetoothLeScanner başlatılamadı")
            return
        }
        
        // Scan filter: mümkün olduğunca sadece bizim Service UUID ile gelen cihazları bul.
        // (Bridge server advertising'i zaten Service UUID ile yapıyor.)
        val scanFilters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(SERVICE_UUID))
                .build()
        )
        
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0) // Anında bildir
            .build()
        
        onLog?.invoke("✅ Scan settings hazır: SCAN_MODE_LOW_LATENCY")
        
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val deviceName = device.name ?: "Bilinmeyen"
                val scanRecord = result.scanRecord
                
                // Tüm cihazları log'la (debug için)
                val serviceUuids = scanRecord?.serviceUuids?.map { it.uuid.toString() } ?: emptyList()
                onLog?.invoke("📡 Cihaz bulundu: '$deviceName' (${device.address})")
                if (serviceUuids.isNotEmpty()) {
                    onLog?.invoke("   Service UUID'ler: $serviceUuids")
                }
                
                // Service UUID kontrolü (opsiyonel - scanRecord'da varsa kontrol et)
                val hasServiceUuid = scanRecord?.serviceUuids?.contains(ParcelUuid(SERVICE_UUID)) == true
                
                // Device name kontrolü (öncelikli - daha esnek)
                val hasMatchingName = DEVICE_NAMES.any { it.equals(deviceName, ignoreCase = true) }

                // Eşleşme kuralı:
                // - Tercihen service UUID ile eşleşsin (en güvenlisi)
                // - Bazı cihazlar scanRecord.serviceUuids'i boş döndürebiliyor; o durumda isim eşleşmesini fallback olarak kabul et.
                if (hasServiceUuid || hasMatchingName) {
                    onLog?.invoke("✅ Cihaz eşleşti: $deviceName")
                    onLog?.invoke("   Service UUID eşleşti: $hasServiceUuid")
                    onLog?.invoke("   Device name eşleşti: $hasMatchingName")
                    // EventTransport interface: deviceId olarak MAC address gönder
                    onDeviceFound?.invoke(device.address)
                    // Internal callback (geriye uyumluluk için)
                    
                } else {
                    onLog?.invoke("❌ Cihaz eşleşmedi: $deviceName (aranan: $DEVICE_NAMES)")
                    onLog?.invoke("   ⚠️  Pairing mode aktif değil veya cihaz eşleşmedi")
                }
            }
            
            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                results?.forEach { result ->
                    onScanResult(0, result)  // callbackType: 0 for batch
                }
            }
            
            override fun onScanFailed(errorCode: Int) {
                isScanning = false
                val errorMsg = when (errorCode) {
                    SCAN_FAILED_ALREADY_STARTED -> "Tarama zaten başlatılmış"
                    SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "Uygulama kaydı başarısız"
                    SCAN_FAILED_FEATURE_UNSUPPORTED -> "Özellik desteklenmiyor"
                    SCAN_FAILED_INTERNAL_ERROR -> "İç hata"
                    else -> "Bilinmeyen hata: $errorCode"
                }
                onLog?.invoke("Tarama hatası: $errorMsg")
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                onLog?.invoke("BLUETOOTH_SCAN izni yok!")
                return
            }
        }
        
        try {
            // Filter ile tara (Service UUID)
            bluetoothLeScanner?.startScan(scanFilters, scanSettings, scanCallback)
            isScanning = true
            onLog?.invoke("✅ BLE tarama başlatıldı")
            onLog?.invoke("   📡 Aranan cihazlar: $DEVICE_NAMES")
            onLog?.invoke("   📡 Service UUID: $SERVICE_UUID")
            onLog?.invoke("   ⚠️  NOT: Sadece pairing mode aktifse cihaz bulunabilir")
        } catch (e: Exception) {
            onLog?.invoke("❌ Tarama başlatılamadı: ${e.message}")
            isScanning = false
        }
    }
    
    fun stopScan() {
        onLog?.invoke("🛑 BLE tarama durduruluyor...")
        scanCallback?.let { callback ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_SCAN
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    bluetoothLeScanner?.stopScan(callback)
                } else {
                    onLog?.invoke("⚠️  BLUETOOTH_SCAN izni yok, tarama durdurulamıyor")
                }
            } else {
                @Suppress("DEPRECATION")
                bluetoothLeScanner?.stopScan(callback)
            }
        }
        scanCallback = null
        isScanning = false
        onLog?.invoke("✅ BLE tarama durduruldu")
    }
    
    fun connect(deviceId: String) {
        // deviceId MAC address olarak gelir, BluetoothDevice oluştur
        val device = try {
            bluetoothAdapter?.getRemoteDevice(deviceId)
        } catch (e: Exception) {
            onLog?.invoke("❌ Geçersiz MAC address: $deviceId")
            return
        }
        
        if (device == null) {
            onLog?.invoke("❌ BluetoothDevice oluşturulamadı: $deviceId")
            return
        }
        
        connectInternal(device)
    }
    
    private fun connectInternal(device: BluetoothDevice) {
        val timestamp = System.currentTimeMillis()
        val threadName = Thread.currentThread().name
        
        onLog?.invoke("\n🔌 ========================================")
        onLog?.invoke("🔌 connect() ÇAĞRILDI")
        onLog?.invoke("========================================")
        onLog?.invoke("   Timestamp: $timestamp")
        onLog?.invoke("   Thread: $threadName")
        onLog?.invoke("   Cihaz: ${device.name ?: "Bilinmeyen"} (${device.address})")
        onLog?.invoke("   Retry sayısı: $connectionRetryCount / $MAX_CONNECTION_RETRIES")
        
        // Önceki bağlantı denemesi varsa temizle
        connectionTimeoutHandler?.removeCallbacksAndMessages(null)
        connectionTimeoutHandler = null
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                onLog?.invoke("❌ BLUETOOTH_CONNECT izni yok, bağlantı yapılamıyor")
                return
            }
        }
        
        onLog?.invoke("✅ İzinler kontrol edildi")
        onLog?.invoke("   Transport: LE (Samsung için gerekli)")
        
        // Mevcut bağlantıyı kapat (varsa)
        if (bluetoothGatt != null) {
            onLog?.invoke("   ⚠️  Mevcut GATT bağlantısı kapatılıyor...")
            try {
                bluetoothGatt?.disconnect()
                bluetoothGatt?.close()
            } catch (e: Exception) {
                onLog?.invoke("   ⚠️  Mevcut bağlantı kapatılırken hata: ${e.message}")
            }
            bluetoothGatt = null
        }
        
        // TRANSPORT_LE parametresi ekle (Samsung S24 Ultra için kritik)
        onLog?.invoke("📡 connectGatt() çağrılıyor...")
        val gattResult = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                onLog?.invoke("   API Level: ${Build.VERSION.SDK_INT} (M+)")
                onLog?.invoke("   TRANSPORT_LE kullanılıyor")
                device.connectGatt(context, false, gattCallback, android.bluetooth.BluetoothDevice.TRANSPORT_LE)
            } else {
                onLog?.invoke("   API Level: ${Build.VERSION.SDK_INT} (< M)")
                onLog?.invoke("   Eski API kullanılıyor")
                device.connectGatt(context, false, gattCallback)
            }
        } catch (e: Exception) {
            onLog?.invoke("❌ connectGatt() EXCEPTION:")
            onLog?.invoke("   Exception: ${e.message}")
            onLog?.invoke("   Stack: ${e.stackTrace.take(5).joinToString("\n")}")
            null
        }
        
        bluetoothGatt = gattResult
        
        // connectGatt() sonucunu doğrula
        if (bluetoothGatt != null) {
            onLog?.invoke("✅ connectGatt() başarılı, bluetoothGatt objesi oluşturuldu")
            onLog?.invoke("   bluetoothGatt != null: true")
            onLog?.invoke("   bluetoothGatt.toString(): ${bluetoothGatt.toString().take(100)}")
            onLog?.invoke("   💡 onConnectionStateChange callback'i bekleniyor...")
            
            // Connection timeout başlat
            lastConnectionAttemptTime = timestamp
            lastConnectionDevice = device
            connectionTimeoutHandler = Handler(Looper.getMainLooper())
            connectionTimeoutHandler?.postDelayed({
                val elapsed = System.currentTimeMillis() - lastConnectionAttemptTime
                onLog?.invoke("\n⏰ ========================================")
                onLog?.invoke("⏰ CONNECTION TIMEOUT!")
                onLog?.invoke("========================================")
                onLog?.invoke("   Geçen süre: ${elapsed}ms (timeout: ${CONNECTION_TIMEOUT_MS}ms)")
                onLog?.invoke("   onConnectionStateChange callback'i tetiklenmedi!")
                
                // Retry mekanizması
                if (connectionRetryCount < MAX_CONNECTION_RETRIES) {
                    connectionRetryCount++
                    onLog?.invoke("   🔄 Retry yapılıyor ($connectionRetryCount / $MAX_CONNECTION_RETRIES)...")
                    lastConnectionDevice?.let { retryDevice ->
                        mainHandler.postDelayed({
                            connectInternal(retryDevice)
                        }, 1000) // 1 saniye bekle
                    }
                } else {
                    onLog?.invoke("   ❌ Maksimum retry sayısına ulaşıldı, bağlantı başarısız")
                    connectionRetryCount = 0
                    mainHandler.post {
                        onConnectionChanged?.invoke(false)
                    }
                }
            }, CONNECTION_TIMEOUT_MS)
            onLog?.invoke("   ⏱️  Connection timeout başlatıldı (${CONNECTION_TIMEOUT_MS}ms)")
        } else {
            onLog?.invoke("❌ connectGatt() başarısız, bluetoothGatt objesi null!")
            onLog?.invoke("   Bu durumda bağlantı başlatılamaz")
            
            // Retry mekanizması
            if (connectionRetryCount < MAX_CONNECTION_RETRIES) {
                connectionRetryCount++
                onLog?.invoke("   🔄 Retry yapılıyor ($connectionRetryCount / $MAX_CONNECTION_RETRIES)...")
                mainHandler.postDelayed({
                    connectInternal(device)
                }, 1000) // 1 saniye bekle
            } else {
                onLog?.invoke("   ❌ Maksimum retry sayısına ulaşıldı")
                connectionRetryCount = 0
            }
        }
        onLog?.invoke("========================================\n")
    }
    
    fun disconnect() {
        onLog?.invoke("🔌 Cihaz bağlantısı kesiliyor...")
        
        // Polling'i durdur
        stopEventPolling()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                onLog?.invoke("❌ BLUETOOTH_CONNECT izni yok, bağlantı kesilemiyor")
                return
            }
        }
        
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        onLog?.invoke("✅ Cihaz bağlantısı kesildi")
    }
    
    private val gattCallback = object : BluetoothGattCallback() {
        @Suppress("MissingPermission")
        override fun onConnectionStateChange(bluetoothGatt: BluetoothGatt, status: Int, newState: Int) {
            val timestamp = System.currentTimeMillis()
            val threadName = Thread.currentThread().name
            val elapsed = if (lastConnectionAttemptTime > 0) timestamp - lastConnectionAttemptTime else 0
            
            onLog?.invoke("\n🔄🔄🔄 ========================================")
            onLog?.invoke("🔄 onConnectionStateChange CALLBACK TETİKLENDİ!")
            onLog?.invoke("========================================")
            onLog?.invoke("   Timestamp: $timestamp")
            onLog?.invoke("   Thread: $threadName")
            onLog?.invoke("   status: $status, newState: $newState")
            onLog?.invoke("   Bağlantı denemesinden geçen süre: ${elapsed}ms")
            
            // Connection timeout'u iptal et
            connectionTimeoutHandler?.removeCallbacksAndMessages(null)
            connectionTimeoutHandler = null
            onLog?.invoke("   ✅ Connection timeout iptal edildi")
            
            if (status != BluetoothGatt.GATT_SUCCESS) {
                onLog?.invoke("❌ Bağlantı hatası: status=$status")
                val statusMessage = when (status) {
                    BluetoothGatt.GATT_READ_NOT_PERMITTED -> "GATT_READ_NOT_PERMITTED"
                    BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> "GATT_WRITE_NOT_PERMITTED"
                    BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION -> "GATT_INSUFFICIENT_AUTHENTICATION"
                    BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED -> "GATT_REQUEST_NOT_SUPPORTED"
                    BluetoothGatt.GATT_INVALID_OFFSET -> "GATT_INVALID_OFFSET"
                    BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH -> "GATT_INVALID_ATTRIBUTE_LENGTH"
                    BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION -> "GATT_INSUFFICIENT_ENCRYPTION"
                    else -> "Bilinmeyen hata (status=$status)"
                }
                onLog?.invoke("   Status mesajı: $statusMessage")
                
                // Retry sayacını sıfırla
                connectionRetryCount = 0
                
                // Main thread'de callback çağır - EventTransport interface: Boolean
                mainHandler.post {
                    onConnectionChanged?.invoke(false)
                }
                return
            }
            
            // Başarılı bağlantı - retry sayacını sıfırla
            connectionRetryCount = 0
            onLog?.invoke("   ✅ Retry sayacı sıfırlandı")
            
            // Main thread'de işlemleri yap - callback'ler farklı thread'den gelebilir
            mainHandler.post {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    // CONNECTED demek için erken: önce service discovery + notify subscribe başarıyla tamamlanmalı
                    isNotificationReady = false
                    onLog?.invoke("✅ Cihaz bağlandı, MTU artırılıyor ve service'ler keşfediliyor...")
                    
                    // MTU size'ı artır (paket bölünmesini önlemek için)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            if (ActivityCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.BLUETOOTH_CONNECT
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                val mtuRequested = bluetoothGatt.requestMtu(512) // 512 byte MTU (default 23 byte)
                                onLog?.invoke("   📡 MTU request gönderildi: $mtuRequested (512 byte isteniyor)")
                            }
                        } else {
                            val mtuRequested = bluetoothGatt.requestMtu(512)
                            onLog?.invoke("   📡 MTU request gönderildi: $mtuRequested (512 byte isteniyor)")
                        }
                    }
                    
                    // Service'leri keşfet
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (ActivityCompat.checkSelfPermission(
                                context,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            bluetoothGatt.discoverServices()
                        }
                    } else {
                        bluetoothGatt.discoverServices()
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    onLog?.invoke("⚠️ Cihaz bağlantısı kesildi")
                    isNotificationReady = false
                    // Paket buffer'ı ve duplicate kontrol değişkenlerini temizle
                    packetBuffer.clear()
                    packetBufferTimeoutHandler?.removeCallbacksAndMessages(null)
                    lastSentEventType = null
                    lastSentEventMainIndex = -1
                    lastSentEventSubIndex = -1
                    lastSentEventTime = 0
                    onConnectionChanged?.invoke(false)
                }
                
                // Not: CONNECTED durumunu burada yayınlamıyoruz; notify hazır olunca yayınlayacağız.
                // EventTransport interface: Boolean (true=connected, false=disconnected)
                if (newState != BluetoothProfile.STATE_CONNECTED) {
                    onConnectionChanged?.invoke(false)
                }
                // CONNECTED durumu notify hazır olunca yayınlanacak
            }
        }
        
        override fun onMtuChanged(bluetoothGatt: BluetoothGatt, mtu: Int, status: Int) {
            onLog?.invoke("\n📡📡📡 ========================================")
            onLog?.invoke("📡 onMtuChanged CALLBACK TETİKLENDİ!")
            onLog?.invoke("========================================")
            onLog?.invoke("   MTU: $mtu bytes")
            onLog?.invoke("   Status: $status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                onLog?.invoke("   ✅ MTU başarıyla değiştirildi: $mtu bytes")
                onLog?.invoke("   💡 Paket bölünmesi azalacak")
            } else {
                onLog?.invoke("   ⚠️ MTU değiştirilemedi, default MTU kullanılacak")
            }
            onLog?.invoke("========================================\n")
        }
        
        override fun onServicesDiscovered(bluetoothGatt: BluetoothGatt, status: Int) {
            val timestamp = System.currentTimeMillis()
            val threadName = Thread.currentThread().name
            
            onLog?.invoke("\n📡📡📡 ========================================")
            onLog?.invoke("📡 onServicesDiscovered CALLBACK TETİKLENDİ!")
            onLog?.invoke("========================================")
            onLog?.invoke("   Timestamp: $timestamp")
            onLog?.invoke("   Thread: $threadName")
            onLog?.invoke("   status: $status")
            onLog?.invoke("   GATT objesi: ${bluetoothGatt.toString().take(100)}")
            
            // Runtime permission check
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    onLog?.invoke("❌ BLUETOOTH_CONNECT izni yok - callback çalışamıyor")
                    return
                }
            }
            
            if (status != BluetoothGatt.GATT_SUCCESS) {
                onLog?.invoke("❌ Service keşif hatası: status=$status")
                return
            }
            
            // Tüm service'leri listele
            val services = bluetoothGatt.services
            onLog?.invoke("Bulunan service sayısı: ${services.size}")
            services.forEach { service ->
                onLog?.invoke("Service: ${service.uuid}")
            }
            
            val service = bluetoothGatt.getService(SERVICE_UUID)
            if (service == null) {
                onLog?.invoke("❌ Service bulunamadı: $SERVICE_UUID")
                return
            }
            
            onLog?.invoke("✅ Service bulundu: ${service.uuid}")
            val characteristic = service.getCharacteristic(CHARACTERISTIC_UUID)
            
            if (characteristic == null) {
                onLog?.invoke("❌ Characteristic bulunamadı: $CHARACTERISTIC_UUID")
                return
            }
            
            onLog?.invoke("✅ Characteristic bulundu: ${characteristic.uuid}")
            
            // Characteristic özelliklerini kontrol et
            val properties = characteristic.properties
            onLog?.invoke("   Characteristic properties: $properties")
            val hasNotify = (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
            val hasRead = (properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0
            onLog?.invoke("   NOTIFY biti set mi: $hasNotify ${if (hasNotify) "✅" else "❌"}")
            onLog?.invoke("   READ biti set mi: $hasRead ${if (hasRead) "✅" else "❌"}")
            
            if (!hasNotify) {
                onLog?.invoke("   ❌ KRİTİK: Characteristic NOTIFY özelliğine sahip değil!")
                onLog?.invoke("   ⚠️  Bu durumda notification subscribe yapılamaz")
                onLog?.invoke("   ⚠️  Bridge server'ın characteristic tanımını kontrol edin")
                return
            }
            
            // Characteristic'in tüm descriptor'larını listele (doğrulama için)
            onLog?.invoke("   Characteristic descriptor'ları:")
            characteristic.descriptors.forEachIndexed { index, desc ->
                onLog?.invoke("      [$index] UUID: ${desc.uuid}")
                onLog?.invoke("      [$index] Value: ${desc.value?.contentToString() ?: "null"}")
            }
            
            // Notification'ı etkinleştir - permission zaten callback başında kontrol edildi
            onLog?.invoke("\n🔔 ========================================")
            onLog?.invoke("🔔 Notification Enable İşlemi Başlatılıyor")
            onLog?.invoke("========================================")
            onLog?.invoke("   Adım 1: setCharacteristicNotification çağrılıyor...")
            try {
                // İlk deneme
                var notificationEnabled = bluetoothGatt.setCharacteristicNotification(characteristic, true)
                onLog?.invoke("   ✅ setCharacteristicNotification sonucu: $notificationEnabled")
                onLog?.invoke("   💡 Bu işlem başarılı olmalı (true dönmeli)")
                
                // Eğer başarısız olursa, kısa bir delay ile tekrar dene (alternatif yöntem)
                if (!notificationEnabled) {
                    onLog?.invoke("   ⚠️  setCharacteristicNotification başarısız, 200ms sonra tekrar deneniyor...")
                    mainHandler.postDelayed({
                        notificationEnabled = bluetoothGatt.setCharacteristicNotification(characteristic, true)
                        onLog?.invoke("   Retry sonucu: $notificationEnabled")
                        if (!notificationEnabled) {
                            onLog?.invoke("   ❌ setCharacteristicNotification retry başarısız!")
                            onLog?.invoke("   ⚠️  Bu durumda descriptor yazma işlemi yapılmayacak")
                            return@postDelayed
                        }
                        // Retry başarılı oldu, descriptor yazma işlemine devam et
                        proceedWithDescriptorWrite(bluetoothGatt, characteristic)
                    }, 200)
                    return
                }
                
                // Descriptor yazma işlemine devam et
                proceedWithDescriptorWrite(bluetoothGatt, characteristic)
            } catch (e: Exception) {
                onLog?.invoke("   ❌ Notification enable exception: ${e.message}")
                onLog?.invoke("   Exception tipi: ${e.javaClass.simpleName}")
                onLog?.invoke("   Stack trace:")
                e.stackTrace.take(5).forEach { trace ->
                    onLog?.invoke("      at ${trace.className}.${trace.methodName}(${trace.fileName}:${trace.lineNumber})")
                }
            }
            onLog?.invoke("========================================\n")
        }
        
        override fun onDescriptorWrite(
            bluetoothGatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            val timestamp = System.currentTimeMillis()
            val threadName = Thread.currentThread().name
            
            onLog?.invoke("\n📝📝📝 ========================================")
            onLog?.invoke("📝 onDescriptorWrite CALLBACK TETİKLENDİ!")
            onLog?.invoke("========================================")
            onLog?.invoke("   Timestamp: $timestamp")
            onLog?.invoke("   Thread: $threadName")
            onLog?.invoke("   Descriptor UUID: ${descriptor.uuid}")
            onLog?.invoke("   Status: $status")
            onLog?.invoke("   Status açıklaması: ${if (status == BluetoothGatt.GATT_SUCCESS) "GATT_SUCCESS" else "HATA ($status)"}")
            onLog?.invoke("   GATT objesi: ${bluetoothGatt.toString().take(100)}")
            
            // Runtime permission check
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    onLog?.invoke("   ❌ BLUETOOTH_CONNECT izni yok - callback çalışamıyor")
                    onLog?.invoke("   ⚠️  Bu callback iptal ediliyor!")
                    return
                }
            }
            
            // Descriptor değerini kontrol et
            val descriptorValue = descriptor.value
            onLog?.invoke("   Descriptor değeri: ${descriptorValue?.contentToString() ?: "null"}")
            onLog?.invoke("   Beklenen değer: ${BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE.contentToString()}")
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                onLog?.invoke("   ✅ Descriptor yazma başarılı!")
                onLog?.invoke("   CCCD (Client Characteristic Configuration Descriptor) yazıldı")
                
                // CCCD yazıldı => notify subscribe hazır sayabiliriz
                val isCCCD = descriptor.uuid.toString().equals("00002902-0000-1000-8000-00805f9b34fb", ignoreCase = true)
                onLog?.invoke("   CCCD kontrolü: $isCCCD")
                
                if (isCCCD) {
                    isNotificationReady = true
                    descriptorWriteRetryCount = 0 // Başarılı oldu, retry sayacını sıfırla
                    descriptorWriteTimeoutHandler?.removeCallbacksAndMessages(null) // Timeout'u iptal et
                    onLog?.invoke("   ✅ Notification subscribe hazır (CCCD başarıyla yazıldı)")
                    onLog?.invoke("   💡 Bridge server'ın onSubscribe callback'i tetiklenmeli")
                    onLog?.invoke("   💡 Artık event'ler alınabilir")
                    
                    // macOS CoreBluetooth'un subscribe'ı algılaması için önce delay, sonra multiple read request
                    onLog?.invoke("   ⏱️  Descriptor yazıldı, 500ms bekleniyor...")
                    mainHandler.postDelayed({
                        onLog?.invoke("   🔄 macOS CoreBluetooth uyumluluğu için multiple read request başlatılıyor...")
                        onLog?.invoke("   💡 3 kez read request yapılacak (her biri 200ms delay ile)")
                        
                        val characteristic = bluetoothGatt.getService(SERVICE_UUID)?.getCharacteristic(CHARACTERISTIC_UUID)
                        if (characteristic == null) {
                            onLog?.invoke("   ⚠️  Characteristic bulunamadı, read yapılamadı")
                            return@postDelayed
                        }
                        
                        // İlk read request (hemen)
                        try {
                            onLog?.invoke("   📖 Read request #1 çağrılıyor...")
                            val readResult1 = bluetoothGatt.readCharacteristic(characteristic)
                            onLog?.invoke("   ✅ Read request #1 çağrıldı, sonuç: $readResult1")
                        } catch (e: Exception) {
                            onLog?.invoke("   ❌ Read request #1 exception: ${e.message}")
                        }
                        
                        // İkinci read request (200ms sonra)
                        mainHandler.postDelayed({
                            try {
                                onLog?.invoke("   📖 Read request #2 çağrılıyor...")
                                val readResult2 = bluetoothGatt.readCharacteristic(characteristic)
                                onLog?.invoke("   ✅ Read request #2 çağrıldı, sonuç: $readResult2")
                            } catch (e: Exception) {
                                onLog?.invoke("   ❌ Read request #2 exception: ${e.message}")
                            }
                            
                            // Üçüncü read request (200ms sonra)
                            mainHandler.postDelayed({
                                try {
                                    onLog?.invoke("   📖 Read request #3 çağrılıyor...")
                                    val readResult3 = bluetoothGatt.readCharacteristic(characteristic)
                                    onLog?.invoke("   ✅ Read request #3 çağrıldı, sonuç: $readResult3")
                                    onLog?.invoke("   ✅ Tüm read request'ler tamamlandı")
                                    onLog?.invoke("   💡 Bridge server'ın onSubscribe callback'i tetiklenmeli")
                                    
                                    // onSubscribe tetiklenip tetiklenmediğini kontrol et
                                    // Eğer 3 saniye içinde event gelmezse, descriptor'ı tekrar yazmayı dene
                                    onLog?.invoke("   ⏱️  Subscribe verification başlatılıyor (${SUBSCRIBE_VERIFICATION_TIMEOUT_MS}ms)...")
                                    lastEventReceivedTime = System.currentTimeMillis()
                                    subscribeVerificationHandler?.removeCallbacksAndMessages(null)
                                    subscribeVerificationHandler = Handler(Looper.getMainLooper())
                                    subscribeVerificationHandler?.postDelayed({
                                        val timeSinceLastEvent = System.currentTimeMillis() - lastEventReceivedTime
                                        if (timeSinceLastEvent >= SUBSCRIBE_VERIFICATION_TIMEOUT_MS && isNotificationReady) {
                                            onLog?.invoke("   ⏰ Subscribe verification timeout: Event gelmedi!")
                                            onLog?.invoke("   🔄 Descriptor'ı tekrar yazmayı deniyoruz (maksimum 2 retry)...")
                                            if (descriptorWriteRetryCount < 2) {
                                                val retryCharacteristic = bluetoothGatt.getService(SERVICE_UUID)?.getCharacteristic(CHARACTERISTIC_UUID)
                                                if (retryCharacteristic != null) {
                                                    proceedWithDescriptorWrite(bluetoothGatt, retryCharacteristic)
                                                }
                                            } else {
                                                onLog?.invoke("   ❌ Maksimum subscribe verification retry sayısına ulaşıldı")
                                            }
                                        } else if (timeSinceLastEvent < SUBSCRIBE_VERIFICATION_TIMEOUT_MS) {
                                            onLog?.invoke("   ✅ Event alındı, subscribe başarılı!")
                                        }
                                    }, SUBSCRIBE_VERIFICATION_TIMEOUT_MS)
                                } catch (e: Exception) {
                                    onLog?.invoke("   ❌ Read request #3 exception: ${e.message}")
                                }
                            }, 200)
                        }, 200)
                    }, 500)
                    
                    onLog?.invoke("   📡 STATE_CONNECTED durumu yayınlanıyor...")
                    
                    // Polling-based read mekanizmasını başlat (notify çalışmazsa yedek olarak)
                    onLog?.invoke("   🔄 Polling-based read mekanizması başlatılıyor...")
                    startEventPolling(bluetoothGatt)
                    
                    mainHandler.post {
                        onConnectionChanged?.invoke(true)
                    }
                } else {
                    onLog?.invoke("   ⚠️  Bu descriptor CCCD değil, notification subscribe hazır değil")
                }
            } else {
                onLog?.invoke("   ❌ Descriptor yazma hatası!")
                onLog?.invoke("   Status kodu: $status")
                val statusMessage = when (status) {
                    BluetoothGatt.GATT_READ_NOT_PERMITTED -> "GATT_READ_NOT_PERMITTED"
                    BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> "GATT_WRITE_NOT_PERMITTED"
                    BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION -> "GATT_INSUFFICIENT_AUTHENTICATION"
                    BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED -> "GATT_REQUEST_NOT_SUPPORTED"
                    BluetoothGatt.GATT_INVALID_OFFSET -> "GATT_INVALID_OFFSET"
                    BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH -> "GATT_INVALID_ATTRIBUTE_LENGTH"
                    BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION -> "GATT_INSUFFICIENT_ENCRYPTION"
                    else -> "Bilinmeyen hata"
                }
                onLog?.invoke("   Status mesajı: $statusMessage")
                onLog?.invoke("   ⚠️  Bu hata notification subscribe'ı engelleyebilir")
                onLog?.invoke("   ⚠️  Bridge server'ın onSubscribe callback'i tetiklenmeyebilir")
                isNotificationReady = false
                // Retry logic - descriptor yazmayı tekrar dene
                onLog?.invoke("   🔄 Retry logic başlatılıyor...")
                retryDescriptorWrite(bluetoothGatt, null)
                mainHandler.post {
                    onConnectionChanged?.invoke(false)
                }
            }
            onLog?.invoke("========================================\n")
        }
        
        override fun onCharacteristicChanged(
            bluetoothGatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            // API 32 ve altı cihazlar buraya düşer (API 33+ için overload aşağıda)
            handleCharacteristicChanged(characteristic, characteristic.value)
        }

        // Android 13+ (API 33) için yeni callback overload'u.
        // Bazı cihazlarda eski overload hiç çağrılmayabiliyor; bu yüzden ikisini de handle ediyoruz.
        override fun onCharacteristicChanged(
            bluetoothGatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleCharacteristicChanged(characteristic, value)
        }
        
        // Write-based polling için onCharacteristicWrite callback'i
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicWrite(
            bluetoothGatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            val timestamp = System.currentTimeMillis()
            val threadName = Thread.currentThread().name
            
            onLog?.invoke("\n✍️✍️✍️  ========================================")
            onLog?.invoke("✍️  onCharacteristicWrite CALLBACK TETİKLENDİ!")
            onLog?.invoke("========================================")
            onLog?.invoke("   Timestamp: $timestamp")
            onLog?.invoke("   Thread: $threadName")
            onLog?.invoke("   Status: $status")
            onLog?.invoke("   UUID: ${characteristic.uuid}")
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                onLog?.invoke("   ✅ Write başarılı")
                onLog?.invoke("   💡 Bridge server write request'i aldı, son event'i hazırladı")
                onLog?.invoke("   📖 Şimdi hemen read yapılacak, bridge server son event'i döndürecek")
                
                // Write sonrası hemen read yap (bridge server son event'i döndürecek)
                try {
                    val readResult = bluetoothGatt.readCharacteristic(characteristic)
                    onLog?.invoke("   📖 Write sonrası read request sonucu: $readResult")
                    onLog?.invoke("   💡 readResult=true ise: onCharacteristicRead callback'i tetiklenecek")
                } catch (e: Exception) {
                    onLog?.invoke("   ❌ Write sonrası read exception: ${e.message}")
                }
            } else {
                onLog?.invoke("   ❌ Write hatası: status=$status")
            }
            onLog?.invoke("========================================\n")
        }
        
        // Polling-based read için onCharacteristicRead callback'i
        override fun onCharacteristicRead(
            bluetoothGatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            val timestamp = System.currentTimeMillis()
            val threadName = Thread.currentThread().name
            
            onLog?.invoke("\n📖📖📖 ========================================")
            onLog?.invoke("📖 onCharacteristicRead CALLBACK TETİKLENDİ!")
            onLog?.invoke("========================================")
            onLog?.invoke("   Timestamp: $timestamp")
            onLog?.invoke("   Thread: $threadName")
            onLog?.invoke("   Status: $status")
            onLog?.invoke("   UUID: ${characteristic.uuid}")
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val value = characteristic.value
                onLog?.invoke("   ✅ Read başarılı: ${value?.size ?: 0} bytes")
                onLog?.invoke("   value null mu: ${value == null}")
                onLog?.invoke("   value empty mu: ${value?.isEmpty() ?: true}")
                
                if (value != null && value.isNotEmpty()) {
                    // Duplicate event kontrolü (aynı event'i tekrar işleme)
                    val valueString = String(value, Charsets.UTF_8)
                    val lastValueString = lastReadValue?.let { String(it, Charsets.UTF_8) }
                    
                    onLog?.invoke("   📦 Value içeriği (ilk 100 karakter): ${valueString.take(100)}")
                    onLog?.invoke("   📦 Last value içeriği: ${lastValueString?.take(100) ?: "null"}")
                    
                    if (valueString != lastValueString) {
                        onLog?.invoke("   ✅ Yeni event alındı (polling-based read)")
                        onLog?.invoke("   📤 handleCharacteristicChanged() çağrılıyor...")
                        lastReadValue = value
                        // Event'i parse et ve callback'i çağır
                        handleCharacteristicChanged(characteristic, value)
                    } else {
                        onLog?.invoke("   ℹ️  Aynı event (duplicate), atlanıyor")
                        onLog?.invoke("   💡 Bridge server henüz yeni event göndermedi")
                    }
                } else {
                    onLog?.invoke("   ℹ️  Read response boş")
                    onLog?.invoke("   💡 Bridge server'da _lastEvent boş olabilir")
                    onLog?.invoke("   💡 Veya event henüz _lastEvent'e kaydedilmemiş olabilir")
                }
            } else {
                onLog?.invoke("   ❌ Read hatası: status=$status")
                onLog?.invoke("   Status açıklaması: ${when(status) {
                    BluetoothGatt.GATT_SUCCESS -> "GATT_SUCCESS"
                    BluetoothGatt.GATT_READ_NOT_PERMITTED -> "GATT_READ_NOT_PERMITTED"
                    BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> "GATT_WRITE_NOT_PERMITTED"
                    BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION -> "GATT_INSUFFICIENT_AUTHENTICATION"
                    BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED -> "GATT_REQUEST_NOT_SUPPORTED"
                    BluetoothGatt.GATT_INVALID_OFFSET -> "GATT_INVALID_OFFSET"
                    BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH -> "GATT_INVALID_ATTRIBUTE_LENGTH"
                    BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION -> "GATT_INSUFFICIENT_ENCRYPTION"
                    BluetoothGatt.GATT_FAILURE -> "GATT_FAILURE"
                    else -> "Bilinmeyen status ($status)"
                }}")
            }
            onLog?.invoke("========================================\n")
        }
    }

    private fun handleCharacteristicChanged(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray?
    ) {
        val timestamp = System.currentTimeMillis()
        val threadName = Thread.currentThread().name
        
        // ============================================
        // KRİTİK LOG: NOTIFY GELDI
        // ============================================
        // Bu log, onCharacteristicChanged callback'inin tetiklendiğini gösterir
        // Eğer bu log görünmüyorsa, notify mekanizması çalışmıyor demektir
        // ============================================
        onLog?.invoke("🔔🔔🔔 NOTIFY GELDI 🔔🔔🔔")
        onLog?.invoke("========================================")
        onLog?.invoke("✅ onCharacteristicChanged CALLBACK TETİKLENDİ!")
        onLog?.invoke("   Bu log görünüyorsa: notify mekanizması ÇALIŞIYOR")
        onLog?.invoke("   Bu log görünmüyorsa: notify mekanizması ÇALIŞMIYOR")
        onLog?.invoke("========================================")
        
        onLog?.invoke("\n📡📡📡 ========================================")
        onLog?.invoke("📡 handleCharacteristicChanged ÇAĞRILDI!")
        onLog?.invoke("========================================")
        onLog?.invoke("   Timestamp: $timestamp")
        onLog?.invoke("   Thread: $threadName")
        onLog?.invoke("   uuid: ${characteristic.uuid}")
        onLog?.invoke("   value: ${value?.size ?: 0} bytes")
        onLog?.invoke("   value null mu: ${value == null}")
        onLog?.invoke("   value empty mu: ${value?.isEmpty() ?: true}")
        onLog?.invoke("========================================")
        onLog?.invoke("📡 onCharacteristicChanged: uuid=${characteristic.uuid}")
        
        // onEventReceived callback'inin set edilip edilmediğini kontrol et
        onLog?.invoke("   onEventReceived callback durumu: ${if (onEventReceived != null) "VAR ✅" else "YOK ❌"}")

        if (characteristic.uuid != CHARACTERISTIC_UUID) {
            onLog?.invoke("❌ UUID eşleşmedi: ${characteristic.uuid} != $CHARACTERISTIC_UUID")
            return
        }

        if (value != null && value.isNotEmpty()) {
            val jsonString = String(value, Charsets.UTF_8)
            onLog?.invoke("✅ Event paketi alındı (BLE): $jsonString")
            onLog?.invoke("   Paket uzunluğu: ${jsonString.length} karakter")
            onLog?.invoke("   Paket içeriği (ilk 100 karakter): ${jsonString.take(100)}")
            
            // Paket birleştirme mekanizması - Buffer timeout'unu iptal et
            packetBufferTimeoutHandler?.removeCallbacksAndMessages(null)
            
            // Buffer'a ekle
            packetBuffer.append(jsonString)
            val combinedJson = packetBuffer.toString()
            onLog?.invoke("   📦 Buffer'a eklendi, toplam uzunluk: ${combinedJson.length} karakter")
            
            // JSON validation - Tam JSON mu kontrol et
            val isCompleteJson = try {
                // JSON'un başında { ve sonunda } veya \n var mı kontrol et
                val trimmed = combinedJson.trim()
                trimmed.startsWith("{") && (trimmed.endsWith("}") || trimmed.endsWith("}\n"))
            } catch (e: Exception) {
                false
            }
            
            if (isCompleteJson) {
                // Tam JSON - Duplicate komut kontrolü yap
                val trimmedJson = combinedJson.trim()
                val now = System.currentTimeMillis()
                
                // Event'i parse et ve komut bilgilerini çıkar
                val event = com.eya.model.DeviceEvent.fromJson(trimmedJson)
                
                if (event != null) {
                    // Aynı komut (type + mainIndex + subIndex) ve 2 saniye içinde gelmişse duplicate say
                    val isDuplicateCommand = lastSentEventType == event.type.name &&
                                            lastSentEventMainIndex == event.mainIndex &&
                                            lastSentEventSubIndex == event.subIndex &&
                                            (now - lastSentEventTime) < DUPLICATE_COMMAND_THRESHOLD_MS
                    
                    if (isDuplicateCommand) {
                        onLog?.invoke("   ⚠️ Duplicate komut tespit edildi (2 saniye içinde), atlanıyor")
                        onLog?.invoke("   Son gönderilen: type=${lastSentEventType}, m=${lastSentEventMainIndex}, s=${lastSentEventSubIndex}")
                        onLog?.invoke("   Yeni gelen: type=${event.type.name}, m=${event.mainIndex}, s=${event.subIndex}")
                        onLog?.invoke("   Geçen süre: ${now - lastSentEventTime}ms")
                        // Buffer'ı temizle ama event'i gönderme
                        packetBuffer.clear()
                        return
                    }
                    
                    // Son gönderilen komutu kaydet
                    lastSentEventType = event.type.name
                    lastSentEventMainIndex = event.mainIndex
                    lastSentEventSubIndex = event.subIndex
                    lastSentEventTime = now
                }
                
                // Buffer'ı temizle ve event'i gönder
                onLog?.invoke("   ✅ Tam JSON tespit edildi, duplicate değil, event gönderiliyor")
                packetBuffer.clear()
                
                // Event alındı - subscribe başarılı demektir
                val eventReceivedTime = System.currentTimeMillis()
                lastEventReceivedTime = eventReceivedTime
                subscribeVerificationHandler?.removeCallbacksAndMessages(null) // Verification timeout'u iptal et
                onLog?.invoke("   ✅ Subscribe verification başarılı - event alındı!")
                onLog?.invoke("   Event alınma zamanı: $eventReceivedTime")
                onLog?.invoke("   💡 sendEvent çağrıldı: type=X m=Y s=Z - Paket alındı")
                
                // onEventReceived callback'inin set edilip edilmediğini kontrol et
                if (onEventReceived == null) {
                    onLog?.invoke("   ❌ KRİTİK: onEventReceived callback NULL!")
                    onLog?.invoke("   ⚠️  Event alındı ama callback yok, event kaybolacak!")
                    onLog?.invoke("   💡 SetupScreen'de onEventReceived callback'i set edilmeli")
                    return
                }
                
                // Callback'i main thread'de çağır - UI güncellemeleri için gerekli
                onLog?.invoke("   📤 Event callback main thread'e gönderiliyor...")
                onLog?.invoke("   📤 onEventReceived callback çağrılacak: $trimmedJson")
                mainHandler.post {
                    try {
                        val callbackTimestamp = System.currentTimeMillis()
                        onLog?.invoke("   ✅ onEventReceived callback çağrılıyor (main thread)")
                        onLog?.invoke("   Callback timestamp: $callbackTimestamp")
                        onLog?.invoke("   📥 Main thread'de event callback çağrılıyor (timestamp: $callbackTimestamp)")
                        onEventReceived?.invoke(trimmedJson)
                        onLog?.invoke("   ✅ Event callback çağrıldı (main thread)")
                        onLog?.invoke("   💡 SetupScreen.onEventReceived tetiklenmeli")
                    } catch (e: Exception) {
                        onLog?.invoke("   ❌ Event callback exception: ${e.message}")
                        onLog?.invoke("   Exception tipi: ${e.javaClass.simpleName}")
                        onLog?.invoke("   Stack: ${e.stackTrace.take(5).joinToString("\n")}")
                    }
                }
            } else {
                // Eksik JSON - Buffer'da tut ve bir sonraki paketi bekle
                onLog?.invoke("   ⚠️ Eksik JSON tespit edildi, buffer'da tutuluyor")
                onLog?.invoke("   Buffer içeriği: $combinedJson")
                
                // Timeout ekle - 100ms içinde tamamlanmazsa buffer'ı temizle
                packetBufferTimeoutHandler = Handler(Looper.getMainLooper())
                packetBufferTimeoutHandler?.postDelayed({
                    onLog?.invoke("   ⏰ Paket buffer timeout - buffer temizleniyor")
                    onLog?.invoke("   Kayıp paket: ${packetBuffer.toString()}")
                    packetBuffer.clear()
                    // Duplicate kontrol değişkenlerini de temizle
                    lastSentEventType = null
                    lastSentEventMainIndex = -1
                    lastSentEventSubIndex = -1
                    lastSentEventTime = 0
                }, PACKET_BUFFER_TIMEOUT_MS)
            }
        } else {
            onLog?.invoke("⚠️ Event value boş veya null")
            onLog?.invoke("   value == null: ${value == null}")
            onLog?.invoke("   value.isEmpty(): ${value?.isEmpty() ?: "N/A"}")
            onLog?.invoke("   💡 Bu durumda event parse edilemez")
        }
    }
    
    fun isConnected(): Boolean {
        if (bluetoothGatt == null) return false
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                bluetoothGatt?.getConnectionState(bluetoothGatt?.device ?: return false) == BluetoothProfile.STATE_CONNECTED
            } else {
                false
            }
        } else {
            true // Android 12 öncesi için basit kontrol
        }
    }
    
    /**
     * Descriptor yazma işlemine devam et (setCharacteristicNotification başarılı olduktan sonra)
     */
    private fun proceedWithDescriptorWrite(bluetoothGatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        onLog?.invoke("   Adım 2: Descriptor (CCCD - 2902) aranıyor...")
        val descriptor = characteristic.getDescriptor(
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        )
        if (descriptor != null) {
            onLog?.invoke("   ✅ Descriptor bulundu (2902)")
            onLog?.invoke("   Descriptor UUID: ${descriptor.uuid}")
            
            // Mevcut descriptor değerini kontrol et
            val currentValue = descriptor.value
            onLog?.invoke("   Mevcut descriptor değeri: ${currentValue?.contentToString() ?: "null"}")
            
            // ENABLE_NOTIFICATION_VALUE değerini hazırla
            val enableValue = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            onLog?.invoke("   Yazılacak değer: ${enableValue.contentToString()}")
            onLog?.invoke("   Adım 3: writeDescriptor çağrılıyor...")
            
            val writeResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ (API 33): yeni imza
                onLog?.invoke("   Android 13+ API kullanılıyor (yeni imza)")
                bluetoothGatt.writeDescriptor(descriptor, enableValue)
            } else {
                // Eski imza (API 32 ve altı)
                onLog?.invoke("   Android 12 ve altı API kullanılıyor (eski imza)")
                descriptor.value = enableValue
                bluetoothGatt.writeDescriptor(descriptor)
            }
            
            onLog?.invoke("   ✅ writeDescriptor çağrıldı, sonuç: $writeResult")
            onLog?.invoke("   SDK Version: ${Build.VERSION.SDK_INT}")
            onLog?.invoke("   💡 onDescriptorWrite callback'i tetiklenmeli (status kontrol edilecek)")
            onLog?.invoke("   ⏳ Callback bekleniyor...")
            onLog?.invoke("   ⏱️  Timeout: ${DESCRIPTOR_WRITE_TIMEOUT_MS}ms (callback gelmezse retry yapılacak)")
            
            // Timeout kontrolü - eğer callback gelmezse retry yap
            descriptorWriteRetryCount = 0
            descriptorWriteTimeoutHandler?.removeCallbacksAndMessages(null)
            descriptorWriteTimeoutHandler = Handler(Looper.getMainLooper())
            descriptorWriteTimeoutHandler?.postDelayed({
                if (!isNotificationReady) {
                    onLog?.invoke("   ⏰ Timeout: onDescriptorWrite callback gelmedi!")
                    onLog?.invoke("   🔄 Timeout sonrası retry yapılacak...")
                    retryDescriptorWrite(bluetoothGatt, characteristic)
                }
            }, DESCRIPTOR_WRITE_TIMEOUT_MS)
        } else {
            onLog?.invoke("   ❌ Descriptor bulunamadı (2902)")
            onLog?.invoke("   Characteristic'in tüm descriptor'larını listele:")
            characteristic.descriptors.forEach { desc ->
                onLog?.invoke("      - ${desc.uuid}")
            }
            onLog?.invoke("   ⚠️  Bu notification subscribe'ı engelleyebilir")
        }
    }
    
    /**
     * Descriptor yazma işlemini retry yap
     */
    private fun retryDescriptorWrite(bluetoothGatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic?) {
        if (descriptorWriteRetryCount >= MAX_DESCRIPTOR_WRITE_RETRIES) {
            onLog?.invoke("   ❌ Maksimum retry sayısına ulaşıldı ($MAX_DESCRIPTOR_WRITE_RETRIES)")
            onLog?.invoke("   ⚠️  Descriptor yazma işlemi başarısız, bağlantı kesiliyor")
            mainHandler.post {
                onConnectionChanged?.invoke(false)
            }
            return
        }
        
        descriptorWriteRetryCount++
        onLog?.invoke("   🔄 Retry #$descriptorWriteRetryCount/$MAX_DESCRIPTOR_WRITE_RETRIES başlatılıyor...")
        onLog?.invoke("   ⏱️  ${DESCRIPTOR_WRITE_RETRY_DELAY_MS}ms sonra denenecek")
        
        mainHandler.postDelayed({
            if (bluetoothGatt == null || this.bluetoothGatt?.getConnectionState(bluetoothGatt.device) != BluetoothProfile.STATE_CONNECTED) {
                onLog?.invoke("   ⚠️  Retry: Cihaz bağlı değil, retry iptal edildi")
                return@postDelayed
            }
            
            try {
                val targetCharacteristic = characteristic ?: bluetoothGatt.getService(SERVICE_UUID)?.getCharacteristic(CHARACTERISTIC_UUID)
                if (targetCharacteristic == null) {
                    onLog?.invoke("   ❌ Retry: Characteristic bulunamadı")
                    retryDescriptorWrite(bluetoothGatt, null)
                    return@postDelayed
                }
                
                val retryDescriptor = targetCharacteristic.getDescriptor(
                    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                )
                if (retryDescriptor == null) {
                    onLog?.invoke("   ❌ Retry: Descriptor bulunamadı")
                    retryDescriptorWrite(bluetoothGatt, targetCharacteristic)
                    return@postDelayed
                }
                
                onLog?.invoke("   🔄 Retry #$descriptorWriteRetryCount: writeDescriptor çağrılıyor...")
                
                // Timeout'u yeniden başlat
                descriptorWriteTimeoutHandler?.removeCallbacksAndMessages(null)
                descriptorWriteTimeoutHandler = Handler(Looper.getMainLooper())
                descriptorWriteTimeoutHandler?.postDelayed({
                    if (!isNotificationReady) {
                        onLog?.invoke("   ⏰ Retry timeout: onDescriptorWrite callback gelmedi!")
                        retryDescriptorWrite(bluetoothGatt, targetCharacteristic)
                    }
                }, DESCRIPTOR_WRITE_TIMEOUT_MS)
                
                val writeResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    bluetoothGatt.writeDescriptor(retryDescriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    retryDescriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    bluetoothGatt.writeDescriptor(retryDescriptor)
                }
                
                onLog?.invoke("   ✅ Retry #$descriptorWriteRetryCount: writeDescriptor çağrıldı, sonuç: $writeResult")
            } catch (e: Exception) {
                onLog?.invoke("   ❌ Descriptor write retry exception: ${e.message}")
                onLog?.invoke("   Stack: ${e.stackTrace.take(3).joinToString("\n")}")
                retryDescriptorWrite(bluetoothGatt, null)
            }
        }, DESCRIPTOR_WRITE_RETRY_DELAY_MS)
    }
    
    /**
     * Polling-based read mekanizmasını başlat
     * Android sürekli read yapacak, bridge server her read'de son event'i döndürecek
     * Bu, macOS CoreBluetooth'un notify sorununu bypass eder
     */
    private fun startEventPolling(bluetoothGatt: BluetoothGatt) {
        onLog?.invoke("\n🔄 ========================================")
        onLog?.invoke("🔄 startEventPolling() ÇAĞRILDI - WRITE-BASED POLLING")
        onLog?.invoke("========================================")
        onLog?.invoke("   Polling interval: ${POLLING_INTERVAL_MS}ms")
        onLog?.invoke("   💡 Android sürekli WRITE yapacak, bridge server event hazırlayacak")
        onLog?.invoke("   💡 Write sonrası hemen READ yapılacak, bridge server son event'i döndürecek")
        onLog?.invoke("   💡 Bu, notify mekanizması çalışmasa bile event'leri almayı sağlar")
        onLog?.invoke("========================================\n")
        
        if (isPollingActive) {
            onLog?.invoke("⚠️  Polling zaten aktif, durduruluyor...")
            stopEventPolling()
        }
        
        isPollingActive = true
        eventPollingHandler = Handler(Looper.getMainLooper())
        
        val pollingRunnable = object : Runnable {
            override fun run() {
                if (!isPollingActive || bluetoothGatt == null) {
                    onLog?.invoke("   ⚠️  Polling durduruldu veya GATT null")
                    return
                }
                
                // Runtime permission check
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        onLog?.invoke("   ❌ BLUETOOTH_CONNECT izni yok, polling durduruluyor")
                        stopEventPolling()
                        return
                    }
                }
                
                try {
                    val characteristic = bluetoothGatt.getService(SERVICE_UUID)?.getCharacteristic(CHARACTERISTIC_UUID)
                    if (characteristic != null) {
                        // WRITE-BASED POLLING: Önce write yap, sonra read yap
                        // Bridge server write request'i alınca son event'i hazır tutacak
                        // Write sonrası hemen read yapınca son event'i alacağız
                        
                        // 1. Write request gönder (dummy data ile - sadece trigger için)
                        val writeData = byteArrayOf(0x01) // Dummy data - bridge server'a "event var mı?" sorusu
                        characteristic.value = writeData
                        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        
                        onLog?.invoke("   ✍️  writeCharacteristic() çağrılıyor...")
                        onLog?.invoke("   Characteristic UUID: ${characteristic.uuid}")
                        onLog?.invoke("   Write data: ${writeData.size} bytes")
                        
                        val writeResult = bluetoothGatt.writeCharacteristic(characteristic)
                        onLog?.invoke("   ✍️  Write request sonucu: $writeResult")
                        onLog?.invoke("   💡 writeResult=true ise: writeCharacteristic() başarılı, onCharacteristicWrite callback'i tetiklenecek")
                        onLog?.invoke("   💡 Write sonrası hemen read yapılacak, bridge server son event'i döndürecek")
                        
                        if (!writeResult) {
                            onLog?.invoke("   ⚠️  writeCharacteristic() false döndü - retry yapılacak")
                            // Retry mekanizması: 25ms sonra tekrar dene
                            eventPollingHandler?.postDelayed({
                                if (isPollingActive && bluetoothGatt != null) {
                                    try {
                                        val retryResult = bluetoothGatt.writeCharacteristic(characteristic)
                                        onLog?.invoke("   🔄 Retry writeCharacteristic() sonucu: $retryResult")
                                    } catch (e: Exception) {
                                        onLog?.invoke("   ❌ Retry exception: ${e.message}")
                                    }
                                }
                            }, 25)
                        }
                    } else {
                        onLog?.invoke("   ⚠️  Characteristic bulunamadı, polling durduruluyor")
                        stopEventPolling()
                    }
                } catch (e: Exception) {
                    onLog?.invoke("   ❌ Polling write exception: ${e.message}")
                    onLog?.invoke("   Exception tipi: ${e.javaClass.simpleName}")
                    onLog?.invoke("   Stack: ${e.stackTrace.take(3).joinToString("\n")}")
                }
                
                // Bir sonraki polling'i planla
                if (isPollingActive && eventPollingHandler != null) {
                    eventPollingHandler?.postDelayed(this, POLLING_INTERVAL_MS)
                }
            }
        }
        
        // İlk polling'i başlat
        eventPollingHandler?.postDelayed(pollingRunnable, POLLING_INTERVAL_MS)
        onLog?.invoke("✅ Write-based polling başlatıldı")
    }
    
    /**
     * Polling-based read mekanizmasını durdur
     */
    private fun stopEventPolling() {
        onLog?.invoke("🛑 Polling durduruluyor...")
        isPollingActive = false
        eventPollingHandler?.removeCallbacksAndMessages(null)
        eventPollingHandler = null
        lastReadValue = null
        onLog?.invoke("✅ Polling durduruldu")
    }
}

