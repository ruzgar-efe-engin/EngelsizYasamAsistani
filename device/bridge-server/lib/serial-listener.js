/**
 * Serial Listener
 * 
 * Serial port'u dinler ve event'leri parse eder.
 */

const { SerialPort } = require('serialport');
const { ReadlineParser } = require('@serialport/parser-readline');
const EventParser = require('./event-parser');

class SerialListener {
    constructor(config = {}) {
        this.config = {
            baudRate: config.baudRate || 115200,
            serialPort: config.serialPort || null,
            autoReconnect: config.autoReconnect !== false,
            reconnectDelay: config.reconnectDelay || 3000
        };
        
        this.serialPort = null;
        this.parser = null;
        this.isConnected = false;
        this.reconnectTimer = null;
        this.eventParser = new EventParser();
        
        this.onDataCallback = null;
        this.onErrorCallback = null;
        this.onConnectCallback = null;
        this.onDisconnectCallback = null;
        
        // Wokwi bağlantı doğrulama
        this.dataReceived = false;
        this.connectionTimeout = null;
        this.CONNECTION_TIMEOUT_MS = 10000; // 10 saniye içinde veri gelmezse bağlantı geçersiz
    }

    /**
     * Serial port'u başlat
     */
    async start() {
        try {
            console.log(`🚀 [Serial] Serial listener başlatılıyor...`);
            await this.findAndConnect();
            console.log(`✅ [Serial] Serial listener başarıyla başlatıldı`);
        } catch (error) {
            console.error(`❌ [Serial] Serial listener başlatma hatası: ${error.message}`);
            console.error(`   Stack: ${error.stack}`);
            if (this.onErrorCallback) {
                this.onErrorCallback(error);
            }
            
            if (this.config.autoReconnect) {
                console.log(`🔄 [Serial] Otomatik yeniden bağlanma planlanıyor...`);
                this.scheduleReconnect();
            }
        }
    }

    /**
     * Serial port'u durdur
     */
    stop() {
        if (this.reconnectTimer) {
            clearTimeout(this.reconnectTimer);
            this.reconnectTimer = null;
        }

        if (this.serialPort && this.serialPort.isOpen) {
            this.serialPort.close();
        }
        
        this.isConnected = false;
    }

    /**
     * Serial port'u bul ve bağlan
     */
    async findAndConnect() {
        try {
            console.log(`🔍 [Serial] Serial port aranıyor...`);
            const { SerialPort } = require('serialport');
            const ports = await SerialPort.list();
            console.log(`📋 [Serial] Bulunan portlar: ${ports.length} adet`);
            ports.forEach((port, index) => {
                console.log(`   ${index + 1}. ${port.path} (${port.manufacturer || 'Unknown'})`);
            });
            
            let targetPort = null;

            if (this.config.serialPort) {
                targetPort = ports.find(port => port.path === this.config.serialPort);
                if (!targetPort) {
                    throw new Error(`Belirtilen port bulunamadı: ${this.config.serialPort}`);
                }
            } else {
                // Önce tüm portları logla (debug için)
                console.log('🔍 Mevcut Serial portlar:');
                ports.forEach(port => {
                    console.log(`   - ${port.path} (${port.manufacturer || 'Unknown'} ${port.vendorId || ''}:${port.productId || ''})`);
                });
                
                // Wokwi port'unu bul - Daha esnek port bulma
                // Wokwi local simülasyonu genellikle şu port isimlerini kullanır:
                // - macOS: /dev/cu.usbserial-* veya /dev/cu.wchusbserial* veya /dev/tty.debug-console
                // - Linux: /dev/ttyUSB* veya /dev/ttyACM*
                // - Windows: COM*
                // Ama Bluetooth ve diğer sistem portlarını hariç tut
                targetPort = ports.find(port => {
                    const path = port.path.toLowerCase();
                    const portManufacturer = (port.manufacturer || '').toLowerCase();
                    
                    // Bluetooth portlarını hariç tut
                    if (path.includes('bluetooth') || path.includes('incoming')) {
                        console.log(`   ⏭️  Bluetooth port atlandı: ${port.path}`);
                        return false;
                    }
                    
                    // Wokwi'ye özel port isimleri (daha esnek)
                    const isWokwiPort = 
                        path.includes('usbserial') ||  // macOS USB serial
                        path.includes('wchusbserial') ||  // CH340/CH341
                        path.includes('ttyusb') ||  // Linux USB serial
                        path.includes('ttyacm') ||  // Linux ACM (Arduino)
                        path.includes('com') ||  // Windows COM port
                        // debug-console port'unu kaldırdık - Wokwi bu port'u kullanmıyor gibi görünüyor
                        // path.includes('debug-console') ||  // Wokwi debug console (macOS) - DEVRE DIŞI
                        // cu.* ve tty.* port'larını da devre dışı bıraktık - Wokwi farklı bir port kullanıyor olabilir
                        // (path.includes('cu.') && !path.includes('bluetooth')) ||  // macOS cu.* portları (Bluetooth hariç) - DEVRE DIŞI
                        // (path.includes('tty.') && !path.includes('bluetooth') && !path.includes('incoming')) ||  // macOS tty.* portları (Bluetooth hariç) - DEVRE DIŞI
                        portManufacturer.includes('wokwi') ||
                        port.vendorId === '1a86' ||  // CH340
                        port.vendorId === '10c4' ||  // Silicon Labs (yaygın USB-Serial)
                        port.productId === '6001';    // Wokwi product ID
                    
                    if (isWokwiPort) {
                        console.log(`   ✅ Wokwi port adayı bulundu: ${port.path}`);
                    }
                    
                    return isWokwiPort;
                });

                if (!targetPort) {
                    const availablePorts = ports.map(p => `${p.path} (${p.manufacturer || 'Unknown'})`).join(', ');
                    console.error(`❌ [Serial] Wokwi port bulunamadı!`);
                    console.error(`   Mevcut portlar: ${availablePorts || 'Yok'}`);
                    console.error(`\n💡 Çözüm önerileri:`);
                    console.error(`   1. Wokwi simülasyonunun çalıştığından emin olun`);
                    console.error(`   2. Serial Monitor'ün açık olduğundan emin olun`);
                    console.error(`   3. Manuel port belirtin: SERIAL_PORT=/dev/tty.debug-console npm start`);
                    console.error(`   4. Veya diğer port'ları deneyin: SERIAL_PORT=/dev/tty.MINORIII npm start`);
                    throw new Error(`Wokwi Serial port bulunamadı. Mevcut portlar: ${availablePorts || 'Yok'}\n\nManuel port belirtmek için: SERIAL_PORT=/dev/tty.xxx npm start`);
                }
            }

            return await this.connect(targetPort);
        } catch (error) {
            throw new Error(`Serial port bulunamadı: ${error.message}`);
        }
    }

    /**
     * Serial port'a bağlan
     */
    connect(portInfo) {
        return new Promise((resolve, reject) => {
            try {
                const { SerialPort } = require('serialport');
                this.serialPort = new SerialPort({
                    path: portInfo.path,
                    baudRate: this.config.baudRate,
                    autoOpen: false
                });

                this.parser = this.serialPort.pipe(new ReadlineParser({ delimiter: '\n' }));

                this.serialPort.open((err) => {
                    if (err) {
                        console.error(`❌ [Serial] Port açılamadı: ${err.message}`);
                        reject(new Error(`Port açılamadı: ${err.message}`));
                        return;
                    }

                    try {
                        console.log(`✅ [Serial] Port açıldı: ${portInfo.path}`);
                        console.log(`   Baud Rate: ${this.config.baudRate}`);
                        console.log(`   ⚠️  Wokwi bağlantısı doğrulanıyor... (10 saniye içinde veri bekleniyor)`);
                        this.isConnected = true;
                        this.dataReceived = false;
                        
                        // 10 saniye içinde veri gelmezse bağlantı geçersiz sayılacak
                        this.connectionTimeout = setTimeout(() => {
                            if (!this.dataReceived) {
                                console.error(`❌ [Serial] Wokwi'den veri gelmedi! Bağlantı geçersiz.`);
                                console.error(`   Port: ${portInfo.path}`);
                                console.error(`   💡 Bu port Wokwi port'u olmayabilir veya Serial Monitor açık değil.`);
                                console.error(`   💡 Wokwi simülasyonunda Serial Monitor'ün açık olduğundan emin olun.`);
                                console.error(`   💡 Alternatif port'ları denemek için: SERIAL_PORT=/dev/tty.xxx npm start`);
                                this.isConnected = false;
                                if (this.serialPort && this.serialPort.isOpen) {
                                    this.serialPort.close();
                                }
                                if (this.onDisconnectCallback) {
                                    this.onDisconnectCallback();
                                }
                            }
                        }, this.CONNECTION_TIMEOUT_MS);

                        this.setupEventHandlers();
                        console.log(`✅ [Serial] Event handler'lar ayarlandı, veri bekleniyor...`);
                        resolve(portInfo);
                    } catch (setupError) {
                        console.error(`❌ [Serial] Event handler setup hatası: ${setupError.message}`);
                        reject(new Error(`Event handler setup hatası: ${setupError.message}`));
                    }
                });
            } catch (error) {
                reject(new Error(`Serial port oluşturulamadı: ${error.message}`));
            }
        });
    }

    /**
     * Event handler'ları ayarla
     */
    setupEventHandlers() {
        try {
            console.log(`🔧 [Serial] Event handler'lar kuruluyor...`);
            
            // Parser'ın data alıp almadığını kontrol et
            this.parser.on('readable', () => {
                console.log(`📖 [Serial] Parser readable event tetiklendi`);
            });
            
            // Data handler
            this.parser.on('data', (data) => {
                try {
                    // İlk veri geldi - Wokwi bağlantısı doğrulandı
                    if (!this.dataReceived) {
                        this.dataReceived = true;
                        if (this.connectionTimeout) {
                            clearTimeout(this.connectionTimeout);
                            this.connectionTimeout = null;
                        }
                        console.log(`\n🎉 ========================================`);
                        console.log(`✅ [Serial] Wokwi'den veri alındı! Bağlantı doğrulandı.`);
                        console.log(`========================================\n`);
                        
                        // Şimdi onConnectCallback'i çağır (gerçek bağlantı doğrulandı)
                        if (this.onConnectCallback) {
                            this.onConnectCallback({ path: this.serialPort.path });
                        }
                    }
                    
                    const rawData = data.toString();
                    const line = rawData.trim();
                    
                    // TÜM satırları log'la (debug için) - boş satırlar dahil
                    console.log(`📥 [Serial Raw] (${rawData.length} bytes) "${line}"`);
                    
                    // Boş satırları da log'la
                    if (line.length === 0) {
                        console.log(`⚠️  [Serial] Boş satır alındı`);
                    }

                    // Pairing mode mesajlarını kontrol et (parse etmeden callback'e gönder)
                    if (line.includes('[BLE] Pairing mode aktif')) {
                        console.log('🔵 [Serial] Pairing mode aktif mesajı tespit edildi!');
                        if (this.onDataCallback) {
                            this.onDataCallback(null, line); // Event null, rawLine pairing mode mesajı
                        }
                        return;
                    }
                    
                    if (line.includes('[BLE] Pairing mode sona erdi')) {
                        console.log('🔴 [Serial] Pairing mode sona erdi mesajı tespit edildi!');
                        if (this.onDataCallback) {
                            this.onDataCallback(null, line); // Event null, rawLine pairing mode mesajı
                        }
                        return;
                    }

                    // [BLE] veya [SERIAL] prefix'li satırları işle
                    if (line.includes('[BLE]') || line.includes('[SERIAL]')) {
                        console.log(`📦 [Serial] Event satırı tespit edildi: ${line}`);
                        const event = this.eventParser.parse(line);
                        
                        if (event) {
                            console.log(`✅ [Serial] Event parse edildi: ${JSON.stringify(event)}`);
                        } else {
                            console.log(`⚠️  [Serial] Event parse edilemedi: ${line}`);
                        }
                        
                        if (this.onDataCallback) {
                            this.onDataCallback(event, line); // Her durumda callback çağır (event null olabilir)
                        }
                    } else {
                        // [BLE] veya [SERIAL] prefix'i yoksa sadece log'la
                        console.log(`ℹ️  [Serial] Prefix yok, atlanıyor: ${line}`);
                    }
                } catch (parseError) {
                    // Parse hatası, logla ama devam et
                    console.error(`❌ [Serial] Parse hatası: ${parseError.message}`);
                    if (this.onErrorCallback) {
                        this.onErrorCallback(new Error(`Data parse hatası: ${parseError.message}`));
                    }
                }
            });

            // Error handler
            this.serialPort.on('error', (err) => {
                this.isConnected = false;
                
                if (this.onErrorCallback) {
                    this.onErrorCallback(err);
                }

                if (this.config.autoReconnect) {
                    this.scheduleReconnect();
                }
            });

            // Close handler
            this.serialPort.on('close', () => {
                this.isConnected = false;
                
                if (this.onDisconnectCallback) {
                    this.onDisconnectCallback();
                }

                if (this.config.autoReconnect) {
                    this.scheduleReconnect();
                }
            });
        } catch (error) {
            throw new Error(`Event handler setup hatası: ${error.message}`);
        }
    }

    /**
     * Yeniden bağlanmayı planla
     */
    scheduleReconnect() {
        if (this.reconnectTimer) {
            return;
        }

        this.reconnectTimer = setTimeout(() => {
            this.reconnectTimer = null;
            this.start().catch(() => {
                // Reconnect failed, will retry on next schedule
            });
        }, this.config.reconnectDelay);
    }

    /**
     * Data callback'i ayarla
     */
    onData(callback) {
        this.onDataCallback = callback;
    }

    /**
     * Error callback'i ayarla
     */
    onError(callback) {
        this.onErrorCallback = callback;
    }

    /**
     * Connect callback'i ayarla
     */
    onConnect(callback) {
        this.onConnectCallback = callback;
    }

    /**
     * Disconnect callback'i ayarla
     */
    onDisconnect(callback) {
        this.onDisconnectCallback = callback;
    }

    /**
     * Bağlı mı kontrol et
     */
    isPortConnected() {
        return this.isConnected && this.serialPort && this.serialPort.isOpen;
    }
}

module.exports = SerialListener;

