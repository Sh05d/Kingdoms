package com.kingdom.RepositoryTest;

import com.kingdom.KingdomApplication;
import com.kingdom.Enums.ConnectedProvider;
import com.kingdom.Enums.UserRole;
import com.kingdom.Model.ConnectedAccount;
import com.kingdom.Model.Player;
import com.kingdom.Model.User;
import com.kingdom.Repository.ConnectedAccountRepository;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@DataJpaTest
@ContextConfiguration(classes = KingdomApplication.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:anas_ca_test;DB_CLOSE_DELAY=-1;MODE=MySQL;NON_KEYWORDS=USER",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
public class ConnectedAccountRepositoryTest {

    @Autowired
    private ConnectedAccountRepository connectedAccountRepository;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private UserRepository userRepository;

    private Player player;
    private ConnectedAccount saved;

    @BeforeEach
    void setUp() {
        // User first (Player uses @MapsId on User).
        User user = new User();
        user.setEmail("neotek-link@example.com");
        user.setUsername("neoplayer");
        user.setPhoneNumber("+966500000001");
        user.setPasswordHash("hash");
        user.setRole(UserRole.PLAYER);
        user.setBanned(false);
        user.setCreatedAt(LocalDateTime.now());
        user = userRepository.save(user);

        // Player: must set the User (drives the shared @MapsId id), displayName and joinedAt.
        player = new Player();
        player.setUser(user);
        player.setDisplayName("Neo Player");
        player.setJoinedAt(LocalDateTime.now());
        player = playerRepository.save(player);

        // ConnectedAccount linked to the player via the NEOTEK provider.
        ConnectedAccount account = new ConnectedAccount();
        account.setPlayer(player);
        account.setProvider(ConnectedProvider.NEOTEK);
        account.setExternalUserId("psu-neotek-123");
        account.setStatus("ACTIVE");
        account.setAccessToken("access-token");
        account.setConnectedAt(LocalDateTime.now());
        saved = connectedAccountRepository.save(account);
    }

    @Test
    void findAllByPlayer_Id_returnsTheSavedAccount() {
        List<ConnectedAccount> found = connectedAccountRepository.findAllByPlayer_Id(player.getId());

        assertThat(found).hasSize(1);
        assertThat(found.get(0).getId()).isEqualTo(saved.getId());
        assertThat(found.get(0).getProvider()).isEqualTo(ConnectedProvider.NEOTEK);
        assertThat(found.get(0).getExternalUserId()).isEqualTo("psu-neotek-123");
    }

}
