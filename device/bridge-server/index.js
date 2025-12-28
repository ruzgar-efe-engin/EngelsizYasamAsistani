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

// macOS CoreBluetooth native loglarını tamamen kapat (sadece BLE modunda)
// OS_ACTIVITY_MODE=disable macOS'un os_log sistemini devre dışı bırakır
const TRANSPORT_MODE = process.env.TRANSPORT_MODE || 'websocket';
if (process.platform === 'darwin' && TRANSPORT_MODE === 'ble') {
    process.env.OS_ACTIVITY_MODE = 'disable';
}

const winston = require('winston');
const SerialListener = require('./lib/serial-listener');
const RFC2217Client = require('./lib/rfc2217-client');
const WebSocketServer = require('./lib/websocket-server');
const bleConfig = require('./config/ble-config');

// Logger setup (BLEPeripheral yüklemeden önce)
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

// BLE Peripheral sadece BLE modunda yüklenecek
let BLEPeripheral = null;
if (TRANSPORT_MODE === 'ble') {
    try {
        BLEPeripheral = require('./lib/ble-peripheral');
    } catch (e) {
        logger.error(`❌ BLE modunda @abandonware/bleno gerekli ama yüklenemedi: ${e.message}`);
        logger.error(`   Kurulum: npm install @abandonware/bleno`);
        process.exit(1);
    }
}

// macOS CoreBluetooth native loglarını filtrele (sadece BLE modunda)
// didReceiveReadRequest gibi sürekli tekrarlanan logları tamamen gizle
const FILTERED_PATTERNS = [
    'didReceiveReadRequest',
    'didReceiveWriteRequest',
    'didUpdateValueForCharacteristic',
    'peripheralManager:didReceiveReadRequest',
    'peripheralManager:didReceiveWriteRequest',
    'ReceiveReadRequest', // Kısaltılmış versiyon
    'ReceiveWriteRequest'
];

// Log filtreleme sadece BLE modunda aktif
const shouldFilterLogs = TRANSPORT_MODE === 'ble';

// Chunk buffer'ı (satırlar chunk'lar halinde gelebilir)
let stderrBuffer = '';
let stdoutBuffer = '';

function shouldFilterLog(message) {
    if (typeof message !== 'string') {
        return false;
    }
    
    // macOS os_log formatını kontrol et: "2025-12-28 05:35:15.834 node[...] didReceiveReadRequest"
    // Bu format doğrudan native C++ kodundan geliyor
    // Daha esnek regex - timestamp formatı farklı olabilir
    if (message.match(/node\[\d+:\d+\].*didReceiveReadRequest/i)) {
        return true; // Bu log'u tamamen filtrele
    }
    
    // Sadece "didReceiveReadRequest" içeren herhangi bir satırı filtrele
    if (message.includes('didReceiveReadRequest')) {
        return true;
    }
    
    // Herhangi bir filtrelenmiş pattern varsa filtrele
    return FILTERED_PATTERNS.some(pattern => message.includes(pattern));
}

// stderr'i filtrele (macOS CoreBluetooth logları buradan geliyor) - sadece BLE modunda
const originalStderrWrite = process.stderr.write.bind(process.stderr);
process.stderr.write = function(chunk, encoding, fd) {
    // Log filtreleme sadece BLE modunda aktif
    if (!shouldFilterLogs) {
        return originalStderrWrite(chunk, encoding, fd);
    }
    
    // Önce direkt kontrol et (buffer olmadan)
    const message = typeof chunk === 'string' ? chunk : chunk.toString();
    
    // Eğer didReceiveReadRequest içeriyorsa direkt filtrele
    if (shouldFilterLog(message)) {
        return true; // Log'u yazma, ama başarılı döndür
    }
    
    // Buffer'a ekle (satırlar chunk'lar halinde gelebilir)
    stderrBuffer += message;
    
    // Satır sonu karakteri varsa buffer'ı kontrol et
    if (stderrBuffer.includes('\n')) {
        const lines = stderrBuffer.split('\n');
        // Son satır tamamlanmamış olabilir, buffer'da tut
        stderrBuffer = lines.pop() || '';
        
        // Her satırı kontrol et
        for (const line of lines) {
            if (!shouldFilterLog(line)) {
                originalStderrWrite(line + '\n', encoding, fd);
            }
            // Filtrelenen satırlar yazılmıyor
        }
    }
    
    // Buffer'da kalan kısım (tam satır değil) için kontrol et
    // Eğer buffer çok büyürse (1000 karakter) zorla kontrol et
    if (stderrBuffer.length > 1000) {
        if (!shouldFilterLog(stderrBuffer)) {
            originalStderrWrite(stderrBuffer, encoding, fd);
        }
        stderrBuffer = '';
    }
    
    return true; // Her zaman başarılı döndür
};

// stdout'u da filtrele (bazı loglar buradan gelebilir) - sadece BLE modunda
const originalStdoutWrite = process.stdout.write.bind(process.stdout);
process.stdout.write = function(chunk, encoding, fd) {
    // Log filtreleme sadece BLE modunda aktif
    if (!shouldFilterLogs) {
        return originalStdoutWrite(chunk, encoding, fd);
    }
    
    const message = chunk.toString();
    
    // Buffer'a ekle
    stdoutBuffer += message;
    
    // Satır sonu karakteri varsa buffer'ı kontrol et
    if (stdoutBuffer.includes('\n')) {
        const lines = stdoutBuffer.split('\n');
        stdoutBuffer = lines.pop() || '';
        
        for (const line of lines) {
            if (!shouldFilterLog(line)) {
                originalStdoutWrite(line + '\n', encoding, fd);
            }
        }
    }
    
    // Buffer çok büyürse zorla kontrol et
    if (stdoutBuffer.length > 1000) {
        if (!shouldFilterLog(stdoutBuffer)) {
            originalStdoutWrite(stdoutBuffer, encoding, fd);
        }
        stdoutBuffer = '';
    }
    
    return true;
};

// Configuration
const config = {
    serialPort: process.env.SERIAL_PORT || null,
    baudRate: parseInt(process.env.BAUD_RATE) || 115200,
    autoReconnect: process.env.AUTO_RECONNECT !== 'false',
    reconnectDelay: parseInt(process.env.RECONNECT_DELAY) || 3000,
    // RFC2217 (Wokwi) configuration
    // Varsayılan olarak RFC2217 kullan (Wokwi için)
    // Serial port kullanmak için: USE_RFC2217=false SERIAL_PORT=/dev/tty.xxx npm start
    useRFC2217: process.env.USE_RFC2217 !== 'false' && process.env.SERIAL_PORT === undefined, // Varsayılan: RFC2217 (Wokwi için)
    rfc2217Host: process.env.RFC2217_HOST || 'localhost',
    rfc2217Port: parseInt(process.env.RFC2217_PORT) || 4000,
    // Transport mode
    transportMode: TRANSPORT_MODE
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

// Transport mode'a göre server başlat
let blePeripheral = null;
let webSocketServer = null;

if (TRANSPORT_MODE === 'ble') {
    // BLE modunda: BLE Peripheral başlat
    if (BLEPeripheral) {
        blePeripheral = new BLEPeripheral(config);
        logger.info('📡 BLE modu aktif - BLE Peripheral başlatılıyor');
    } else {
        logger.error('❌ BLE modunda @abandonware/bleno gerekli ama yüklenemedi');
        process.exit(1);
    }
} else {
    // WebSocket modunda: WebSocket Server başlat
    webSocketServer = new WebSocketServer({
        port: 8080,
        logger: logger
    });
    logger.info('🌐 WebSocket modu aktif - WebSocket Server başlatılıyor (port 8080)');
}

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
    
    // Wokwi bağlandı - transport mode'a göre bildir
    if (TRANSPORT_MODE === 'ble' && blePeripheral) {
        blePeripheral.setWokwiConnected(true);
        logger.info('✅ Wokwi bağlandı - BLE advertising için hazır (pairing mode bekleniyor)');
        console.log(`✅ [Bridge] Wokwi bağlandı - BLE advertising için hazır (pairing mode bekleniyor)`);
    } else if (TRANSPORT_MODE === 'websocket' && webSocketServer) {
        logger.info('✅ Wokwi bağlandı - WebSocket server hazır');
        console.log(`✅ [Bridge] Wokwi bağlandı - WebSocket server hazır`);
    }
});

activeListener.onData((event, rawLine) => {
    // Pairing mode mesajını kontrol et
    if (rawLine && rawLine.includes('[BLE] Pairing mode aktif')) {
        console.log('\n🔵🔵🔵 ========================================');
        console.log('🔵 [WOKWI] Pairing mode aktif mesajı tespit edildi!');
        console.log('========================================\n');
        logger.info('🔵 [Bridge] Pairing mode mesajı alındı - Pairing mode aktif ediliyor');
        
        if (TRANSPORT_MODE === 'ble' && blePeripheral) {
            blePeripheral.enablePairingMode();
        } else if (TRANSPORT_MODE === 'websocket' && webSocketServer) {
            webSocketServer.setPairingMode(true);
        }
    } else if (rawLine && rawLine.includes('[BLE] Pairing mode sona erdi')) {
        console.log('\n🔴🔴🔴 ========================================');
        console.log('🔴 [WOKWI] Pairing mode sona erdi mesajı tespit edildi!');
        console.log('========================================\n');
        logger.info('🔴 [Bridge] Pairing mode sona erdi mesajı alındı');
        
        if (TRANSPORT_MODE === 'ble' && blePeripheral) {
            blePeripheral.disablePairingMode();
        } else if (TRANSPORT_MODE === 'websocket' && webSocketServer) {
            webSocketServer.setPairingMode(false);
        }
    } else if (event) {
        // Normal event - transport mode'a göre gönder
        // Log formatı: sendEvent çağrıldı: type=X m=Y s=Z - Wokwi'dan alındı
        const logParts = [`sendEvent çağrıldı: type=${event.type}`];
        if (event.mainIndex !== undefined) {
            logParts.push(`m=${event.mainIndex}`);
        }
        if (event.subIndex !== undefined) {
            logParts.push(`s=${event.subIndex}`);
        }
        const logMessage = `${logParts.join(' ')} - Wokwi'dan alındı`;
        
        // Wokwi event'lerini belirgin göster
        console.log(`\n🎮🎮🎮 ========================================`);
        console.log(`🎮 [WOKWI] EVENT GELDİ!`);
        console.log(`========================================`);
        console.log(`   ${logMessage}`);
        console.log(`   Raw line: "${rawLine}"`);
        console.log(`   Event: ${JSON.stringify(event)}`);
        console.log(`   📤 ${TRANSPORT_MODE === 'ble' ? 'BLE' : 'WebSocket'}'ye gönderiliyor...`);
        console.log(`========================================\n`);
        
        logger.info(logMessage);
        
        if (TRANSPORT_MODE === 'ble' && blePeripheral) {
            logger.info(`📤 [Bridge] BLE'ye gönderiliyor: ${JSON.stringify(event)}`);
            blePeripheral.sendEvent(event);
        } else if (TRANSPORT_MODE === 'websocket' && webSocketServer) {
            logger.info(`📤 [Bridge] WebSocket'e gönderiliyor: ${JSON.stringify(event)}`);
            webSocketServer.sendEvent(event);
        }
    } else {
        // Parse edilemeyen satırlar için minimal log
        logger.debug(`ℹ️  [Bridge] Event parse edilemedi: ${rawLine}`);
    }
});

activeListener.onError((error) => {
    logger.error(`❌ ${config.useRFC2217 ? 'RFC2217' : 'Serial port'} hatası: ${error.message}`);
    // Hata durumunda Wokwi bağlantısını kes olarak işaretle
    if (TRANSPORT_MODE === 'ble' && blePeripheral) {
        blePeripheral.setWokwiConnected(false);
    }
});

activeListener.onDisconnect(() => {
    logger.warn(`⚠️  ${config.useRFC2217 ? 'RFC2217' : 'Serial port'} kapandı`);
    // Wokwi bağlantısı kesildi
    if (TRANSPORT_MODE === 'ble' && blePeripheral) {
        blePeripheral.setWokwiConnected(false);
    }
});

// Setup BLE peripheral callbacks (sadece BLE modunda)
if (TRANSPORT_MODE === 'ble' && blePeripheral) {
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
}

// Startup
logger.info('========================================');
logger.info('Wokwi Bridge Server');
logger.info('========================================');
logger.info(`Transport Mode: ${TRANSPORT_MODE.toUpperCase()}`);
if (TRANSPORT_MODE === 'ble') {
    logger.info(`Device Name: ${bleConfig.DEVICE_NAME}`);
    logger.info(`Service UUID: ${bleConfig.getServiceUUID()}`);
    logger.info(`Characteristic UUID: ${bleConfig.getCharacteristicUUID()}`);
} else {
    logger.info(`WebSocket Server Port: 8080`);
}
logger.info('========================================\n');

console.log(`\n🚀 ========================================`);
console.log(`🚀 Bridge Server başlatılıyor...`);
console.log(`   Transport Mode: ${TRANSPORT_MODE.toUpperCase()}`);
console.log(`   Serial Port: ${config.serialPort || 'Otomatik'}`);
console.log(`   Baud Rate: ${config.baudRate}`);
console.log(`   Auto Reconnect: ${config.autoReconnect}`);
console.log(`========================================\n`);

logger.info('🚀 Server başlatılıyor...\n');

// Start listener (RFC2217 veya Serial)
const listenerType = config.useRFC2217 ? 'RFC2217' : 'Serial';

// WebSocket server'ı başlat (WebSocket modunda)
if (TRANSPORT_MODE === 'websocket' && webSocketServer) {
    webSocketServer.start().then(() => {
        logger.info('✅ WebSocket server başlatıldı');
    }).catch((error) => {
        logger.error(`❌ WebSocket server başlatılamadı: ${error.message}`);
        process.exit(1);
    });
}

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
process.on('SIGINT', async () => {
    logger.info('\n\n🛑 Server kapatılıyor...');
    
    if (activeListener) {
        activeListener.stop();
    }
    
    if (TRANSPORT_MODE === 'ble' && blePeripheral) {
        blePeripheral.stopAdvertising();
    } else if (TRANSPORT_MODE === 'websocket' && webSocketServer) {
        await webSocketServer.stop();
    }
    
    process.exit(0);
});

process.on('SIGTERM', async () => {
    logger.info('\n\n🛑 Server kapatılıyor (SIGTERM)...');
    
    if (activeListener) {
        activeListener.stop();
    }
    
    if (TRANSPORT_MODE === 'ble' && blePeripheral) {
        blePeripheral.stopAdvertising();
    } else if (TRANSPORT_MODE === 'websocket' && webSocketServer) {
        await webSocketServer.stop();
    }
    
    process.exit(0);
});
