package com.kingdom.seed;

import com.kingdom.Enums.KingdomType;
import com.kingdom.Model.Kingdom;
import com.kingdom.Repository.KingdomRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Seeds the 9 kingdoms — the system's fixed reference catalog that EVERY environment needs (players join them,
 * challenges/lobbies belong to them). This runs ALWAYS (no demo.seed.enabled flag) and BEFORE DemoSeeder
 * (@Order(0) < DemoSeeder's @Order(1)). Idempotent: getOrCreateKingdom only creates a type that doesn't exist
 * yet, so it's a no-op after the first boot and never duplicates.
 *
 * In production set DEMO_SEED_ENABLED=false: the demo accounts/challenges/lobbies in DemoSeeder are skipped, but
 * the kingdoms are still created here. Locally (flag unset -> default on) both run, so the demo flow is unchanged.
 */
@Component
@Order(0)
@RequiredArgsConstructor
public class KingdomSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(KingdomSeeder.class);

    private final KingdomRepository kingdomRepository;

    @Override
    public void run(String... args) {
        try {
            getOrCreateKingdom(KingdomType.SPORTS,       "Fitness Kingdom",   "حرّك جسدك — يتم التحقق عبر أنشطتك على Strava.");
            getOrCreateKingdom(KingdomType.CHARITY,      "Charity Kingdom",   "اعطِ وردّ الجميل — يتم التحقق من تبرّع بنكي عبر Neotek Open Banking.");
            getOrCreateKingdom(KingdomType.VOLUNTEERING, "Volunteer Kingdom", "اخدم مجتمعك — يتم التحقق من شهادة PDF يفحصها الذكاء الاصطناعي.");
            getOrCreateKingdom(KingdomType.READING,      "Reading Kingdom",   "اقرأ الكتب — يتم التحقق عبر اختبار قصير على WhatsApp (Google Books).");
            getOrCreateKingdom(KingdomType.GAMING,       "Gaming Kingdom",    "ارفع مستواك — يتم التحقق من وقت اللعب والإنجازات على Steam.");
            getOrCreateKingdom(KingdomType.FAITH,        "Faith Kingdom",     "ازدد قربًا — يتم التحقق عبر اختبار على WhatsApp حول محتوى القرآن.");
            getOrCreateKingdom(KingdomType.KNOWLEDGE,    "Knowledge Kingdom",  "تعلّم — يتم التحقق عبر اختبار معرفي بالذكاء الاصطناعي.");
            getOrCreateKingdom(KingdomType.NUTRITION,    "Nutrition Kingdom",  "تغذَّ جيدًا — يتم التحقق من صورة وجبتك بالذكاء الاصطناعي.");
            getOrCreateKingdom(KingdomType.PROGRAMMING,  "Programming Kingdom", "برمِج — يتم التحقق من نشاطك على GitHub.");
            log.info("[KingdomSeeder] ensured the 9 kingdoms exist.");
        } catch (Exception e) {
            log.warn("[KingdomSeeder] kingdom seeding skipped due to: {}", e.getMessage());
        }
    }

    private Kingdom getOrCreateKingdom(KingdomType type, String name, String description) {
        for (Kingdom existing : kingdomRepository.findAll()) {
            if (existing.getType() == type) {
                return existing;
            }
        }
        Kingdom kingdom = new Kingdom();
        kingdom.setName(name);
        kingdom.setDescription(description);
        kingdom.setType(type);
        return kingdomRepository.save(kingdom);
    }
}
