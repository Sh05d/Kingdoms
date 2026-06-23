package com.kingdom.Model;

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
public class KingdomMembership {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(columnDefinition = "int not null")
    private Integer totalXP;
    //Derived from xp: D1 >= 25,000 | D2 10,000-24,999 | D3 0-9,999.
    @Column(columnDefinition = "int not null")
    private Integer division;

    @Column(columnDefinition = "int not null")
    private Integer streak;

    @Column(columnDefinition = "datetime")
    private LocalDateTime joinedAt;

    @ManyToOne
    @JsonIgnore
    private Player player;

    @ManyToOne
    @JsonIgnore
    private Kingdom kingdom;

    @OneToMany(mappedBy = "kingdomMembership", cascade = CascadeType.ALL)
    private Set<PeriodScore> periodScores;

    @OneToMany(mappedBy = "kingdomMembership", cascade = CascadeType.ALL)
    private Set<ChallengeProgress> challengeProgresses;

    // Inverse of PlayerBadge.kingdomMembership — matches periodScores/challengeProgresses so that deleting a
    // membership (e.g. leaveKingdom) cascade-removes the badges earned in that kingdom instead of FK-failing.
    @OneToMany(mappedBy = "kingdomMembership", cascade = CascadeType.ALL)
    private Set<PlayerBadge> playerBadges;
}
