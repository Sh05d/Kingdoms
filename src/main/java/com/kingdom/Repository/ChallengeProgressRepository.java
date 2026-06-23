package com.kingdom.Repository;

import com.kingdom.Enums.ProgressStatus;
import com.kingdom.Model.Challenge;
import com.kingdom.Model.ChallengeProgress;
import com.kingdom.Model.KingdomMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface ChallengeProgressRepository extends JpaRepository<ChallengeProgress, Integer> {

    ChallengeProgress findChallengeProgressById(Integer id);

    List<ChallengeProgress> findAllByKingdomMembership_Player_Id(Integer playerId);

    List<ChallengeProgress> findAllByKingdomMembership_Player_IdAndStatusIn(Integer playerId, Collection<ProgressStatus> statuses);

    List<ChallengeProgress> findAllByKingdomMembership_Player_IdAndStatus(Integer playerId, ProgressStatus status);

    // Finished challenges for ONE membership (used to compute the daily kingdom streak).
    List<ChallengeProgress> findAllByKingdomMembership_IdAndStatus(Integer membershipId, ProgressStatus status);

    // VERIFIED runs for ONE challenge (used to find a lobby challenge's winner — the earliest finisher).
    List<ChallengeProgress> findAllByChallenge_IdAndStatus(Integer challengeId, ProgressStatus status);

    // Shahad (WhatsApp quiz): the player's active run looked up by their WhatsApp phone number.
    @Query("select cp from ChallengeProgress cp where cp.kingdomMembership.player.user.phoneNumber = ?1 and cp.status = ?2 order by cp.startAt desc")
    List<ChallengeProgress> findActiveProgressByPhone(String phone, ProgressStatus status);

    // Maysun (Nutrition/Coding/Knowledge + admin stats):
    ChallengeProgress findByKingdomMembershipIdAndChallengeId(Integer kingdomMembershipId, Integer challengeId);
    List<ChallengeProgress> findAllByStatus(ProgressStatus status);
    long countByStatus(ProgressStatus status);
    List<ChallengeProgress> findAllByKingdomMembershipAndChallenge(KingdomMembership kingdomMembership, Challenge challenge);

    @Query("select count(cp) from ChallengeProgress cp where cp.kingdomMembership.player.id = ?1 and cp.status = 'VERIFIED'")
    Integer completedChallenges(Integer playerId);

    @Query("select count(cp) from ChallengeProgress cp where cp.kingdomMembership.player.id = ?1 and cp.kingdomMembership.kingdom.id = ?2 and cp.status = 'VERIFIED'")
    Integer completedChallengesInKingdom(Integer playerId, Integer kingdomId);
    List<ChallengeProgress> findAllByKingdomMembershipAndStatus(KingdomMembership kingdomMembership, ProgressStatus status);
}
