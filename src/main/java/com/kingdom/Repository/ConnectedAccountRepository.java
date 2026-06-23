package com.kingdom.Repository;

import com.kingdom.Enums.ConnectedProvider;
import com.kingdom.Model.ConnectedAccount;
import com.kingdom.Model.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
@Repository
public interface ConnectedAccountRepository extends JpaRepository<ConnectedAccount, Integer> {

    ConnectedAccount findConnectedAccountById(Integer id);

    List<ConnectedAccount> findAllByPlayer_Id(Integer playerId);

    ConnectedAccount findByPlayer_IdAndProvider(Integer playerId, ConnectedProvider provider);

    ConnectedAccount findByPlayerAndProvider(Player player, ConnectedProvider provider);
}
