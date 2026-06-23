package com.kingdom.Repository;

import com.kingdom.Model.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
@Repository

public interface PlayerRepository extends JpaRepository<Player, Integer> {

    Player findPlayerById(Integer id);
    boolean existsById(Integer userId);

    Player findPlayerByUserUsername(String userUsername);
}
