package com.kingdom.Model;

import com.kingdom.Enums.InviteStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class LobbyInvite {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private InviteStatus status;

    @Column(columnDefinition = "datetime")
    private LocalDateTime sentAt;

    @Column(columnDefinition = "datetime")
    private LocalDateTime respondedAt;

    @Column(unique = true)
    private String inviteCode;
    @ManyToOne
    @JsonIgnore
    private Lobby lobby;

    @ManyToOne
    @JsonIgnore
    private Player invitedPlayer;
    
}
