package com.kingdom.RepositoryTest;

import com.kingdom.Enums.Difficulty;
import com.kingdom.Enums.KingdomType;
import com.kingdom.Enums.Period;
import com.kingdom.Enums.ProgressStatus;
import com.kingdom.Enums.UserRole;
import com.kingdom.Model.Challenge;
import com.kingdom.Model.ChallengeProgress;
import com.kingdom.Model.Kingdom;
import com.kingdom.Model.KingdomMembership;
import com.kingdom.Model.Player;
import com.kingdom.Model.User;
import com.kingdom.Repository.ChallengeProgressRepository;
import com.kingdom.Repository.ChallengeRepository;
import com.kingdom.Repository.KingdomMembershipRepository;
import com.kingdom.Repository.KingdomRepository;
import com.kingdom.Repository.PlayerRepository;
import com.kingdom.Repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import com.kingdom.KingdomApplication;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@DataJpaTest
@ContextConfiguration(classes = KingdomApplication.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:anas_cp_test;DB_CLOSE_DELAY=-1;MODE=MySQL;NON_KEYWORDS=USER",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
public class ChallengeProgressRepositoryTest {

    @Autowired
    private ChallengeProgressRepository challengeProgressRepository;

    @Autowired
    private KingdomMembershipRepository kingdomMembershipRepository;

    @Autowired
    private ChallengeRepository challengeRepository;

    @Autowired
    private KingdomRepository kingdomRepository;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private UserRepository userRepository;

    private KingdomMembership membership;
    private Challenge challenge;
    private ChallengeProgress saved;

    @BeforeEach
    void setUp() {
        // User first (Player uses @MapsId on User).
        User user = new User();
        user.setEmail("progress-player@example.com");
        user.setUsername("progressplayer");
        user.setPhoneNumber("+966500000010");
        user.setPasswordHash("hash");
        user.setRole(UserRole.PLAYER);
        user.setBanned(false);
        user.setCreatedAt(LocalDateTime.now());
        user = userRepository.save(user);

        // Player: must set the User (drives the shared @MapsId id), displayName and joinedAt.
        Player player = new Player();
        player.setUser(user);
        player.setDisplayName("Progress Player");
        player.setJoinedAt(LocalDateTime.now());
        player = playerRepository.save(player);

        // Kingdom: name + description are nullable=false.
        Kingdom kingdom = new Kingdom();
        kingdom.setName("Sports Kingdom");
        kingdom.setDescription("Move and earn XP");
        kingdom.setType(KingdomType.SPORTS);
        kingdom = kingdomRepository.save(kingdom);

        // KingdomMembership: totalXP / division / streak are all "int not null".
        membership = new KingdomMembership();
        membership.setPlayer(player);
        membership.setKingdom(kingdom);
        membership.setTotalXP(0);
        membership.setDivision(3);
        membership.setStreak(0);
        membership.setJoinedAt(LocalDateTime.now());
        membership = kingdomMembershipRepository.save(membership);

        // Challenge: title, description, period, difficulty are nullable=false; xpReward is "int not null".
        challenge = new Challenge();
        challenge.setKingdom(kingdom);
        challenge.setTitle("Walk 10k steps");
        challenge.setDescription("Hit ten thousand steps today");
        challenge.setPeriod(Period.DAILY);
        challenge.setDifficulty(Difficulty.EASY);
        challenge.setXpReward(50);
        challenge = challengeRepository.save(challenge);

        // ChallengeProgress: status is nullable=false; link membership + challenge; set startAt.
        ChallengeProgress progress = new ChallengeProgress();
        progress.setKingdomMembership(membership);
        progress.setChallenge(challenge);
        progress.setStatus(ProgressStatus.JOINED);
        progress.setStartAt(LocalDateTime.now());
        saved = challengeProgressRepository.save(progress);
    }

    @Test
    void findByKingdomMembershipIdAndChallengeId_returnsTheSavedProgress() {
        ChallengeProgress found = challengeProgressRepository
                .findByKingdomMembershipIdAndChallengeId(membership.getId(), challenge.getId());

        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getStatus()).isEqualTo(ProgressStatus.JOINED);
        assertThat(found.getKingdomMembership().getId()).isEqualTo(membership.getId());
        assertThat(found.getChallenge().getId()).isEqualTo(challenge.getId());
    }

}
