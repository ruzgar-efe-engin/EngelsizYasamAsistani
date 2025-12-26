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
import android.os.ParcelUuid
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import java.util.UUID

class BLEManager(private val context: Context) {
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
    private var gatt: BluetoothGatt? = null
    
    private var scanCallback: ScanCallback? = null
    private var isScanning = false

    // Notification/subscription hazır mı?
    private var isNotificationReady: Boolean = false
    
    // Callbacks
    var onDeviceFound: ((BluetoothDevice) -> Unit)? = null
    var onEventReceived: ((String) -> Unit)? = null
    var onConnectionStateChanged: ((Int) -> Unit)? = null
    
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
    
    fun startScan() {
        android.util.Log.d("BLEManager", "🔍 BLE tarama başlatılıyor...")
        
        if (!isBluetoothEnabled()) {
            android.util.Log.e("BLEManager", "❌ Bluetooth etkin değil, tarama başlatılamıyor")
            return
        }
        
        if (isScanning) {
            android.util.Log.d("BLEManager", "⚠️  Tarama zaten aktif, durduruluyor...")
            stopScan()
        }
        
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        
        if (bluetoothLeScanner == null) {
            android.util.Log.e("BLEManager", "❌ BluetoothLeScanner başlatılamadı")
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
        
        android.util.Log.d("BLEManager", "✅ Scan settings hazır: SCAN_MODE_LOW_LATENCY")
        
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val deviceName = device.name ?: "Bilinmeyen"
                val scanRecord = result.scanRecord
                
                // Tüm cihazları log'la (debug için)
                val serviceUuids = scanRecord?.serviceUuids?.map { it.uuid.toString() } ?: emptyList()
                android.util.Log.d("BLEManager", "📡 Cihaz bulundu: '$deviceName' (${device.address})")
                if (serviceUuids.isNotEmpty()) {
                    android.util.Log.d("BLEManager", "   Service UUID'ler: $serviceUuids")
                }
                
                // Service UUID kontrolü (opsiyonel - scanRecord'da varsa kontrol et)
                val hasServiceUuid = scanRecord?.serviceUuids?.contains(ParcelUuid(SERVICE_UUID)) == true
                
                // Device name kontrolü (öncelikli - daha esnek)
                val hasMatchingName = DEVICE_NAMES.any { it.equals(deviceName, ignoreCase = true) }

                // Eşleşme kuralı:
                // - Tercihen service UUID ile eşleşsin (en güvenlisi)
                // - Bazı cihazlar scanRecord.serviceUuids'i boş döndürebiliyor; o durumda isim eşleşmesini fallback olarak kabul et.
                if (hasServiceUuid || hasMatchingName) {
                    android.util.Log.d("BLEManager", "✅ Cihaz eşleşti: $deviceName")
                    android.util.Log.d("BLEManager", "   Service UUID eşleşti: $hasServiceUuid")
                    android.util.Log.d("BLEManager", "   Device name eşleşti: $hasMatchingName")
                    onDeviceFound?.invoke(device)
                } else {
                    android.util.Log.d("BLEManager", "❌ Cihaz eşleşmedi: $deviceName (aranan: $DEVICE_NAMES)")
                    android.util.Log.d("BLEManager", "   ⚠️  Pairing mode aktif değil veya cihaz eşleşmedi")
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
                android.util.Log.e("BLEManager", "Tarama hatası: $errorMsg")
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                android.util.Log.e("BLEManager", "BLUETOOTH_SCAN izni yok!")
                return
            }
        }
        
        try {
            // Filter ile tara (Service UUID)
            bluetoothLeScanner?.startScan(scanFilters, scanSettings, scanCallback)
            isScanning = true
            android.util.Log.d("BLEManager", "✅ BLE tarama başlatıldı")
            android.util.Log.d("BLEManager", "   📡 Aranan cihazlar: $DEVICE_NAMES")
            android.util.Log.d("BLEManager", "   📡 Service UUID: $SERVICE_UUID")
            android.util.Log.d("BLEManager", "   ⚠️  NOT: Sadece pairing mode aktifse cihaz bulunabilir")
        } catch (e: Exception) {
            android.util.Log.e("BLEManager", "❌ Tarama başlatılamadı: ${e.message}")
            isScanning = false
        }
    }
    
    fun stopScan() {
        android.util.Log.d("BLEManager", "🛑 BLE tarama durduruluyor...")
        scanCallback?.let { callback ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_SCAN
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    bluetoothLeScanner?.stopScan(callback)
                } else {
                    android.util.Log.w("BLEManager", "⚠️  BLUETOOTH_SCAN izni yok, tarama durdurulamıyor")
                }
            } else {
                @Suppress("DEPRECATION")
                bluetoothLeScanner?.stopScan(callback)
            }
        }
        scanCallback = null
        isScanning = false
        android.util.Log.d("BLEManager", "✅ BLE tarama durduruldu")
    }
    
    fun connect(device: BluetoothDevice) {
        android.util.Log.d("BLEManager", "🔌 Cihaza bağlanılıyor: ${device.name ?: "Bilinmeyen"} (${device.address})")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                android.util.Log.e("BLEManager", "❌ BLUETOOTH_CONNECT izni yok, bağlantı yapılamıyor")
                return
            }
        }
        
        android.util.Log.d("BLEManager", "✅ İzinler kontrol edildi, GATT bağlantısı başlatılıyor...")
        gatt = device.connectGatt(context, false, gattCallback)
    }
    
    fun disconnect() {
        android.util.Log.d("BLEManager", "🔌 Cihaz bağlantısı kesiliyor...")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                android.util.Log.e("BLEManager", "❌ BLUETOOTH_CONNECT izni yok, bağlantı kesilemiyor")
                return
            }
        }
        
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        android.util.Log.d("BLEManager", "✅ Cihaz bağlantısı kesildi")
    }
    
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            android.util.Log.d("BLEManager", "onConnectionStateChange: status=$status, newState=$newState")
            
            if (status != BluetoothGatt.GATT_SUCCESS) {
                android.util.Log.e("BLEManager", "❌ Bağlantı hatası: status=$status")
                onConnectionStateChanged?.invoke(BluetoothProfile.STATE_DISCONNECTED)
                return
            }
            
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // CONNECTED demek için erken: önce service discovery + notify subscribe başarıyla tamamlanmalı
                isNotificationReady = false
                android.util.Log.d("BLEManager", "✅ Cihaz bağlandı, service'ler keşfediliyor...")
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
                android.util.Log.d("BLEManager", "⚠️ Cihaz bağlantısı kesildi")
                isNotificationReady = false
                onConnectionStateChanged?.invoke(BluetoothProfile.STATE_DISCONNECTED)
            }
            
            // Not: CONNECTED durumunu burada yayınlamıyoruz; notify hazır olunca yayınlayacağız.
            if (newState != BluetoothProfile.STATE_CONNECTED) {
                onConnectionStateChanged?.invoke(newState)
            } else {
                onConnectionStateChanged?.invoke(BluetoothProfile.STATE_CONNECTING)
            }
        }
        
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            android.util.Log.d("BLEManager", "onServicesDiscovered: status=$status")
            
            if (status != BluetoothGatt.GATT_SUCCESS) {
                android.util.Log.e("BLEManager", "❌ Service keşif hatası: status=$status")
                return
            }
            
            // Tüm service'leri listele
            val services = gatt.services
            android.util.Log.d("BLEManager", "Bulunan service sayısı: ${services.size}")
            services.forEach { service ->
                android.util.Log.d("BLEManager", "Service: ${service.uuid}")
            }
            
            val service = gatt.getService(SERVICE_UUID)
            if (service == null) {
                android.util.Log.e("BLEManager", "❌ Service bulunamadı: $SERVICE_UUID")
                return
            }
            
            android.util.Log.d("BLEManager", "✅ Service bulundu: ${service.uuid}")
            val characteristic = service.getCharacteristic(CHARACTERISTIC_UUID)
            
            if (characteristic == null) {
                android.util.Log.e("BLEManager", "❌ Characteristic bulunamadı: $CHARACTERISTIC_UUID")
                return
            }
            
            android.util.Log.d("BLEManager", "✅ Characteristic bulundu: ${characteristic.uuid}")
            
            // Notification'ı etkinleştir
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    android.util.Log.d("BLEManager", "🔔 Notification enable ediliyor...")
                    val notificationEnabled = gatt.setCharacteristicNotification(characteristic, true)
                    android.util.Log.d("BLEManager", "   setCharacteristicNotification sonucu: $notificationEnabled")
                    
                    // Descriptor'ı yaz
                    val descriptor = characteristic.getDescriptor(
                        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                    )
                    if (descriptor != null) {
                        android.util.Log.d("BLEManager", "📝 Descriptor bulundu (2902), yazılıyor...")
                        val writeResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            // Android 13+ (API 33): yeni imza
                            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        } else {
                            // Eski imza (API 32 ve altı)
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(descriptor)
                        }
                        android.util.Log.d("BLEManager", "   writeDescriptor sonucu: $writeResult (sdk=${Build.VERSION.SDK_INT})")
                        android.util.Log.d("BLEManager", "   💡 onDescriptorWrite callback'inde sonuç görünecek")
                    } else {
                        android.util.Log.e("BLEManager", "❌ Descriptor bulunamadı (2902)")
                        android.util.Log.e("BLEManager", "   Bu notification subscribe'ı engelleyebilir")
                    }
                }
                } else {
                    android.util.Log.d("BLEManager", "🔔 Notification enable ediliyor (API < S)...")
                    val notificationEnabled = gatt.setCharacteristicNotification(characteristic, true)
                    android.util.Log.d("BLEManager", "   setCharacteristicNotification sonucu: $notificationEnabled")
                    
                    val descriptor = characteristic.getDescriptor(
                        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                    )
                    if (descriptor != null) {
                        android.util.Log.d("BLEManager", "📝 Descriptor bulundu (2902), yazılıyor...")
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        val writeResult = gatt.writeDescriptor(descriptor)
                        android.util.Log.d("BLEManager", "   writeDescriptor sonucu: $writeResult")
                        android.util.Log.d("BLEManager", "   💡 onDescriptorWrite callback'inde sonuç görünecek")
                    } else {
                        android.util.Log.e("BLEManager", "❌ Descriptor bulunamadı (2902)")
                        android.util.Log.e("BLEManager", "   Bu notification subscribe'ı engelleyebilir")
                    }
                }
        }
        
        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            android.util.Log.d("BLEManager", "📝 onDescriptorWrite: uuid=${descriptor.uuid}, status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                android.util.Log.d("BLEManager", "✅ Descriptor yazıldı: ${descriptor.uuid}")
                // CCCD yazıldı => notify subscribe hazır sayabiliriz
                if (descriptor.uuid.toString().equals("00002902-0000-1000-8000-00805f9b34fb", ignoreCase = true)) {
                    isNotificationReady = true
                    android.util.Log.d("BLEManager", "✅ Notification subscribe hazır (CCCD yazıldı)")
                    onConnectionStateChanged?.invoke(BluetoothProfile.STATE_CONNECTED)
                }
            } else {
                android.util.Log.e("BLEManager", "❌ Descriptor yazma hatası: status=$status")
                android.util.Log.e("BLEManager", "   Bu hata notification subscribe'ı engelleyebilir")
                isNotificationReady = false
                onConnectionStateChanged?.invoke(BluetoothProfile.STATE_DISCONNECTED)
            }
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
    }

    private fun handleCharacteristicChanged(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray?
    ) {
        android.util.Log.d("BLEManager", "📡 onCharacteristicChanged: uuid=${characteristic.uuid}")

        if (characteristic.uuid != CHARACTERISTIC_UUID) {
            android.util.Log.d("BLEManager", "❌ UUID eşleşmedi: ${characteristic.uuid} != $CHARACTERISTIC_UUID")
            return
        }

        if (value != null && value.isNotEmpty()) {
            val jsonString = String(value, Charsets.UTF_8)
            android.util.Log.d("BLEManager", "✅ Event alındı (BLE): $jsonString")
            // Callback'i çağır - SetupScreen'de event log paneline yazılacak
            onEventReceived?.invoke(jsonString)
        } else {
            android.util.Log.w("BLEManager", "⚠️ Event value boş veya null")
        }
    }
    
    fun isConnected(): Boolean {
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
}

