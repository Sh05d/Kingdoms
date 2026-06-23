package com.kingdom.Repository;

import com.kingdom.Enums.Period;
import com.kingdom.Model.PeriodScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;


@Repository
public interface PeriodScoreRepository extends JpaRepository<PeriodScore, Integer> {
    PeriodScore findPeriodScoreById(Integer id);

    @Query("select p from PeriodScore p where p.kingdomMembership.id = ?1 and p.period = ?2 and p.endDate >= ?3")
    PeriodScore findActiveByMembershipAndPeriod(Integer membershipId, Period period, LocalDateTime now);

    @Query("select ps from PeriodScore ps where ps.kingdomMembership.kingdom.id = ?1 and ps.period = ?2 and ps.startDate <= ?3 and ps.endDate >= ?3 order by ps.seasonalXp desc")
    List<PeriodScore> leaderboardByPeriod(Integer kingdomId, Period period, LocalDateTime now);

    @Query("select ps from PeriodScore ps where ps.kingdomMembership.kingdom.id = ?1 and ps.kingdomMembership.division = ?2 and ps.period = ?3 and ps.startDate <= ?4 and ps.endDate >= ?4 order by ps.seasonalXp desc")
    List<PeriodScore> leaderboardByPeriodAndDivision(Integer kingdomId, Integer division, Period period, LocalDateTime now);
}
