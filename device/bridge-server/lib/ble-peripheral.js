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
                this.startAdvertising();
            } else {
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
     */
    startAdvertising() {
        if (bleno.state !== 'poweredOn') {
            return;
        }

        bleno.startAdvertising(bleConfig.DEVICE_NAME, [bleConfig.SERVICE_UUID], (error) => {
            if (error) {
                console.error('❌ BLE Advertising hatası:', error);
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
            if (bleno.state !== 'poweredOn') {
                return;
            }

            if (!event || typeof event !== 'object') {
                throw new Error('Geçersiz event object');
            }

            const jsonString = JSON.stringify(event);
            const data = Buffer.from(jsonString, 'utf8');

            if (this.eventCharacteristic) {
                this.eventCharacteristic.updateValue(data);
            } else {
                // Service henüz hazır değilse, bir sonraki bağlantıda gönderilecek
                this.currentEventValue = data;
            }
        } catch (error) {
            console.error(`❌ Event gönderme hatası: ${error.message}`);
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
}

/**
 * BLE Characteristic
 */
class EventCharacteristic extends bleno.Characteristic {
    constructor() {
        super({
            uuid: bleConfig.CHARACTERISTIC_UUID,
            properties: ['read', 'write', 'notify', 'indicate'],
            descriptors: [
                new bleno.Descriptor({
                    uuid: '2902',
                    value: Buffer.alloc(2)
                })
            ]
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
        this._updateValueCallback = updateValueCallback;
    }

    onUnsubscribe() {
        this._updateValueCallback = null;
    }

    updateValue(data) {
        this._value = data;
        if (this._updateValueCallback) {
            this._updateValueCallback(data);
        }
    }
}

/**
 * BLE Service
 */
class EventService extends bleno.PrimaryService {
    constructor() {
        super({
            uuid: bleConfig.SERVICE_UUID,
            characteristics: [
                new EventCharacteristic()
            ]
        });
    }
}

module.exports = BLEPeripheral;

