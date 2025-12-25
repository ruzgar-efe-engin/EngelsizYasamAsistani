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
    }

    /**
     * Serial port'u başlat
     */
    async start() {
        try {
            await this.findAndConnect();
        } catch (error) {
            if (this.onErrorCallback) {
                this.onErrorCallback(error);
            }
            
            if (this.config.autoReconnect) {
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
            const { SerialPort } = require('serialport');
            const ports = await SerialPort.list();
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
                
                // Wokwi veya USB serial port'u bul
                // Wokwi simülasyonu için özel port isimleri
                targetPort = ports.find(port =>
                    port.path.includes('usbserial') ||
                    port.path.includes('ttyUSB') ||
                    port.path.includes('COM') ||
                    port.path.includes('cu.') ||  // macOS cu.* portları
                    port.path.includes('tty.') ||   // macOS tty.* portları
                    port.manufacturer?.toLowerCase().includes('wokwi') ||
                    port.manufacturer?.toLowerCase().includes('serial') ||
                    port.productId === '6001' ||  // Wokwi'nin kullandığı product ID
                    port.vendorId === '1a86'      // CH340 USB-Serial (yaygın)
                );

                if (!targetPort) {
                    const availablePorts = ports.map(p => `${p.path} (${p.manufacturer || 'Unknown'})`).join(', ');
                    throw new Error(`Serial port bulunamadı. Mevcut portlar: ${availablePorts || 'Yok'}\n\nWokwi simülasyonu için:\n1. Wokwi simülasyonunun çalıştığından emin olun\n2. Serial Monitor'ün açık olduğundan emin olun\n3. Manuel port belirtin: SERIAL_PORT=/dev/tty.xxx npm start`);
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
                        reject(new Error(`Port açılamadı: ${err.message}`));
                        return;
                    }

                    try {
                        this.isConnected = true;
                        
                        if (this.onConnectCallback) {
                            this.onConnectCallback(portInfo);
                        }

                        this.setupEventHandlers();
                        resolve(portInfo);
                    } catch (setupError) {
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
            // Data handler
            this.parser.on('data', (data) => {
                try {
                    const line = data.toString().trim();

                    // [BLE] veya [SERIAL] prefix'li satırları işle
                    if (line.includes('[BLE]') || line.includes('[SERIAL]')) {
                        const event = this.eventParser.parse(line);
                        
                        if (event && this.onDataCallback) {
                            this.onDataCallback(event, line);
                        }
                    }
                } catch (parseError) {
                    // Parse hatası, logla ama devam et
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

