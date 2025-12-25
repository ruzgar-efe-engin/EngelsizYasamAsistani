/**
 * BLE Configuration
 * 
 * Bu dosya BLE UUID'leri ve device name'i içerir.
 * Device'daki EventTransport.h ile aynı değerleri kullanmalıdır.
 */

module.exports = {
    // BLE UUID'leri - device'daki EventTransport.h ile aynı
    SERVICE_UUID: '12345678123412341234123456789abc',
    CHARACTERISTIC_UUID: '12345678123412341234123456789abd',
    
    // Device name - fiziksel cihazla aynı olmalı
    DEVICE_NAME: 'GormeEngellilerKumanda',
    
    // UUID formatı (dash'li format için)
    getServiceUUID() {
        return this.SERVICE_UUID.replace(/(.{8})(.{4})(.{4})(.{4})(.{12})/, '$1-$2-$3-$4-$5');
    },
    
    getCharacteristicUUID() {
        return this.CHARACTERISTIC_UUID.replace(/(.{8})(.{4})(.{4})(.{4})(.{12})/, '$1-$2-$3-$4-$5');
    }
};

