package com.kingdom.RepositoryTest;

import com.kingdom.Enums.Period;
import com.kingdom.Enums.UserRole;
import com.kingdom.Model.Kingdom;
import com.kingdom.Model.KingdomMembership;
import com.kingdom.Model.PeriodScore;
import com.kingdom.Model.Player;
import com.kingdom.Model.User;
import com.kingdom.Repository.KingdomMembershipRepository;
import com.kingdom.Repository.KingdomRepository;
import com.kingdom.Repository.PeriodScoreRepository;
import com.kingdom.Repository.PlayerRepository;
import com.kingdom.Repository.UserRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDateTime;

@ExtendWith(SpringExtension.class)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class PeriodScoreRepositoryTest {
    @Autowired
    PeriodScoreRepository periodScoreRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PlayerRepository playerRepository;

    @Autowired
    KingdomRepository kingdomRepository;

    @Autowired
    KingdomMembershipRepository kingdomMembershipRepository;

    KingdomMembership membership;
    PeriodScore activeScore;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setUsername("psRepoUser");
        user.setEmail("ps.repo.user@kingdoms.test");
        user.setPhoneNumber("+966500000003");
        user.setPasswordHash("hash");
        user.setRole(UserRole.PLAYER);
        user = userRepository.save(user);

        Player player = new Player();
        player.setDisplayName("PS Repo Player");
        player.setJoinedAt(LocalDateTime.now());
        player.setUser(user);
        player = playerRepository.save(player);

        Kingdom kingdom = new Kingdom();
        kingdom.setName("PS Repo Kingdom");
        kingdom.setDescription("desc");
        kingdom = kingdomRepository.save(kingdom);

        membership = new KingdomMembership();
        membership.setTotalXP(0);
        membership.setDivision(3);
        membership.setStreak(0);
        membership.setJoinedAt(LocalDateTime.now());
        membership.setPlayer(player);
        membership.setKingdom(kingdom);
        membership = kingdomMembershipRepository.save(membership);

        // active weekly record
        activeScore = new PeriodScore();
        activeScore.setPeriod(Period.WEEKLY);
        activeScore.setStartDate(LocalDateTime.now().minusDays(1));
        activeScore.setEndDate(LocalDateTime.now().plusDays(1));
        activeScore.setSeasonalXp(100);
        activeScore.setKingdomMembership(membership);
        activeScore = periodScoreRepository.save(activeScore);
    }

    @Test
    public void findActiveByMembershipAndPeriodTest() {
        PeriodScore found = periodScoreRepository.findActiveByMembershipAndPeriod(membership.getId(), Period.WEEKLY, LocalDateTime.now());
        Assertions.assertThat(found.getId()).isEqualTo(activeScore.getId());
    }
}
