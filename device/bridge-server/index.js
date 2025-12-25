#!/usr/bin/env node

/**
 * Wokwi BLE Bridge Server
 * 
 * Bu server, Wokwi simülasyonundan gelen Serial port verilerini dinler
 * ve bunları BLE Peripheral olarak mobile app'lere iletir.
 * 
 * Flow: Wokwi Device (Serial) -> Bridge Server -> BLE Peripheral -> Mobile App
 */

require('dotenv').config();
const winston = require('winston');
const SerialListener = require('./lib/serial-listener');
const BLEPeripheral = require('./lib/ble-peripheral');
const bleConfig = require('./config/ble-config');

// Logger setup
const logger = winston.createLogger({
    level: process.env.LOG_LEVEL || 'info',
    format: winston.format.combine(
        winston.format.timestamp(),
        winston.format.printf(({ timestamp, level, message }) => {
            return `${timestamp} [${level.toUpperCase()}] ${message}`;
        })
    ),
    transports: [
        new winston.transports.Console({
            format: winston.format.combine(
                winston.format.colorize(),
                winston.format.printf(({ timestamp, level, message }) => {
                    return `${timestamp} [${level}] ${message}`;
                })
            )
        })
    ]
});

// Configuration
const config = {
    serialPort: process.env.SERIAL_PORT || null,
    baudRate: parseInt(process.env.BAUD_RATE) || 115200,
    autoReconnect: process.env.AUTO_RECONNECT !== 'false',
    reconnectDelay: parseInt(process.env.RECONNECT_DELAY) || 3000
};

// Initialize components
const serialListener = new SerialListener(config);
const blePeripheral = new BLEPeripheral(config);

// Setup serial listener callbacks
serialListener.onConnect((portInfo) => {
    logger.info(`✅ Serial port açıldı: ${portInfo.path}`);
    if (portInfo.manufacturer) {
        logger.info(`   Üretici: ${portInfo.manufacturer}`);
    }
    logger.info(`   Baud Rate: ${config.baudRate}`);
});

serialListener.onData((event, rawLine) => {
    logger.debug(`📥 Serial: ${rawLine}`);
    logger.info(`📤 BLE'ye gönderiliyor: ${JSON.stringify(event)}`);
    blePeripheral.sendEvent(event);
});

serialListener.onError((error) => {
    logger.error(`❌ Serial port hatası: ${error.message}`);
});

serialListener.onDisconnect(() => {
    logger.warn('⚠️  Serial port kapandı');
});

// Setup BLE peripheral callbacks
blePeripheral.onStateChange((state) => {
    logger.info(`📡 BLE State: ${state}`);
    
    if (state === 'poweredOn') {
        logger.info('✅ Bluetooth açık, BLE Peripheral başlatılıyor...');
        // BLE advertising'i başlat (Serial port olmasa bile)
        blePeripheral.startAdvertising();
    } else {
        logger.warn('❌ Bluetooth kapalı, lütfen açın');
        logger.warn('   macOS: System Preferences > Bluetooth');
        logger.warn('   Linux: bluetoothctl power on');
    }
});

blePeripheral.onClientConnect((clientAddress) => {
    logger.info(`✅ BLE Client bağlandı: ${clientAddress}`);
});

blePeripheral.onClientDisconnect((clientAddress) => {
    logger.warn(`⚠️  BLE Client bağlantısı kesildi: ${clientAddress}`);
});

// Startup
logger.info('========================================');
logger.info('Wokwi BLE Bridge Server');
logger.info('========================================');
logger.info(`Device Name: ${bleConfig.DEVICE_NAME}`);
logger.info(`Service UUID: ${bleConfig.getServiceUUID()}`);
logger.info(`Characteristic UUID: ${bleConfig.getCharacteristicUUID()}`);
logger.info('========================================\n');

logger.info('🚀 Server başlatılıyor...\n');

// Start serial listener
serialListener.start().catch(async (error) => {
    logger.error(`❌ Serial listener başlatılamadı: ${error.message}`);
    
    // Port listesini göster
    try {
        const { SerialPort } = require('serialport');
        const ports = await SerialPort.list();
        logger.info('\n📋 Mevcut Serial portlar:');
        if (ports.length === 0) {
            logger.warn('   Hiç port bulunamadı!');
            logger.info('\n💡 Wokwi simülasyonu için:');
            logger.info('   1. Wokwi simülasyonunun çalıştığından emin olun');
            logger.info('   2. Serial Monitor\'ün açık olduğundan emin olun');
            logger.info('   3. Wokwi local Serial port oluşturuyor mu kontrol edin');
            logger.info('   4. Manuel port belirtmeyi deneyin: SERIAL_PORT=/dev/tty.xxx npm start');
        } else {
            ports.forEach(port => {
                logger.info(`   - ${port.path} (${port.manufacturer || 'Unknown'} ${port.vendorId || ''}:${port.productId || ''})`);
            });
            logger.info('\n💡 Manuel port belirtmek için:');
            logger.info('   SERIAL_PORT=/dev/tty.usbserial-XXXX npm start');
            logger.info('   veya');
            logger.info('   SERIAL_PORT=COM3 npm start (Windows)');
        }
    } catch (listError) {
        logger.error(`Port listesi alınamadı: ${listError.message}`);
    }
    
    logger.info('\n⚠️  Bridge server Serial port olmadan da çalışabilir (BLE advertising başlatılacak)');
    logger.info('   Ancak Wokwi\'den veri almak için Serial port bağlantısı gereklidir.\n');
});

// Graceful shutdown
process.on('SIGINT', () => {
    logger.info('\n\n🛑 Server kapatılıyor...');
    
    serialListener.stop();
    blePeripheral.stopAdvertising();
    
    process.exit(0);
});

process.on('SIGTERM', () => {
    logger.info('\n\n🛑 Server kapatılıyor (SIGTERM)...');
    
    serialListener.stop();
    blePeripheral.stopAdvertising();
    
    process.exit(0);
});
