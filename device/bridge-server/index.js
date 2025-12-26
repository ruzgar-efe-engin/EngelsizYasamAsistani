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
const RFC2217Client = require('./lib/rfc2217-client');
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
    reconnectDelay: parseInt(process.env.RECONNECT_DELAY) || 3000,
    // RFC2217 (Wokwi) configuration
    useRFC2217: process.env.USE_RFC2217 === 'true' || process.env.SERIAL_PORT === null, // Otomatik: SERIAL_PORT yoksa RFC2217 kullan
    rfc2217Host: process.env.RFC2217_HOST || 'localhost',
    rfc2217Port: parseInt(process.env.RFC2217_PORT) || 4000
};

// Initialize components
// Wokwi için RFC2217 client kullan, gerçek cihaz için Serial port kullan
const wokwiListener = config.useRFC2217 ? new RFC2217Client({
    host: config.rfc2217Host,
    port: config.rfc2217Port,
    baudRate: config.baudRate,
    autoReconnect: config.autoReconnect,
    reconnectDelay: config.reconnectDelay
}) : null;

const serialListener = config.useRFC2217 ? null : new SerialListener(config);
const blePeripheral = new BLEPeripheral(config);

// Setup listener callbacks (RFC2217 veya Serial)
const activeListener = wokwiListener || serialListener;

activeListener.onConnect((portInfo) => {
    // Bu callback sadece Wokwi'den gerçekten veri geldiğinde çağrılacak
    const portIdentifier = config.useRFC2217 ? `${portInfo.host}:${portInfo.port}` : portInfo.path;
    console.log(`\n🎉 ========================================`);
    console.log(`✅ [Bridge] Wokwi bağlantısı DOĞRULANDI: ${portIdentifier}`);
    console.log(`   Baud Rate: ${config.baudRate}`);
    console.log(`   Wokwi'den veri alındı, bağlantı aktif!`);
    console.log(`========================================\n`);
    
    logger.info(`✅ Wokwi bağlantısı doğrulandı: ${portIdentifier}`);
    logger.info(`   Baud Rate: ${config.baudRate}`);
    
    // Wokwi bağlandı - BLE peripheral'a bildir
    blePeripheral.setWokwiConnected(true);
    logger.info('✅ Wokwi bağlandı - BLE advertising için hazır (pairing mode bekleniyor)');
    console.log(`✅ [Bridge] Wokwi bağlandı - BLE advertising için hazır (pairing mode bekleniyor)`);
});

activeListener.onData((event, rawLine) => {
    console.log(`\n📥 [Bridge] onData callback çağrıldı:`);
    console.log(`   rawLine: "${rawLine}"`);
    console.log(`   event: ${event ? JSON.stringify(event) : 'null'}`);
    logger.info(`📥 [Bridge] Serial'dan gelen: ${rawLine}`);
    
    // Pairing mode mesajını kontrol et
    if (rawLine && rawLine.includes('[BLE] Pairing mode aktif')) {
        console.log('🔵 [Bridge] Pairing mode aktif mesajı tespit edildi!');
        logger.info('🔵 [Bridge] Pairing mode mesajı alındı - Pairing mode aktif ediliyor');
        blePeripheral.enablePairingMode();
    } else if (rawLine && rawLine.includes('[BLE] Pairing mode sona erdi')) {
        console.log('🔴 [Bridge] Pairing mode sona erdi mesajı tespit edildi!');
        logger.info('🔴 [Bridge] Pairing mode sona erdi mesajı alındı');
        blePeripheral.disablePairingMode();
    } else if (event) {
        // Normal event - BLE'ye gönder
        // Log formatı: sendEvent çağrıldı: type=X m=Y s=Z - Wokwi'dan alındı
        const logParts = [`sendEvent çağrıldı: type=${event.type}`];
        if (event.mainIndex !== undefined) {
            logParts.push(`m=${event.mainIndex}`);
        }
        if (event.subIndex !== undefined) {
            logParts.push(`s=${event.subIndex}`);
        }
        const logMessage = `${logParts.join(' ')} - Wokwi'dan alındı`;
        console.log(`\n✅ ${logMessage}`);
        logger.info(logMessage);
        
        logger.info(`📤 [Bridge] BLE'ye gönderiliyor: ${JSON.stringify(event)}`);
        blePeripheral.sendEvent(event);
    } else {
        console.log(`⚠️  [Bridge] Event parse edilemedi veya pairing mode mesajı değil: "${rawLine}"`);
        logger.debug(`ℹ️  [Bridge] Event parse edilemedi veya pairing mode mesajı değil: ${rawLine}`);
    }
});

activeListener.onError((error) => {
    logger.error(`❌ ${config.useRFC2217 ? 'RFC2217' : 'Serial port'} hatası: ${error.message}`);
    // Hata durumunda Wokwi bağlantısını kes olarak işaretle
    blePeripheral.setWokwiConnected(false);
});

activeListener.onDisconnect(() => {
    logger.warn(`⚠️  ${config.useRFC2217 ? 'RFC2217' : 'Serial port'} kapandı`);
    // Wokwi bağlantısı kesildi
    blePeripheral.setWokwiConnected(false);
});

// Setup BLE peripheral callbacks
blePeripheral.onStateChange((state) => {
    logger.info(`📡 BLE State: ${state}`);
    
    if (state === 'poweredOn') {
        logger.info('✅ Bluetooth açık, BLE Peripheral hazır');
        logger.info('⚠️  NOT: Advertising sadece Wokwi bağlıysa ve pairing mode aktifse başlatılacak');
        logger.info('   💡 Wokwi bağlandığında ve pairing mode aktif olduğunda otomatik başlatılacak');
        // BLE advertising'i burada başlatmıyoruz
        // startAdvertising() sadece Wokwi bağlıysa ve pairing mode aktifse çağrılacak
        // Bu çağrılar setWokwiConnected() ve enablePairingMode() içinde yapılıyor
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

console.log(`\n🚀 ========================================`);
console.log(`🚀 Bridge Server başlatılıyor...`);
console.log(`   Serial Port: ${config.serialPort || 'Otomatik'}`);
console.log(`   Baud Rate: ${config.baudRate}`);
console.log(`   Auto Reconnect: ${config.autoReconnect}`);
console.log(`========================================\n`);

logger.info('🚀 Server başlatılıyor...\n');

// Start listener (RFC2217 veya Serial)
const listenerType = config.useRFC2217 ? 'RFC2217' : 'Serial';
console.log(`\n🚀 ========================================`);
console.log(`🚀 Bridge Server başlatılıyor...`);
console.log(`   Connection Type: ${listenerType}`);
if (config.useRFC2217) {
    console.log(`   RFC2217 Host: ${config.rfc2217Host}:${config.rfc2217Port}`);
} else {
    console.log(`   Serial Port: ${config.serialPort || 'Otomatik'}`);
}
console.log(`   Baud Rate: ${config.baudRate}`);
console.log(`   Auto Reconnect: ${config.autoReconnect}`);
console.log(`========================================\n`);

// activeListener null kontrolü
if (!activeListener) {
    console.error(`\n❌ ========================================`);
    console.error(`❌ [Bridge] Listener oluşturulamadı!`);
    console.error(`   useRFC2217: ${config.useRFC2217}`);
    console.error(`   SERIAL_PORT: ${config.serialPort || 'null'}`);
    console.error(`   💡 useRFC2217=true veya SERIAL_PORT belirtin`);
    console.error(`========================================\n`);
    logger.error('❌ Listener oluşturulamadı - useRFC2217 ve SERIAL_PORT kontrol edin');
    process.exit(1);
}

activeListener.start().catch(async (error) => {
    console.error(`\n❌ ========================================`);
    console.error(`❌ [Bridge] ${listenerType} listener başlatılamadı: ${error.message}`);
    console.error(`========================================\n`);
    logger.error(`❌ ${listenerType} listener başlatılamadı: ${error.message}`);
    
    // Port listesini göster (sadece Serial port modunda)
    if (!config.useRFC2217) {
        try {
            const { SerialPort } = require('serialport');
            const ports = await SerialPort.list();
            logger.info('\n📋 Mevcut Serial portlar:');
            if (ports.length === 0) {
                logger.warn('   Hiç port bulunamadı!');
                logger.info('\n💡 Serial port bağlantısı için:');
                logger.info('   1. Wokwi simülasyonunun çalıştığından emin olun');
                logger.info('   2. Serial Monitor\'ün açık olduğundan emin olun');
                logger.info('   3. Wokwi local Serial port oluşturuyor mu kontrol edin');
                logger.info('   4. RFC2217 kullanmak için: USE_RFC2217=true npm start');
            } else {
                ports.forEach(port => {
                    logger.info(`   - ${port.path} (${port.manufacturer || 'Unknown'} ${port.vendorId || ''}:${port.productId || ''})`);
                });
                logger.info('\n💡 Manuel port belirtmek için:');
                logger.info('   SERIAL_PORT=/dev/tty.usbserial-XXXX npm start');
                logger.info('   veya');
            }
        } catch (portError) {
            logger.error(`   Port listesi alınamadı: ${portError.message}`);
        }
    } else {
        // RFC2217 modunda
        logger.info('\n💡 Wokwi RFC2217 bağlantısı için:');
        logger.info('   1. Wokwi simülasyonunun çalıştığından emin olun');
        logger.info('   2. Serial Monitor\'ün açık olduğundan emin olun');
        logger.info('   3. RFC2217 server port 4000\'de çalışıyor mu kontrol edin');
        logger.info('   4. Wokwi\'de Serial Monitor\'ü açın ve veri gönderildiğinden emin olun');
    }
    
    logger.info('\n⚠️  Bridge server Serial port olmadan da çalışabilir (BLE advertising başlatılacak)');
    logger.info('   Ancak Wokwi\'den veri almak için Serial port bağlantısı gereklidir.\n');
});

// Graceful shutdown
process.on('SIGINT', () => {
    logger.info('\n\n🛑 Server kapatılıyor...');
    
    if (activeListener) {
        activeListener.stop();
    }
    blePeripheral.stopAdvertising();
    
    process.exit(0);
});

process.on('SIGTERM', () => {
    logger.info('\n\n🛑 Server kapatılıyor (SIGTERM)...');
    
    if (activeListener) {
        activeListener.stop();
    }
    blePeripheral.stopAdvertising();
    
    process.exit(0);
});
