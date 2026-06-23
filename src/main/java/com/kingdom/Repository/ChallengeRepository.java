package com.kingdom.Repository;

import com.kingdom.Enums.Difficulty;
import com.kingdom.Enums.Period;
import com.kingdom.Model.Challenge;
import com.kingdom.Model.Kingdom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ChallengeRepository extends JpaRepository<Challenge, Integer> {

    Challenge findChallengeById(Integer id);

    List<Challenge> findAllByKingdom_Id(Integer kingdomId);

    List<Challenge> findAllByDifficulty(Difficulty difficulty);

    List<Challenge> findAllByPeriod(Period period);

    List<Challenge> findByKingdomAndEndDateAfter(Kingdom kingdom, LocalDateTime now);
}
