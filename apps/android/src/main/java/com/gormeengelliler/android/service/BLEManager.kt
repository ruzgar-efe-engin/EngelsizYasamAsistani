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
    
    // Callbacks
    var onDeviceFound: ((BluetoothDevice) -> Unit)? = null
    var onEventReceived: ((String) -> Unit)? = null
    var onConnectionStateChanged: ((Int) -> Unit)? = null
    
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
    
    fun startScan() {
        if (!isBluetoothEnabled()) {
            return
        }
        
        if (isScanning) {
            stopScan()
        }
        
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        
        // Scan filter - Service UUID'ye göre filtrele (opsiyonel, daha esnek arama için)
        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()
        
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0) // Anında bildir
            .build()
        
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val deviceName = device.name ?: ""
                val scanRecord = result.scanRecord
                
                // Debug log
                android.util.Log.d("BLEManager", "Cihaz bulundu: $deviceName (${device.address})")
                
                // Service UUID kontrolü (öncelikli)
                val hasServiceUuid = scanRecord?.serviceUuids?.contains(ParcelUuid(SERVICE_UUID)) == true
                
                // Device name kontrolü
                val hasMatchingName = DEVICE_NAMES.any { it.equals(deviceName, ignoreCase = true) }
                
                // Eğer Service UUID eşleşiyorsa veya device name eşleşiyorsa kabul et
                if (hasServiceUuid || hasMatchingName) {
                    android.util.Log.d("BLEManager", "✅ Cihaz eşleşti: $deviceName")
                    onDeviceFound?.invoke(device)
                } else {
                    android.util.Log.d("BLEManager", "❌ Cihaz eşleşmedi: $deviceName")
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
            // Filter olmadan da dene (daha geniş arama)
            bluetoothLeScanner?.startScan(null, scanSettings, scanCallback)
            // Alternatif: Filter ile dene
            // bluetoothLeScanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)
            isScanning = true
            android.util.Log.d("BLEManager", "✅ BLE tarama başlatıldı")
        } catch (e: Exception) {
            android.util.Log.e("BLEManager", "Tarama başlatılamadı: ${e.message}")
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
                    // Permission yoksa hiçbir şey yapma
                }
            } else {
                @Suppress("DEPRECATION")
                bluetoothLeScanner?.stopScan(callback)
            }
        }
        scanCallback = null
        isScanning = false
    }
    
    fun connect(device: BluetoothDevice) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }
        
        gatt = device.connectGatt(context, false, gattCallback)
    }
    
    fun disconnect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }
        
        gatt?.disconnect()
        gatt?.close()
        gatt = null
    }
    
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
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
                onConnectionStateChanged?.invoke(BluetoothProfile.STATE_DISCONNECTED)
            }
            
            onConnectionStateChanged?.invoke(newState)
        }
        
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                val characteristic = service?.getCharacteristic(CHARACTERISTIC_UUID)
                
                if (characteristic != null) {
                    // Notification'ı etkinleştir
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (ActivityCompat.checkSelfPermission(
                                context,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            gatt.setCharacteristicNotification(characteristic, true)
                            
                            // Descriptor'ı yaz
                            val descriptor = characteristic.getDescriptor(
                                UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                            )
                            descriptor?.let {
                                it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                gatt.writeDescriptor(it)
                            }
                        }
                    } else {
                        gatt.setCharacteristicNotification(characteristic, true)
                        
                        val descriptor = characteristic.getDescriptor(
                            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                        )
                        descriptor?.let {
                            it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(it)
                        }
                    }
                }
            }
        }
        
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == CHARACTERISTIC_UUID) {
                val value = characteristic.value
                val jsonString = String(value, Charsets.UTF_8)
                onEventReceived?.invoke(jsonString)
            }
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

