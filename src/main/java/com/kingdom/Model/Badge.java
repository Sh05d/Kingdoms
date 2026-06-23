package com.kingdom.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.kingdom.Enums.BadgeType;
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
public class Badge {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    private BadgeType type;

    @Column(columnDefinition = "int not null")
    private Integer requiredValue;

    @ManyToOne
    @JsonIgnore
    private Kingdom kingdom;

    @OneToMany(mappedBy = "badge", cascade = CascadeType.ALL)
    private Set<PlayerBadge> playerBadges;
}