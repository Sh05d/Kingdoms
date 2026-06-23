package com.kingdom.DTO.IN;

import com.kingdom.Enums.ConnectedProvider;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Request body to add / update / link a ConnectedAccount — the OAuth tokens the backend stores so it can
 * auto-verify a player's activity (e.g. Neotek Open Banking for Charity, Google Health for Fitness).
 * The server sets status + connectedAt itself; they are not taken from the client.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConnectedAccountIn {

    @NotNull(message = "provider is required")
    private ConnectedProvider provider;

    private String accessToken;

    private String refreshToken;

    private LocalDateTime expiresAt;

    private String externalUserId;
}
