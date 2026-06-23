package com.kingdom.Service;

import com.kingdom.API.ApiException;
import com.kingdom.DTO.IN.ConnectedAccountIn;
import com.kingdom.Enums.ConnectedProvider;
import com.kingdom.Model.ConnectedAccount;
import com.kingdom.Model.Player;
import com.kingdom.Repository.ConnectedAccountRepository;
import com.kingdom.Repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ConnectedAccountService {

    private final ConnectedAccountRepository connectedAccountRepository;
    // TEAMMATE (User/Kingdom flow owns Player): used read-only to attach a linked account to its player.
    private final PlayerRepository playerRepository;

    public List<ConnectedAccount> getAllConnectedAccounts() {
        return connectedAccountRepository.findAll();
    }

    public void addConnectedAccount(ConnectedAccountIn in) {
        ConnectedAccount account = new ConnectedAccount();
        account.setProvider(in.getProvider());
        account.setAccessToken(in.getAccessToken());
        account.setRefreshToken(in.getRefreshToken());
        account.setExpiresAt(in.getExpiresAt());
        account.setExternalUserId(in.getExternalUserId());
        connectedAccountRepository.save(account);
    }

    public ConnectedAccount getConnectedAccountById(Integer id) {
        ConnectedAccount account = connectedAccountRepository.findConnectedAccountById(id);
        if (account == null) {
            throw new ApiException("Connected account not found");
        }
        return account;
    }

    // Update the editable fields. status + connectedAt are server-managed, so they are NOT taken from the client.
    public void updateConnectedAccount(Integer id, ConnectedAccountIn in) {
        ConnectedAccount oldAccount = connectedAccountRepository.findConnectedAccountById(id);
        if (oldAccount == null) {
            throw new ApiException("Connected account not found");
        }

        oldAccount.setProvider(in.getProvider());
        oldAccount.setAccessToken(in.getAccessToken());
        oldAccount.setRefreshToken(in.getRefreshToken());
        oldAccount.setExpiresAt(in.getExpiresAt());
        oldAccount.setExternalUserId(in.getExternalUserId());

        connectedAccountRepository.save(oldAccount);
    }

    public void deleteConnectedAccount(Integer id) {
        ConnectedAccount account = connectedAccountRepository.findConnectedAccountById(id);
        if (account == null) {
            throw new ApiException("Connected account not found");
        }

        connectedAccountRepository.delete(account);
    }

    // ---- Flow endpoints (player-scoped) ----

    public List<ConnectedAccount> getAccountsByPlayer(Integer playerId) {
        return connectedAccountRepository.findAllByPlayer_Id(playerId);
    }

    // Link (or re-link) a provider for a player. One link per provider per player, so re-linking the same
    // provider updates the existing row instead of creating a duplicate.
    public void linkAccount(Integer playerId, ConnectedAccountIn in) {
        Player player = playerRepository.findPlayerById(playerId);
        if (player == null) {
            throw new ApiException("Player not found");
        }

        ConnectedAccount existing =
                connectedAccountRepository.findByPlayer_IdAndProvider(playerId, in.getProvider());
        if (existing != null) {
            existing.setAccessToken(in.getAccessToken());
            existing.setRefreshToken(in.getRefreshToken());
            existing.setExpiresAt(in.getExpiresAt());
            existing.setExternalUserId(in.getExternalUserId());
            existing.setStatus("ACTIVE");
            existing.setConnectedAt(LocalDateTime.now());
            connectedAccountRepository.save(existing);
            return;
        }

        ConnectedAccount account = new ConnectedAccount();
        account.setProvider(in.getProvider());
        account.setAccessToken(in.getAccessToken());
        account.setRefreshToken(in.getRefreshToken());
        account.setExpiresAt(in.getExpiresAt());
        account.setExternalUserId(in.getExternalUserId());
        account.setPlayer(player);
        account.setStatus("ACTIVE");
        account.setConnectedAt(LocalDateTime.now());
        connectedAccountRepository.save(account);
    }

    public void disconnect(Integer playerId, ConnectedProvider provider) {
        ConnectedAccount account = connectedAccountRepository.findByPlayer_IdAndProvider(playerId, provider);
        if (account == null) {
            throw new ApiException("Connected account not found for this provider");
        }
        connectedAccountRepository.delete(account);
    }

    public String getSteamId(Player player) {

        ConnectedAccount account =
                connectedAccountRepository.findByPlayerAndProvider(
                        player,
                        ConnectedProvider.STEAM
                );

        if (account == null) {
            throw new ApiException("Steam account not connected");
        }

        return account.getExternalUserId();
    }
}
