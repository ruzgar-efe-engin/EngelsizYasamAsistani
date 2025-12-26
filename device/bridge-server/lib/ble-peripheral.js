/**
 * BLE Peripheral
 * 
 * BLE Peripheral olarak davranır ve mobile app'lere event'leri iletir.
 */

const bleno = require('@abandonware/bleno');
const bleConfig = require('../config/ble-config');

class BLEPeripheral {
    constructor(config = {}) {
        this.config = {
            autoReconnect: config.autoReconnect !== false
        };
        
        this.isAdvertising = false;
        this.eventCharacteristic = null;
        this.currentEventValue = Buffer.alloc(0);
        
        // Wokwi ve pairing mode kontrolü
        this.isWokwiConnected = false;
        this.isPairingModeActive = false;
        this.pairingModeStartTime = null;
        this.PAIRING_MODE_DURATION_MS = 30000; // 30 saniye
        
        this.onStateChangeCallback = null;
        this.onClientConnectCallback = null;
        this.onClientDisconnectCallback = null;
        
        this.setupBleno();
    }

    /**
     * Bleno event handler'larını ayarla
     */
    setupBleno() {
        bleno.on('stateChange', (state) => {
            if (this.onStateChangeCallback) {
                this.onStateChangeCallback(state);
            }

            if (state === 'poweredOn') {
                console.log('✅ BLE poweredOn - Advertising için Wokwi ve pairing mode kontrolü yapılacak');
                // Otomatik advertising başlatma - sadece Wokwi bağlıysa ve pairing mode aktifse başlatılacak
                // startAdvertising() içinde kontrol yapılıyor
            } else {
                console.log('⚠️  BLE state değişti, advertising durduruluyor');
                this.stopAdvertising();
            }
        });

        bleno.on('advertisingStart', (error) => {
            if (error) {
                return;
            }

            this.isAdvertising = true;
            this.setupServices();
        });

        bleno.on('advertisingStop', () => {
            this.isAdvertising = false;
        });

        bleno.on('accept', (clientAddress) => {
            if (this.onClientConnectCallback) {
                this.onClientConnectCallback(clientAddress);
            }
        });

        bleno.on('disconnect', (clientAddress) => {
            if (this.onClientDisconnectCallback) {
                this.onClientDisconnectCallback(clientAddress);
            }
        });

        bleno.on('servicesSet', (error) => {
            if (!error) {
                // Characteristic referansını al
                const service = new EventService();
                this.eventCharacteristic = service.characteristics[0];

                if (this.currentEventValue.length > 0) {
                    this.eventCharacteristic.updateValue(this.currentEventValue);
                    this.currentEventValue = Buffer.alloc(0);
                }
            }
        });
    }

    /**
     * BLE Advertising başlat
     * Sadece Wokwi bağlıysa ve pairing mode aktifse advertising yap
     */
    startAdvertising() {
        console.log(`🔍 startAdvertising çağrıldı - Wokwi: ${this.isWokwiConnected}, Pairing: ${this.isPairingModeActive}`);
        
        if (bleno.state !== 'poweredOn') {
            console.log('⚠️  BLE Advertising: Bluetooth açık değil');
            return;
        }

        // Wokwi kontrolü - Wokwi bağlı değilse advertising yapma
        if (!this.isWokwiConnected) {
            console.log('❌ BLE Advertising: Wokwi bağlı değil, advertising yapılmıyor');
            console.log('   💡 Wokwi simülasyonunu başlatın ve Serial port bağlantısını kontrol edin');
            return;
        }

        // Pairing mode kontrolü - Pairing mode aktif değilse advertising yapma
        if (!this.isPairingModeActive) {
            console.log('❌ BLE Advertising: Pairing mode aktif değil, advertising yapılmıyor');
            console.log('   💡 Wokwi\'da AI butonuna 5 saniye basılı tutun (pairing mode aktif olacak)');
            return;
        }

        console.log('✅ BLE Advertising başlatılıyor (Wokwi bağlı, Pairing mode aktif)');
        bleno.startAdvertising(bleConfig.DEVICE_NAME, [bleConfig.getServiceUUID()], (error) => {
            if (error) {
                console.error('❌ BLE Advertising hatası:', error);
            } else {
                console.log('✅ BLE Advertising başlatıldı - Android cihazlar artık bulabilir');
            }
        });
    }

    /**
     * BLE Advertising durdur
     */
    stopAdvertising() {
        if (bleno.state === 'poweredOn') {
            bleno.stopAdvertising();
        }
        this.isAdvertising = false;
    }

    /**
     * BLE Service'leri ayarla
     */
    setupServices() {
        bleno.setServices([
            new EventService()
        ], (error) => {
            if (error) {
                console.error('❌ Service set hatası:', error);
            }
        });
    }

    /**
     * Event'i BLE'ye gönder
     * @param {Object} event - Event object
     */
    sendEvent(event) {
        try {
            // Log formatı: sendEvent çağrıldı: type=X m=Y s=Z - Telefona gönderildi
            const logParts = [`sendEvent çağrıldı: type=${event.type}`];
            if (event.mainIndex !== undefined) {
                logParts.push(`m=${event.mainIndex}`);
            }
            if (event.subIndex !== undefined) {
                logParts.push(`s=${event.subIndex}`);
            }
            const logMessage = `${logParts.join(' ')} - Telefona gönderildi`;
            console.log(logMessage);
            
            console.log(`📤 Event gönderiliyor: ${JSON.stringify(event)}`);
            
            if (bleno.state !== 'poweredOn') {
                console.warn('⚠️  BLE state poweredOn değil, event gönderilemiyor');
                return;
            }

            if (!event || typeof event !== 'object') {
                throw new Error('Geçersiz event object');
            }

            const jsonString = JSON.stringify(event);
            const data = Buffer.from(jsonString, 'utf8');
            
            console.log(`📦 Event buffer oluşturuldu: ${data.length} bytes`);

            if (this.eventCharacteristic) {
                const result = this.eventCharacteristic.updateValue(data);
                console.log(`✅ Event characteristic'e yazıldı: ${result ? 'başarılı (notify gönderildi)' : 'başarısız (subscribe yok veya hata)'}`);
            } else {
                // Service henüz hazır değilse, bir sonraki bağlantıda gönderilecek
                console.warn('⚠️  Characteristic henüz hazır değil, event buffer\'a kaydediliyor');
                this.currentEventValue = data;
            }
        } catch (error) {
            console.error(`❌ Event gönderme hatası: ${error.message}`);
            console.error(error.stack);
        }
    }

    /**
     * State change callback'i ayarla
     */
    onStateChange(callback) {
        this.onStateChangeCallback = callback;
    }

    /**
     * Client connect callback'i ayarla
     */
    onClientConnect(callback) {
        this.onClientConnectCallback = callback;
    }

    /**
     * Client disconnect callback'i ayarla
     */
    onClientDisconnect(callback) {
        this.onClientDisconnectCallback = callback;
    }

    /**
     * Advertising durumunu kontrol et
     */
    isAdvertisingActive() {
        return this.isAdvertising;
    }

    /**
     * Wokwi bağlantı durumunu ayarla
     */
    setWokwiConnected(connected) {
        const wasConnected = this.isWokwiConnected;
        this.isWokwiConnected = connected;
        
        if (connected && !wasConnected) {
            console.log('✅ Wokwi bağlandı');
        } else if (!connected && wasConnected) {
            console.log('⚠️  Wokwi bağlantısı kesildi, advertising durduruluyor');
            this.stopAdvertising();
        }
        
        // Wokwi bağlandıysa ve pairing mode aktifse advertising başlat
        if (connected && this.isPairingModeActive && bleno.state === 'poweredOn') {
            this.startAdvertising();
        }
    }

    /**
     * Pairing mode'u aktif et
     */
    enablePairingMode() {
        this.isPairingModeActive = true;
        this.pairingModeStartTime = Date.now();
        console.log('✅ Pairing mode aktif - 30 saniye');
        
        // Wokwi bağlıysa advertising başlat
        if (this.isWokwiConnected && bleno.state === 'poweredOn') {
            this.startAdvertising();
        }
        
        // 30 saniye sonra pairing mode'u kapat
        setTimeout(() => {
            if (this.isPairingModeActive) {
                this.disablePairingMode();
            }
        }, this.PAIRING_MODE_DURATION_MS);
    }

    /**
     * Pairing mode'u kapat
     */
    disablePairingMode() {
        if (this.isPairingModeActive) {
            this.isPairingModeActive = false;
            this.pairingModeStartTime = null;
            console.log('⚠️  Pairing mode sona erdi, advertising durduruluyor');
            this.stopAdvertising();
        }
    }

    /**
     * Pairing mode aktif mi kontrol et
     */
    getPairingModeStatus() {
        // Zaman aşımı kontrolü
        if (this.isPairingModeActive && this.pairingModeStartTime) {
            const elapsed = Date.now() - this.pairingModeStartTime;
            if (elapsed >= this.PAIRING_MODE_DURATION_MS) {
                this.disablePairingMode();
                return false;
            }
        }
        return this.isPairingModeActive;
    }
}

/**
 * BLE Characteristic
 */
class EventCharacteristic extends bleno.Characteristic {
    constructor() {
        super({
            uuid: bleConfig.getCharacteristicUUID(),
            properties: ['read', 'notify']
            // macOS CoreBluetooth 2902 descriptor'ı otomatik yönetir, manuel eklemeye gerek yok
        });

        this._value = Buffer.alloc(0);
        this._updateValueCallback = null;
    }

    onReadRequest(offset, callback) {
        callback(this.RESULT_SUCCESS, this._value);
    }

    onWriteRequest(data, offset, withoutResponse, callback) {
        this._value = data;
        callback(this.RESULT_SUCCESS);
    }

    onSubscribe(maxValueSize, updateValueCallback) {
        console.log(`\n🎉 ========================================`);
        console.log(`✅ Client subscribe oldu!`);
        console.log(`   maxValueSize: ${maxValueSize}`);
        console.log(`   updateValueCallback: ${updateValueCallback ? 'var' : 'yok'}`);
        console.log(`========================================\n`);
        this._updateValueCallback = updateValueCallback;

        // Subscribe olur olmaz, elde son değer varsa hemen gönder (ilk event kaybolmasın)
        if (this._value && this._value.length > 0) {
            try {
                console.log(`📡 Subscribe sonrası son değer gönderiliyor: ${this._value.length} bytes`);
                this._updateValueCallback(this._value);
                console.log(`✅ Subscribe sonrası son değer gönderildi`);
            } catch (error) {
                console.error(`❌ Subscribe sonrası ilk değer gönderilemedi: ${error.message}`);
            }
        }
    }

    onUnsubscribe() {
        console.log('⚠️  Client unsubscribe oldu');
        this._updateValueCallback = null;
    }

    updateValue(data) {
        this._value = data;
        if (this._updateValueCallback) {
            console.log(`📡 Characteristic value güncellendi, notification gönderiliyor: ${data.length} bytes`);
            try {
                this._updateValueCallback(data);
                console.log(`✅ Notification gönderildi`);
                return true;
            } catch (error) {
                console.error(`❌ Notification gönderme hatası: ${error.message}`);
                return false;
            }
        } else {
            console.warn('⚠️  Notification callback yok, client subscribe olmamış');
            console.warn('   💡 Android cihazın descriptor yazdıktan sonra subscribe olması gerekiyor');
            console.warn('   💡 Android loglarını kontrol edin: adb logcat | grep BLEManager');
            return false;
        }
    }
}

/**
 * BLE Service
 */
class EventService extends bleno.PrimaryService {
    constructor() {
        super({
            uuid: bleConfig.getServiceUUID(),
            characteristics: [
                new EventCharacteristic()
            ]
        });
    }
}

module.exports = BLEPeripheral;

