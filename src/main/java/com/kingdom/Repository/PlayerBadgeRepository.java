package com.kingdom.Repository;

import com.kingdom.Model.Kingdom;
import com.kingdom.Model.Player;
import com.kingdom.Model.PlayerBadge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlayerBadgeRepository extends JpaRepository<PlayerBadge, Integer> {

    PlayerBadge findPlayerBadgeById(Integer id);

    @Query("select pb from PlayerBadge pb where pb.kingdomMembership.player=?1")
    List<PlayerBadge> findPlayerBadgeByPlayer(Player player);

    @Query("select pb from PlayerBadge pb where pb.kingdomMembership.player=?1 and pb.kingdomMembership.kingdom=?2")
    List<PlayerBadge> earnedKingdomBadges(Player playerId, Kingdom kingdomId);
}
