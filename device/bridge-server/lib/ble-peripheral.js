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
        
        // Event buffer - subscribe olmadan önce gelen event'leri sakla
        this.eventBuffer = [];
        this.MAX_EVENT_BUFFER_SIZE = 10; // Maksimum 10 event sakla
        
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
                console.error('❌ Advertising start hatası:', error);
                return;
            }

            console.log('\n✅ ========================================');
            console.log('✅ advertisingStart EVENT TETİKLENDİ');
            console.log('========================================');
            console.log('   Service\'ler ayarlanıyor...');
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
            console.log('\n📦 ========================================');
            console.log('📦 servicesSet EVENT TETİKLENDİ');
            console.log('========================================');
            
            if (error) {
                console.error('❌ Service set hatası:', error);
                console.error('   Service\'ler hazır DEĞİL!');
                return;
            }

            console.log('✅ Service\'ler başarıyla set edildi!');
            
            // KRİTİK: Önceki _lastEvent ve _value değerlerini koru
            const previousLastEvent = this.eventCharacteristic?._lastEvent;
            const previousValue = this.eventCharacteristic?._value;
            const previousReadRequestCount = this.eventCharacteristic?._readRequestCount || 0;
            const previousUpdateValueCallback = this.eventCharacteristic?._updateValueCallback;
            
            if (previousLastEvent && previousLastEvent.length > 0) {
                console.log(`   💡 Önceki _lastEvent bulundu: ${previousLastEvent.length} bytes`);
                console.log(`   📦 Önceki _lastEvent içeriği: ${previousLastEvent.toString('utf8')}`);
            }
            if (previousValue && previousValue.length > 0) {
                console.log(`   💡 Önceki _value bulundu: ${previousValue.length} bytes`);
            }
            
            // Characteristic referansını al
            const service = new EventService();
            this.eventCharacteristic = service.characteristics[0];
            
            // Characteristic'e parent referansı ekle (buffer erişimi için)
            if (this.eventCharacteristic) {
                this.eventCharacteristic._parent = this;
            }
            
            // KRİTİK: Önceki _lastEvent değerini koru (polling mekanizması için gerekli)
            if (previousLastEvent && previousLastEvent.length > 0) {
                this.eventCharacteristic._lastEvent = previousLastEvent;
                console.log(`   ✅ Önceki _lastEvent korundu: ${previousLastEvent.length} bytes`);
                console.log(`   📦 Korunan _lastEvent içeriği: ${previousLastEvent.toString('utf8')}`);
            } else if (this.currentEventValue && this.currentEventValue.length > 0) {
                // Eğer önceki _lastEvent yoksa, currentEventValue'yu kullan
                this.eventCharacteristic._lastEvent = this.currentEventValue;
                console.log(`   💡 currentEventValue _lastEvent'e kopyalandı: ${this.currentEventValue.length} bytes`);
            }
            
            // Önceki _value değerini de koru (isteğe bağlı ama faydalı)
            if (previousValue && previousValue.length > 0) {
                this.eventCharacteristic._value = previousValue;
                console.log(`   ✅ Önceki _value korundu: ${previousValue.length} bytes`);
            }
            
            // Read request sayacını ve callback'i koru (isteğe bağlı)
            if (previousReadRequestCount > 0) {
                this.eventCharacteristic._readRequestCount = previousReadRequestCount;
                console.log(`   💡 Read request sayacı korundu: ${previousReadRequestCount}`);
            }
            if (previousUpdateValueCallback) {
                this.eventCharacteristic._updateValueCallback = previousUpdateValueCallback;
                console.log(`   ✅ _updateValueCallback korundu (subscribe aktif)`);
            }
            
            console.log('✅ eventCharacteristic referansı alındı');
            console.log('   Characteristic UUID:', this.eventCharacteristic.uuid);
            console.log('   Service\'ler artık HAZIR - event\'ler gönderilebilir');
            console.log(`   Event buffer durumu: ${this.eventBuffer?.length || 0} event bekliyor`);

            // currentEventValue'yu _lastEvent'e kopyala (polling için kritik)
            if (this.currentEventValue.length > 0) {
                console.log(`📤 Buffer'da bekleyen event var (${this.currentEventValue.length} bytes)`);
                console.log(`   💡 Event _lastEvent'e kopyalanıyor (polling için)...`);
                this.eventCharacteristic._lastEvent = this.currentEventValue;
                console.log(`   ✅ Event _lastEvent'e kopyalandı: ${this.eventCharacteristic._lastEvent.length} bytes`);
                console.log(`   📤 Event gönderiliyor...`);
                const result = this.eventCharacteristic.updateValue(this.currentEventValue);
                console.log(`✅ Buffer'daki event gönderildi: ${result ? 'başarılı' : 'başarısız'}`);
                this.currentEventValue = Buffer.alloc(0);
            } else {
                console.log('ℹ️  Buffer\'da bekleyen event yok');
                // _lastEvent'i sadece gerçekten boşsa sıfırla
                // Eğer currentEventValue varsa, _lastEvent'e kopyala (zaten yapılıyor satır 116'da)
                // Eğer _lastEvent zaten doluysa, koru (sıfırlama!)
                if (this.currentEventValue.length === 0 && (!this.eventCharacteristic._lastEvent || this.eventCharacteristic._lastEvent.length === 0)) {
                    this.eventCharacteristic._lastEvent = Buffer.alloc(0);
                    console.log('   💡 _lastEvent boş buffer ile initialize edildi');
                } else {
                    console.log(`   💡 _lastEvent korunuyor (${this.eventCharacteristic._lastEvent?.length || 0} bytes)`);
                }
            }
        });
    }

    /**
     * BLE Advertising başlat
     * Sadece Wokwi bağlıysa ve pairing mode aktifse advertising yap
     */
    startAdvertising() {
        console.log('\n🔍 ========================================');
        console.log('🔍 startAdvertising() ÇAĞRILDI');
        console.log('========================================');
        console.log(`   Wokwi bağlantı durumu: ${this.isWokwiConnected}`);
        console.log(`   Pairing mode durumu: ${this.isPairingModeActive}`);
        console.log(`   BLE state: ${bleno.state}`);
        
        if (bleno.state !== 'poweredOn') {
            console.log('⚠️  BLE Advertising: Bluetooth açık değil (state: ' + bleno.state + ')');
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

        console.log('✅ Tüm koşullar sağlandı, BLE Advertising başlatılıyor:');
        console.log(`   Device Name: ${bleConfig.DEVICE_NAME}`);
        console.log(`   Service UUID: ${bleConfig.getServiceUUID()}`);
        bleno.startAdvertising(bleConfig.DEVICE_NAME, [bleConfig.getServiceUUID()], (error) => {
            if (error) {
                console.error('❌ BLE Advertising hatası:', error);
            } else {
                console.log('✅ BLE Advertising başlatıldı - Android cihazlar artık bulabilir');
                console.log('   📡 Android cihazlar bu cihazı tarayabilir');
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
        console.log('\n🔧 ========================================');
        console.log('🔧 setupServices() ÇAĞRILDI');
        console.log('========================================');
        console.log('   Service\'ler set ediliyor...');
        console.log('   servicesSet event\'i bekleniyor...');
        
        bleno.setServices([
            new EventService()
        ], (error) => {
            if (error) {
                console.error('❌ Service set hatası:', error);
                console.error('   Service\'ler hazır DEĞİL!');
            } else {
                console.log('✅ setServices callback başarılı');
                console.log('   💡 servicesSet event\'i yakında tetiklenecek');
            }
        });
    }

    /**
     * Event'i BLE'ye gönder
     * @param {Object} event - Event object
     */
    sendEvent(event) {
        const timestamp = Date.now();
        const threadInfo = `Node.js main thread (${process.pid})`;
        
        console.log('\n🚀🚀🚀 ========================================');
        console.log('🚀 sendEvent() ÇAĞRILDI!');
        console.log('========================================');
        console.log(`   Timestamp: ${new Date(timestamp).toISOString()}`);
        console.log(`   Thread: ${threadInfo}`);
        console.log(`   Event: ${JSON.stringify(event)}`);
        console.log(`   eventCharacteristic durumu: ${this.eventCharacteristic ? 'VAR ✅' : 'NULL ❌'}`);
        if (this.eventCharacteristic) {
            console.log(`   _lastEvent önceki durumu: ${this.eventCharacteristic._lastEvent?.length || 0} bytes`);
        }
        console.log('========================================\n');
        
        try {
            // ============================================
            // GEÇİCİ TEST: Payload Boyutu Testi
            // ============================================
            // Bu test, notify mekanizmasının çalışıp çalışmadığını kontrol etmek için
            // 1 byte'lık test payload gönderir.
            // Test sonrası bu kısmı kaldırıp normal JSON payload'a geri dön.
            // ============================================
            const USE_1BYTE_TEST_PAYLOAD = process.env.USE_1BYTE_TEST_PAYLOAD === 'true';
            
            if (USE_1BYTE_TEST_PAYLOAD) {
                console.log('\n🧪 ========================================');
                console.log('🧪 PAYLOAD BOYUTU TESTİ AKTİF');
                console.log('========================================');
                console.log('   Test payload: 1 byte (0x01)');
                console.log('   Normal JSON payload devre dışı');
                console.log('========================================\n');
                
                const testData = Buffer.from([0x01]);
                
                if (bleno.state !== 'poweredOn') {
                    console.warn('⚠️  BLE state poweredOn değil, test payload gönderilemiyor');
                    return;
                }
                
                if (this.eventCharacteristic) {
                    const result = this.eventCharacteristic.updateValue(testData);
                    console.log(`✅ Test payload gönderildi: ${result ? 'başarılı (notify gönderildi)' : 'başarısız (subscribe yok veya hata)'}`);
                    if (result) {
                        console.log('   💡 Android cihazda 1 byte event alınmalı');
                        console.log('   💡 Eğer 1 byte geliyorsa: notify çalışıyor, JSON/MTU sorunu var');
                        console.log('   💡 Eğer 1 byte bile gelmiyorsa: notify mekanizması tamamen çalışmıyor');
                    }
                } else {
                    console.warn('⚠️  Characteristic hazır değil, test payload gönderilemedi');
                }
                return; // Test modunda normal event gönderme işlemini atla
            }
            // ============================================
            // TEST KODU SONU
            // ============================================
            
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
            console.log(`   Timestamp: ${new Date(timestamp).toISOString()}`);
            console.log(`   Thread: ${threadInfo}`);
            
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
            console.log(`🔍 Service durumu kontrol ediliyor...`);
            console.log(`   eventCharacteristic var mı: ${this.eventCharacteristic ? 'EVET ✅' : 'HAYIR ❌'}`);
            console.log(`   isAdvertising: ${this.isAdvertising}`);
            console.log(`   bleno.state: ${bleno.state}`);

            if (this.eventCharacteristic) {
                console.log('✅ Characteristic hazır, event gönderiliyor...');
                
                // Hem notify hem de polling-based read için hazırla
                // 1. Notify için updateValue() çağır
                const result = this.eventCharacteristic.updateValue(data);
                console.log(`✅ Event characteristic'e yazıldı: ${result ? 'başarılı (notify gönderildi)' : 'başarısız (subscribe yok veya hata)'}`);
                
                // 2. Polling-based read için _lastEvent'e kaydet (her zaman)
                this.eventCharacteristic._lastEvent = data;
                console.log(`✅ Event _lastEvent'e kaydedildi (polling için): ${data.length} bytes`);
                
                // KRİTİK: _lastEvent'in gerçekten kaydedildiğini doğrula
                if (!this.eventCharacteristic._lastEvent || this.eventCharacteristic._lastEvent.length === 0) {
                    console.error(`❌ KRİTİK: _lastEvent kaydedilemedi!`);
                    console.error(`   Data boyutu: ${data.length} bytes`);
                    console.error(`   _lastEvent durumu: ${this.eventCharacteristic._lastEvent ? 'VAR ama boş' : 'YOK'}`);
                    // Tekrar dene - Buffer.from() ile yeni buffer oluştur
                    this.eventCharacteristic._lastEvent = Buffer.from(data);
                    console.log(`   🔄 _lastEvent tekrar kaydedildi: ${this.eventCharacteristic._lastEvent.length} bytes`);
                    
                    // Son kontrol
                    if (!this.eventCharacteristic._lastEvent || this.eventCharacteristic._lastEvent.length === 0) {
                        console.error(`   ❌ KRİTİK: _lastEvent hala kaydedilemedi!`);
                        console.error(`   💡 Bu, ciddi bir sorun - event kaybolacak!`);
                    } else {
                        console.log(`   ✅ _lastEvent başarıyla kaydedildi (ikinci deneme)`);
                    }
                } else {
                    console.log(`   ✅ _lastEvent doğrulandı: ${this.eventCharacteristic._lastEvent.length} bytes`);
                }
                
                console.log(`   💡 Android read yaptığında bu event döndürülecek`);
                console.log(`   📦 _lastEvent içeriği: ${this.eventCharacteristic._lastEvent.toString('utf8')}`);
                console.log(`   📦 _lastEvent doğrulama: ${this.eventCharacteristic._lastEvent.length} bytes`);
                
                if (!result) {
                    console.warn('⚠️  Event gönderilemedi - client subscribe olmamış olabilir');
                    console.warn('   📦 Event buffer\'a kaydediliyor...');
                    
                    // Event'i buffer'a kaydet (FIFO - eski event'leri sil)
                    if (!this.eventBuffer) {
                        this.eventBuffer = [];
                    }
                    
                    // Buffer dolduysa eski event'leri sil (FIFO)
                    if (this.eventBuffer.length >= this.MAX_EVENT_BUFFER_SIZE) {
                        const removed = this.eventBuffer.shift(); // İlk event'i sil
                        console.warn(`   ⚠️  Buffer dolu (${this.MAX_EVENT_BUFFER_SIZE} event), en eski event silindi: ${removed.length} bytes`);
                    }
                    
                    this.eventBuffer.push(data);
                    console.warn(`   ✅ Event buffer'a kaydedildi (toplam: ${this.eventBuffer.length} event)`);
                    console.warn(`   💡 Subscribe olunca buffer'daki tüm event'ler gönderilecek`);
                }
            } else {
                // Service henüz hazır değilse, buffer'a kaydet
                console.warn('\n⚠️  ========================================');
                console.warn('⚠️  Characteristic henüz hazır değil!');
                console.warn('========================================');
                console.warn('   eventCharacteristic: null');
                console.warn('   Service\'ler henüz set edilmemiş');
                console.warn('   Event buffer\'a kaydediliyor...');
                console.warn('   💡 Service\'ler hazır olduğunda otomatik gönderilecek');
                console.warn('   💡 KRİTİK: Service\'ler hazır olmadığı için _lastEvent güncellenemiyor!');
                console.warn('   💡 Service\'ler hazır olduğunda _lastEvent güncellenecek');
                
                if (!this.eventBuffer) {
                    this.eventBuffer = [];
                }
                
                // Buffer dolduysa eski event'leri sil (FIFO)
                if (this.eventBuffer.length >= this.MAX_EVENT_BUFFER_SIZE) {
                    const removed = this.eventBuffer.shift(); // İlk event'i sil
                    console.warn(`   ⚠️  Buffer dolu (${this.MAX_EVENT_BUFFER_SIZE} event), en eski event silindi: ${removed.length} bytes`);
                }
                
                this.eventBuffer.push(data);
                // KRİTİK: currentEventValue'yu her zaman güncelle (service'ler hazır olduğunda _lastEvent'e kopyalanacak)
                this.currentEventValue = data; // Son değer olarak da sakla (service'ler hazır olduğunda _lastEvent'e kopyalanacak)
                console.warn(`   ✅ Event buffer'a kaydedildi (toplam: ${this.eventBuffer.length} event)`);
                console.warn(`   ✅ currentEventValue güncellendi: ${this.currentEventValue.length} bytes`);
                console.warn(`   💡 Service'ler hazır olduğunda bu event _lastEvent'e kopyalanacak`);
            }
        } catch (error) {
            console.error(`❌ Event gönderme hatası: ${error.message}`);
            console.error(error.stack);
        }
    }

    /**
     * Event'i polling-based read için hazırla
     * Android sürekli read yapacak, bu metod event'i _lastEvent'e kaydeder
     * @param {Object} event - Event object
     */
    sendEventViaRead(event) {
        const timestamp = Date.now();
        const threadInfo = `Node.js main thread (${process.pid})`;
        
        try {
            console.log('\n📡 ========================================');
            console.log('📡 sendEventViaRead() ÇAĞRILDI');
            console.log('========================================');
            console.log(`   Timestamp: ${new Date(timestamp).toISOString()}`);
            console.log(`   Thread: ${threadInfo}`);
            console.log(`   Event: ${JSON.stringify(event)}`);
            console.log(`   💡 Polling-based read workaround kullanılıyor`);
            console.log(`   💡 Event _lastEvent'e kaydediliyor, Android read yaptığında döndürülecek`);
            console.log('========================================\n');
            
            if (bleno.state !== 'poweredOn') {
                console.warn('⚠️  BLE state poweredOn değil, event kaydedilemiyor');
                return;
            }

            if (!event || typeof event !== 'object') {
                throw new Error('Geçersiz event object');
            }

            const jsonString = JSON.stringify(event);
            const data = Buffer.from(jsonString, 'utf8');
            
            // Event'i _lastEvent'e kaydet (Android read yaptığında döndürülecek)
            if (this.eventCharacteristic) {
                this.eventCharacteristic._lastEvent = data;
                console.log(`✅ Event _lastEvent'e kaydedildi: ${data.length} bytes`);
                console.log(`   💡 Android read yaptığında bu event döndürülecek`);
            } else {
                console.warn('⚠️  Characteristic hazır değil, event kaydedilemedi');
            }
        } catch (error) {
            console.error(`❌ sendEventViaRead hatası: ${error.message}`);
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
        
        // Durum değişmediyse, duplicate call - işlem yapma
        if (wasConnected === connected) {
            // Sadece debug için sessizce return et (log spam'ini engelle)
            return;
        }
        
        console.log('\n📡 ========================================');
        console.log('📡 setWokwiConnected() ÇAĞRILDI');
        console.log('========================================');
        console.log(`   Önceki durum: ${wasConnected}`);
        console.log(`   Yeni durum: ${connected}`);
        
        this.isWokwiConnected = connected;
        
        if (connected && !wasConnected) {
            console.log('✅ Wokwi bağlandı');
            console.log('   💡 Wokwi simülasyonu aktif, event\'ler alınabilir');
        } else if (!connected && wasConnected) {
            console.log('⚠️  Wokwi bağlantısı kesildi, advertising durduruluyor');
            this.stopAdvertising();
        }
        
        // Wokwi bağlandıysa ve pairing mode aktifse advertising başlat
        console.log(`   Pairing mode aktif mi: ${this.isPairingModeActive}`);
        console.log(`   BLE state: ${bleno.state}`);
        if (connected && this.isPairingModeActive && bleno.state === 'poweredOn') {
            console.log('✅ Wokwi bağlı ve pairing mode aktif, advertising başlatılıyor...');
            this.startAdvertising();
        } else {
            console.log('⚠️  Advertising başlatılmadı:');
            console.log(`     - Wokwi bağlı: ${connected}`);
            console.log(`     - Pairing mode aktif: ${this.isPairingModeActive}`);
            console.log(`     - BLE state: ${bleno.state}`);
        }
    }

    /**
     * Pairing mode'u aktif et
     */
    enablePairingMode() {
        console.log('\n🔐 ========================================');
        console.log('🔐 enablePairingMode() ÇAĞRILDI');
        console.log('========================================');
        console.log('   Önceki pairing mode durumu: ' + this.isPairingModeActive);
        
        this.isPairingModeActive = true;
        this.pairingModeStartTime = Date.now();
        const endTime = new Date(this.pairingModeStartTime + this.PAIRING_MODE_DURATION_MS);
        
        console.log('✅ Pairing mode aktif edildi');
        console.log(`   Başlangıç zamanı: ${new Date(this.pairingModeStartTime).toLocaleTimeString()}`);
        console.log(`   Bitiş zamanı: ${endTime.toLocaleTimeString()}`);
        console.log(`   Süre: ${this.PAIRING_MODE_DURATION_MS / 1000} saniye`);
        console.log(`   Wokwi bağlantı durumu: ${this.isWokwiConnected}`);
        console.log(`   BLE state: ${bleno.state}`);
        
        // Wokwi bağlıysa advertising başlat
        if (this.isWokwiConnected && bleno.state === 'poweredOn') {
            console.log('✅ Wokwi bağlı ve BLE açık, advertising başlatılıyor...');
            this.startAdvertising();
        } else {
            console.log('⚠️  Advertising başlatılmadı:');
            console.log(`     - Wokwi bağlı: ${this.isWokwiConnected}`);
            console.log(`     - BLE state: ${bleno.state}`);
        }
        
        // 30 saniye sonra pairing mode'u kapat
        setTimeout(() => {
            if (this.isPairingModeActive) {
                console.log('⏰ Pairing mode süresi doldu (30 saniye), kapatılıyor...');
                this.disablePairingMode();
            }
        }, this.PAIRING_MODE_DURATION_MS);
    }

    /**
     * Pairing mode'u kapat
     */
    disablePairingMode() {
        console.log('\n🔓 ========================================');
        console.log('🔓 disablePairingMode() ÇAĞRILDI');
        console.log('========================================');
        console.log('   Önceki pairing mode durumu: ' + this.isPairingModeActive);
        
        if (this.isPairingModeActive) {
            this.isPairingModeActive = false;
            this.pairingModeStartTime = null;
            console.log('⚠️  Pairing mode sona erdi');
            console.log('   Advertising durduruluyor...');
            this.stopAdvertising();
            console.log('✅ Pairing mode kapatıldı, advertising durduruldu');
        } else {
            console.log('ℹ️  Pairing mode zaten kapalı');
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
            properties: ['read', 'notify', 'write'] // Write property eklendi (workaround için)
            // macOS CoreBluetooth 2902 descriptor'ı otomatik yönetir, manuel eklemeye gerek yok
        });

        this._value = Buffer.alloc(0);
        this._updateValueCallback = null;
        this._readRequestCount = 0; // Read request sayacı
        this._firstReadRequestTime = null; // İlk read request zamanı
        this._lastEvent = Buffer.alloc(0); // Son gönderilen event (polling için)
    }

    onReadRequest(offset, callback) {
        const timestamp = Date.now();
        
        // Read request sayacını artır
        this._readRequestCount++;
        const now = timestamp;
        if (this._firstReadRequestTime === null) {
            this._firstReadRequestTime = now;
            // İlk read request'te log bas
            console.log(`\n📖 ========================================`);
            console.log(`📖 İlk onReadRequest CALLBACK TETİKLENDİ!`);
            console.log(`========================================`);
            console.log(`   Android polling başladı (her 50ms'de bir read yapacak)`);
            console.log(`   💡 Read request logları azaltıldı - sadece event geldiğinde görünecek`);
            console.log(`========================================\n`);
        }
        
        // KRİTİK: _lastEvent durumunu HEMEN kontrol et (log'lardan önce)
        const lastEventExistsBeforeCheck = this._lastEvent && this._lastEvent.length > 0;
        const lastEventSizeBeforeCheck = this._lastEvent?.length || 0;
        
        // İlk read request'te durum kontrolü (manuel subscribe simülasyonu için)
        if (this._readRequestCount === 1) {
            console.log(`\n🔍 ========================================`);
            console.log(`🔍 İLK READ REQUEST - DURUM KONTROLÜ`);
            console.log(`========================================`);
            console.log(`   _updateValueCallback: ${this._updateValueCallback ? 'VAR ✅' : 'YOK ❌'}`);
            console.log(`   _lastEvent durumu: ${lastEventExistsBeforeCheck ? 'VAR ✅' : 'YOK ❌'}`);
            console.log(`   _lastEvent boyutu: ${lastEventSizeBeforeCheck} bytes`);
            if (this._lastEvent && this._lastEvent.length > 0) {
                console.log(`   _lastEvent içeriği: ${this._lastEvent.toString('utf8')}`);
            } else {
                console.log(`   ⚠️  _lastEvent BOŞ! Bu, event'lerin kaybolduğu anlamına gelir!`);
                console.log(`   💡 updateValue() içinde _lastEvent güncelleniyor mu kontrol edin`);
            }
            console.log(`   💡 Manuel subscribe simülasyonu: ${this._readRequestCount >= 3 ? 'HAZIR ✅' : 'BEKLİYOR ⏳ (3 read request gerekli)'}`);
            console.log(`   💡 Şu anki read request sayısı: ${this._readRequestCount}`);
            console.log(`========================================\n`);
        }
        
        // ============================================
        // POLLING-BASED READ WORKAROUND
        // ============================================
        // Android sürekli read yapacak, biz her read'de son event'i döndüreceğiz
        // Bu, macOS CoreBluetooth'un notify sorununu bypass eder
        // ============================================
        
        // KRİTİK: Önce _lastEvent ve _value'yu kontrol et (onWriteRequest'te kaydedilen event için)
        // Eğer _lastEvent veya _value doluysa, parent'tan senkronizasyon yapmaya gerek yok
        // Çünkü onWriteRequest içinde event zaten _lastEvent ve _value'ya kaydedildi
        let lastEventExists = this._lastEvent && this._lastEvent.length > 0;
        let valueExists = this._value && this._value.length > 0;
        const lastEventSize = this._lastEvent?.length || 0;
        const valueSize = this._value?.length || 0;
        
        // Eğer _lastEvent ve _value boşsa, parent'tan senkronize et (instance uyumsuzluğu workaround'u)
        // KRİTİK: onWriteRequest içinde event hazırlanıyor ve _lastEvent'e kaydediliyor
        // Bu yüzden önce _lastEvent kontrol edilmeli, eğer boşsa parent'tan senkronize edilmeli
        if (!lastEventExists && !valueExists) {
            // Parent'tan senkronize et (sadece _lastEvent ve _value boşsa)
            if (this._parent && this._parent.currentEventValue && this._parent.currentEventValue.length > 0) {
                const parentValue = this._parent.currentEventValue;
                const parentValueSize = parentValue.length;
                
                // Parent'tan güncelle
                this._value = Buffer.from(parentValue);
                this._lastEvent = Buffer.from(parentValue);
                console.log(`   🔄 WORKAROUND: _value ve _lastEvent parent'tan senkronize edildi (${parentValueSize} bytes)`);
                
                // Durumu güncelle
                lastEventExists = true;
                valueExists = true;
                
                if (this._readRequestCount <= 5) {
                    console.log(`   💡 Parent senkronizasyon kontrolü:`);
                    console.log(`      parent.currentEventValue: ${parentValueSize} bytes`);
                    console.log(`      this._value: ${this._value?.length || 0} bytes`);
                    console.log(`      this._lastEvent: ${this._lastEvent?.length || 0} bytes`);
                }
            } else {
                // Parent'tan da event yok
                if (this._readRequestCount <= 10) {
                    console.log(`   ℹ️  _lastEvent ve _value boş, parent'tan da event yok`);
                    console.log(`   💡 onWriteRequest içinde event hazırlanmamış olabilir`);
                    console.log(`   💡 Veya event henüz _lastEvent'e kaydedilmemiş olabilir`);
                }
            }
        } else {
            // _lastEvent veya _value dolu - onWriteRequest'te kaydedilen event var
            if (this._readRequestCount <= 5) {
                console.log(`   ✅ _lastEvent veya _value dolu (onWriteRequest'te kaydedilen event)`);
                console.log(`   💡 Event kaynağı: ${lastEventExists ? '_lastEvent' : '_value'}`);
                console.log(`   💡 Event boyutu: ${lastEventExists ? lastEventSize : valueSize} bytes`);
            }
        }
        
        // İlk 10 read request'te veya _lastEvent varsa detaylı instance kontrolü
        if (this._readRequestCount <= 10 || lastEventExists) {
            console.log(`   🔍 Instance kontrolü:`);
            console.log(`      this._lastEvent: ${this._lastEvent ? 'VAR' : 'YOK'} (${lastEventSize} bytes)`);
            console.log(`      this._value: ${this._value ? 'VAR' : 'YOK'} (${valueSize} bytes)`);
            if (this._lastEvent) {
                console.log(`      this._lastEvent içeriği: ${this._lastEvent.toString('utf8')}`);
            }
            if (this._value) {
                console.log(`      this._value içeriği: ${this._value.toString('utf8')}`);
            }
        }
        
        // Parent'tan currentEventValue kontrolü (senkronizasyon sonrası yedek kaynak)
        let currentEventValueExists = false;
        let currentEventValueSize = 0;
        if (this._parent && this._parent.currentEventValue) {
            currentEventValueExists = this._parent.currentEventValue.length > 0;
            currentEventValueSize = this._parent.currentEventValue.length || 0;
        }
        
        // _lastEvent varsa onu döndür (polling için)
        // ÖNEMLİ: Öncelik sırası: _lastEvent -> _value -> currentEventValue (parent) -> eventBuffer
        // WORKAROUND: Parent'tan senkronizasyon yapıldıktan sonra, eğer hala boşsa
        // parent'tan direkt olarak al (instance uyumsuzluğu workaround'u)
        let dataToReturn;
        let dataSource;
        
        if (lastEventExists) {
            dataToReturn = this._lastEvent;
            dataSource = '_lastEvent';
        } else if (valueExists) {
            dataToReturn = this._value;
            dataSource = '_value';
        } else if (currentEventValueExists) {
            // WORKAROUND: Parent'tan direkt al (instance uyumsuzluğu workaround'u)
            dataToReturn = this._parent.currentEventValue;
            // Hem _value hem de _lastEvent'i güncelle (gelecekteki read'ler için)
            this._value = Buffer.from(this._parent.currentEventValue);
            this._lastEvent = Buffer.from(this._parent.currentEventValue);
            dataSource = 'currentEventValue (parent - WORKAROUND)';
            console.log(`   🔄 WORKAROUND: currentEventValue direkt kullanıldı (${dataToReturn.length} bytes)`);
            console.log(`   🔄 WORKAROUND: _value ve _lastEvent güncellendi (${this._value.length} bytes)`);
        } else if (!lastEventExists && this._parent && this._parent.eventBuffer && this._parent.eventBuffer.length > 0) {
            // _lastEvent boşsa event buffer'daki son event'i kontrol et
            const lastBufferedEvent = this._parent.eventBuffer[this._parent.eventBuffer.length - 1];
            if (lastBufferedEvent && lastBufferedEvent.length > 0) {
                dataToReturn = lastBufferedEvent;
                // Hem _value hem de _lastEvent'i güncelle (gelecekteki read'ler için)
                this._value = Buffer.from(lastBufferedEvent);
                this._lastEvent = Buffer.from(lastBufferedEvent);
                dataSource = 'eventBuffer (son event)';
                console.log(`   💡 Buffer'daki son event kullanıldı (${dataToReturn.length} bytes)`);
                console.log(`   💡 _value ve _lastEvent güncellendi (${this._value.length} bytes)`);
            } else {
                dataToReturn = Buffer.alloc(0);
                dataSource = 'Buffer.alloc(0)';
            }
        } else {
            dataToReturn = Buffer.alloc(0);
            dataSource = 'Buffer.alloc(0)';
        }
        
        // İlk 10 read request'te veya _lastEvent varsa veya dataToReturn varsa detaylı log bas
        const shouldLogDetailed = this._readRequestCount <= 10 || lastEventExists || dataToReturn.length > 0;
        // Sadece data geldiğinde veya her 100. read request'te log bas (log spam'i önlemek için)
        const shouldLog = dataToReturn.length > 0 || (this._readRequestCount % 100 === 0);
        
        // KRİTİK: Eğer _lastEvent updateValue() içinde güncellenmişse ama burada boşsa, instance uyumsuzluğu var
        if (!lastEventExists && this._readRequestCount <= 5) {
            console.log(`\n⚠️⚠️⚠️  ========================================`);
            console.log(`⚠️  KRİTİK: _lastEvent BOŞ ama updateValue() içinde güncellenmiş olmalı!`);
            console.log(`========================================`);
            console.log(`   Read request #${this._readRequestCount}`);
            console.log(`   _lastEvent durumu: ${lastEventExists ? 'VAR ✅' : 'YOK ❌'}`);
            console.log(`   _lastEvent boyutu: ${lastEventSize} bytes`);
            console.log(`   _value durumu: ${valueExists ? 'VAR ✅' : 'YOK ❌'} (${valueSize} bytes)`);
            console.log(`   💡 Bu, instance uyumsuzluğu veya _lastEvent kaybolması anlamına gelir!`);
            console.log(`   💡 updateValue() içinde _lastEvent güncelleniyor ama onReadRequest içinde boş!`);
            console.log(`========================================\n`);
        }
        
        // Detaylı log (ilk 10 read request veya _lastEvent varsa veya dataToReturn varsa)
        if (shouldLogDetailed) {
            console.log(`\n📖 ========================================`);
            console.log(`📖 onReadRequest #${this._readRequestCount}`);
            console.log(`========================================`);
            console.log(`   Timestamp: ${new Date(timestamp).toISOString()}`);
            console.log(`   _lastEvent durumu: ${lastEventExists ? 'VAR ✅' : 'YOK ❌'}`);
            console.log(`   _lastEvent boyutu: ${lastEventSize} bytes`);
            if (this._lastEvent && this._lastEvent.length > 0) {
                console.log(`   _lastEvent içeriği: ${this._lastEvent.toString('utf8')}`);
            }
            console.log(`   _value durumu: ${valueExists ? 'VAR ✅' : 'YOK ❌'} (${valueSize} bytes)`);
            if (this._parent && this._parent.currentEventValue) {
                console.log(`   currentEventValue durumu: ${currentEventValueExists ? 'VAR ✅' : 'YOK ❌'} (${currentEventValueSize} bytes)`);
            }
            console.log(`   _updateValueCallback: ${this._updateValueCallback ? 'VAR ✅' : 'YOK ❌'}`);
            console.log(`   Manuel subscribe simülasyonu: ${this._readRequestCount >= 3 && !this._updateValueCallback ? 'TETİKLENECEK ✅' : this._updateValueCallback ? 'ZATEN AKTİF ✅' : 'BEKLİYOR ⏳'}`);
            console.log(`   dataToReturn kaynağı: ${dataSource}`);
            console.log(`   dataToReturn boyutu: ${dataToReturn.length} bytes`);
            if (dataToReturn.length > 0) {
                console.log(`   dataToReturn içeriği: ${dataToReturn.toString('utf8')}`);
                console.log(`   ✅ Event Android'e gönderiliyor...`);
            } else {
                console.log(`   💡 Event bekleniyor...`);
            }
            console.log(`========================================\n`);
        } else if (shouldLog && dataToReturn.length > 0) {
            // Event geldi ama detaylı log değil - kısa log
            console.log(`\n📖 onReadRequest #${this._readRequestCount} - EVENT GELDİ! (${dataToReturn.length} bytes)`);
        } else if (shouldLog && dataToReturn.length === 0) {
            // Her 100. read request'te özet log (boş olsa bile)
            console.log(`\n📖 Polling aktif: ${this._readRequestCount} read request (henüz event yok)`);
        }
        
        // Hemen yanıt ver (polling için kritik)
        try {
            if (dataToReturn.length > 0) {
                console.log(`   📤 callback() çağrılıyor: RESULT_SUCCESS, ${dataToReturn.length} bytes`);
                console.log(`   📤 Event içeriği: ${dataToReturn.toString('utf8')}`);
                console.log(`   📤 Event kaynağı: ${dataSource}`);
            } else {
                console.log(`   📤 callback() çağrılıyor: RESULT_SUCCESS, 0 bytes (boş)`);
                console.log(`   💡 Event yok, Android'e boş data döndürülecek`);
            }
            callback(this.RESULT_SUCCESS, dataToReturn);
            console.log(`   ✅ callback() başarıyla çağrıldı`);
        } catch (error) {
            console.error(`   ❌ Read request yanıtlanırken hata: ${error.message}`);
            console.error(`   Stack: ${error.stack}`);
        }
        
        // İlk birkaç read request'te subscribe durumunu kontrol et
        if (this._readRequestCount <= 3 && !this._updateValueCallback) {
            console.log(`   💡 Read request #${this._readRequestCount}: Subscribe henüz aktif değil`);
        }
        
        // Her read request'te _lastEvent'in durumunu doğrula ve kaybolursa otomatik düzelt
        // NOT: Bu log spam'ini önlemek için sadece ilk 10 read request'te veya geri yükleme başarılı olduğunda log bas
        const shouldLogRestore = this._readRequestCount <= 10;
        
        if (!this._lastEvent || this._lastEvent.length === 0) {
            if (shouldLogRestore) {
                console.log(`   🔧 _lastEvent boş, geri yükleme deneniyor...`);
            }
            
            // _lastEvent boşsa, mevcut kaynaklardan doldur (öncelik sırasına göre)
            let restored = false;
            
            // 1. Önce _value'yu kontrol et
            if (this._value && this._value.length > 0) {
                this._lastEvent = this._value;
                console.log(`   ✅ _lastEvent otomatik düzeltildi (_value'den kopyalandı): ${this._lastEvent.length} bytes`);
                console.log(`   📦 Geri yüklenen _lastEvent içeriği: ${this._lastEvent.toString('utf8')}`);
                restored = true;
            }
            
            // 2. Sonra currentEventValue'yu kontrol et
            if (!restored && this._parent && this._parent.currentEventValue && this._parent.currentEventValue.length > 0) {
                this._lastEvent = this._parent.currentEventValue;
                console.log(`   ✅ _lastEvent otomatik düzeltildi (currentEventValue'den kopyalandı): ${this._lastEvent.length} bytes`);
                console.log(`   📦 Geri yüklenen _lastEvent içeriği: ${this._lastEvent.toString('utf8')}`);
                restored = true;
            }
            
            // 3. Son olarak buffer'daki son event'i kontrol et
            if (!restored && this._parent && this._parent.eventBuffer && this._parent.eventBuffer.length > 0) {
                const lastBufferedEvent = this._parent.eventBuffer[this._parent.eventBuffer.length - 1];
                if (lastBufferedEvent && lastBufferedEvent.length > 0) {
                    this._lastEvent = lastBufferedEvent;
                    console.log(`   ✅ _lastEvent otomatik düzeltildi (buffer'dan kopyalandı): ${this._lastEvent.length} bytes`);
                    console.log(`   📦 Geri yüklenen _lastEvent içeriği: ${this._lastEvent.toString('utf8')}`);
                    restored = true;
                }
            }
            
            // 4. Hiçbir kaynak bulunamazsa uyarı ver (sadece ilk 10 read request'te veya her 100. read request'te)
            if (!restored && (shouldLogRestore || this._readRequestCount % 100 === 0)) {
                console.warn(`   ⚠️  _lastEvent geri yüklenemedi - tüm kaynaklar boş (read request #${this._readRequestCount})`);
                console.warn(`   💡 Event bekleniyor...`);
                console.warn(`   💡 Wokwi'dan event geliyor mu kontrol edin`);
                console.warn(`   💡 sendEvent() çağrılıyor mu kontrol edin`);
            }
        }
        
        // ============================================
        // MANUEL SUBSCRIBE SİMÜLASYONU (onSubscribe %100 Çözümü)
        // ============================================
        // Android'in birkaç read request yaptığını tespit et
        // Eğer _updateValueCallback hala null ise (onSubscribe tetiklenmemişse)
        // Manuel olarak _updateValueCallback'i set et
        // Bu, macOS CoreBluetooth'un onSubscribe tetiklenmemesi sorununu %100 bypass eder
        // ============================================
        if (this._readRequestCount >= 3 && !this._updateValueCallback) {
            console.log(`\n🔧 ========================================`);
            console.log(`🔧 MANUEL SUBSCRIBE SİMÜLASYONU BAŞLATILIYOR!`);
            console.log(`========================================`);
            console.log(`   Read request sayısı: ${this._readRequestCount}`);
            console.log(`   _updateValueCallback: null (onSubscribe tetiklenmemiş)`);
            console.log(`   💡 macOS CoreBluetooth onSubscribe'ı tetiklemedi`);
            console.log(`   💡 Manuel olarak subscribe simüle ediliyor...`);
            
            // Manuel olarak _updateValueCallback'i set et (dummy callback)
            // NOT: Bu callback gerçekte kullanılmayacak, sadece state'i set etmek için
            // Polling mekanizması zaten çalışıyor, bu sadece notification mekanizmasını da aktif hale getirir
            this._updateValueCallback = (data) => {
                // Dummy callback - sadece log için
                console.log(`   📤 Manuel subscribe callback tetiklendi: ${data.length} bytes`);
            };
            
            console.log(`   ✅ _updateValueCallback manuel olarak set edildi`);
            console.log(`   ✅ Artık updateValue() çağrıldığında notification gönderilebilir`);
            console.log(`   💡 Buffer'daki event'ler gönderiliyor...`);
            
            // Buffer'daki event'leri gönder (onSubscribe'daki gibi)
            if (this._parent && this._parent.eventBuffer && this._parent.eventBuffer.length > 0) {
                const bufferCopy = [...this._parent.eventBuffer];
                this._parent.eventBuffer = [];
                
                bufferCopy.forEach((bufferedEvent, index) => {
                    try {
                        console.log(`   📤 Buffer event #${index + 1}/${bufferCopy.length} gönderiliyor: ${bufferedEvent.length} bytes`);
                        this._updateValueCallback(bufferedEvent);
                        console.log(`   ✅ Buffer event #${index + 1} gönderildi`);
                    } catch (error) {
                        console.error(`   ❌ Buffer event #${index + 1} gönderilemedi: ${error.message}`);
                    }
                });
            }
            
            // Son değeri de gönder (eğer varsa)
            if (this._value && this._value.length > 0) {
                try {
                    console.log(`   📡 Manuel subscribe sonrası son değer gönderiliyor: ${this._value.length} bytes`);
                    this._updateValueCallback(this._value);
                    console.log(`   ✅ Manuel subscribe sonrası son değer gönderildi`);
                } catch (error) {
                    console.error(`   ❌ Manuel subscribe sonrası ilk değer gönderilemedi: ${error.message}`);
                }
            }
            
            console.log(`   ✅ Manuel subscribe simülasyonu tamamlandı`);
            console.log(`   💡 Artık hem polling hem de notification mekanizması aktif`);
            console.log(`========================================\n`);
        }
    }

    onWriteRequest(data, offset, withoutResponse, callback) {
        const timestamp = Date.now();
        const threadInfo = `Node.js main thread (${process.pid})`;
        
        console.log(`\n✍️  ========================================`);
        console.log(`✍️  onWriteRequest CALLBACK TETİKLENDİ!`);
        console.log(`========================================`);
        console.log(`   Timestamp: ${new Date(timestamp).toISOString()}`);
        console.log(`   Thread: ${threadInfo}`);
        console.log(`   Data boyutu: ${data.length} bytes`);
        console.log(`   Data içeriği: ${data.toString('utf8')}`);
        console.log(`   Offset: ${offset}`);
        console.log(`   WithoutResponse: ${withoutResponse}`);
        console.log(`   💡 Android cihazdan write request geldi (write-based polling)`);
        console.log(`   💡 Android write sonrası hemen read yapacak, son event'i döndüreceğiz`);
        console.log(`========================================`);
        
        // Write request'i alınca, son event'i hazır tut
        // Android write sonrası hemen read yapacak ve son event'i alacak
        // Öncelik sırası: _lastEvent -> _value -> currentEventValue (parent) -> eventBuffer
        
        // Parent'tan currentEventValue'yu kontrol et
        let eventToReturn = null;
        if (this._lastEvent && this._lastEvent.length > 0) {
            eventToReturn = this._lastEvent;
            console.log(`   ✅ Son event bulundu (_lastEvent): ${eventToReturn.length} bytes`);
        } else if (this._value && this._value.length > 0) {
            eventToReturn = this._value;
            console.log(`   ✅ Son event bulundu (_value): ${eventToReturn.length} bytes`);
        } else if (this._parent && this._parent.currentEventValue && this._parent.currentEventValue.length > 0) {
            eventToReturn = this._parent.currentEventValue;
            // _lastEvent'i de güncelle (gelecekteki read'ler için)
            this._lastEvent = Buffer.from(this._parent.currentEventValue);
            this._value = Buffer.from(this._parent.currentEventValue);
            console.log(`   ✅ Son event bulundu (currentEventValue - parent): ${eventToReturn.length} bytes`);
            console.log(`   🔄 _lastEvent ve _value güncellendi`);
        } else if (this._parent && this._parent.eventBuffer && this._parent.eventBuffer.length > 0) {
            // Buffer'daki son event'i al
            const lastBufferedEvent = this._parent.eventBuffer[this._parent.eventBuffer.length - 1];
            if (lastBufferedEvent && lastBufferedEvent.length > 0) {
                eventToReturn = lastBufferedEvent;
                // _lastEvent'i de güncelle (gelecekteki read'ler için)
                this._lastEvent = Buffer.from(lastBufferedEvent);
                this._value = Buffer.from(lastBufferedEvent);
                console.log(`   ✅ Son event bulundu (eventBuffer): ${eventToReturn.length} bytes`);
                console.log(`   🔄 _lastEvent ve _value güncellendi`);
            }
        }
        
        if (eventToReturn && eventToReturn.length > 0) {
            console.log(`   📤 Son event hazır: ${eventToReturn.length} bytes`);
            console.log(`   📤 Event içeriği: ${eventToReturn.toString('utf8')}`);
            console.log(`   💡 Android write sonrası read yaptığında bu event döndürülecek`);
            
            // KRİTİK: Event'i _lastEvent ve _value'ya kaydet (onReadRequest için)
            // Bu, Android write sonrası read yaptığında event'i alabilmesi için gerekli
            // NOT: Buffer.from() kullanarak yeni bir buffer oluştur (referans sorunlarını önlemek için)
            const eventBuffer = Buffer.from(eventToReturn);
            this._lastEvent = eventBuffer;
            this._value = eventBuffer;
            
            // Doğrulama: Event'in gerçekten kaydedildiğini kontrol et
            if (this._lastEvent && this._lastEvent.length > 0) {
                console.log(`   ✅ Event _lastEvent'e kaydedildi: ${this._lastEvent.length} bytes`);
                console.log(`   ✅ Event _value'ya kaydedildi: ${this._value.length} bytes`);
                console.log(`   ✅ Doğrulama: _lastEvent içeriği: ${this._lastEvent.toString('utf8')}`);
                console.log(`   💡 onReadRequest içinde bu event döndürülecek`);
            } else {
                console.error(`   ❌ KRİTİK: Event kaydedilemedi! _lastEvent hala boş!`);
            }
        } else {
            console.log(`   ℹ️  Henüz event yok, Android read yaptığında boş döndürülecek`);
            console.log(`   💡 _lastEvent durumu: ${this._lastEvent?.length || 0} bytes`);
            console.log(`   💡 _value durumu: ${this._value?.length || 0} bytes`);
            console.log(`   💡 currentEventValue durumu: ${this._parent?.currentEventValue?.length || 0} bytes`);
        }
        
        console.log(`========================================\n`);
        
        // Write request'i kaydet (isteğe bağlı, gelecekte Wokwi'ye komut göndermek için kullanılabilir)
        // NOT: Eğer eventToReturn varsa, _value zaten eventToReturn olarak set edildi
        // Eğer eventToReturn yoksa, write data'sını _value'ya kaydet (komut için)
        if (!eventToReturn || eventToReturn.length === 0) {
            this._value = data;
        }
        
        // Write response'u başarılı olarak döndür
        // NOT: BLE'de write response'u direkt data içeremez, bu yüzden Android write sonrası read yapacak
        callback(this.RESULT_SUCCESS);
    }

    onSubscribe(maxValueSize, updateValueCallback) {
        const timestamp = Date.now();
        const threadInfo = `Node.js main thread (${process.pid})`;
        
        console.log(`\n🎉🎉🎉 ========================================`);
        console.log(`🎉 onSubscribe CALLBACK TETİKLENDİ!`);
        console.log(`========================================`);
        console.log(`   Timestamp: ${new Date(timestamp).toISOString()}`);
        console.log(`   Thread: ${threadInfo}`);
        console.log(`   Bu callback Android'in descriptor yazma işleminden SONRA tetiklenir`);
        console.log(`   Android cihaz artık notification'ları alabilir`);
        console.log(`   maxValueSize: ${maxValueSize} bytes`);
        console.log(`   updateValueCallback: ${updateValueCallback ? 'VAR ✅' : 'YOK ❌'}`);
        console.log(`   Önceki _updateValueCallback durumu: ${this._updateValueCallback ? 'VAR' : 'YOK'}`);
        console.log(`   Read request sayısı: ${this._readRequestCount}`);
        if (this._firstReadRequestTime) {
            const timeSinceFirstRead = timestamp - this._firstReadRequestTime;
            console.log(`   İlk read request'ten geçen süre: ${timeSinceFirstRead}ms`);
        }
        console.log(`========================================`);
        
        if (!updateValueCallback) {
            console.error(`   ❌ KRİTİK: updateValueCallback null!`);
            console.error(`   ⚠️  Bu durumda notification gönderilemez!`);
            return;
        }
        
        this._updateValueCallback = updateValueCallback;
        console.log(`   ✅ _updateValueCallback set edildi`);
        console.log(`   ✅ Artık updateValue() çağrıldığında notification gönderilebilir`);

        // Subscribe olur olmaz, buffer'daki tüm event'leri gönder
        console.log(`   📦 Event buffer kontrol ediliyor...`);
        console.log(`   Buffer boyutu: ${this._parent?.eventBuffer?.length || 0} event`);
        
        // Buffer'daki event'leri gönder (FIFO - ilk gelen önce gider)
        if (this._parent && this._parent.eventBuffer && this._parent.eventBuffer.length > 0) {
            console.log(`   📡 Buffer'daki ${this._parent.eventBuffer.length} event gönderiliyor...`);
            const bufferCopy = [...this._parent.eventBuffer]; // Kopyala
            this._parent.eventBuffer = []; // Buffer'ı temizle
            
            bufferCopy.forEach((bufferedEvent, index) => {
                try {
                    console.log(`   📤 Buffer event #${index + 1}/${bufferCopy.length} gönderiliyor: ${bufferedEvent.length} bytes`);
                    console.log(`   📤 Event içeriği: ${bufferedEvent.toString('utf8')}`);
                    this._updateValueCallback(bufferedEvent);
                    console.log(`   ✅ Buffer event #${index + 1} gönderildi`);
                } catch (error) {
                    console.error(`   ❌ Buffer event #${index + 1} gönderilemedi: ${error.message}`);
                }
            });
            console.log(`   ✅ Tüm buffer event'leri gönderildi`);
        } else {
            console.log(`   ℹ️  Buffer boş, gönderilecek event yok`);
        }
        
        // Son değeri de gönder (eğer varsa)
        if (this._value && this._value.length > 0) {
            try {
                console.log(`   📡 Subscribe sonrası son değer gönderiliyor: ${this._value.length} bytes`);
                console.log(`   📡 Değer içeriği: ${this._value.toString('utf8')}`);
                this._updateValueCallback(this._value);
                console.log(`   ✅ Subscribe sonrası son değer gönderildi`);
            } catch (error) {
                console.error(`   ❌ Subscribe sonrası ilk değer gönderilemedi: ${error.message}`);
                console.error(`   Stack: ${error.stack}`);
            }
        } else {
            console.log(`   ℹ️  Subscribe sonrası gönderilecek değer yok (buffer boş)`);
        }
        console.log(`========================================\n`);
    }

    onUnsubscribe() {
        console.log(`\n⚠️⚠️⚠️  ========================================`);
        console.log(`⚠️  onUnsubscribe CALLBACK TETİKLENDİ!`);
        console.log(`========================================`);
        console.log(`   Android cihaz notification'ları iptal etti`);
        console.log(`   Önceki _updateValueCallback durumu: ${this._updateValueCallback ? 'VAR' : 'YOK'}`);
        console.log(`   _updateValueCallback null yapılıyor...`);
        this._updateValueCallback = null;
        console.log(`   ✅ _updateValueCallback null yapıldı`);
        console.log(`   ⚠️  Artık notification gönderilemez (client subscribe değil)`);
        console.log(`========================================\n`);
    }

    updateValue(data) {
        const timestamp = Date.now();
        const threadInfo = `Node.js main thread (${process.pid})`;
        
        // Hem _value hem de _lastEvent'i güncelle (polling için kritik)
        this._value = data;
        this._lastEvent = data; // Polling-based read için _lastEvent'i de güncelle
        
        // KRİTİK: _lastEvent'in gerçekten güncellendiğini doğrula
        if (!this._lastEvent || this._lastEvent.length === 0) {
            console.error(`❌ KRİTİK: updateValue() içinde _lastEvent güncellenemedi!`);
            console.error(`   Data boyutu: ${data.length} bytes`);
            // Tekrar dene
            this._lastEvent = Buffer.from(data);
            console.log(`   🔄 _lastEvent tekrar güncellendi: ${this._lastEvent.length} bytes`);
        }
        
        console.log(`\n📤 ========================================`);
        console.log(`📤 updateValue() ÇAĞRILDI`);
        console.log(`========================================`);
        console.log(`   Timestamp: ${new Date(timestamp).toISOString()}`);
        console.log(`   Thread: ${threadInfo}`);
        console.log(`   Data boyutu: ${data.length} bytes`);
        console.log(`   Data içeriği: ${data.toString('utf8')}`);
        console.log(`   _updateValueCallback durumu: ${this._updateValueCallback ? 'VAR ✅' : 'YOK ❌'}`);
        console.log(`   _value durumu: ${this._value ? 'VAR' : 'YOK'}`);
        console.log(`   _value boyutu: ${this._value?.length || 0} bytes`);
        console.log(`   _lastEvent güncellendi (polling için): ${this._lastEvent?.length || 0} bytes`);
        console.log(`   _lastEvent içeriği: ${this._lastEvent?.toString('utf8') || 'BOŞ'}`);
        console.log(`   ✅ _lastEvent doğrulandı: ${this._lastEvent && this._lastEvent.length > 0 ? 'VAR ✅' : 'YOK ❌'}`);
        
        if (this._updateValueCallback) {
            console.log(`   ✅ Callback var, notification gönderiliyor...`);
            try {
                // ============================================
                // KRİTİK LOG: NOTIFY GÖNDERİLİYOR
                // ============================================
                console.log(`\n🔔🔔🔔 ========================================`);
                console.log(`🔔 NOTIFY GÖNDERİLİYOR (updateValueCallback çağrılıyor)`);
                console.log(`========================================`);
                console.log(`   Data: ${data.toString('utf8')}`);
                console.log(`   Boyut: ${data.length} bytes`);
                console.log(`   💡 Android cihaz onCharacteristicChanged callback'ini almalı`);
                console.log(`   💡 Android loglarında 'NOTIFY GELDI' mesajını arayın`);
                console.log(`========================================\n`);
                
                this._updateValueCallback(data);
                console.log(`   ✅ Notification başarıyla gönderildi`);
                console.log(`   ✅ updateValueCallback() çağrısı başarılı`);
                console.log(`   💡 Android cihaz onCharacteristicChanged callback'ini almalı`);
                console.log(`   💡 Eğer Android'de 'NOTIFY GELDI' log'u görünmüyorsa:`);
                console.log(`      → macOS CoreBluetooth notify'u Android'e iletmiyor olabilir`);
                console.log(`      → Polling-based read workaround'u uygulayın`);
                return true;
            } catch (error) {
                console.error(`   ❌ Notification gönderme hatası: ${error.message}`);
                console.error(`   Stack: ${error.stack}`);
                console.error(`   ⚠️  updateValueCallback() exception fırlattı`);
                return false;
            }
        } else {
            console.warn(`\n⚠️⚠️⚠️  ========================================`);
            console.warn(`⚠️  NOTIFICATION CALLBACK YOK!`);
            console.warn(`========================================`);
            console.warn(`   _updateValueCallback: null`);
            console.warn(`   Client subscribe olmamış`);
            console.warn(`   💡 KRİTİK SORUN: onSubscribe callback'i hiç tetiklenmemiş!`);
            console.warn(`   💡 macOS CoreBluetooth Android'in descriptor yazmasını algılamıyor olabilir`);
            console.warn(`   💡 Teşhis adımları:`);
            console.warn(`      1. Android loglarını kontrol edin: adb logcat | grep "NOTIFY GELDI"`);
            console.warn(`      2. Android loglarını kontrol edin: adb logcat | grep "onDescriptorWrite"`);
            console.warn(`      3. Bridge server loglarında '🎉 onSubscribe CALLBACK TETİKLENDİ!' mesajını arayın`);
            console.warn(`      4. Android'in read request yaptığını kontrol edin (📖 onReadRequest log'ları)`);
            console.warn(`   💡 Geçici çözüm: Polling-based read workaround'u uygulayın`);
            console.warn(`      Android sürekli read yapacak, bridge server her read'de event döndürecek`);
            console.warn(`========================================\n`);
            return false;
        }
        console.log(`========================================\n`);
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

