package com.kingdom.Model;

import com.kingdom.Enums.KingdomType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class Kingdom {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(unique = true, nullable = false)
    @Enumerated(EnumType.STRING)
    private KingdomType type;

    @OneToMany(mappedBy = "kingdom", cascade = CascadeType.ALL)
    private Set<KingdomMembership> kingdomMemberships;

    @OneToMany(mappedBy = "kingdom", cascade = CascadeType.ALL)
    private Set<Challenge> challenges;

    @OneToMany(mappedBy = "kingdom", cascade = CascadeType.ALL)
    private Set<Lobby> lobbies;

    @OneToMany(mappedBy = "kingdom", cascade = CascadeType.ALL)
    private Set<Badge> badges;
}
