package com.kingdom.Model;

import com.kingdom.Enums.MemberRole;
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
public class LobbyMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Enumerated(EnumType.STRING)
    private MemberRole role;
    
    @Column(columnDefinition = "datetime")
    private LocalDateTime joinedAt;

    @ManyToOne
    @JsonIgnore
    private Lobby lobby;

    @ManyToOne
    @JsonIgnore
    private Player player;
}
