package com.kingdom.Service;

import com.kingdom.API.ApiException;
import com.kingdom.Enums.ProgressStatus;
import com.kingdom.Model.ChallengeProgress;
import com.kingdom.Model.KingdomMembership;
import com.kingdom.Repository.ChallengeProgressRepository;
import com.kingdom.Repository.KingdomMembershipRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Keeps the per-kingdom daily streak honest. The streak window is a CALENDAR DAY: finish at least one challenge
 * in a kingdom each day to keep its streak (ProgressRewardService.updateStreak builds it up on a verified finish).
 * This service actively DROPS the streak once a full day passes with no finish, and WARNS the player ~6 hours
 * before they'd lose it.
 *
 * Scheduled jobs:
 *  - 18:00 daily  -> warnStreaksAtRisk(): streak alive (finished yesterday) but nothing finished today yet
 *    => 6 hours before midnight, send a "your streak ends tonight" WhatsApp.
 *  - 00:05 daily  -> resetLostStreaks(): a full calendar day passed with no finish => set streak to 0 + notify.
 *
 * runNow() runs both immediately (manual / demo trigger). Best-effort WhatsApp: a notification failure never
 * breaks the schedule.
 */
@Service
@RequiredArgsConstructor
public class StreakService {

    private static final Logger log = LoggerFactory.getLogger(StreakService.class);

    private final KingdomMembershipRepository membershipRepository;
    private final ChallengeProgressRepository challengeProgressRepository;
    private final WhatsAppService whatsAppService;

    /** 6h before midnight: warn players whose streak will be lost tonight unless they finish a challenge today. */
    @Scheduled(cron = "0 0 18 * * *")
    public int warnStreaksAtRisk() {
        LocalDate today = LocalDate.now();
        int warned = 0;
        for (KingdomMembership m : membershipRepository.findAll()) {
            int streak = (m.getStreak() == null) ? 0 : m.getStreak();
            if (streak <= 0) {
                continue;
            }
            LocalDate last = lastFinishedDate(m);
            // streak alive (last finish was yesterday) but not continued today -> at risk tonight
            if (last != null && last.equals(today.minusDays(1))) {
                notifyAtRisk(m, streak);
                warned++;
            }
        }
        log.info("[StreakService] at-risk warnings sent: {}", warned);
        return warned;
    }

    /** Just after midnight: drop any streak where a full calendar day passed with no verified finish. */
    @Scheduled(cron = "0 5 0 * * *")
    public int resetLostStreaks() {
        LocalDate today = LocalDate.now();
        int reset = 0;
        for (KingdomMembership m : membershipRepository.findAll()) {
            int streak = (m.getStreak() == null) ? 0 : m.getStreak();
            if (streak <= 0) {
                continue;
            }
            LocalDate last = lastFinishedDate(m);
            // never finished, or the last finish is older than yesterday -> a whole day was missed -> streak lost
            if (last == null || last.isBefore(today.minusDays(1))) {
                m.setStreak(0);
                membershipRepository.save(m);
                notifyLost(m, streak);
                reset++;
            }
        }
        log.info("[StreakService] streaks reset to 0: {}", reset);
        return reset;
    }

    /** Manual / demo trigger: run both checks right now and report the counts. */
    public Map<String, Integer> runNow() {
        Map<String, Integer> out = new LinkedHashMap<>();
        out.put("atRiskWarned", warnStreaksAtRisk());
        out.put("streaksReset", resetLostStreaks());
        return out;
    }

    /** Demo helper: send the at-risk warning for one player+kingdom on demand (so the message can be shown live). */
    public void previewWarning(Integer playerId, Integer kingdomId) {
        KingdomMembership m = membershipRepository.findByPlayer_IdAndKingdom_Id(playerId, kingdomId);
        if (m == null) {
            throw new ApiException("No membership for that player and kingdom");
        }
        int streak = (m.getStreak() == null || m.getStreak() <= 0) ? 1 : m.getStreak();
        notifyAtRisk(m, streak);
    }

    // Latest calendar date this membership finished + verified a challenge (null if it never has).
    private LocalDate lastFinishedDate(KingdomMembership m) {
        LocalDate last = null;
        for (ChallengeProgress p :
                challengeProgressRepository.findAllByKingdomMembership_IdAndStatus(m.getId(), ProgressStatus.VERIFIED)) {
            if (p.getFinishedAt() != null) {
                LocalDate d = p.getFinishedAt().toLocalDate();
                if (last == null || d.isAfter(last)) {
                    last = d;
                }
            }
        }
        return last;
    }

    private void notifyAtRisk(KingdomMembership m, int streak) {
        send(m, "🔥 سلسلتك في " + kingdomName(m) + " (" + streak + " يوم) ستنتهي خلال ٦ ساعات! "
                + "أكمل تحدياً اليوم للحفاظ عليها. 👑");
    }

    private void notifyLost(KingdomMembership m, int streak) {
        send(m, "💔 انتهت سلسلتك في " + kingdomName(m) + " (كانت " + streak + " يوم). "
                + "ابدأ من جديد بإكمال تحدٍّ اليوم! 🔥");
    }

    // Best-effort WhatsApp to the member; never throws so a notification failure can't break the schedule.
    private void send(KingdomMembership m, String message) {
        try {
            if (m.getPlayer() == null || m.getPlayer().getUser() == null) {
                return;
            }
            String phone = m.getPlayer().getUser().getPhoneNumber();
            if (phone == null || phone.isBlank()) {
                return;
            }
            whatsAppService.sendMessage(phone, message);
        } catch (Exception e) {
            // best-effort
        }
    }

    private String kingdomName(KingdomMembership m) {
        return (m.getKingdom() != null && m.getKingdom().getName() != null) ? m.getKingdom().getName() : "المملكة";
    }
}
