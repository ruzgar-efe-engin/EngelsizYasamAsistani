package com.gormeengelliler.android.service

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

class BLEEventTransport(private val context: Context) : EventTransport {
    companion object {
        // BLE UUID'leri - device'daki EventTransport.h ile aynı
        val SERVICE_UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc")
        val CHARACTERISTIC_UUID = UUID.fromString("12345678-1234-1234-1234-123456789abd")
        
        // Device name - device kodunda "GormeEngellilerKumanda" olarak geçiyor
        // Ama plan dosyasında "Engelsiz Yaşam Asistanı" veya mevcut isim olarak belirtilmiş
        // Her iki durumu da kontrol edeceğiz
        val DEVICE_NAMES = listOf("GormeEngellilerKumanda", "Engelsiz Yaşam Asistanı", "EngelsizYasamAsistani")
    }
    
    init {
        // Context tipini doğrula - Application context kullanılmalı
        val contextType = if (context.applicationContext == context) "Application" else "Activity/Service"
        android.util.Log.d("BLEEventTransport", "🔧 BLEEventTransport başlatılıyor - Context tipi: $contextType")
        if (context.applicationContext != context) {
            android.util.Log.w("BLEEventTransport", "⚠️  Activity/Service context kullanılıyor, Application context önerilir")
        }
    }
    
    private val bluetoothManager: BluetoothManager = 
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null
    
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
    
    // EventTransport interface callbacks
    override var onDeviceFound: ((String) -> Unit)? = null
    override var onEventReceived: ((String) -> Unit)? = null
    override var onConnectionStateChanged: ((Boolean) -> Unit)? = null
    
    // Internal callback for BluetoothDevice (used internally)
    private var onDeviceFoundInternal: ((BluetoothDevice) -> Unit)? = null
    
    override fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
    
    override fun startScan() {
        android.util.Log.d("BLEEventTransport", "🔍 BLE tarama başlatılıyor...")
        
        if (!isBluetoothEnabled()) {
            android.util.Log.e("BLEEventTransport", "❌ Bluetooth etkin değil, tarama başlatılamıyor")
            return
        }
        
        if (isScanning) {
            android.util.Log.d("BLEEventTransport", "⚠️  Tarama zaten aktif, durduruluyor...")
            stopScan()
        }
        
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        
        if (bluetoothLeScanner == null) {
            android.util.Log.e("BLEEventTransport", "❌ BluetoothLeScanner başlatılamadı")
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
        
        android.util.Log.d("BLEEventTransport", "✅ Scan settings hazır: SCAN_MODE_LOW_LATENCY")
        
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val deviceName = device.name ?: "Bilinmeyen"
                val scanRecord = result.scanRecord
                
                // Tüm cihazları log'la (debug için)
                val serviceUuids = scanRecord?.serviceUuids?.map { it.uuid.toString() } ?: emptyList()
                android.util.Log.d("BLEEventTransport", "📡 Cihaz bulundu: '$deviceName' (${device.address})")
                if (serviceUuids.isNotEmpty()) {
                    android.util.Log.d("BLEEventTransport", "   Service UUID'ler: $serviceUuids")
                }
                
                // Service UUID kontrolü (opsiyonel - scanRecord'da varsa kontrol et)
                val hasServiceUuid = scanRecord?.serviceUuids?.contains(ParcelUuid(SERVICE_UUID)) == true
                
                // Device name kontrolü (öncelikli - daha esnek)
                val hasMatchingName = DEVICE_NAMES.any { it.equals(deviceName, ignoreCase = true) }

                // Eşleşme kuralı:
                // - Tercihen service UUID ile eşleşsin (en güvenlisi)
                // - Bazı cihazlar scanRecord.serviceUuids'i boş döndürebiliyor; o durumda isim eşleşmesini fallback olarak kabul et.
                if (hasServiceUuid || hasMatchingName) {
                    android.util.Log.d("BLEEventTransport", "✅ Cihaz eşleşti: $deviceName")
                    android.util.Log.d("BLEEventTransport", "   Service UUID eşleşti: $hasServiceUuid")
                    android.util.Log.d("BLEEventTransport", "   Device name eşleşti: $hasMatchingName")
                    // EventTransport interface: deviceId olarak MAC address gönder
                    onDeviceFound?.invoke(device.address)
                    // Internal callback (geriye uyumluluk için)
                    onDeviceFoundInternal?.invoke(device)
                } else {
                    android.util.Log.d("BLEEventTransport", "❌ Cihaz eşleşmedi: $deviceName (aranan: $DEVICE_NAMES)")
                    android.util.Log.d("BLEEventTransport", "   ⚠️  Pairing mode aktif değil veya cihaz eşleşmedi")
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
                android.util.Log.e("BLEEventTransport", "Tarama hatası: $errorMsg")
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                android.util.Log.e("BLEEventTransport", "BLUETOOTH_SCAN izni yok!")
                return
            }
        }
        
        try {
            // Filter ile tara (Service UUID)
            bluetoothLeScanner?.startScan(scanFilters, scanSettings, scanCallback)
            isScanning = true
            android.util.Log.d("BLEEventTransport", "✅ BLE tarama başlatıldı")
            android.util.Log.d("BLEEventTransport", "   📡 Aranan cihazlar: $DEVICE_NAMES")
            android.util.Log.d("BLEEventTransport", "   📡 Service UUID: $SERVICE_UUID")
            android.util.Log.d("BLEEventTransport", "   ⚠️  NOT: Sadece pairing mode aktifse cihaz bulunabilir")
        } catch (e: Exception) {
            android.util.Log.e("BLEEventTransport", "❌ Tarama başlatılamadı: ${e.message}")
            isScanning = false
        }
    }
    
    override fun stopScan() {
        android.util.Log.d("BLEEventTransport", "🛑 BLE tarama durduruluyor...")
        scanCallback?.let { callback ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_SCAN
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    bluetoothLeScanner?.stopScan(callback)
                } else {
                    android.util.Log.w("BLEEventTransport", "⚠️  BLUETOOTH_SCAN izni yok, tarama durdurulamıyor")
                }
            } else {
                @Suppress("DEPRECATION")
                bluetoothLeScanner?.stopScan(callback)
            }
        }
        scanCallback = null
        isScanning = false
        android.util.Log.d("BLEEventTransport", "✅ BLE tarama durduruldu")
    }
    
    override fun connect(deviceId: String) {
        // deviceId MAC address olarak gelir, BluetoothDevice oluştur
        val device = try {
            bluetoothAdapter?.getRemoteDevice(deviceId)
        } catch (e: Exception) {
            android.util.Log.e("BLEEventTransport", "❌ Geçersiz MAC address: $deviceId")
            return
        }
        
        if (device == null) {
            android.util.Log.e("BLEEventTransport", "❌ BluetoothDevice oluşturulamadı: $deviceId")
            return
        }
        
        connectInternal(device)
    }
    
    private fun connectInternal(device: BluetoothDevice) {
        val timestamp = System.currentTimeMillis()
        val threadName = Thread.currentThread().name
        
        android.util.Log.d("BLEEventTransport", "\n🔌 ========================================")
        android.util.Log.d("BLEEventTransport", "🔌 connect() ÇAĞRILDI")
        android.util.Log.d("BLEEventTransport", "========================================")
        android.util.Log.d("BLEEventTransport", "   Timestamp: $timestamp")
        android.util.Log.d("BLEEventTransport", "   Thread: $threadName")
        android.util.Log.d("BLEEventTransport", "   Cihaz: ${device.name ?: "Bilinmeyen"} (${device.address})")
        android.util.Log.d("BLEEventTransport", "   Retry sayısı: $connectionRetryCount / $MAX_CONNECTION_RETRIES")
        
        // Önceki bağlantı denemesi varsa temizle
        connectionTimeoutHandler?.removeCallbacksAndMessages(null)
        connectionTimeoutHandler = null
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                android.util.Log.e("BLEEventTransport", "❌ BLUETOOTH_CONNECT izni yok, bağlantı yapılamıyor")
                return
            }
        }
        
        android.util.Log.d("BLEEventTransport", "✅ İzinler kontrol edildi")
        android.util.Log.d("BLEEventTransport", "   Transport: LE (Samsung için gerekli)")
        
        // Mevcut bağlantıyı kapat (varsa)
        if (gatt != null) {
            android.util.Log.d("BLEEventTransport", "   ⚠️  Mevcut GATT bağlantısı kapatılıyor...")
            try {
                gatt?.disconnect()
                gatt?.close()
            } catch (e: Exception) {
                android.util.Log.w("BLEEventTransport", "   ⚠️  Mevcut bağlantı kapatılırken hata: ${e.message}")
            }
            gatt = null
        }
        
        // TRANSPORT_LE parametresi ekle (Samsung S24 Ultra için kritik)
        android.util.Log.d("BLEEventTransport", "📡 connectGatt() çağrılıyor...")
        val gattResult = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.util.Log.d("BLEEventTransport", "   API Level: ${Build.VERSION.SDK_INT} (M+)")
                android.util.Log.d("BLEEventTransport", "   TRANSPORT_LE kullanılıyor")
                device.connectGatt(context, false, gattCallback, android.bluetooth.BluetoothDevice.TRANSPORT_LE)
            } else {
                android.util.Log.d("BLEEventTransport", "   API Level: ${Build.VERSION.SDK_INT} (< M)")
                android.util.Log.d("BLEEventTransport", "   Eski API kullanılıyor")
                device.connectGatt(context, false, gattCallback)
            }
        } catch (e: Exception) {
            android.util.Log.e("BLEEventTransport", "❌ connectGatt() EXCEPTION:")
            android.util.Log.e("BLEEventTransport", "   Exception: ${e.message}")
            android.util.Log.e("BLEEventTransport", "   Stack: ${e.stackTrace.take(5).joinToString("\n")}")
            null
        }
        
        gatt = gattResult
        
        // connectGatt() sonucunu doğrula
        if (gatt != null) {
            android.util.Log.d("BLEEventTransport", "✅ connectGatt() başarılı, gatt objesi oluşturuldu")
            android.util.Log.d("BLEEventTransport", "   gatt != null: true")
            android.util.Log.d("BLEEventTransport", "   gatt.toString(): ${gatt.toString().take(100)}")
            android.util.Log.d("BLEEventTransport", "   💡 onConnectionStateChange callback'i bekleniyor...")
            
            // Connection timeout başlat
            lastConnectionAttemptTime = timestamp
            lastConnectionDevice = device
            connectionTimeoutHandler = Handler(Looper.getMainLooper())
            connectionTimeoutHandler?.postDelayed({
                val elapsed = System.currentTimeMillis() - lastConnectionAttemptTime
                android.util.Log.w("BLEEventTransport", "\n⏰ ========================================")
                android.util.Log.w("BLEEventTransport", "⏰ CONNECTION TIMEOUT!")
                android.util.Log.w("BLEEventTransport", "========================================")
                android.util.Log.w("BLEEventTransport", "   Geçen süre: ${elapsed}ms (timeout: ${CONNECTION_TIMEOUT_MS}ms)")
                android.util.Log.w("BLEEventTransport", "   onConnectionStateChange callback'i tetiklenmedi!")
                
                // Retry mekanizması
                if (connectionRetryCount < MAX_CONNECTION_RETRIES) {
                    connectionRetryCount++
                    android.util.Log.w("BLEEventTransport", "   🔄 Retry yapılıyor ($connectionRetryCount / $MAX_CONNECTION_RETRIES)...")
                    lastConnectionDevice?.let { retryDevice ->
                        mainHandler.postDelayed({
                            connectInternal(retryDevice)
                        }, 1000) // 1 saniye bekle
                    }
                } else {
                    android.util.Log.e("BLEEventTransport", "   ❌ Maksimum retry sayısına ulaşıldı, bağlantı başarısız")
                    connectionRetryCount = 0
                    mainHandler.post {
                        onConnectionStateChanged?.invoke(false)
                    }
                }
            }, CONNECTION_TIMEOUT_MS)
            android.util.Log.d("BLEEventTransport", "   ⏱️  Connection timeout başlatıldı (${CONNECTION_TIMEOUT_MS}ms)")
        } else {
            android.util.Log.e("BLEEventTransport", "❌ connectGatt() başarısız, gatt objesi null!")
            android.util.Log.e("BLEEventTransport", "   Bu durumda bağlantı başlatılamaz")
            
            // Retry mekanizması
            if (connectionRetryCount < MAX_CONNECTION_RETRIES) {
                connectionRetryCount++
                android.util.Log.w("BLEEventTransport", "   🔄 Retry yapılıyor ($connectionRetryCount / $MAX_CONNECTION_RETRIES)...")
                mainHandler.postDelayed({
                    connectInternal(device)
                }, 1000) // 1 saniye bekle
            } else {
                android.util.Log.e("BLEEventTransport", "   ❌ Maksimum retry sayısına ulaşıldı")
                connectionRetryCount = 0
            }
        }
        android.util.Log.d("BLEEventTransport", "========================================\n")
    }
    
    override fun disconnect() {
        android.util.Log.d("BLEEventTransport", "🔌 Cihaz bağlantısı kesiliyor...")
        
        // Polling'i durdur
        stopEventPolling()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                android.util.Log.e("BLEEventTransport", "❌ BLUETOOTH_CONNECT izni yok, bağlantı kesilemiyor")
                return
            }
        }
        
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        android.util.Log.d("BLEEventTransport", "✅ Cihaz bağlantısı kesildi")
    }
    
    private val gattCallback = object : BluetoothGattCallback() {
        @Suppress("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val timestamp = System.currentTimeMillis()
            val threadName = Thread.currentThread().name
            val elapsed = if (lastConnectionAttemptTime > 0) timestamp - lastConnectionAttemptTime else 0
            
            android.util.Log.d("BLEEventTransport", "\n🔄🔄🔄 ========================================")
            android.util.Log.d("BLEEventTransport", "🔄 onConnectionStateChange CALLBACK TETİKLENDİ!")
            android.util.Log.d("BLEEventTransport", "========================================")
            android.util.Log.d("BLEEventTransport", "   Timestamp: $timestamp")
            android.util.Log.d("BLEEventTransport", "   Thread: $threadName")
            android.util.Log.d("BLEEventTransport", "   status: $status, newState: $newState")
            android.util.Log.d("BLEEventTransport", "   Bağlantı denemesinden geçen süre: ${elapsed}ms")
            
            // Connection timeout'u iptal et
            connectionTimeoutHandler?.removeCallbacksAndMessages(null)
            connectionTimeoutHandler = null
            android.util.Log.d("BLEEventTransport", "   ✅ Connection timeout iptal edildi")
            
            if (status != BluetoothGatt.GATT_SUCCESS) {
                android.util.Log.e("BLEEventTransport", "❌ Bağlantı hatası: status=$status")
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
                android.util.Log.e("BLEEventTransport", "   Status mesajı: $statusMessage")
                
                // Retry sayacını sıfırla
                connectionRetryCount = 0
                
                // Main thread'de callback çağır - EventTransport interface: Boolean
                mainHandler.post {
                    onConnectionStateChanged?.invoke(false)
                }
                return
            }
            
            // Başarılı bağlantı - retry sayacını sıfırla
            connectionRetryCount = 0
            android.util.Log.d("BLEEventTransport", "   ✅ Retry sayacı sıfırlandı")
            
            // Main thread'de işlemleri yap - callback'ler farklı thread'den gelebilir
            mainHandler.post {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    // CONNECTED demek için erken: önce service discovery + notify subscribe başarıyla tamamlanmalı
                    isNotificationReady = false
                    android.util.Log.d("BLEEventTransport", "✅ Cihaz bağlandı, MTU artırılıyor ve service'ler keşfediliyor...")
                    
                    // MTU size'ı artır (paket bölünmesini önlemek için)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            if (ActivityCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.BLUETOOTH_CONNECT
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                val mtuRequested = gatt.requestMtu(512) // 512 byte MTU (default 23 byte)
                                android.util.Log.d("BLEEventTransport", "   📡 MTU request gönderildi: $mtuRequested (512 byte isteniyor)")
                            }
                        } else {
                            val mtuRequested = gatt.requestMtu(512)
                            android.util.Log.d("BLEEventTransport", "   📡 MTU request gönderildi: $mtuRequested (512 byte isteniyor)")
                        }
                    }
                    
                    // Service'leri keşfet
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (ActivityCompat.checkSelfPermission(
                                context,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            gatt.discoverServices()
                        }
                    } else {
                        gatt.discoverServices()
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    android.util.Log.d("BLEEventTransport", "⚠️ Cihaz bağlantısı kesildi")
                    isNotificationReady = false
                    // Paket buffer'ı temizle
                    packetBuffer.clear()
                    packetBufferTimeoutHandler?.removeCallbacksAndMessages(null)
                    onConnectionStateChanged?.invoke(false)
                }
                
                // Not: CONNECTED durumunu burada yayınlamıyoruz; notify hazır olunca yayınlayacağız.
                // EventTransport interface: Boolean (true=connected, false=disconnected)
                if (newState != BluetoothProfile.STATE_CONNECTED) {
                    onConnectionStateChanged?.invoke(false)
                }
                // CONNECTED durumu notify hazır olunca yayınlanacak
            }
        }
        
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            android.util.Log.d("BLEEventTransport", "\n📡📡📡 ========================================")
            android.util.Log.d("BLEEventTransport", "📡 onMtuChanged CALLBACK TETİKLENDİ!")
            android.util.Log.d("BLEEventTransport", "========================================")
            android.util.Log.d("BLEEventTransport", "   MTU: $mtu bytes")
            android.util.Log.d("BLEEventTransport", "   Status: $status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                android.util.Log.d("BLEEventTransport", "   ✅ MTU başarıyla değiştirildi: $mtu bytes")
                android.util.Log.d("BLEEventTransport", "   💡 Paket bölünmesi azalacak")
            } else {
                android.util.Log.w("BLEEventTransport", "   ⚠️ MTU değiştirilemedi, default MTU kullanılacak")
            }
            android.util.Log.d("BLEEventTransport", "========================================\n")
        }
        
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val timestamp = System.currentTimeMillis()
            val threadName = Thread.currentThread().name
            
            android.util.Log.d("BLEEventTransport", "\n📡📡📡 ========================================")
            android.util.Log.d("BLEEventTransport", "📡 onServicesDiscovered CALLBACK TETİKLENDİ!")
            android.util.Log.d("BLEEventTransport", "========================================")
            android.util.Log.d("BLEEventTransport", "   Timestamp: $timestamp")
            android.util.Log.d("BLEEventTransport", "   Thread: $threadName")
            android.util.Log.d("BLEEventTransport", "   status: $status")
            android.util.Log.d("BLEEventTransport", "   GATT objesi: ${gatt.toString().take(100)}")
            
            // Runtime permission check
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    android.util.Log.e("BLEEventTransport", "❌ BLUETOOTH_CONNECT izni yok - callback çalışamıyor")
                    return
                }
            }
            
            if (status != BluetoothGatt.GATT_SUCCESS) {
                android.util.Log.e("BLEEventTransport", "❌ Service keşif hatası: status=$status")
                return
            }
            
            // Tüm service'leri listele
            val services = gatt.services
            android.util.Log.d("BLEEventTransport", "Bulunan service sayısı: ${services.size}")
            services.forEach { service ->
                android.util.Log.d("BLEEventTransport", "Service: ${service.uuid}")
            }
            
            val service = gatt.getService(SERVICE_UUID)
            if (service == null) {
                android.util.Log.e("BLEEventTransport", "❌ Service bulunamadı: $SERVICE_UUID")
                return
            }
            
            android.util.Log.d("BLEEventTransport", "✅ Service bulundu: ${service.uuid}")
            val characteristic = service.getCharacteristic(CHARACTERISTIC_UUID)
            
            if (characteristic == null) {
                android.util.Log.e("BLEEventTransport", "❌ Characteristic bulunamadı: $CHARACTERISTIC_UUID")
                return
            }
            
            android.util.Log.d("BLEEventTransport", "✅ Characteristic bulundu: ${characteristic.uuid}")
            
            // Characteristic özelliklerini kontrol et
            val properties = characteristic.properties
            android.util.Log.d("BLEEventTransport", "   Characteristic properties: $properties")
            val hasNotify = (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
            val hasRead = (properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0
            android.util.Log.d("BLEEventTransport", "   NOTIFY biti set mi: $hasNotify ${if (hasNotify) "✅" else "❌"}")
            android.util.Log.d("BLEEventTransport", "   READ biti set mi: $hasRead ${if (hasRead) "✅" else "❌"}")
            
            if (!hasNotify) {
                android.util.Log.e("BLEEventTransport", "   ❌ KRİTİK: Characteristic NOTIFY özelliğine sahip değil!")
                android.util.Log.e("BLEEventTransport", "   ⚠️  Bu durumda notification subscribe yapılamaz")
                android.util.Log.e("BLEEventTransport", "   ⚠️  Bridge server'ın characteristic tanımını kontrol edin")
                return
            }
            
            // Characteristic'in tüm descriptor'larını listele (doğrulama için)
            android.util.Log.d("BLEEventTransport", "   Characteristic descriptor'ları:")
            characteristic.descriptors.forEachIndexed { index, desc ->
                android.util.Log.d("BLEEventTransport", "      [$index] UUID: ${desc.uuid}")
                android.util.Log.d("BLEEventTransport", "      [$index] Value: ${desc.value?.contentToString() ?: "null"}")
            }
            
            // Notification'ı etkinleştir - permission zaten callback başında kontrol edildi
            android.util.Log.d("BLEEventTransport", "\n🔔 ========================================")
            android.util.Log.d("BLEEventTransport", "🔔 Notification Enable İşlemi Başlatılıyor")
            android.util.Log.d("BLEEventTransport", "========================================")
            android.util.Log.d("BLEEventTransport", "   Adım 1: setCharacteristicNotification çağrılıyor...")
            try {
                // İlk deneme
                var notificationEnabled = gatt.setCharacteristicNotification(characteristic, true)
                android.util.Log.d("BLEEventTransport", "   ✅ setCharacteristicNotification sonucu: $notificationEnabled")
                android.util.Log.d("BLEEventTransport", "   💡 Bu işlem başarılı olmalı (true dönmeli)")
                
                // Eğer başarısız olursa, kısa bir delay ile tekrar dene (alternatif yöntem)
                if (!notificationEnabled) {
                    android.util.Log.w("BLEEventTransport", "   ⚠️  setCharacteristicNotification başarısız, 200ms sonra tekrar deneniyor...")
                    mainHandler.postDelayed({
                        notificationEnabled = gatt.setCharacteristicNotification(characteristic, true)
                        android.util.Log.d("BLEEventTransport", "   Retry sonucu: $notificationEnabled")
                        if (!notificationEnabled) {
                            android.util.Log.e("BLEEventTransport", "   ❌ setCharacteristicNotification retry başarısız!")
                            android.util.Log.e("BLEEventTransport", "   ⚠️  Bu durumda descriptor yazma işlemi yapılmayacak")
                            return@postDelayed
                        }
                        // Retry başarılı oldu, descriptor yazma işlemine devam et
                        proceedWithDescriptorWrite(gatt, characteristic)
                    }, 200)
                    return
                }
                
                // Descriptor yazma işlemine devam et
                proceedWithDescriptorWrite(gatt, characteristic)
            } catch (e: Exception) {
                android.util.Log.e("BLEEventTransport", "   ❌ Notification enable exception: ${e.message}")
                android.util.Log.e("BLEEventTransport", "   Exception tipi: ${e.javaClass.simpleName}")
                android.util.Log.e("BLEEventTransport", "   Stack trace:")
                e.stackTrace.take(5).forEach { trace ->
                    android.util.Log.e("BLEEventTransport", "      at ${trace.className}.${trace.methodName}(${trace.fileName}:${trace.lineNumber})")
                }
            }
            android.util.Log.d("BLEEventTransport", "========================================\n")
        }
        
        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            val timestamp = System.currentTimeMillis()
            val threadName = Thread.currentThread().name
            
            android.util.Log.d("BLEEventTransport", "\n📝📝📝 ========================================")
            android.util.Log.d("BLEEventTransport", "📝 onDescriptorWrite CALLBACK TETİKLENDİ!")
            android.util.Log.d("BLEEventTransport", "========================================")
            android.util.Log.d("BLEEventTransport", "   Timestamp: $timestamp")
            android.util.Log.d("BLEEventTransport", "   Thread: $threadName")
            android.util.Log.d("BLEEventTransport", "   Descriptor UUID: ${descriptor.uuid}")
            android.util.Log.d("BLEEventTransport", "   Status: $status")
            android.util.Log.d("BLEEventTransport", "   Status açıklaması: ${if (status == BluetoothGatt.GATT_SUCCESS) "GATT_SUCCESS" else "HATA ($status)"}")
            android.util.Log.d("BLEEventTransport", "   GATT objesi: ${gatt.toString().take(100)}")
            
            // Runtime permission check
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    android.util.Log.e("BLEEventTransport", "   ❌ BLUETOOTH_CONNECT izni yok - callback çalışamıyor")
                    android.util.Log.e("BLEEventTransport", "   ⚠️  Bu callback iptal ediliyor!")
                    return
                }
            }
            
            // Descriptor değerini kontrol et
            val descriptorValue = descriptor.value
            android.util.Log.d("BLEEventTransport", "   Descriptor değeri: ${descriptorValue?.contentToString() ?: "null"}")
            android.util.Log.d("BLEEventTransport", "   Beklenen değer: ${BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE.contentToString()}")
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                android.util.Log.d("BLEEventTransport", "   ✅ Descriptor yazma başarılı!")
                android.util.Log.d("BLEEventTransport", "   CCCD (Client Characteristic Configuration Descriptor) yazıldı")
                
                // CCCD yazıldı => notify subscribe hazır sayabiliriz
                val isCCCD = descriptor.uuid.toString().equals("00002902-0000-1000-8000-00805f9b34fb", ignoreCase = true)
                android.util.Log.d("BLEEventTransport", "   CCCD kontrolü: $isCCCD")
                
                if (isCCCD) {
                    isNotificationReady = true
                    descriptorWriteRetryCount = 0 // Başarılı oldu, retry sayacını sıfırla
                    descriptorWriteTimeoutHandler?.removeCallbacksAndMessages(null) // Timeout'u iptal et
                    android.util.Log.d("BLEEventTransport", "   ✅ Notification subscribe hazır (CCCD başarıyla yazıldı)")
                    android.util.Log.d("BLEEventTransport", "   💡 Bridge server'ın onSubscribe callback'i tetiklenmeli")
                    android.util.Log.d("BLEEventTransport", "   💡 Artık event'ler alınabilir")
                    
                    // macOS CoreBluetooth'un subscribe'ı algılaması için önce delay, sonra multiple read request
                    android.util.Log.d("BLEEventTransport", "   ⏱️  Descriptor yazıldı, 500ms bekleniyor...")
                    mainHandler.postDelayed({
                        android.util.Log.d("BLEEventTransport", "   🔄 macOS CoreBluetooth uyumluluğu için multiple read request başlatılıyor...")
                        android.util.Log.d("BLEEventTransport", "   💡 3 kez read request yapılacak (her biri 200ms delay ile)")
                        
                        val characteristic = gatt.getService(SERVICE_UUID)?.getCharacteristic(CHARACTERISTIC_UUID)
                        if (characteristic == null) {
                            android.util.Log.w("BLEEventTransport", "   ⚠️  Characteristic bulunamadı, read yapılamadı")
                            return@postDelayed
                        }
                        
                        // İlk read request (hemen)
                        try {
                            android.util.Log.d("BLEEventTransport", "   📖 Read request #1 çağrılıyor...")
                            val readResult1 = gatt.readCharacteristic(characteristic)
                            android.util.Log.d("BLEEventTransport", "   ✅ Read request #1 çağrıldı, sonuç: $readResult1")
                        } catch (e: Exception) {
                            android.util.Log.e("BLEEventTransport", "   ❌ Read request #1 exception: ${e.message}")
                        }
                        
                        // İkinci read request (200ms sonra)
                        mainHandler.postDelayed({
                            try {
                                android.util.Log.d("BLEEventTransport", "   📖 Read request #2 çağrılıyor...")
                                val readResult2 = gatt.readCharacteristic(characteristic)
                                android.util.Log.d("BLEEventTransport", "   ✅ Read request #2 çağrıldı, sonuç: $readResult2")
                            } catch (e: Exception) {
                                android.util.Log.e("BLEEventTransport", "   ❌ Read request #2 exception: ${e.message}")
                            }
                            
                            // Üçüncü read request (200ms sonra)
                            mainHandler.postDelayed({
                                try {
                                    android.util.Log.d("BLEEventTransport", "   📖 Read request #3 çağrılıyor...")
                                    val readResult3 = gatt.readCharacteristic(characteristic)
                                    android.util.Log.d("BLEEventTransport", "   ✅ Read request #3 çağrıldı, sonuç: $readResult3")
                                    android.util.Log.d("BLEEventTransport", "   ✅ Tüm read request'ler tamamlandı")
                                    android.util.Log.d("BLEEventTransport", "   💡 Bridge server'ın onSubscribe callback'i tetiklenmeli")
                                    
                                    // onSubscribe tetiklenip tetiklenmediğini kontrol et
                                    // Eğer 3 saniye içinde event gelmezse, descriptor'ı tekrar yazmayı dene
                                    android.util.Log.d("BLEEventTransport", "   ⏱️  Subscribe verification başlatılıyor (${SUBSCRIBE_VERIFICATION_TIMEOUT_MS}ms)...")
                                    lastEventReceivedTime = System.currentTimeMillis()
                                    subscribeVerificationHandler?.removeCallbacksAndMessages(null)
                                    subscribeVerificationHandler = Handler(Looper.getMainLooper())
                                    subscribeVerificationHandler?.postDelayed({
                                        val timeSinceLastEvent = System.currentTimeMillis() - lastEventReceivedTime
                                        if (timeSinceLastEvent >= SUBSCRIBE_VERIFICATION_TIMEOUT_MS && isNotificationReady) {
                                            android.util.Log.w("BLEEventTransport", "   ⏰ Subscribe verification timeout: Event gelmedi!")
                                            android.util.Log.w("BLEEventTransport", "   🔄 Descriptor'ı tekrar yazmayı deniyoruz (maksimum 2 retry)...")
                                            if (descriptorWriteRetryCount < 2) {
                                                val retryCharacteristic = gatt.getService(SERVICE_UUID)?.getCharacteristic(CHARACTERISTIC_UUID)
                                                if (retryCharacteristic != null) {
                                                    proceedWithDescriptorWrite(gatt, retryCharacteristic)
                                                }
                                            } else {
                                                android.util.Log.e("BLEEventTransport", "   ❌ Maksimum subscribe verification retry sayısına ulaşıldı")
                                            }
                                        } else if (timeSinceLastEvent < SUBSCRIBE_VERIFICATION_TIMEOUT_MS) {
                                            android.util.Log.d("BLEEventTransport", "   ✅ Event alındı, subscribe başarılı!")
                                        }
                                    }, SUBSCRIBE_VERIFICATION_TIMEOUT_MS)
                                } catch (e: Exception) {
                                    android.util.Log.e("BLEEventTransport", "   ❌ Read request #3 exception: ${e.message}")
                                }
                            }, 200)
                        }, 200)
                    }, 500)
                    
                    android.util.Log.d("BLEEventTransport", "   📡 STATE_CONNECTED durumu yayınlanıyor...")
                    
                    // Polling-based read mekanizmasını başlat (notify çalışmazsa yedek olarak)
                    android.util.Log.d("BLEEventTransport", "   🔄 Polling-based read mekanizması başlatılıyor...")
                    startEventPolling(gatt)
                    
                    mainHandler.post {
                        onConnectionStateChanged?.invoke(true)
                    }
                } else {
                    android.util.Log.w("BLEEventTransport", "   ⚠️  Bu descriptor CCCD değil, notification subscribe hazır değil")
                }
            } else {
                android.util.Log.e("BLEEventTransport", "   ❌ Descriptor yazma hatası!")
                android.util.Log.e("BLEEventTransport", "   Status kodu: $status")
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
                android.util.Log.e("BLEEventTransport", "   Status mesajı: $statusMessage")
                android.util.Log.e("BLEEventTransport", "   ⚠️  Bu hata notification subscribe'ı engelleyebilir")
                android.util.Log.e("BLEEventTransport", "   ⚠️  Bridge server'ın onSubscribe callback'i tetiklenmeyebilir")
                isNotificationReady = false
                // Retry logic - descriptor yazmayı tekrar dene
                android.util.Log.w("BLEEventTransport", "   🔄 Retry logic başlatılıyor...")
                retryDescriptorWrite(gatt, null)
                mainHandler.post {
                    onConnectionStateChanged?.invoke(false)
                }
            }
            android.util.Log.d("BLEEventTransport", "========================================\n")
        }
        
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            // API 32 ve altı cihazlar buraya düşer (API 33+ için overload aşağıda)
            handleCharacteristicChanged(characteristic, characteristic.value)
        }

        // Android 13+ (API 33) için yeni callback overload'u.
        // Bazı cihazlarda eski overload hiç çağrılmayabiliyor; bu yüzden ikisini de handle ediyoruz.
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleCharacteristicChanged(characteristic, value)
        }
        
        // Write-based polling için onCharacteristicWrite callback'i
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            val timestamp = System.currentTimeMillis()
            val threadName = Thread.currentThread().name
            
            android.util.Log.d("BLEEventTransport", "\n✍️✍️✍️  ========================================")
            android.util.Log.d("BLEEventTransport", "✍️  onCharacteristicWrite CALLBACK TETİKLENDİ!")
            android.util.Log.d("BLEEventTransport", "========================================")
            android.util.Log.d("BLEEventTransport", "   Timestamp: $timestamp")
            android.util.Log.d("BLEEventTransport", "   Thread: $threadName")
            android.util.Log.d("BLEEventTransport", "   Status: $status")
            android.util.Log.d("BLEEventTransport", "   UUID: ${characteristic.uuid}")
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                android.util.Log.d("BLEEventTransport", "   ✅ Write başarılı")
                android.util.Log.d("BLEEventTransport", "   💡 Bridge server write request'i aldı, son event'i hazırladı")
                android.util.Log.d("BLEEventTransport", "   📖 Şimdi hemen read yapılacak, bridge server son event'i döndürecek")
                
                // Write sonrası hemen read yap (bridge server son event'i döndürecek)
                try {
                    val readResult = gatt.readCharacteristic(characteristic)
                    android.util.Log.d("BLEEventTransport", "   📖 Write sonrası read request sonucu: $readResult")
                    android.util.Log.d("BLEEventTransport", "   💡 readResult=true ise: onCharacteristicRead callback'i tetiklenecek")
                } catch (e: Exception) {
                    android.util.Log.e("BLEEventTransport", "   ❌ Write sonrası read exception: ${e.message}")
                }
            } else {
                android.util.Log.e("BLEEventTransport", "   ❌ Write hatası: status=$status")
            }
            android.util.Log.d("BLEEventTransport", "========================================\n")
        }
        
        // Polling-based read için onCharacteristicRead callback'i
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            val timestamp = System.currentTimeMillis()
            val threadName = Thread.currentThread().name
            
            android.util.Log.d("BLEEventTransport", "\n📖📖📖 ========================================")
            android.util.Log.d("BLEEventTransport", "📖 onCharacteristicRead CALLBACK TETİKLENDİ!")
            android.util.Log.d("BLEEventTransport", "========================================")
            android.util.Log.d("BLEEventTransport", "   Timestamp: $timestamp")
            android.util.Log.d("BLEEventTransport", "   Thread: $threadName")
            android.util.Log.d("BLEEventTransport", "   Status: $status")
            android.util.Log.d("BLEEventTransport", "   UUID: ${characteristic.uuid}")
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val value = characteristic.value
                android.util.Log.d("BLEEventTransport", "   ✅ Read başarılı: ${value?.size ?: 0} bytes")
                android.util.Log.d("BLEEventTransport", "   value null mu: ${value == null}")
                android.util.Log.d("BLEEventTransport", "   value empty mu: ${value?.isEmpty() ?: true}")
                
                if (value != null && value.isNotEmpty()) {
                    // Duplicate event kontrolü (aynı event'i tekrar işleme)
                    val valueString = String(value, Charsets.UTF_8)
                    val lastValueString = lastReadValue?.let { String(it, Charsets.UTF_8) }
                    
                    android.util.Log.d("BLEEventTransport", "   📦 Value içeriği (ilk 100 karakter): ${valueString.take(100)}")
                    android.util.Log.d("BLEEventTransport", "   📦 Last value içeriği: ${lastValueString?.take(100) ?: "null"}")
                    
                    if (valueString != lastValueString) {
                        android.util.Log.d("BLEEventTransport", "   ✅ Yeni event alındı (polling-based read)")
                        android.util.Log.d("BLEEventTransport", "   📤 handleCharacteristicChanged() çağrılıyor...")
                        lastReadValue = value
                        // Event'i parse et ve callback'i çağır
                        handleCharacteristicChanged(characteristic, value)
                    } else {
                        android.util.Log.d("BLEEventTransport", "   ℹ️  Aynı event (duplicate), atlanıyor")
                        android.util.Log.d("BLEEventTransport", "   💡 Bridge server henüz yeni event göndermedi")
                    }
                } else {
                    android.util.Log.d("BLEEventTransport", "   ℹ️  Read response boş")
                    android.util.Log.d("BLEEventTransport", "   💡 Bridge server'da _lastEvent boş olabilir")
                    android.util.Log.d("BLEEventTransport", "   💡 Veya event henüz _lastEvent'e kaydedilmemiş olabilir")
                }
            } else {
                android.util.Log.e("BLEEventTransport", "   ❌ Read hatası: status=$status")
                android.util.Log.e("BLEEventTransport", "   Status açıklaması: ${when(status) {
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
            android.util.Log.d("BLEEventTransport", "========================================\n")
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
        android.util.Log.e("BLEEventTransport", "🔔🔔🔔 NOTIFY GELDI 🔔🔔🔔")
        android.util.Log.e("BLEEventTransport", "========================================")
        android.util.Log.e("BLEEventTransport", "✅ onCharacteristicChanged CALLBACK TETİKLENDİ!")
        android.util.Log.e("BLEEventTransport", "   Bu log görünüyorsa: notify mekanizması ÇALIŞIYOR")
        android.util.Log.e("BLEEventTransport", "   Bu log görünmüyorsa: notify mekanizması ÇALIŞMIYOR")
        android.util.Log.e("BLEEventTransport", "========================================")
        
        android.util.Log.d("BLEEventTransport", "\n📡📡📡 ========================================")
        android.util.Log.d("BLEEventTransport", "📡 handleCharacteristicChanged ÇAĞRILDI!")
        android.util.Log.d("BLEEventTransport", "========================================")
        android.util.Log.d("BLEEventTransport", "   Timestamp: $timestamp")
        android.util.Log.d("BLEEventTransport", "   Thread: $threadName")
        android.util.Log.d("BLEEventTransport", "   uuid: ${characteristic.uuid}")
        android.util.Log.d("BLEEventTransport", "   value: ${value?.size ?: 0} bytes")
        android.util.Log.d("BLEEventTransport", "   value null mu: ${value == null}")
        android.util.Log.d("BLEEventTransport", "   value empty mu: ${value?.isEmpty() ?: true}")
        android.util.Log.d("BLEEventTransport", "========================================")
        android.util.Log.d("BLEEventTransport", "📡 onCharacteristicChanged: uuid=${characteristic.uuid}")
        
        // onEventReceived callback'inin set edilip edilmediğini kontrol et
        android.util.Log.d("BLEEventTransport", "   onEventReceived callback durumu: ${if (onEventReceived != null) "VAR ✅" else "YOK ❌"}")

        if (characteristic.uuid != CHARACTERISTIC_UUID) {
            android.util.Log.d("BLEEventTransport", "❌ UUID eşleşmedi: ${characteristic.uuid} != $CHARACTERISTIC_UUID")
            return
        }

        if (value != null && value.isNotEmpty()) {
            val jsonString = String(value, Charsets.UTF_8)
            android.util.Log.d("BLEEventTransport", "✅ Event paketi alındı (BLE): $jsonString")
            android.util.Log.d("BLEEventTransport", "   Paket uzunluğu: ${jsonString.length} karakter")
            android.util.Log.d("BLEEventTransport", "   Paket içeriği (ilk 100 karakter): ${jsonString.take(100)}")
            
            // Paket birleştirme mekanizması - Buffer timeout'unu iptal et
            packetBufferTimeoutHandler?.removeCallbacksAndMessages(null)
            
            // Buffer'a ekle
            packetBuffer.append(jsonString)
            val combinedJson = packetBuffer.toString()
            android.util.Log.d("BLEEventTransport", "   📦 Buffer'a eklendi, toplam uzunluk: ${combinedJson.length} karakter")
            
            // JSON validation - Tam JSON mu kontrol et
            val isCompleteJson = try {
                // JSON'un başında { ve sonunda } veya \n var mı kontrol et
                val trimmed = combinedJson.trim()
                trimmed.startsWith("{") && (trimmed.endsWith("}") || trimmed.endsWith("}\n"))
            } catch (e: Exception) {
                false
            }
            
            if (isCompleteJson) {
                // Tam JSON - Buffer'ı temizle ve event'i gönder
                android.util.Log.d("BLEEventTransport", "   ✅ Tam JSON tespit edildi, event gönderiliyor")
                packetBuffer.clear()
                
                // Event alındı - subscribe başarılı demektir
                val eventReceivedTime = System.currentTimeMillis()
                lastEventReceivedTime = eventReceivedTime
                subscribeVerificationHandler?.removeCallbacksAndMessages(null) // Verification timeout'u iptal et
                android.util.Log.d("BLEEventTransport", "   ✅ Subscribe verification başarılı - event alındı!")
                android.util.Log.d("BLEEventTransport", "   Event alınma zamanı: $eventReceivedTime")
                android.util.Log.d("BLEEventTransport", "   💡 sendEvent çağrıldı: type=X m=Y s=Z - Paket alındı")
                
                // onEventReceived callback'inin set edilip edilmediğini kontrol et
                if (onEventReceived == null) {
                    android.util.Log.e("BLEEventTransport", "   ❌ KRİTİK: onEventReceived callback NULL!")
                    android.util.Log.e("BLEEventTransport", "   ⚠️  Event alındı ama callback yok, event kaybolacak!")
                    android.util.Log.e("BLEEventTransport", "   💡 SetupScreen'de onEventReceived callback'i set edilmeli")
                    return
                }
                
                // Callback'i main thread'de çağır - UI güncellemeleri için gerekli
                android.util.Log.d("BLEEventTransport", "   📤 Event callback main thread'e gönderiliyor...")
                android.util.Log.d("BLEEventTransport", "   📤 onEventReceived callback çağrılacak: $combinedJson")
                mainHandler.post {
                    try {
                        val callbackTimestamp = System.currentTimeMillis()
                        android.util.Log.d("BLEEventTransport", "   ✅ onEventReceived callback çağrılıyor (main thread)")
                        android.util.Log.d("BLEEventTransport", "   Callback timestamp: $callbackTimestamp")
                        android.util.Log.d("BLEEventTransport", "   📥 Main thread'de event callback çağrılıyor (timestamp: $callbackTimestamp)")
                        onEventReceived?.invoke(combinedJson.trim())
                        android.util.Log.d("BLEEventTransport", "   ✅ Event callback çağrıldı (main thread)")
                        android.util.Log.d("BLEEventTransport", "   💡 SetupScreen.onEventReceived tetiklenmeli")
                    } catch (e: Exception) {
                        android.util.Log.e("BLEEventTransport", "   ❌ Event callback exception: ${e.message}")
                        android.util.Log.e("BLEEventTransport", "   Exception tipi: ${e.javaClass.simpleName}")
                        android.util.Log.e("BLEEventTransport", "   Stack: ${e.stackTrace.take(5).joinToString("\n")}")
                    }
                }
            } else {
                // Eksik JSON - Buffer'da tut ve bir sonraki paketi bekle
                android.util.Log.w("BLEEventTransport", "   ⚠️ Eksik JSON tespit edildi, buffer'da tutuluyor")
                android.util.Log.w("BLEEventTransport", "   Buffer içeriği: $combinedJson")
                
                // Timeout ekle - 100ms içinde tamamlanmazsa buffer'ı temizle
                packetBufferTimeoutHandler = Handler(Looper.getMainLooper())
                packetBufferTimeoutHandler?.postDelayed({
                    android.util.Log.w("BLEEventTransport", "   ⏰ Paket buffer timeout - buffer temizleniyor")
                    android.util.Log.w("BLEEventTransport", "   Kayıp paket: ${packetBuffer.toString()}")
                    packetBuffer.clear()
                }, PACKET_BUFFER_TIMEOUT_MS)
            }
        } else {
            android.util.Log.w("BLEEventTransport", "⚠️ Event value boş veya null")
            android.util.Log.w("BLEEventTransport", "   value == null: ${value == null}")
            android.util.Log.w("BLEEventTransport", "   value.isEmpty(): ${value?.isEmpty() ?: "N/A"}")
            android.util.Log.w("BLEEventTransport", "   💡 Bu durumda event parse edilemez")
        }
    }
    
    override fun isConnected(): Boolean {
        if (gatt == null) return false
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                gatt?.getConnectionState(gatt?.device ?: return false) == BluetoothProfile.STATE_CONNECTED
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
    private fun proceedWithDescriptorWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        android.util.Log.d("BLEEventTransport", "   Adım 2: Descriptor (CCCD - 2902) aranıyor...")
        val descriptor = characteristic.getDescriptor(
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        )
        if (descriptor != null) {
            android.util.Log.d("BLEEventTransport", "   ✅ Descriptor bulundu (2902)")
            android.util.Log.d("BLEEventTransport", "   Descriptor UUID: ${descriptor.uuid}")
            
            // Mevcut descriptor değerini kontrol et
            val currentValue = descriptor.value
            android.util.Log.d("BLEEventTransport", "   Mevcut descriptor değeri: ${currentValue?.contentToString() ?: "null"}")
            
            // ENABLE_NOTIFICATION_VALUE değerini hazırla
            val enableValue = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            android.util.Log.d("BLEEventTransport", "   Yazılacak değer: ${enableValue.contentToString()}")
            android.util.Log.d("BLEEventTransport", "   Adım 3: writeDescriptor çağrılıyor...")
            
            val writeResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ (API 33): yeni imza
                android.util.Log.d("BLEEventTransport", "   Android 13+ API kullanılıyor (yeni imza)")
                gatt.writeDescriptor(descriptor, enableValue)
            } else {
                // Eski imza (API 32 ve altı)
                android.util.Log.d("BLEEventTransport", "   Android 12 ve altı API kullanılıyor (eski imza)")
                descriptor.value = enableValue
                gatt.writeDescriptor(descriptor)
            }
            
            android.util.Log.d("BLEEventTransport", "   ✅ writeDescriptor çağrıldı, sonuç: $writeResult")
            android.util.Log.d("BLEEventTransport", "   SDK Version: ${Build.VERSION.SDK_INT}")
            android.util.Log.d("BLEEventTransport", "   💡 onDescriptorWrite callback'i tetiklenmeli (status kontrol edilecek)")
            android.util.Log.d("BLEEventTransport", "   ⏳ Callback bekleniyor...")
            android.util.Log.d("BLEEventTransport", "   ⏱️  Timeout: ${DESCRIPTOR_WRITE_TIMEOUT_MS}ms (callback gelmezse retry yapılacak)")
            
            // Timeout kontrolü - eğer callback gelmezse retry yap
            descriptorWriteRetryCount = 0
            descriptorWriteTimeoutHandler?.removeCallbacksAndMessages(null)
            descriptorWriteTimeoutHandler = Handler(Looper.getMainLooper())
            descriptorWriteTimeoutHandler?.postDelayed({
                if (!isNotificationReady) {
                    android.util.Log.w("BLEEventTransport", "   ⏰ Timeout: onDescriptorWrite callback gelmedi!")
                    android.util.Log.w("BLEEventTransport", "   🔄 Timeout sonrası retry yapılacak...")
                    retryDescriptorWrite(gatt, characteristic)
                }
            }, DESCRIPTOR_WRITE_TIMEOUT_MS)
        } else {
            android.util.Log.e("BLEEventTransport", "   ❌ Descriptor bulunamadı (2902)")
            android.util.Log.e("BLEEventTransport", "   Characteristic'in tüm descriptor'larını listele:")
            characteristic.descriptors.forEach { desc ->
                android.util.Log.e("BLEEventTransport", "      - ${desc.uuid}")
            }
            android.util.Log.e("BLEEventTransport", "   ⚠️  Bu notification subscribe'ı engelleyebilir")
        }
    }
    
    /**
     * Descriptor yazma işlemini retry yap
     */
    private fun retryDescriptorWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic?) {
        if (descriptorWriteRetryCount >= MAX_DESCRIPTOR_WRITE_RETRIES) {
            android.util.Log.e("BLEEventTransport", "   ❌ Maksimum retry sayısına ulaşıldı ($MAX_DESCRIPTOR_WRITE_RETRIES)")
            android.util.Log.e("BLEEventTransport", "   ⚠️  Descriptor yazma işlemi başarısız, bağlantı kesiliyor")
            mainHandler.post {
                onConnectionStateChanged?.invoke(false)
            }
            return
        }
        
        descriptorWriteRetryCount++
        android.util.Log.w("BLEEventTransport", "   🔄 Retry #$descriptorWriteRetryCount/$MAX_DESCRIPTOR_WRITE_RETRIES başlatılıyor...")
        android.util.Log.w("BLEEventTransport", "   ⏱️  ${DESCRIPTOR_WRITE_RETRY_DELAY_MS}ms sonra denenecek")
        
        mainHandler.postDelayed({
            if (gatt == null || this.gatt?.getConnectionState(gatt.device) != BluetoothProfile.STATE_CONNECTED) {
                android.util.Log.w("BLEEventTransport", "   ⚠️  Retry: Cihaz bağlı değil, retry iptal edildi")
                return@postDelayed
            }
            
            try {
                val targetCharacteristic = characteristic ?: gatt.getService(SERVICE_UUID)?.getCharacteristic(CHARACTERISTIC_UUID)
                if (targetCharacteristic == null) {
                    android.util.Log.e("BLEEventTransport", "   ❌ Retry: Characteristic bulunamadı")
                    retryDescriptorWrite(gatt, null)
                    return@postDelayed
                }
                
                val retryDescriptor = targetCharacteristic.getDescriptor(
                    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                )
                if (retryDescriptor == null) {
                    android.util.Log.e("BLEEventTransport", "   ❌ Retry: Descriptor bulunamadı")
                    retryDescriptorWrite(gatt, targetCharacteristic)
                    return@postDelayed
                }
                
                android.util.Log.d("BLEEventTransport", "   🔄 Retry #$descriptorWriteRetryCount: writeDescriptor çağrılıyor...")
                
                // Timeout'u yeniden başlat
                descriptorWriteTimeoutHandler?.removeCallbacksAndMessages(null)
                descriptorWriteTimeoutHandler = Handler(Looper.getMainLooper())
                descriptorWriteTimeoutHandler?.postDelayed({
                    if (!isNotificationReady) {
                        android.util.Log.w("BLEEventTransport", "   ⏰ Retry timeout: onDescriptorWrite callback gelmedi!")
                        retryDescriptorWrite(gatt, targetCharacteristic)
                    }
                }, DESCRIPTOR_WRITE_TIMEOUT_MS)
                
                val writeResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(retryDescriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    retryDescriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(retryDescriptor)
                }
                
                android.util.Log.d("BLEEventTransport", "   ✅ Retry #$descriptorWriteRetryCount: writeDescriptor çağrıldı, sonuç: $writeResult")
            } catch (e: Exception) {
                android.util.Log.e("BLEEventTransport", "   ❌ Descriptor write retry exception: ${e.message}")
                android.util.Log.e("BLEEventTransport", "   Stack: ${e.stackTrace.take(3).joinToString("\n")}")
                retryDescriptorWrite(gatt, null)
            }
        }, DESCRIPTOR_WRITE_RETRY_DELAY_MS)
    }
    
    /**
     * Polling-based read mekanizmasını başlat
     * Android sürekli read yapacak, bridge server her read'de son event'i döndürecek
     * Bu, macOS CoreBluetooth'un notify sorununu bypass eder
     */
    private fun startEventPolling(gatt: BluetoothGatt) {
        android.util.Log.d("BLEEventTransport", "\n🔄 ========================================")
        android.util.Log.d("BLEEventTransport", "🔄 startEventPolling() ÇAĞRILDI - WRITE-BASED POLLING")
        android.util.Log.d("BLEEventTransport", "========================================")
        android.util.Log.d("BLEEventTransport", "   Polling interval: ${POLLING_INTERVAL_MS}ms")
        android.util.Log.d("BLEEventTransport", "   💡 Android sürekli WRITE yapacak, bridge server event hazırlayacak")
        android.util.Log.d("BLEEventTransport", "   💡 Write sonrası hemen READ yapılacak, bridge server son event'i döndürecek")
        android.util.Log.d("BLEEventTransport", "   💡 Bu, notify mekanizması çalışmasa bile event'leri almayı sağlar")
        android.util.Log.d("BLEEventTransport", "========================================\n")
        
        if (isPollingActive) {
            android.util.Log.w("BLEEventTransport", "⚠️  Polling zaten aktif, durduruluyor...")
            stopEventPolling()
        }
        
        isPollingActive = true
        eventPollingHandler = Handler(Looper.getMainLooper())
        
        val pollingRunnable = object : Runnable {
            override fun run() {
                if (!isPollingActive || gatt == null) {
                    android.util.Log.d("BLEEventTransport", "   ⚠️  Polling durduruldu veya GATT null")
                    return
                }
                
                // Runtime permission check
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        android.util.Log.e("BLEEventTransport", "   ❌ BLUETOOTH_CONNECT izni yok, polling durduruluyor")
                        stopEventPolling()
                        return
                    }
                }
                
                try {
                    val characteristic = gatt.getService(SERVICE_UUID)?.getCharacteristic(CHARACTERISTIC_UUID)
                    if (characteristic != null) {
                        // WRITE-BASED POLLING: Önce write yap, sonra read yap
                        // Bridge server write request'i alınca son event'i hazır tutacak
                        // Write sonrası hemen read yapınca son event'i alacağız
                        
                        // 1. Write request gönder (dummy data ile - sadece trigger için)
                        val writeData = byteArrayOf(0x01) // Dummy data - bridge server'a "event var mı?" sorusu
                        characteristic.value = writeData
                        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        
                        android.util.Log.d("BLEEventTransport", "   ✍️  writeCharacteristic() çağrılıyor...")
                        android.util.Log.d("BLEEventTransport", "   Characteristic UUID: ${characteristic.uuid}")
                        android.util.Log.d("BLEEventTransport", "   Write data: ${writeData.size} bytes")
                        
                        val writeResult = gatt.writeCharacteristic(characteristic)
                        android.util.Log.d("BLEEventTransport", "   ✍️  Write request sonucu: $writeResult")
                        android.util.Log.d("BLEEventTransport", "   💡 writeResult=true ise: writeCharacteristic() başarılı, onCharacteristicWrite callback'i tetiklenecek")
                        android.util.Log.d("BLEEventTransport", "   💡 Write sonrası hemen read yapılacak, bridge server son event'i döndürecek")
                        
                        if (!writeResult) {
                            android.util.Log.w("BLEEventTransport", "   ⚠️  writeCharacteristic() false döndü - retry yapılacak")
                            // Retry mekanizması: 25ms sonra tekrar dene
                            eventPollingHandler?.postDelayed({
                                if (isPollingActive && gatt != null) {
                                    try {
                                        val retryResult = gatt.writeCharacteristic(characteristic)
                                        android.util.Log.d("BLEEventTransport", "   🔄 Retry writeCharacteristic() sonucu: $retryResult")
                                    } catch (e: Exception) {
                                        android.util.Log.e("BLEEventTransport", "   ❌ Retry exception: ${e.message}")
                                    }
                                }
                            }, 25)
                        }
                    } else {
                        android.util.Log.w("BLEEventTransport", "   ⚠️  Characteristic bulunamadı, polling durduruluyor")
                        stopEventPolling()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("BLEEventTransport", "   ❌ Polling write exception: ${e.message}")
                    android.util.Log.e("BLEEventTransport", "   Exception tipi: ${e.javaClass.simpleName}")
                    android.util.Log.e("BLEEventTransport", "   Stack: ${e.stackTrace.take(3).joinToString("\n")}")
                }
                
                // Bir sonraki polling'i planla
                if (isPollingActive && eventPollingHandler != null) {
                    eventPollingHandler?.postDelayed(this, POLLING_INTERVAL_MS)
                }
            }
        }
        
        // İlk polling'i başlat
        eventPollingHandler?.postDelayed(pollingRunnable, POLLING_INTERVAL_MS)
        android.util.Log.d("BLEEventTransport", "✅ Write-based polling başlatıldı")
    }
    
    /**
     * Polling-based read mekanizmasını durdur
     */
    private fun stopEventPolling() {
        android.util.Log.d("BLEEventTransport", "🛑 Polling durduruluyor...")
        isPollingActive = false
        eventPollingHandler?.removeCallbacksAndMessages(null)
        eventPollingHandler = null
        lastReadValue = null
        android.util.Log.d("BLEEventTransport", "✅ Polling durduruldu")
    }
}

