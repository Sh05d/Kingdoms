package com.kingdom.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.kingdom.Enums.ConnectedProvider;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Stores OAuth tokens so the backend can auto-verify activity on the player's behalf.
 * UNIQUE(playerId, provider): one link per provider per player.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"player_id", "provider"}))
public class ConnectedAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Enumerated(EnumType.STRING)
    private ConnectedProvider provider;

    @Column(length = 2000)
    private String accessToken;

    @Column(length = 2000)
    private String refreshToken;

    private LocalDateTime expiresAt;

    private String externalUserId;

    private String status;

    private LocalDateTime connectedAt;

    @ManyToOne
    @JsonIgnore
    private Player player;
}
