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
        if (!isBluetoothEnabled()) {
            onLog?.invoke("Bluetooth etkin değil")
            return
        }
        
        if (isScanning) {
            stopScan()
        }
        
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        
        if (bluetoothLeScanner == null) {
            onLog?.invoke("Bluetooth başlatılamadı")
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
        
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val deviceName = device.name ?: "Bilinmeyen"
                val scanRecord = result.scanRecord
                
                // Service UUID kontrolü (opsiyonel - scanRecord'da varsa kontrol et)
                val hasServiceUuid = scanRecord?.serviceUuids?.contains(ParcelUuid(SERVICE_UUID)) == true
                
                // Device name kontrolü (öncelikli - daha esnek)
                val hasMatchingName = DEVICE_NAMES.any { it.equals(deviceName, ignoreCase = true) }

                // Eşleşme kuralı:
                // - Tercihen service UUID ile eşleşsin (en güvenlisi)
                // - Bazı cihazlar scanRecord.serviceUuids'i boş döndürebiliyor; o durumda isim eşleşmesini fallback olarak kabul et.
                if (hasServiceUuid || hasMatchingName) {
                    // EventTransport interface: deviceId olarak MAC address gönder
                    onDeviceFound?.invoke(device.address)
                } else {
                    // Cihaz eşleşmedi, hiçbir şey yapma
                }
            }
            
            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                results?.forEach { result ->
                    onScanResult(0, result)  // callbackType: 0 for batch
                }
            }
            
            override fun onScanFailed(errorCode: Int) {
                isScanning = false
                onLog?.invoke("Tarama hatası")
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }
        
        try {
            // Filter ile tara (Service UUID)
            bluetoothLeScanner?.startScan(scanFilters, scanSettings, scanCallback)
            isScanning = true
        } catch (e: Exception) {
            onLog?.invoke("Tarama başlatılamadı")
            isScanning = false
        }
    }
    
    fun stopScan() {
        scanCallback?.let { callback ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_SCAN
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    bluetoothLeScanner?.stopScan(callback)
                } else {
                    // İzin yok, hiçbir şey yapma
                }
            } else {
                @Suppress("DEPRECATION")
                bluetoothLeScanner?.stopScan(callback)
            }
        }
        scanCallback = null
        isScanning = false
    }
    
    fun connect(deviceId: String) {
        // deviceId MAC address olarak gelir, BluetoothDevice oluştur
        val device = try {
            bluetoothAdapter?.getRemoteDevice(deviceId)
        } catch (e: Exception) {
            return
        }
        
        if (device == null) {
            return
        }
        
        connectInternal(device)
    }
    
    private fun connectInternal(device: BluetoothDevice) {
        val timestamp = System.currentTimeMillis()
        
        // Önceki bağlantı denemesi varsa temizle
        connectionTimeoutHandler?.removeCallbacksAndMessages(null)
        connectionTimeoutHandler = null
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }
        
        // Mevcut bağlantıyı kapat (varsa)
        if (bluetoothGatt != null) {
            try {
                bluetoothGatt?.disconnect()
                bluetoothGatt?.close()
            } catch (e: Exception) {
                // Ignore
            }
            bluetoothGatt = null
        }
        
        // TRANSPORT_LE parametresi ekle (Samsung S24 Ultra için kritik)
        val gattResult = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, gattCallback, android.bluetooth.BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, gattCallback)
            }
        } catch (e: Exception) {
            null
        }
        
        bluetoothGatt = gattResult
        
        // connectGatt() sonucunu doğrula
        if (bluetoothGatt != null) {
            // Connection timeout başlat
            lastConnectionAttemptTime = timestamp
            lastConnectionDevice = device
            connectionTimeoutHandler = Handler(Looper.getMainLooper())
            connectionTimeoutHandler?.postDelayed({
                // Retry mekanizması
                if (connectionRetryCount < MAX_CONNECTION_RETRIES) {
                    connectionRetryCount++
                    lastConnectionDevice?.let { retryDevice ->
                        mainHandler.postDelayed({
                            connectInternal(retryDevice)
                        }, 1000) // 1 saniye bekle
                    }
                } else {
                    connectionRetryCount = 0
                    mainHandler.post {
                        onConnectionChanged?.invoke(false)
                    }
                }
            }, CONNECTION_TIMEOUT_MS)
        } else {
            // Retry mekanizması
            if (connectionRetryCount < MAX_CONNECTION_RETRIES) {
                connectionRetryCount++
                mainHandler.postDelayed({
                    connectInternal(device)
                }, 1000) // 1 saniye bekle
            } else {
                connectionRetryCount = 0
            }
        }
    }
    
    fun disconnect() {
        // Polling'i durdur
        stopEventPolling()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }
        
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
    }
    
    private val gattCallback = object : BluetoothGattCallback() {
        @Suppress("MissingPermission")
        override fun onConnectionStateChange(bluetoothGatt: BluetoothGatt, status: Int, newState: Int) {
            // Connection timeout'u iptal et
            connectionTimeoutHandler?.removeCallbacksAndMessages(null)
            connectionTimeoutHandler = null
            
            if (status != BluetoothGatt.GATT_SUCCESS) {
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
            
            // Main thread'de işlemleri yap - callback'ler farklı thread'den gelebilir
            mainHandler.post {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    // CONNECTED demek için erken: önce service discovery + notify subscribe başarıyla tamamlanmalı
                    isNotificationReady = false
                    onLog?.invoke("Bağlanıyor...")
                    
                    // MTU size'ı artır (paket bölünmesini önlemek için)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            if (ActivityCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.BLUETOOTH_CONNECT
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                bluetoothGatt.requestMtu(512) // 512 byte MTU (default 23 byte)
                            }
                        } else {
                            bluetoothGatt.requestMtu(512)
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
            // MTU değişikliği - kullanıcı için log gerekmez
        }
        
        override fun onServicesDiscovered(bluetoothGatt: BluetoothGatt, status: Int) {
            // Runtime permission check
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
            }
            
            if (status != BluetoothGatt.GATT_SUCCESS) {
                onLog?.invoke("Bağlantı hatası")
                return
            }
            
            val service = bluetoothGatt.getService(SERVICE_UUID)
            if (service == null) {
                onLog?.invoke("Cihaz uyumsuz")
                return
            }
            
            val characteristic = service.getCharacteristic(CHARACTERISTIC_UUID)
            
            if (characteristic == null) {
                onLog?.invoke("Cihaz uyumsuz")
                return
            }
            
            // Characteristic özelliklerini kontrol et
            val properties = characteristic.properties
            val hasNotify = (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
            
            if (!hasNotify) {
                onLog?.invoke("Cihaz uyumsuz")
                return
            }
            
            // Notification'ı etkinleştir
            try {
                var notificationEnabled = bluetoothGatt.setCharacteristicNotification(characteristic, true)
                
                // Eğer başarısız olursa, kısa bir delay ile tekrar dene
                if (!notificationEnabled) {
                    mainHandler.postDelayed({
                        notificationEnabled = bluetoothGatt.setCharacteristicNotification(characteristic, true)
                        if (!notificationEnabled) {
                            return@postDelayed
                        }
                        proceedWithDescriptorWrite(bluetoothGatt, characteristic)
                    }, 200)
                    return
                }
                
                proceedWithDescriptorWrite(bluetoothGatt, characteristic)
            } catch (e: Exception) {
                onLog?.invoke("Bağlantı hatası")
            }
        }
        
        override fun onDescriptorWrite(
            bluetoothGatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            // Runtime permission check
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
            }
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // CCCD yazıldı => notify subscribe hazır sayabiliriz
                val isCCCD = descriptor.uuid.toString().equals("00002902-0000-1000-8000-00805f9b34fb", ignoreCase = true)
                
                if (isCCCD) {
                    isNotificationReady = true
                    descriptorWriteRetryCount = 0 // Başarılı oldu, retry sayacını sıfırla
                    descriptorWriteTimeoutHandler?.removeCallbacksAndMessages(null) // Timeout'u iptal et
                    
                    // macOS CoreBluetooth'un subscribe'ı algılaması için önce delay, sonra multiple read request
                    mainHandler.postDelayed({
                        val characteristic = bluetoothGatt.getService(SERVICE_UUID)?.getCharacteristic(CHARACTERISTIC_UUID)
                        if (characteristic == null) {
                            return@postDelayed
                        }
                        
                        // İlk read request (hemen)
                        try {
                            bluetoothGatt.readCharacteristic(characteristic)
                        } catch (e: Exception) {
                            // Ignore
                        }
                        
                        // İkinci read request (200ms sonra)
                        mainHandler.postDelayed({
                            try {
                                bluetoothGatt.readCharacteristic(characteristic)
                            } catch (e: Exception) {
                                // Ignore
                            }
                            
                            // Üçüncü read request (200ms sonra)
                            mainHandler.postDelayed({
                                try {
                                    bluetoothGatt.readCharacteristic(characteristic)
                                    
                                    // onSubscribe tetiklenip tetiklenmediğini kontrol et
                                    // Eğer 3 saniye içinde event gelmezse, descriptor'ı tekrar yazmayı dene
                                    lastEventReceivedTime = System.currentTimeMillis()
                                    subscribeVerificationHandler?.removeCallbacksAndMessages(null)
                                    subscribeVerificationHandler = Handler(Looper.getMainLooper())
                                    subscribeVerificationHandler?.postDelayed({
                                        val timeSinceLastEvent = System.currentTimeMillis() - lastEventReceivedTime
                                        if (timeSinceLastEvent >= SUBSCRIBE_VERIFICATION_TIMEOUT_MS && isNotificationReady) {
                                            if (descriptorWriteRetryCount < 2) {
                                                val retryCharacteristic = bluetoothGatt.getService(SERVICE_UUID)?.getCharacteristic(CHARACTERISTIC_UUID)
                                                if (retryCharacteristic != null) {
                                                    proceedWithDescriptorWrite(bluetoothGatt, retryCharacteristic)
                                                }
                                            }
                                        }
                                    }, SUBSCRIBE_VERIFICATION_TIMEOUT_MS)
                                } catch (e: Exception) {
                                    // Ignore
                                }
                            }, 200)
                        }, 200)
                    }, 500)
                    
                    // Polling-based read mekanizmasını başlat (notify çalışmazsa yedek olarak)
                    startEventPolling(bluetoothGatt)
                    
                    mainHandler.post {
                        onLog?.invoke("Bağlandı")
                        onConnectionChanged?.invoke(true)
                    }
                }
            } else {
                isNotificationReady = false
                // Retry logic - descriptor yazmayı tekrar dene
                retryDescriptorWrite(bluetoothGatt, null)
                mainHandler.post {
                    onConnectionChanged?.invoke(false)
                }
            }
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
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Write sonrası hemen read yap (bridge server son event'i döndürecek)
                try {
                    bluetoothGatt.readCharacteristic(characteristic)
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
        
        // Polling-based read için onCharacteristicRead callback'i
        override fun onCharacteristicRead(
            bluetoothGatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val value = characteristic.value
                
                if (value != null && value.isNotEmpty()) {
                    // Duplicate event kontrolü (aynı event'i tekrar işleme)
                    val valueString = String(value, Charsets.UTF_8)
                    val lastValueString = lastReadValue?.let { String(it, Charsets.UTF_8) }
                    
                    if (valueString != lastValueString) {
                        lastReadValue = value
                        // Event'i parse et ve callback'i çağır
                        handleCharacteristicChanged(characteristic, value)
                    }
                }
            }
        }
    }

    private fun handleCharacteristicChanged(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray?
    ) {
        if (characteristic.uuid != CHARACTERISTIC_UUID) {
            return
        }

        if (value != null && value.isNotEmpty()) {
            val jsonString = String(value, Charsets.UTF_8)
            
            // Paket birleştirme mekanizması - Buffer timeout'unu iptal et
            packetBufferTimeoutHandler?.removeCallbacksAndMessages(null)
            
            // Buffer'a ekle
            packetBuffer.append(jsonString)
            val combinedJson = packetBuffer.toString()
            
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
                packetBuffer.clear()
                
                // Event alındı - subscribe başarılı demektir
                lastEventReceivedTime = System.currentTimeMillis()
                subscribeVerificationHandler?.removeCallbacksAndMessages(null) // Verification timeout'u iptal et
                
                // onEventReceived callback'inin set edilip edilmediğini kontrol et
                if (onEventReceived == null) {
                    return
                }
                
                // Callback'i main thread'de çağır - UI güncellemeleri için gerekli
                mainHandler.post {
                    try {
                        onEventReceived?.invoke(trimmedJson)
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            } else {
                // Eksik JSON - Buffer'da tut ve bir sonraki paketi bekle
                // Timeout ekle - 100ms içinde tamamlanmazsa buffer'ı temizle
                packetBufferTimeoutHandler = Handler(Looper.getMainLooper())
                packetBufferTimeoutHandler?.postDelayed({
                    packetBuffer.clear()
                    // Duplicate kontrol değişkenlerini de temizle
                    lastSentEventType = null
                    lastSentEventMainIndex = -1
                    lastSentEventSubIndex = -1
                    lastSentEventTime = 0
                }, PACKET_BUFFER_TIMEOUT_MS)
            }
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
        val descriptor = characteristic.getDescriptor(
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        )
        if (descriptor != null) {
            // ENABLE_NOTIFICATION_VALUE değerini hazırla
            val enableValue = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ (API 33): yeni imza
                bluetoothGatt.writeDescriptor(descriptor, enableValue)
            } else {
                // Eski imza (API 32 ve altı)
                descriptor.value = enableValue
                bluetoothGatt.writeDescriptor(descriptor)
            }
            
            // Timeout kontrolü - eğer callback gelmezse retry yap
            descriptorWriteRetryCount = 0
            descriptorWriteTimeoutHandler?.removeCallbacksAndMessages(null)
            descriptorWriteTimeoutHandler = Handler(Looper.getMainLooper())
            descriptorWriteTimeoutHandler?.postDelayed({
                if (!isNotificationReady) {
                    retryDescriptorWrite(bluetoothGatt, characteristic)
                }
            }, DESCRIPTOR_WRITE_TIMEOUT_MS)
        }
    }
    
    /**
     * Descriptor yazma işlemini retry yap
     */
    private fun retryDescriptorWrite(bluetoothGatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic?) {
        if (descriptorWriteRetryCount >= MAX_DESCRIPTOR_WRITE_RETRIES) {
            mainHandler.post {
                onConnectionChanged?.invoke(false)
            }
            return
        }
        
        descriptorWriteRetryCount++
        
        mainHandler.postDelayed({
            if (bluetoothGatt == null || this.bluetoothGatt?.getConnectionState(bluetoothGatt.device) != BluetoothProfile.STATE_CONNECTED) {
                return@postDelayed
            }
            
            try {
                val targetCharacteristic = characteristic ?: bluetoothGatt.getService(SERVICE_UUID)?.getCharacteristic(CHARACTERISTIC_UUID)
                if (targetCharacteristic == null) {
                    retryDescriptorWrite(bluetoothGatt, null)
                    return@postDelayed
                }
                
                val retryDescriptor = targetCharacteristic.getDescriptor(
                    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                )
                if (retryDescriptor == null) {
                    retryDescriptorWrite(bluetoothGatt, targetCharacteristic)
                    return@postDelayed
                }
                
                // Timeout'u yeniden başlat
                descriptorWriteTimeoutHandler?.removeCallbacksAndMessages(null)
                descriptorWriteTimeoutHandler = Handler(Looper.getMainLooper())
                descriptorWriteTimeoutHandler?.postDelayed({
                    if (!isNotificationReady) {
                        retryDescriptorWrite(bluetoothGatt, targetCharacteristic)
                    }
                }, DESCRIPTOR_WRITE_TIMEOUT_MS)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    bluetoothGatt.writeDescriptor(retryDescriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    retryDescriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    bluetoothGatt.writeDescriptor(retryDescriptor)
                }
            } catch (e: Exception) {
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
        if (isPollingActive) {
            stopEventPolling()
        }
        
        isPollingActive = true
        eventPollingHandler = Handler(Looper.getMainLooper())
        
        val pollingRunnable = object : Runnable {
            override fun run() {
                if (!isPollingActive || bluetoothGatt == null) {
                    return
                }
                
                // Runtime permission check
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
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
                        
                        val writeResult = bluetoothGatt.writeCharacteristic(characteristic)
                        
                        if (!writeResult) {
                            // Retry mekanizması: 25ms sonra tekrar dene
                            eventPollingHandler?.postDelayed({
                                if (isPollingActive && bluetoothGatt != null) {
                                    try {
                                        bluetoothGatt.writeCharacteristic(characteristic)
                                    } catch (e: Exception) {
                                        // Ignore
                                    }
                                }
                            }, 25)
                        }
                    } else {
                        stopEventPolling()
                    }
                } catch (e: Exception) {
                    // Ignore
                }
                
                // Bir sonraki polling'i planla
                if (isPollingActive && eventPollingHandler != null) {
                    eventPollingHandler?.postDelayed(this, POLLING_INTERVAL_MS)
                }
            }
        }
        
        // İlk polling'i başlat
        eventPollingHandler?.postDelayed(pollingRunnable, POLLING_INTERVAL_MS)
    }
    
    /**
     * Polling-based read mekanizmasını durdur
     */
    private fun stopEventPolling() {
        isPollingActive = false
        eventPollingHandler?.removeCallbacksAndMessages(null)
        eventPollingHandler = null
        lastReadValue = null
    }
}

