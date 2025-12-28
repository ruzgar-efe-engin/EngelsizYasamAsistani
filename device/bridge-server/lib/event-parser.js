/**
 * Event Parser
 * 
 * Serial port'tan gelen event'leri parse eder.
 * İki format destekler: Text format ve JSON format.
 */

class EventParser {
    constructor() {
        // Event type mapping
        this.typeMap = {
            'MAIN_ROTATE': 0,
            'SUB_ROTATE': 1,
            'CONFIRM': 2,
            'CANCEL': 3,
            'EVENT_CANCEL': 3,
            'AI_PRESS': 4,
            'AI_RELEASE': 5
        };
    }

    /**
     * Text formatını parse et: [BLE] MAIN_ROTATE m=1 s=0 ts=12345
     * @param {string} line - Parse edilecek satır
     * @returns {Object|null} Event object veya null
     */
    parseTextFormat(line) {
        try {
            // Event type'ı bul
            let eventType = 0;
            for (const [key, value] of Object.entries(this.typeMap)) {
                if (line.includes(key)) {
                    eventType = value;
                    break;
                }
            }

            // Parametreleri parse et
            const mMatch = line.match(/m=(\d+)/);
            const sMatch = line.match(/s=(\d+)/);
            const tsMatch = line.match(/ts=(\d+)/);

            const event = {
                type: eventType,
                mainIndex: mMatch ? parseInt(mMatch[1]) : 0,
                subIndex: sMatch ? parseInt(sMatch[1]) : 0,
                ts: tsMatch ? parseInt(tsMatch[1]) : Date.now()
            };

            return event;
        } catch (e) {
            return null;
        }
    }

    /**
     * JSON formatını parse et: {"type":0,"mainIndex":1,"subIndex":0,"ts":12345}
     * @param {string} jsonString - Parse edilecek JSON string
     * @returns {Object|null} Event object veya null
     */
    parseJsonFormat(jsonString) {
        try {
            const jsonMatch = jsonString.match(/\{.*\}/);
            if (jsonMatch) {
                return JSON.parse(jsonMatch[0]);
            }
            return null;
        } catch (e) {
            return null;
        }
    }

    /**
     * Event'i JSON string'e çevir
     * @param {Object} event - Event object
     * @returns {string} JSON string
     */
    toJson(event) {
        return JSON.stringify(event);
    }

    /**
     * Satırdan event parse et (otomatik format tespiti)
     * @param {string} line - Parse edilecek satır
     * @returns {Object|null} Event object veya null
     */
    parse(line) {
        // Önce JSON formatını dene
        const jsonEvent = this.parseJsonFormat(line);
        if (jsonEvent) {
            return jsonEvent;
        }

        // JSON değilse text formatını dene
        return this.parseTextFormat(line);
    }
}

module.exports = EventParser;

