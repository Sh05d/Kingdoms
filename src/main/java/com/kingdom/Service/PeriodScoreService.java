package com.kingdom.Service;

import com.kingdom.API.ApiException;
import com.kingdom.Enums.Period;
import com.kingdom.Model.Kingdom;
import com.kingdom.Model.KingdomMembership;
import com.kingdom.Model.PeriodScore;
import com.kingdom.Repository.KingdomMembershipRepository;
import com.kingdom.Repository.PeriodScoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PeriodScoreService {
    private final PeriodScoreRepository periodScoreRepository;
    private final KingdomMembershipRepository kingdomMembershipRepository;

    public List<PeriodScore> getAllPeriodScores() {
        return periodScoreRepository.findAll();
    }

    public void addPeriodScore(Integer kingdomMembershipId, PeriodScore periodScore) {

        KingdomMembership kingdomMembership = findKingdomMembershipById(kingdomMembershipId);

        PeriodScore newScore = new PeriodScore();

        newScore.setPeriod(periodScore.getPeriod());
        newScore.setStartDate(periodScore.getStartDate());
        newScore.setEndDate(periodScore.getEndDate());
        newScore.setSeasonalXp(0);
        newScore.setKingdomMembership(kingdomMembership);

        periodScoreRepository.save(newScore);
    }

    public PeriodScore getPeriodScoreById(Integer id) {

        PeriodScore periodScore = periodScoreRepository.findPeriodScoreById(id);

        if (periodScore == null) {
            throw new ApiException("Period score not found");
        }

        return periodScore;
    }

    public void updatePeriodScore(Integer id, PeriodScore periodScore) {

        PeriodScore old = getPeriodScoreById(id);

        old.setPeriod(periodScore.getPeriod());
        old.setStartDate(periodScore.getStartDate());
        old.setEndDate(periodScore.getEndDate());
        old.setSeasonalXp(periodScore.getSeasonalXp());
        old.setKingdomMembership(periodScore.getKingdomMembership());

        periodScoreRepository.save(old);
    }

    public void deletePeriodScore(Integer id) {

        PeriodScore periodScore = getPeriodScoreById(id);
        periodScoreRepository.delete(periodScore);
    }

    public KingdomMembership findKingdomMembershipById(Integer id) {
        KingdomMembership kingdomMembership = kingdomMembershipRepository.findKingdomMembershipById(id);
        if (kingdomMembership == null) {
            throw new ApiException("kingdomMembership not found");
        }
        return kingdomMembership;
    }

    public void addToPeriodScore(KingdomMembership membership, int earnedXp, Period period) {
        LocalDateTime now = LocalDateTime.now();
        PeriodScore active = periodScoreRepository.findActiveByMembershipAndPeriod(membership.getId(), period, now);
        if (active != null) {
            active.setSeasonalXp(active.getSeasonalXp() + earnedXp);
            periodScoreRepository.save(active);
        } else {
            PeriodScore newScore = new PeriodScore();
            newScore.setKingdomMembership(membership);
            newScore.setPeriod(period);
            newScore.setSeasonalXp(earnedXp);
            newScore.setStartDate(periodStart(period, now));
            newScore.setEndDate(periodEnd(period, now));
            periodScoreRepository.save(newScore);
        }
    }

    private LocalDateTime periodStart(Period period, LocalDateTime now) {
        if (period == Period.WEEKLY) {
            return now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)).atStartOfDay();
        }
        return now.toLocalDate().with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay();
    }

    private LocalDateTime periodEnd(Period period, LocalDateTime now) {
        if (period == Period.WEEKLY) {
            return now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY)).atTime(23, 59, 59);
        }
        return now.toLocalDate().with(TemporalAdjusters.lastDayOfMonth()).atTime(23, 59, 59);
    }
}
