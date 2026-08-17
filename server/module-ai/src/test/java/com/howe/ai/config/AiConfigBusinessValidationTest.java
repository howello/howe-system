package com.howe.ai.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiConfigBusinessValidationTest {
    @Test
    void rejectsUnknownAndRelationshipInvalidResources() {
        var service = new AiConfigService(null);
        assertThrows(IllegalArgumentException.class, () -> service.validateResource("unknown"));
        assertDoesNotThrow(() -> service.validateResource("channels"));
        assertDoesNotThrow(() -> service.validateResource("route-items"));
        assertDoesNotThrow(() -> service.validateResource("prices"));
    }

    @Test
    void priceRequestRequiresDedicatedPriceFields() {
        var service = new AiConfigService(null);
        assertThrows(IllegalArgumentException.class, () -> service.validatePrice(null, "USD", null, "1", null, null, null));
        assertDoesNotThrow(() -> service.validatePrice(1L, "USD", "0.1", "1", "0", "0", "2026-08-13 00:00:00"));
    }
}
