package com.kingdom.seed;

import com.kingdom.Enums.Difficulty;
import com.kingdom.Enums.KingdomType;
import com.kingdom.Enums.LobbyStatus;
import com.kingdom.Enums.LobbyVisibility;
import com.kingdom.Enums.MemberRole;
import com.kingdom.Enums.Period;
import com.kingdom.Enums.SubscriptionPlan;
import com.kingdom.Enums.SubscriptionStatus;
import com.kingdom.Enums.UserRole;
import com.kingdom.Model.Challenge;
import com.kingdom.Model.ChallengeQuestion;
import com.kingdom.Model.Kingdom;
import com.kingdom.Model.KingdomMembership;
import com.kingdom.Model.Lobby;
import com.kingdom.Model.LobbyMember;
import com.kingdom.Model.Player;
import com.kingdom.Model.Subscription;
import com.kingdom.Model.User;
import com.kingdom.Repository.ChallengeRepository;
import com.kingdom.Repository.KingdomMembershipRepository;
import com.kingdom.Repository.KingdomRepository;
import com.kingdom.Repository.LobbyMemberRepository;
import com.kingdom.Repository.LobbyRepository;
import com.kingdom.Repository.PlayerRepository;
import com.kingdom.Repository.SubscriptionRepository;
import com.kingdom.Repository.UserRepository;
import com.kingdom.Service.AiService.ChallengeXp;
import com.kingdom.Service.ChallengeService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Demo seeder. NOTE: the live demo is run with a REAL registration (Anas registers himself), so this seeder no
 * longer creates the 'anas' account — only the supporting cast:
 *  - admin (admin/admin1234) for the admin-only endpoints (AI generate, review, ...).
 *  - the 9 kingdoms.
 *  - FITNESS + NUTRITION challenges are AI-GENERATED (3 each) so they are real, varied, and dedup against each
 *    other; the other kingdoms keep ready-made challenges so every kingdom is browsable.
 *  - a few host players (player2..player5, all premium) and a handful of PUBLIC lobbies so the lobby
 *    recommendation has real data, plus player2 to send Anas a PRIVATE-lobby invite during the demo.
 *
 * NOT @Transactional on run(): the AI-generation calls run in their own transactions, so one failed generate can't
 * poison the whole seed. Best-effort throughout. Disable with demo.seed.enabled=false.
 */
@Component
@Order(1)
@ConditionalOnProperty(name = "demo.seed.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class DemoSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);

    private final UserRepository userRepository;
    private final PlayerRepository playerRepository;
    private final KingdomRepository kingdomRepository;
    private final KingdomMembershipRepository membershipRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ChallengeRepository challengeRepository;
    private final ChallengeService challengeService;        // AI generation (dedups against existing titles)
    private final LobbyRepository lobbyRepository;           // direct lobby seeding (bypasses createLobby's auth)
    private final LobbyMemberRepository lobbyMemberRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        try {
            // 1) ADMIN only (no 'anas' — Anas registers live for the demo). Login: admin / admin1234
            seedAdmin();

            // 2) the 9 kingdoms (Kingdom.type is unique -> idempotent).
            Kingdom sports    = getOrCreateKingdom(KingdomType.SPORTS,       "Fitness Kingdom",   "حرّك جسدك — يتم التحقق عبر أنشطتك على Strava.");
            Kingdom charity   = getOrCreateKingdom(KingdomType.CHARITY,      "Charity Kingdom",   "اعطِ وردّ الجميل — يتم التحقق من تبرّع بنكي عبر Neotek Open Banking.");
            Kingdom volunteer = getOrCreateKingdom(KingdomType.VOLUNTEERING, "Volunteer Kingdom", "اخدم مجتمعك — يتم التحقق من شهادة PDF يفحصها الذكاء الاصطناعي.");
            Kingdom reading   = getOrCreateKingdom(KingdomType.READING,      "Reading Kingdom",   "اقرأ الكتب — يتم التحقق عبر اختبار قصير على WhatsApp (Google Books).");
            Kingdom gaming    = getOrCreateKingdom(KingdomType.GAMING,        "Gaming Kingdom",    "ارفع مستواك — يتم التحقق من وقت اللعب والإنجازات على Steam.");
            Kingdom faith     = getOrCreateKingdom(KingdomType.FAITH,         "Faith Kingdom",     "ازدد قربًا — يتم التحقق عبر اختبار على WhatsApp حول محتوى القرآن.");
            Kingdom knowledge   = getOrCreateKingdom(KingdomType.KNOWLEDGE,   "Knowledge Kingdom",   "تعلّم — يتم التحقق عبر اختبار معرفي بالذكاء الاصطناعي.");
            Kingdom nutrition   = getOrCreateKingdom(KingdomType.NUTRITION,   "Nutrition Kingdom",   "تغذَّ جيدًا — يتم التحقق من صورة وجبتك بالذكاء الاصطناعي.");
            Kingdom programming = getOrCreateKingdom(KingdomType.PROGRAMMING, "Programming Kingdom", "برمِج — يتم التحقق من نشاطك على GitHub.");

            // 3) FITNESS + NUTRITION challenges: AI-GENERATED (3 each), not hardcoded. addChallenge dedups against
            //    existing titles, so the three in each kingdom differ. Best-effort: a failed generate is skipped.
            aiSeedChallenges(sports.getId(),
                    new Difficulty[]{Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD},
                    new Period[]{Period.DAILY, Period.WEEKLY, Period.WEEKLY});
            aiSeedChallenges(nutrition.getId(),
                    new Difficulty[]{Difficulty.EASY, Difficulty.MEDIUM, Difficulty.EASY},
                    new Period[]{Period.DAILY, Period.DAILY, Period.WEEKLY});

            // 4) the OTHER kingdoms keep ready-made challenges so they're browsable out of the box.
            seedHardcodedChallenges(charity, volunteer);
            seedQuizAndRichChallenges(reading, gaming, faith, knowledge, programming);

            // 5) host players for the lobby demo (all premium). player2 is used to invite the LIVE Anas to a
            //    private lobby; player3..5 each host a PUBLIC lobby so the recommendation has real options.
            Player p2 = seedHostPlayer("player2", "Player Two", "player2.demo@kingdoms.local", "+966500000102", sports, nutrition);
            Player p3 = seedHostPlayer("player3", "Player Three", "player3.demo@kingdoms.local", "+966500000103", sports);
            Player p4 = seedHostPlayer("player4", "Player Four", "player4.demo@kingdoms.local", "+966500000104", nutrition);
            Player p5 = seedHostPlayer("player5", "Player Five", "player5.demo@kingdoms.local", "+966500000105", sports);

            // 6) PUBLIC lobbies in Sports + Nutrition (division 3, OPEN) so suggestLobbyForPlayer returns real options
            //    to any player joined in those kingdoms. Each references a real challenge from its kingdom.
            List<Challenge> sportsCh = challengeRepository.findAllByKingdom_Id(sports.getId());
            List<Challenge> nutCh = challengeRepository.findAllByKingdom_Id(nutrition.getId());
            if (!sportsCh.isEmpty()) {
                seedPublicLobby(p3, sports, sportsCh.get(0), "سباق الجري الأسبوعي",
                        "نتنافس على أطول مسافة جري هذا الأسبوع — انضم وتحدَّ نفسك مع البقية!");
                seedPublicLobby(p5, sports, sportsCh.get(sportsCh.size() > 1 ? 1 : 0), "تحدي النشاط اليومي",
                        "من يسجّل أكثر نشاط رياضي اليوم؟ منافسة ودية وحماسية.");
            }
            if (!nutCh.isEmpty()) {
                seedPublicLobby(p4, nutrition, nutCh.get(0), "لوبي الأكل الصحي",
                        "شاركنا صور وجباتك الصحية ونافس على أفضل التزام غذائي.");
            }

            log.info("[DemoSeeder] seeded admin + 9 kingdoms; fitness challenges={}, nutrition challenges={}; "
                            + "host players player2..player5; public lobbies in Sports/Nutrition. (No 'anas' — register live.)",
                    sportsCh.size(), nutCh.size());
        } catch (Exception e) {
            log.warn("[DemoSeeder] seeding skipped due to: {}", e.getMessage());
        }
    }

    private void seedAdmin() {
        User adminUser = userRepository.findUserByUsername("admin");
        if (adminUser == null) {
            adminUser = new User();
            adminUser.setUsername("admin");
            adminUser.setEmail("admin@kingdom.test");
            adminUser.setPhoneNumber("+966500000099");
            adminUser.setRole(UserRole.ADMIN);
        }
        adminUser.setPasswordHash(passwordEncoder.encode("admin1234"));
        adminUser.setPhoneVerified(true);
        userRepository.save(adminUser);
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

    // AI-generate `diffs.length` challenges for a kingdom via the same path the /challenge/generate endpoint uses
    // (addChallenge dedups against existing titles). Each call is its own transaction; a failure is logged + skipped.
    private void aiSeedChallenges(Integer kingdomId, Difficulty[] diffs, Period[] periods) {
        if (!challengeRepository.findAllByKingdom_Id(kingdomId).isEmpty()) {
            return; // already seeded
        }
        for (int i = 0; i < diffs.length; i++) {
            try {
                challengeService.addChallenge(kingdomId, diffs[i], periods[i]);
            } catch (Exception e) {
                log.warn("[DemoSeeder] AI challenge generation failed for kingdom {} ({}/{}): {}",
                        kingdomId, diffs[i], periods[i], e.getMessage());
            }
        }
    }

    private Player seedHostPlayer(String username, String displayName, String email, String phone, Kingdom... kingdoms) {
        User u = userRepository.findUserByUsername(username);
        if (u == null) {
            u = new User();
            u.setUsername(username);
            u.setRole(UserRole.PLAYER);
        }
        u.setEmail(email);
        u.setPhoneNumber(phone);
        u.setPasswordHash(passwordEncoder.encode("demo1234")); // login: <username> / demo1234
        u.setPhoneVerified(true);
        u = userRepository.save(u);

        Player p = u.getPlayer();
        if (p == null) {
            p = new Player();
            p.setDisplayName(displayName);
            p.setJoinedAt(LocalDateTime.now());
            p.setUser(u);
            p = playerRepository.save(p);
        }
        for (Kingdom k : kingdoms) {
            seedMembership(p, k);
        }
        seedPremiumSubscription(p);
        return p;
    }

    private void seedMembership(Player player, Kingdom kingdom) {
        if (membershipRepository.findByPlayerAndKingdom(player, kingdom) != null) {
            return;
        }
        KingdomMembership membership = new KingdomMembership();
        membership.setPlayer(player);
        membership.setKingdom(kingdom);
        membership.setTotalXP(0);
        membership.setDivision(3); // D3 — matches a brand-new player, so seeded lobbies are recommendable to them.
        membership.setStreak(0);
        membership.setJoinedAt(LocalDateTime.now());
        membershipRepository.save(membership);
    }

    // Create a PUBLIC lobby directly (bypassing createLobby's auth/premium checks) with its host member, so the
    // lobby recommendation + browse have real data on a fresh DB.
    private void seedPublicLobby(Player host, Kingdom kingdom, Challenge challenge, String name, String description) {
        Lobby lobby = new Lobby();
        lobby.setHostPlayerId(host.getId());
        lobby.setName(name);
        lobby.setDescription(description);
        lobby.setVisibility(LobbyVisibility.PUBLIC);
        lobby.setStatus(LobbyStatus.OPEN);
        lobby.setDivision(3);
        lobby.setKingdom(kingdom);
        lobby.setChallenge(challenge);
        lobby.setStartsAt(LocalDateTime.now());
        lobby.setEndsAt(LocalDateTime.now().plusDays(7));
        Lobby saved = lobbyRepository.save(lobby);

        LobbyMember member = new LobbyMember();
        member.setLobby(saved);
        member.setPlayer(host);
        member.setRole(MemberRole.HOST);
        member.setJoinedAt(LocalDateTime.now());
        lobbyMemberRepository.save(member);
    }

    private void seedHardcodedChallenges(Kingdom charity, Kingdom volunteer) {
        if (challengeRepository.findAllByKingdom_Id(charity.getId()).isEmpty()) {
            seedChallenge(charity, "صدقة اليوم", "تبرّع بمبلغ 5 ريال اليوم (يُتحقق عبر تبرّع بنكي).", Difficulty.EASY, Period.DAILY, "NEOTEK_OPEN_BANKING", null, 5);
            seedChallenge(charity, "أسبوع العطاء", "تبرّع بمبلغ 20 ريال خلال هذا الأسبوع.", Difficulty.EASY, Period.WEEKLY, "NEOTEK_OPEN_BANKING", null, 20);
            seedChallenge(charity, "عطاء أكبر", "تبرّع بمبلغ 30 ريال اليوم.", Difficulty.MEDIUM, Period.DAILY, "NEOTEK_OPEN_BANKING", null, 30);
            seedChallenge(charity, "كرم الأسبوع", "تبرّع بمبلغ 75 ريال خلال هذا الأسبوع.", Difficulty.MEDIUM, Period.WEEKLY, "NEOTEK_OPEN_BANKING", null, 75);
            seedChallenge(charity, "محسن الشهر", "تبرّع بمبلغ 600 ريال خلال هذا الشهر.", Difficulty.HARD, Period.MONTHLY, "NEOTEK_OPEN_BANKING", null, 600);
        }
        if (challengeRepository.findAllByKingdom_Id(volunteer.getId()).isEmpty()) {
            seedChallenge(volunteer, "ساعة عطاء", "تطوّع لمدة ساعة وأرفق شهادة التطوع (PDF).", Difficulty.EASY, Period.DAILY, "AI_PDF_MATCH", null, 1);
            seedChallenge(volunteer, "تطوع الأسبوع", "تطوّع لمدة 3 ساعات هذا الأسبوع وأرفق الشهادة.", Difficulty.EASY, Period.WEEKLY, "AI_PDF_MATCH", null, 3);
            seedChallenge(volunteer, "متطوع نشط", "تطوّع لمدة 6 ساعات هذا الأسبوع وأرفق الشهادة.", Difficulty.MEDIUM, Period.WEEKLY, "AI_PDF_MATCH", null, 6);
            seedChallenge(volunteer, "خدمة المجتمع", "تطوّع لمدة 12 ساعة هذا الشهر وأرفق الشهادة.", Difficulty.MEDIUM, Period.MONTHLY, "AI_PDF_MATCH", null, 12);
            seedChallenge(volunteer, "بطل التطوع", "تطوّع لمدة 25 ساعة هذا الشهر وأرفق الشهادة.", Difficulty.HARD, Period.MONTHLY, "AI_PDF_MATCH", null, 25);
        }
    }

    private void seedChallenge(Kingdom kingdom, String title, String description, Difficulty difficulty,
                               Period period, String source, String metricKey, Integer target) {
        Challenge c = new Challenge();
        c.setKingdom(kingdom);
        c.setTitle(title);
        c.setDescription(description);
        c.setDifficulty(difficulty);
        c.setPeriod(period);
        c.setXpReward(ChallengeXp.xpFor(difficulty, period));
        c.setVerificationSource(source);
        c.setMetricKey(metricKey);
        c.setTargetValue(target);
        challengeRepository.save(c);
    }

    private void seedQuizAndRichChallenges(Kingdom reading, Kingdom gaming, Kingdom faith, Kingdom knowledge,
                                           Kingdom programming) {
        if (challengeRepository.findAllByKingdom_Id(reading.getId()).isEmpty()) {
            seedQuizChallenge(reading, "اختبار قراءة الكتاب", "اقرأ كتابك ثم أجب عن 5 أسئلة قصيرة على WhatsApp لإثبات قراءتك.",
                    Difficulty.EASY, Period.WEEKLY, "BOOK_QUESTIONS", "أسئلة الكتاب");
        }
        if (challengeRepository.findAllByKingdom_Id(faith.getId()).isEmpty()) {
            seedQuizChallenge(faith, "اختبار حفظ القرآن", "راجع وردك من القرآن ثم أجب عن 5 أسئلة على WhatsApp للتحقق.",
                    Difficulty.EASY, Period.WEEKLY, "QURAN_QUESTIONS", "أسئلة القرآن");
        }
        if (challengeRepository.findAllByKingdom_Id(knowledge.getId()).isEmpty()) {
            seedQuizChallenge(knowledge, "اختبار المعرفة العامة", "اختبر معلوماتك العامة بالإجابة عن 5 أسئلة على WhatsApp.",
                    Difficulty.EASY, Period.WEEKLY, "KNOWLEDGE_QUESTIONS", "أسئلة المعرفة");
        }
        if (challengeRepository.findAllByKingdom_Id(gaming.getId()).isEmpty()) {
            seedRichChallenge(gaming, "ساعة لعب في Portal 2", "العب لمدة 60 دقيقة في لعبة Portal 2 على Steam هذا الأسبوع.",
                    Difficulty.EASY, Period.WEEKLY, "STEAM", "PLAYTIME", "Portal 2", "Portal 2", 60);
            seedRichChallenge(gaming, "محترف Rocket League", "اجمع 120 دقيقة لعب في Rocket League على Steam هذا الأسبوع.",
                    Difficulty.MEDIUM, Period.WEEKLY, "STEAM", "PLAYTIME", "Rocket League", "Rocket League", 120);
        }
        if (challengeRepository.findAllByKingdom_Id(programming.getId()).isEmpty()) {
            seedRichChallenge(programming, "كومِت على GitHub", "ادفع تعديلاً واحداً على الأقل (commit) إلى مستودعك على GitHub اليوم.",
                    Difficulty.EASY, Period.DAILY, "GITHUB", "REPOSITORY_COMMITS", "GitHub Repository", "GITHUB_REPOSITORY", 1);
        }
    }

    private void seedRichChallenge(Kingdom kingdom, String title, String description, Difficulty difficulty,
                                   Period period, String source, String rule, String targetName,
                                   String verificationTarget, Integer target) {
        Challenge c = new Challenge();
        c.setKingdom(kingdom);
        c.setTitle(title);
        c.setDescription(description);
        c.setDifficulty(difficulty);
        c.setPeriod(period);
        c.setVerificationSource(source);
        c.setVerificationRule(rule);
        c.setTargetName(targetName);
        c.setVerificationTarget(verificationTarget);
        c.setTargetValue(target);
        c.setXpReward(ChallengeXp.xpFor(difficulty, period));
        challengeRepository.save(c);
    }

    private void seedQuizChallenge(Kingdom kingdom, String title, String description, Difficulty difficulty,
                                   Period period, String rule, String targetName) {
        Challenge c = new Challenge();
        c.setKingdom(kingdom);
        c.setTitle(title);
        c.setDescription(description);
        c.setDifficulty(difficulty);
        c.setPeriod(period);
        c.setVerificationSource("WHATSAPP");
        c.setVerificationRule(rule);
        c.setTargetName(targetName);
        c.setVerificationTarget(targetName);
        c.setTargetValue(5);
        c.setXpReward(ChallengeXp.xpFor(difficulty, period));

        Set<ChallengeQuestion> questions = new HashSet<>();
        for (int i = 1; i <= 5; i++) {
            ChallengeQuestion q = new ChallengeQuestion();
            q.setChallenge(c);
            q.setQuestion("سؤال تجريبي رقم " + i + " للتحقق من \"" + targetName + "\"");
            q.setOptionA("الخيار الصحيح أ");
            q.setOptionB("الخيار ب");
            q.setOptionC("الخيار ج");
            q.setOptionD("الخيار د");
            q.setCorrectAnswer("A");
            questions.add(q);
        }
        c.setChallengeQuestions(questions);
        challengeRepository.save(c);
    }

    private void seedPremiumSubscription(Player player) {
        Subscription subscription = subscriptionRepository.findSubscriptionById(player.getId());
        if (subscription == null) {
            subscription = new Subscription();
            subscription.setPlayer(player); // @MapsId -> subscription.id = player.id
        }
        subscription.setPlan(SubscriptionPlan.PREMIUM_MONTHLY);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setAutoRenew(true);
        if (subscription.getStartDate() == null) {
            subscription.setStartDate(LocalDateTime.now());
        }
        subscription.setExpiresAt(LocalDateTime.now().plusYears(1));
        subscriptionRepository.save(subscription);
    }
}
