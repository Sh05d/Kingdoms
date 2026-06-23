package com.kingdom.RepositoryTest;

import com.kingdom.Enums.BadgeType;
import com.kingdom.Enums.UserRole;
import com.kingdom.Model.Badge;
import com.kingdom.Model.Kingdom;
import com.kingdom.Model.KingdomMembership;
import com.kingdom.Model.Player;
import com.kingdom.Model.PlayerBadge;
import com.kingdom.Model.User;
import com.kingdom.Repository.BadgeRepository;
import com.kingdom.Repository.KingdomMembershipRepository;
import com.kingdom.Repository.KingdomRepository;
import com.kingdom.Repository.PlayerBadgeRepository;
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
import java.util.List;

@ExtendWith(SpringExtension.class)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class PlayerBadgeRepositoryTest {
    @Autowired
    PlayerBadgeRepository playerBadgeRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PlayerRepository playerRepository;

    @Autowired
    KingdomRepository kingdomRepository;

    @Autowired
    KingdomMembershipRepository kingdomMembershipRepository;

    @Autowired
    BadgeRepository badgeRepository;

    Player player;
    PlayerBadge playerBadge;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setUsername("pbRepoUser");
        user.setEmail("pb.repo.user@kingdoms.test");
        user.setPhoneNumber("+966500000002");
        user.setPasswordHash("hash");
        user.setRole(UserRole.PLAYER);
        user = userRepository.save(user);

        player = new Player();
        player.setDisplayName("PB Repo Player");
        player.setJoinedAt(LocalDateTime.now());
        player.setUser(user);
        player = playerRepository.save(player);

        Kingdom kingdom = new Kingdom();
        kingdom.setName("PB Repo Kingdom");
        kingdom.setDescription("desc");
        kingdom = kingdomRepository.save(kingdom);

        KingdomMembership membership = new KingdomMembership();
        membership.setTotalXP(0);
        membership.setDivision(3);
        membership.setStreak(0);
        membership.setJoinedAt(LocalDateTime.now());
        membership.setPlayer(player);
        membership.setKingdom(kingdom);
        membership = kingdomMembershipRepository.save(membership);

        Badge badge = new Badge();
        badge.setName("First XP");
        badge.setDescription("Earned first XP");
        badge.setType(BadgeType.XP);
        badge.setRequiredValue(0);
        badge.setKingdom(kingdom);
        badge = badgeRepository.save(badge);

        playerBadge = new PlayerBadge();
        playerBadge.setBadge(badge);
        playerBadge.setKingdomMembership(membership);
        playerBadge.setEarnedAt(LocalDateTime.now());
        playerBadge = playerBadgeRepository.save(playerBadge);
    }

    @Test
    public void findPlayerBadgeByPlayerTest() {
        List<PlayerBadge> found = playerBadgeRepository.findPlayerBadgeByPlayer(player);
        Assertions.assertThat(found.get(0).getId()).isEqualTo(playerBadge.getId());
    }
}
