package com.kingdom.Service;

import com.kingdom.API.ApiException;
import com.kingdom.Model.Kingdom;
import com.kingdom.Model.KingdomMembership;
import com.kingdom.Model.Player;
import com.kingdom.Repository.ChallengeProgressRepository;
import com.kingdom.Repository.KingdomMembershipRepository;
import com.kingdom.Repository.KingdomRepository;
import com.kingdom.Repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class KingdomMembershipService {

    private final KingdomMembershipRepository kingdomMembershipRepository;
    private final KingdomRepository kingdomRepository;
    private final PlayerRepository playerRepository;
    private final ChallengeProgressRepository challengeProgressRepository;

    public List<KingdomMembership> getAllKingdomMemberships() {
        return kingdomMembershipRepository.findAll();
    }

    // Resolve a player's membership id in a kingdom — needed by the membership-based completion endpoints
    // (submit-image / submit-github / charity manual-donate). Read-only helper for the Postman/API flow.
    public Integer getMembershipId(Integer playerId, Integer kingdomId) {
        KingdomMembership m = kingdomMembershipRepository.findByPlayerIdAndKingdomId(playerId, kingdomId);
        if (m == null) {
            throw new ApiException("اللاعب ليس عضواً في هذه المملكة");
        }
        return m.getId();
    }

    public Kingdom joinToKingdom (Integer playerId, Integer kingdomId) {
        KingdomMembership kingdomMembership = new KingdomMembership();

        Player player = findPlayerById(playerId);

        Kingdom kingdom = findKingdomById(kingdomId);

        //check if user already in the kingdom
        KingdomMembership existing = kingdomMembershipRepository.findByPlayerAndKingdom(player, kingdom);

        if (existing != null) {
            throw new ApiException("لقد انضممت بالفعل إلى هذه المملكة");
        }

        //check player subscription
        boolean subscribed = player.getSubscription() != null;

        //if not Subscribed check number of join to kingdoms
        List<KingdomMembership> memberships = kingdomMembershipRepository.findByPlayer(player);

        int joinedKingdoms = memberships.size();

        if (!subscribed && joinedKingdoms >= 2) {
            throw new ApiException("بدون اشتراك يمكنك الانضمام إلى مملكتين فقط، اشترك للانضمام إلى المزيد.");
        }

        kingdomMembership.setTotalXP(0);
        kingdomMembership.setDivision(3);
        kingdomMembership.setStreak(0);
        kingdomMembership.setJoinedAt(LocalDateTime.now());

        kingdomMembership.setPlayer(player);
        kingdomMembership.setKingdom(kingdom);
        kingdomMembershipRepository.save(kingdomMembership);
        return kingdom;
    }

    public KingdomMembership getKingdomMembershipById(Integer id) {
        KingdomMembership kingdomMembership = kingdomMembershipRepository.findKingdomMembershipById(id);
        if (kingdomMembership == null) {
            throw new ApiException("عضوية المملكة غير موجودة");
        }
        return kingdomMembership;
    }

    public void updateKingdomMembership(Integer id, KingdomMembership kingdomMembership) {
        KingdomMembership oldKingdomMembership = getKingdomMembershipById(id);

        oldKingdomMembership.setTotalXP(kingdomMembership.getTotalXP());
        oldKingdomMembership.setDivision(kingdomMembership.getDivision());
        oldKingdomMembership.setStreak(kingdomMembership.getStreak());
        oldKingdomMembership.setJoinedAt(kingdomMembership.getJoinedAt());

        kingdomMembershipRepository.save(oldKingdomMembership);
    }

    public void leaveKingdom(Integer playerId, Integer kingdomId) {
        Player player = findPlayerById(playerId);
        Kingdom kingdom = findKingdomById(kingdomId);

        //check if user in the kingdom
        KingdomMembership kingdomMembership = playerInKingdom(player, kingdom);

        kingdomMembershipRepository.delete(kingdomMembership);
    }

    public String memberXp(Integer playerId, Integer kingdomId){
        Player player = findPlayerById(playerId);
        Kingdom kingdom = findKingdomById(kingdomId);

        KingdomMembership kingdomMembership = playerInKingdom(player, kingdom);

        return ""+kingdomMembership.getTotalXP();
    }

    public String memberDivision(Integer playerId, Integer kingdomId){
        Player player = findPlayerById(playerId);
        Kingdom kingdom = findKingdomById(kingdomId);

        KingdomMembership kingdomMembership = playerInKingdom(player, kingdom);

        return ""+kingdomMembership.getDivision();
    }

    public String memberStreak(Integer playerId, Integer kingdomId){
        Player player = findPlayerById(playerId);
        Kingdom kingdom = findKingdomById(kingdomId);

        KingdomMembership kingdomMembership = playerInKingdom(player, kingdom);

        return ""+kingdomMembership.getStreak();
    }

    public String memberPercentage(Integer playerId, Integer kingdomId) {
        Player player = findPlayerById(playerId);
        Kingdom kingdom = findKingdomById(kingdomId);

        KingdomMembership membership = playerInKingdom(player, kingdom);

        List<KingdomMembership> memberships = kingdomMembershipRepository.membersInDivision(kingdomId,membership.getDivision());

        // total XP of kingdom in division
        int totalXP = 0;

        for (KingdomMembership m : memberships) {
            totalXP += m.getTotalXP();

        }

        // player XP
        int playerXP = (membership.getTotalXP());

        double percentage = 0.0;

        if (totalXP != 0) {
            percentage = (playerXP * 100.0) / totalXP;
        }

        return String.format(java.util.Locale.US, "%.1f%%", percentage);    }

    public String memberRank(Integer playerId, Integer kingdomId) {

        Player player = findPlayerById(playerId);
        Kingdom kingdom = findKingdomById(kingdomId);

        KingdomMembership membership = playerInKingdom(player, kingdom);

        List<KingdomMembership> memberships = kingdomMembershipRepository.membersInDivision(membership.getKingdom().getId(), membership.getDivision());

        int rank = 1;

        for (KingdomMembership m : memberships) {
            if (m.getId().equals(membership.getId())) {
                return ""+rank;
            }
            rank++;
        }

        return ""+rank;
    }
    public String xpNeededForHigherRank(Integer playerId, Integer kingdomId) {

        Player player = findPlayerById(playerId);
        Kingdom kingdom = findKingdomById(kingdomId);

        KingdomMembership membership = playerInKingdom(player, kingdom);

        List<KingdomMembership> memberships = kingdomMembershipRepository.membersInDivision(kingdomId, membership.getDivision());

        int playerXP = membership.getTotalXP();

        Integer nextHigherXp = null;
        for (KingdomMembership m : memberships) {
            int xp = m.getTotalXP();
            if (xp > playerXP && (nextHigherXp == null || xp < nextHigherXp)) {
                nextHigherXp = xp;
            }
        }

        if (nextHigherXp == null) {
            return "أنت في المركز الأول";
        }

        int playersAboveNextTier = 0;
        for (KingdomMembership m : memberships) {
            if (m.getTotalXP() > nextHigherXp) {
                playersAboveNextTier++;
            }
        }

        int needed = (nextHigherXp + 1) - playerXP; // strictly overtake the tier
        int targetRank = playersAboveNextTier + 1;

        return "تحتاج " + needed + " نقطة للوصول إلى المركز #" + targetRank;
    }

    public String numberOfCompletedChallenges(Integer playerId, Integer kingdomId) {

        Player player = findPlayerById(playerId);
        Kingdom kingdom = findKingdomById(kingdomId);

        Integer completed = challengeProgressRepository.completedChallengesInKingdom(player.getId(), kingdom.getId());

        return ""+completed;
    }

    public String divisionProgress(Integer playerId, Integer kingdomId) {
        Player player = findPlayerById(playerId);
        Kingdom kingdom = findKingdomById(kingdomId);

        KingdomMembership membership = playerInKingdom(player, kingdom);

        int xp = membership.getTotalXP();
        int division = membership.getDivision();

        switch (division) {

            case 3:
                return xp + "/10000";

            case 2:
                return xp + "/25000";

            case 1:
                return "MAX";

            default:
                throw new ApiException("الدرجة غير صالحة");
        }
    }

    public Kingdom findKingdomById(Integer id) {
        Kingdom kingdom = kingdomRepository.findKingdomById(id);
        if (kingdom == null) {
            throw new ApiException("المملكة غير موجودة");
        }
        return kingdom;
    }

    public Player findPlayerById(Integer id) {
        Player player = playerRepository.findPlayerById(id);
        if (player == null) {
            throw new ApiException("اللاعب غير موجود");
        }
        return player;
    }

    public KingdomMembership playerInKingdom(Player player, Kingdom kingdom){
        KingdomMembership kingdomMembership = kingdomMembershipRepository.findByPlayerAndKingdom(player, kingdom);

        if (kingdomMembership == null) {
            throw new ApiException("أنت لست عضواً في هذه المملكة");
        }
        return kingdomMembership;
    }

    public KingdomMembership findKingdomMembershipById(Integer id) {

        KingdomMembership kingdomMembership =
                kingdomMembershipRepository.findKingdomMembershipById(id);

        if (kingdomMembership == null) {
            throw new ApiException("عضوية المملكة غير موجودة");
        }

        return kingdomMembership;
    }

    //MAYSUN ENDPOINT
    public void adjustXpByAdmin(Integer membershipId, Integer xp) {
        KingdomMembership membership = kingdomMembershipRepository.findKingdomMembershipById(membershipId);
        if (membership == null) {
            throw new ApiException("عضوية المملكة غير موجودة");
        }
        if (xp < 0) {
            throw new ApiException("لا يمكن أن تكون نقاط الخبرة قيمة سالبة");
        }
        membership.setTotalXP(xp);
        kingdomMembershipRepository.save(membership);
    }
}
