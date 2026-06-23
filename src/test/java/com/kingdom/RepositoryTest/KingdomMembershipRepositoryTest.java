package com.kingdom.RepositoryTest;

import org.assertj.core.api.Assertions;
import com.kingdom.Enums.UserRole;
import com.kingdom.Model.Kingdom;
import com.kingdom.Model.KingdomMembership;
import com.kingdom.Model.Player;
import com.kingdom.Model.User;
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
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDateTime;

@ExtendWith(SpringExtension.class)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class KingdomMembershipRepositoryTest {
    @Autowired
    KingdomMembershipRepository kingdomMembershipRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PlayerRepository playerRepository;

    @Autowired
    KingdomRepository kingdomRepository;

    Player player;
    Kingdom kingdom;
    KingdomMembership membership;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setUsername("kmRepoUser");
        user.setEmail("km.repo.user@kingdoms.test");
        user.setPhoneNumber("+966500000001");
        user.setPasswordHash("hash");
        user.setRole(UserRole.PLAYER);
        user = userRepository.save(user);

        player = new Player();
        player.setDisplayName("KM Repo Player");
        player.setJoinedAt(LocalDateTime.now());
        player.setUser(user);
        player = playerRepository.save(player);

        kingdom = new Kingdom();
        kingdom.setName("KM Repo Kingdom");
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
    }

    @Test
    public void findByPlayerAndKingdomTest() {
        KingdomMembership found = kingdomMembershipRepository.findByPlayerAndKingdom(player, kingdom);
        Assertions.assertThat(found.getId()).isEqualTo(membership.getId());
    }
}
