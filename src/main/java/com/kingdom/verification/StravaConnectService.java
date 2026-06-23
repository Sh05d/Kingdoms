package com.kingdom.verification;

import com.kingdom.API.ApiException;
import com.kingdom.Enums.ConnectedProvider;
import com.kingdom.Model.ConnectedAccount;
import com.kingdom.Model.Player;
import com.kingdom.Repository.ConnectedAccountRepository;
import com.kingdom.Repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-player Strava consent (OAuth). A player authorizes their OWN Strava; we store THEIR refresh token on a
 * ConnectedAccount(provider=STRAVA). At finish, {@link FitnessVerificationService} reads that player's token,
 * so each player is verified against their own activities — exactly the Neotek/PSU pattern used by Charity.
 *
 * Flow:
 *   1. GET /api/v1/verify/fitness/connect/{playerId}  -> {@link #authorizeUrlForPlayer} returns the Strava URL.
 *   2. Player approves on Strava; Strava redirects to /api/v1/verify/fitness/callback?code&state.
 *   3. {@link #handleCallback} swaps the code for tokens and saves the player's refresh token.
 *
 * NOTE (security, pre-final): {@code state} is just the playerId here. Before production this should be a
 * random, server-stored, single-use token to prevent CSRF / linking to the wrong player.
 */
@Service
@RequiredArgsConstructor
public class StravaConnectService {

    private final StravaClient stravaClient;
    private final PlayerRepository playerRepository;
    private final ConnectedAccountRepository connectedAccountRepository;

    /** Step 1: the Strava authorize URL for this player to open in a browser. */
    public String authorizeUrlForPlayer(Integer playerId) {
        if (!stravaClient.isConfigured()) {
            throw new ApiException("Strava is off or unconfigured (set strava.enabled + client-id/secret)");
        }
        Player player = playerRepository.findPlayerById(playerId);
        if (player == null) {
            throw new ApiException("Player not found");
        }
        return stravaClient.authorizeUrl(String.valueOf(playerId));
    }

    /** Step 3: swap the code for tokens and store the player's refresh token on their STRAVA ConnectedAccount. */
    public Map<String, Object> handleCallback(String code, String state, String error) {
        if (error != null && !error.isBlank()) {
            throw new ApiException("Strava authorization was denied: " + error);
        }
        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            throw new ApiException("Missing code/state from Strava");
        }
        Integer playerId;
        try {
            playerId = Integer.valueOf(state.trim());
        } catch (NumberFormatException e) {
            throw new ApiException("Invalid state");
        }
        Player player = playerRepository.findPlayerById(playerId);
        if (player == null) {
            throw new ApiException("Player not found for this authorization");
        }

        JsonNode tokens = stravaClient.exchangeAuthorizationCode(code);
        if (tokens == null) {
            throw new ApiException("Could not exchange the Strava code (off / unconfigured / code expired)");
        }
        String refresh = tokens.path("refresh_token").asText(null);
        if (refresh == null || refresh.isBlank()) {
            throw new ApiException("Strava did not return a refresh token");
        }
        String access = tokens.path("access_token").asText(null);
        long expiresAtEpoch = tokens.path("expires_at").asLong(0);
        String athleteId = tokens.path("athlete").path("id").asText(null);

        // One STRAVA link per player (re-connecting just refreshes the stored tokens).
        ConnectedAccount account = connectedAccountRepository.findByPlayer_IdAndProvider(playerId, ConnectedProvider.STRAVA);
        if (account == null) {
            account = new ConnectedAccount();
            account.setPlayer(player);
            account.setProvider(ConnectedProvider.STRAVA);
            account.setConnectedAt(LocalDateTime.now());
        }
        account.setRefreshToken(refresh);
        account.setAccessToken(access);
        if (expiresAtEpoch > 0) {
            account.setExpiresAt(LocalDateTime.ofInstant(Instant.ofEpochSecond(expiresAtEpoch), ZoneId.systemDefault()));
        }
        account.setExternalUserId(athleteId);
        account.setStatus("ACTIVE");
        connectedAccountRepository.save(account);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("connected", true);
        out.put("playerId", playerId);
        out.put("stravaAthleteId", athleteId);
        out.put("message", "Strava connected. Finish your SPORTS challenges and they'll be verified against your own activities.");
        return out;
    }
}
