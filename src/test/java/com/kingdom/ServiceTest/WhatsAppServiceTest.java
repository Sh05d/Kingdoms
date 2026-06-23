package com.kingdom.ServiceTest;

import com.kingdom.Service.WhatsAppService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link WhatsAppService#normalizeToE164(String)}.
 * Pure string logic (no Twilio call), so the service is instantiated directly with no mocks.
 */
public class WhatsAppServiceTest {

    private final WhatsAppService service = new WhatsAppService();

    @Test
    public void normalizesSaudiPhoneFormatsToE164() {
        assertEquals("+966501234567", service.normalizeToE164("0501234567"));            // local 05...
        assertEquals("+966501234567", service.normalizeToE164("501234567"));              // bare 5...
        assertEquals("+966501234567", service.normalizeToE164("966501234567"));           // 966 without +
        assertEquals("+966501234567", service.normalizeToE164("+966501234567"));          // already E.164
        assertEquals("+966501234567", service.normalizeToE164("whatsapp:+966501234567")); // strips whatsapp:
    }
}
