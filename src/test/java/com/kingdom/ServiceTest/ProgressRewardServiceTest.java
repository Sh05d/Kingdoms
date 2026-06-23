package com.kingdom.ServiceTest;

import com.kingdom.Enums.ProgressStatus;
import com.kingdom.Model.Challenge;
import com.kingdom.Model.ChallengeProgress;
import com.kingdom.Model.KingdomMembership;
import com.kingdom.Repository.ChallengeProgressRepository;
import com.kingdom.Repository.KingdomMembershipRepository;
import com.kingdom.Service.BadgeService;
import com.kingdom.Service.PeriodScoreService;
import com.kingdom.Service.ProgressRewardService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ProgressRewardServiceTest {

    @InjectMocks
    ProgressRewardService progressRewardService;

    @Mock
    KingdomMembershipRepository kingdomMembershipRepository;
    @Mock
    ChallengeProgressRepository challengeProgressRepository;
    @Mock
    PeriodScoreService periodScoreService;
    @Mock
    BadgeService badgeService;

    @Test
    public void applyVerifiedReward_addsXp_setsStreak_setsDivision3() {
        KingdomMembership membership = new KingdomMembership();
        membership.setId(1);
        membership.setTotalXP(0);
        membership.setStreak(0);

        Challenge challenge = new Challenge();
        challenge.setXpReward(50);

        ChallengeProgress progress = new ChallengeProgress();
        progress.setChallenge(challenge);

        // No prior VERIFIED runs -> updateStreak resets streak to 1.
        when(challengeProgressRepository
                .findAllByKingdomMembership_IdAndStatus(1, ProgressStatus.VERIFIED))
                .thenReturn(Collections.emptyList());

        progressRewardService.applyVerifiedReward(progress, membership);

        assertEquals(50, membership.getTotalXP());
        assertEquals(1, membership.getStreak());
        assertEquals(3, membership.getDivision()); // 0-9,999 -> division 3
        verify(kingdomMembershipRepository).save(membership);
    }
}
