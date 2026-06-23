package com.kingdom.Service;

import com.kingdom.Enums.BadgeType;
import com.kingdom.Enums.Period;
import com.kingdom.Enums.ProgressStatus;
import com.kingdom.Model.ChallengeProgress;
import com.kingdom.Model.KingdomMembership;
import com.kingdom.Repository.ChallengeProgressRepository;
import com.kingdom.Repository.KingdomMembershipRepository;
import com.kingdom.Repository.PeriodScoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Single source of truth for "a run was PASSED -> daily streak + XP + division" (Member 3 / Anas owns the
 * XP/streak/division rules). EVERY finish path routes through here so the rules can never drift between them:
 *  - ChallengeProgressService.markVerified  (Strava / Neotek pass + volunteer PDF approved)
 *  - ChallengeQuestionWhatsappService        (Shahad's Reading/Faith WhatsApp quiz pass)
 *
 * Caller contract: call applyVerifiedReward BEFORE marking the run VERIFIED, so updateStreak's "finished today?"
 * query does not count the run that is finishing right now.
 */
@Service
@RequiredArgsConstructor
public class ProgressRewardService {

    private final KingdomMembershipRepository kingdomMembershipRepository;
    private final ChallengeProgressRepository challengeProgressRepository;
    private final PeriodScoreService periodScoreService;
    private final BadgeService badgeService;

    /** Bump the daily streak, add the challenge's fixed XP, recompute the division, and save the membership. */
    @Transactional
    public void applyVerifiedReward(ChallengeProgress progress, KingdomMembership membership) {
        if (membership == null) {
            return;
        }
        // Streak FIRST: the finishing run must still be non-VERIFIED in the DB so updateStreak excludes it.
        updateStreak(membership);

        int currentXp = membership.getTotalXP() == null ? 0 : membership.getTotalXP();
        Integer reward = (progress.getChallenge() == null) ? null : progress.getChallenge().getXpReward();
        int earnedXp = reward == null ? 0 : reward;
        int newXp = currentXp + earnedXp;
        membership.setTotalXP(newXp);
        membership.setDivision(divisionForXp(newXp));

        kingdomMembershipRepository.save(membership);
        // Update PeriodScore (weekly + monthly)
        periodScoreService.addToPeriodScore(membership, earnedXp, Period.WEEKLY);
        periodScoreService.addToPeriodScore(membership, earnedXp, Period.MONTHLY);

        // Award badges
        badgeService.awardBadges(BadgeType.XP, newXp, membership);
        badgeService.awardBadges(BadgeType.STREAK, membership.getStreak(), membership);
        badgeService.awardBadges(BadgeType.DIVISION, membership.getDivision(), membership);
    }

    // Division (lower = higher tier): D1 >= 25,000 | D2 10,000-24,999 | D3 0-9,999.
    private int divisionForXp(int totalXp) {
        if (totalXp >= 25000) {
            return 1;
        }
        if (totalXp >= 10000) {
            return 2;
        }
        return 3;
    }

    // Daily per-kingdom streak: +1 if also finished one here yesterday, unchanged if today, reset to 1 otherwise.
    private void updateStreak(KingdomMembership membership) {
        LocalDate today = LocalDate.now();
        LocalDate lastFinished = null;
        for (ChallengeProgress p :
                challengeProgressRepository.findAllByKingdomMembership_IdAndStatus(membership.getId(), ProgressStatus.VERIFIED)) {
            if (p.getFinishedAt() != null) {
                LocalDate d = p.getFinishedAt().toLocalDate();
                if (lastFinished == null || d.isAfter(lastFinished)) {
                    lastFinished = d;
                }
            }
        }
        int current = membership.getStreak() == null ? 0 : membership.getStreak();
        if (lastFinished != null && lastFinished.equals(today)) {
            return; // already finished a challenge today -> streak stays the same
        }
        if (lastFinished != null && lastFinished.equals(today.minusDays(1))) {
            membership.setStreak(current + 1);
        } else {
            membership.setStreak(1);
        }
    }
}
