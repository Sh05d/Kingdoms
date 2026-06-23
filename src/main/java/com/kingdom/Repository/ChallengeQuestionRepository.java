package com.kingdom.Repository;

import com.kingdom.Model.ChallengeQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChallengeQuestionRepository extends JpaRepository<ChallengeQuestion,Integer> {

    ChallengeQuestion findChallengeQuestionById(Integer id);

    @Query("select q from ChallengeQuestion q where q.challenge.id =?1 order by q.id asc")
    List<ChallengeQuestion> findQuestionsByChallengeId(Integer challengeId);

    // Maysun:
    List<ChallengeQuestion> findAllByChallengeId(Integer challengeId);
}
