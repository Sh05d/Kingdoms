package com.kingdom.Repository;

import com.kingdom.Model.Kingdom;
import com.kingdom.Model.KingdomMembership;
import com.kingdom.Model.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface KingdomMembershipRepository extends JpaRepository<KingdomMembership, Integer> {

    KingdomMembership findKingdomMembershipById(Integer id);

    // Added by Anas (Challenge flow): resolve a player's membership in a specific kingdom when joining a challenge.
    KingdomMembership findByPlayer_IdAndKingdom_Id(Integer playerId, Integer kingdomId);

    List<KingdomMembership> findByPlayer(Player player);

    KingdomMembership findByPlayerAndKingdom(Player player, Kingdom kingdom);

    @Query("select m from KingdomMembership m where m.kingdom.id=?1 and m.division=?2 order by m.totalXP desc")
    List<KingdomMembership> membersInDivision(Integer kingdomId, Integer division);

    // Added by Maysun: same lookup as Anas's findByPlayer_IdAndKingdom_Id, different name (Spring Data resolves both identically).
    KingdomMembership findByPlayerIdAndKingdomId(Integer playerId, Integer kingdomId);

    @Query("select km from KingdomMembership km where km.kingdom.id = ?1 and km.division = ?2 order by km.totalXP desc ")
    List<KingdomMembership> leaderboardByDivision(Integer kingdomId, Integer division);
    List<KingdomMembership> findAllByPlayerId(Integer playerId);

}
