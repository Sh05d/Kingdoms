package com.kingdom.Service.AiService;

import com.kingdom.Enums.KingdomType;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves the right per-kingdom AI by {@link KingdomType}. Spring injects every {@link KingdomAiService}
 * bean; we index them by their {@code kingdom()} so the challenge-generation flow can pick the matching one.
 *
 * Only the implemented kingdoms are present (Anas's: SPORTS / CHARITY / VOLUNTEERING). Asking for a kingdom
 * type that has no AI yet returns null.
 */
@Component
public class KingdomAiRegistry {

    private final Map<KingdomType, KingdomAiService> byKingdom = new HashMap<>();

    public KingdomAiRegistry(List<KingdomAiService> services) {
        for (KingdomAiService service : services) {
            byKingdom.put(service.kingdom(), service);
        }
    }

    /** The AI for this kingdom type, or null if none is implemented yet. */
    public KingdomAiService forKingdom(KingdomType type) {
        return byKingdom.get(type);
    }
}
