package com.kingdom.Repository;

import com.kingdom.Enums.BadgeType;
import com.kingdom.Model.Badge;
import com.kingdom.Model.KingdomMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BadgeRepository extends JpaRepository<Badge, Integer> {

    Badge findBadgeById(Integer id);

    @Query("select b from Badge b left join PlayerBadge pb on pb.badge = b and pb.kingdomMembership = ?3 where b.type = ?1 and b.requiredValue <= ?2 and pb.id is null order by b.requiredValue asc")
    List<Badge> findAwardableBadges(BadgeType type, Integer value, KingdomMembership membership);
}
