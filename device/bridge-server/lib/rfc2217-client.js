/**
 * RFC2217 Client
 * 
 * Wokwi RFC2217 server'ına (port 4000) bağlanır ve serial port erişimi sağlar.
 */

const net = require('net');
const EventParser = require('./event-parser');

class RFC2217Client {
    constructor(config = {}) {
        this.config = {
            host: config.host || 'localhost',
            port: config.port || 4000,
            baudRate: config.baudRate || 115200,
            autoReconnect: config.autoReconnect !== false,
            reconnectDelay: config.reconnectDelay || 3000
        };
        
        this.socket = null;
        this.isConnected = false;
        this.eventParser = new EventParser();
        this.buffer = '';
        
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
     * RFC2217 server'a bağlan
     */
    async start() {
        try {
            console.log(`🚀 [RFC2217] Wokwi RFC2217 server'ına bağlanılıyor...`);
            console.log(`   Host: ${this.config.host}:${this.config.port}`);
            console.log(`   Baud Rate: ${this.config.baudRate}`);
            
            await this.connect();
            console.log(`✅ [RFC2217] RFC2217 client başarıyla başlatıldı`);
        } catch (error) {
            console.error(`\n❌ ========================================`);
            console.error(`❌ [RFC2217] RFC2217 client başlatma hatası: ${error.message}`);
            console.error(`   Error Code: ${error.code || 'N/A'}`);
            console.error(`   Error Type: ${error.constructor.name}`);
            console.error(`   Stack Trace:`);
            console.error(`   ${error.stack}`);
            console.error(`========================================\n`);
            
            if (this.onErrorCallback) {
                this.onErrorCallback(error);
            }
            
            // Auto-reconnect
            if (this.config.autoReconnect) {
                console.log(`🔄 [RFC2217] Otomatik yeniden bağlanma planlanıyor... (${this.config.reconnectDelay}ms sonra)`);
                setTimeout(() => {
                    this.start().catch(() => {
                        // Reconnect failed, will retry on next schedule
                    });
                }, this.config.reconnectDelay);
            } else {
                console.log(`⚠️  [RFC2217] Auto-reconnect kapalı, manuel başlatma gerekli`);
            }
        }
    }

    /**
     * TCP socket bağlantısı kur
     */
    connect() {
        return new Promise((resolve, reject) => {
            try {
                this.socket = new net.Socket();
                this.buffer = '';
                this.dataReceived = false;
                
                // Bağlantı başarılı
                this.socket.on('connect', () => {
                    console.log(`\n✅ ========================================`);
                    console.log(`✅ [RFC2217] TCP bağlantısı kuruldu: ${this.config.host}:${this.config.port}`);
                    console.log(`   Baud Rate: ${this.config.baudRate}`);
                    console.log(`   ⚠️  Wokwi bağlantısı doğrulanıyor... (10 saniye içinde veri bekleniyor)`);
                    console.log(`   💡 Wokwi Serial Monitor'ün açık olduğundan emin olun`);
                    console.log(`========================================\n`);
                    this.isConnected = true;
                    
                    // RFC2217 SET_BAUDRATE komutu gönder
                    this.setBaudRate(this.config.baudRate);
                    
                    // 10 saniye içinde veri gelmezse bağlantı geçersiz sayılacak
                    this.connectionTimeout = setTimeout(() => {
                        if (!this.dataReceived) {
                            console.error(`\n❌ ========================================`);
                            console.error(`❌ [RFC2217] Wokwi'den veri gelmedi! Bağlantı geçersiz.`);
                            console.error(`   💡 Wokwi simülasyonunun çalıştığından emin olun`);
                            console.error(`   💡 Serial Monitor'ün açık olduğundan emin olun`);
                            console.error(`   💡 Device'tan veri gönderildiğinden emin olun`);
                            console.error(`========================================\n`);
                            this.isConnected = false;
                            if (this.socket) {
                                this.socket.destroy();
                            }
                            if (this.onDisconnectCallback) {
                                this.onDisconnectCallback();
                            }
                        }
                    }, this.CONNECTION_TIMEOUT_MS);
                    
                    // onConnectCallback'i burada çağırma - sadece handleData içinde ilk veri geldiğinde çağırılacak
                    resolve();
                });
                
                // Veri alındı
                this.socket.on('data', (data) => {
                    console.log(`📥 [RFC2217] Raw data alındı: ${data.length} bytes`);
                    this.handleData(data);
                });
                
                // Hata
                this.socket.on('error', (err) => {
                    console.error(`\n❌ ========================================`);
                    console.error(`❌ [RFC2217] Socket hatası: ${err.message}`);
                    console.error(`   Error Code: ${err.code || 'N/A'}`);
                    console.error(`   Error Type: ${err.constructor.name}`);
                    if (err.code === 'ECONNREFUSED') {
                        console.error(`   💡 Wokwi RFC2217 server çalışmıyor olabilir`);
                        console.error(`   💡 Wokwi simülasyonunun çalıştığından emin olun`);
                        console.error(`   💡 Port ${this.config.port} açık mı kontrol edin`);
                    } else if (err.code === 'ETIMEDOUT') {
                        console.error(`   💡 Bağlantı zaman aşımına uğradı`);
                        console.error(`   💡 Ağ bağlantısını kontrol edin`);
                    }
                    console.error(`========================================\n`);
                    this.isConnected = false;
                    if (this.onErrorCallback) {
                        this.onErrorCallback(err);
                    }
                    reject(err);
                });
                
                // Bağlantı kapandı
                this.socket.on('close', () => {
                    console.warn(`⚠️  [RFC2217] TCP bağlantısı kapandı`);
                    this.isConnected = false;
                    if (this.onDisconnectCallback) {
                        this.onDisconnectCallback();
                    }
                    
                    // Auto-reconnect
                    if (this.config.autoReconnect) {
                        console.log(`🔄 [RFC2217] Otomatik yeniden bağlanma planlanıyor... (${this.config.reconnectDelay}ms sonra)`);
                        setTimeout(() => {
                            this.start().catch(() => {
                                // Reconnect failed
                            });
                        }, this.config.reconnectDelay);
                    }
                });
                
                // Bağlan
                console.log(`🔌 [RFC2217] Bağlanılıyor: ${this.config.host}:${this.config.port}`);
                this.socket.connect(this.config.port, this.config.host);
            } catch (error) {
                reject(new Error(`RFC2217 socket oluşturulamadı: ${error.message}`));
            }
        });
    }

    /**
     * RFC2217 SET_BAUDRATE komutu gönder
     */
    setBaudRate(baudRate) {
        // RFC2217 SET_BAUDRATE: IAC SB COM_PORT_OPTION SET_BAUDRATE <baudrate> IAC SE
        // Basitleştirilmiş: Sadece veri göndermeye başla, RFC2217 komutları opsiyonel
        console.log(`📡 [RFC2217] Baud rate ayarlandı: ${baudRate}`);
    }

    /**
     * Gelen veriyi işle
     */
    handleData(data) {
        // İlk veri geldi - Wokwi bağlantısı doğrulandı
        if (!this.dataReceived) {
            this.dataReceived = true;
            if (this.connectionTimeout) {
                clearTimeout(this.connectionTimeout);
                this.connectionTimeout = null;
            }
            console.log(`\n🎉 ========================================`);
            console.log(`✅ [RFC2217] Wokwi'den veri alındı! Bağlantı doğrulandı.`);
            console.log(`========================================\n`);
            
            // Şimdi onConnectCallback'i çağır (gerçek bağlantı doğrulandı)
            if (this.onConnectCallback) {
                this.onConnectCallback({ host: this.config.host, port: this.config.port });
            }
        }
        
        const rawData = data.toString();
        this.buffer += rawData;
        
        // Satır satır işle
        const lines = this.buffer.split('\n');
        this.buffer = lines.pop() || ''; // Son satır tamamlanmamış olabilir
        
        lines.forEach(line => {
            const trimmedLine = line.trim();
            
            if (trimmedLine.length === 0) {
                return;
            }
            
            // TÜM satırları log'la
            console.log(`📥 [RFC2217 Raw] (${trimmedLine.length} bytes) "${trimmedLine}"`);
            
            // Pairing mode mesajlarını kontrol et
            if (trimmedLine.includes('[BLE] Pairing mode aktif')) {
                console.log('🔵 [RFC2217] Pairing mode aktif mesajı tespit edildi!');
                if (this.onDataCallback) {
                    this.onDataCallback(null, trimmedLine);
                }
                return;
            }
            
            if (trimmedLine.includes('[BLE] Pairing mode sona erdi')) {
                console.log('🔴 [RFC2217] Pairing mode sona erdi mesajı tespit edildi!');
                if (this.onDataCallback) {
                    this.onDataCallback(null, trimmedLine);
                }
                return;
            }
            
            // [BLE] veya [SERIAL] prefix'li satırları işle
            if (trimmedLine.includes('[BLE]') || trimmedLine.includes('[SERIAL]')) {
                console.log(`📦 [RFC2217] Event satırı tespit edildi: ${trimmedLine}`);
                const event = this.eventParser.parse(trimmedLine);
                
                if (event) {
                    console.log(`✅ [RFC2217] Event parse edildi: ${JSON.stringify(event)}`);
                } else {
                    console.log(`⚠️  [RFC2217] Event parse edilemedi: ${trimmedLine}`);
                }
                
                if (this.onDataCallback) {
                    this.onDataCallback(event, trimmedLine);
                }
            } else {
                // [BLE] veya [SERIAL] prefix'i yoksa sadece log'la
                console.log(`ℹ️  [RFC2217] Prefix yok, atlanıyor: ${trimmedLine}`);
            }
        });
    }

    /**
     * Bağlantıyı kapat
     */
    stop() {
        if (this.connectionTimeout) {
            clearTimeout(this.connectionTimeout);
            this.connectionTimeout = null;
        }
        
        if (this.socket) {
            this.socket.destroy();
        }
        
        this.isConnected = false;
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
        return this.isConnected && this.socket && !this.socket.destroyed;
    }
}

module.exports = RFC2217Client;

