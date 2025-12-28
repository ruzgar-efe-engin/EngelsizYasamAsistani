/**
 * WebSocket Server
 * 
 * Bridge server için WebSocket server implementasyonu.
 * Wokwi event'lerini WebSocket üzerinden client'lara iletir.
 */

const WebSocket = require('ws');

class WebSocketServer {
    constructor(config = {}) {
        this.port = config.port || 8080;
        this.wss = null;
        this.clients = new Set();
        this.isPairingModeActive = false;
        this.logger = config.logger || console;
    }
    
    /**
     * WebSocket server'ı başlat
     */
    start() {
        return new Promise((resolve, reject) => {
            try {
                this.wss = new WebSocket.Server({ port: this.port });
                
                this.wss.on('listening', () => {
                    this.logger.info(`✅ WebSocket server başlatıldı: ws://localhost:${this.port}`);
                    resolve();
                });
                
                this.wss.on('error', (error) => {
                    this.logger.error(`❌ WebSocket server hatası: ${error.message}`);
                    reject(error);
                });
                
                this.wss.on('connection', (ws, req) => {
                    const clientAddress = req.socket.remoteAddress;
                    this.logger.info(`📡 WebSocket client bağlandı: ${clientAddress}`);
                    
                    this.clients.add(ws);
                    
                    // Bağlantı mesajı gönder
                    this.sendToClient(ws, {
                        type: 'connected',
                        message: 'WebSocket bağlantısı başarılı'
                    });
                    
                    // Pairing mode durumunu gönder
                    this.sendToClient(ws, {
                        type: 'pairing_mode',
                        active: this.isPairingModeActive
                    });
                    
                    ws.on('message', (message) => {
                        try {
                            const data = JSON.parse(message.toString());
                            this.logger.debug(`📥 WebSocket mesajı alındı: ${JSON.stringify(data)}`);
                            // Client mesajlarını handle et (gerekirse)
                        } catch (e) {
                            this.logger.warn(`⚠️  WebSocket mesaj parse hatası: ${e.message}`);
                        }
                    });
                    
                    ws.on('close', () => {
                        this.logger.info(`🔌 WebSocket client bağlantısı kesildi: ${clientAddress}`);
                        this.clients.delete(ws);
                    });
                    
                    ws.on('error', (error) => {
                        this.logger.error(`❌ WebSocket client hatası: ${error.message}`);
                        this.clients.delete(ws);
                    });
                });
                
            } catch (error) {
                this.logger.error(`❌ WebSocket server başlatılamadı: ${error.message}`);
                reject(error);
            }
        });
    }
    
    /**
     * WebSocket server'ı durdur
     */
    stop() {
        return new Promise((resolve) => {
            if (this.wss) {
                // Tüm client'lara kapanma mesajı gönder
                this.broadcast({
                    type: 'closing',
                    message: 'Server kapanıyor'
                });
                
                // Tüm client bağlantılarını kapat
                this.clients.forEach(client => {
                    client.close();
                });
                this.clients.clear();
                
                // Server'ı kapat
                this.wss.close(() => {
                    this.logger.info('✅ WebSocket server durduruldu');
                    this.wss = null;
                    resolve();
                });
            } else {
                resolve();
            }
        });
    }
    
    /**
     * Belirli bir client'a mesaj gönder
     * @param {WebSocket} client WebSocket client
     * @param {Object} message Mesaj objesi
     */
    sendToClient(client, message) {
        if (client && client.readyState === WebSocket.OPEN) {
            try {
                const json = JSON.stringify(message);
                client.send(json);
                this.logger.debug(`📤 WebSocket mesajı gönderildi: ${JSON.stringify(message)}`);
            } catch (e) {
                this.logger.error(`❌ WebSocket mesaj gönderme hatası: ${e.message}`);
            }
        }
    }
    
    /**
     * Tüm client'lara mesaj gönder (broadcast)
     * @param {Object} message Mesaj objesi
     */
    broadcast(message) {
        const json = JSON.stringify(message);
        let sentCount = 0;
        
        this.clients.forEach(client => {
            if (client.readyState === WebSocket.OPEN) {
                try {
                    client.send(json);
                    sentCount++;
                } catch (e) {
                    this.logger.error(`❌ WebSocket broadcast hatası: ${e.message}`);
                }
            }
        });
        
        if (sentCount > 0) {
            this.logger.debug(`📤 WebSocket broadcast: ${sentCount} client'a gönderildi`);
        }
    }
    
    /**
     * Event'i tüm client'lara gönder
     * @param {Object} event Event objesi
     */
    sendEvent(event) {
        this.broadcast({
            type: 'event',
            data: JSON.stringify(event)
        });
    }
    
    /**
     * Pairing mode durumunu güncelle ve client'lara bildir
     * @param {Boolean} isActive Pairing mode aktif mi?
     */
    setPairingMode(isActive) {
        this.isPairingModeActive = isActive;
        this.broadcast({
            type: 'pairing_mode',
            active: isActive
        });
        this.logger.info(`🔵 Pairing mode güncellendi: ${isActive ? 'aktif' : 'pasif'}`);
    }
    
    /**
     * Bağlı client sayısını al
     * @return {Number} Bağlı client sayısı
     */
    getClientCount() {
        return this.clients.size;
    }
}

module.exports = WebSocketServer;

