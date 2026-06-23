package com.kingdom.Model;

import com.kingdom.Enums.Division;
import com.kingdom.Enums.LobbyStatus;
import com.kingdom.Enums.LobbyVisibility;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Set;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class Lobby {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Integer hostPlayerId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private LobbyVisibility visibility;

    @Column(columnDefinition = "datetime")
    private LocalDateTime startsAt;

    @Column(columnDefinition = "datetime")
    private LocalDateTime endsAt;

    // PUBLIC lobbies are locked to the host's division in this kingdom — only same-division players may join.
    // Nullable: PRIVATE (invite-only) lobbies are not division-matched, so they leave this null.
    @Column
    private Integer division;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private LobbyStatus status;

    private Integer winnerPlayerId;


    @ManyToOne
    @JsonIgnore
    private Kingdom kingdom;

    @OneToMany(mappedBy = "lobby", cascade = CascadeType.ALL)
    private Set<LobbyMember> lobbyMembers;

    @OneToMany(mappedBy = "lobby", cascade = CascadeType.ALL)
    private Set<LobbyInvite> lobbyInvites;

    @ManyToOne
    @JsonIgnore
    private Challenge challenge;
}
