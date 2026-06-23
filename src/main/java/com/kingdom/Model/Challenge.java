package com.kingdom.Model;

import com.kingdom.Enums.ChallengeScope;
import com.kingdom.Enums.Difficulty;
import com.kingdom.Enums.Period;
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
public class Challenge {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Period period;

    @Column(columnDefinition = "datetime")
    private LocalDateTime startDate;

    @Column(columnDefinition = "datetime")
    private LocalDateTime endDate;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Difficulty difficulty;

    @Column(columnDefinition = "int not null")
    private Integer xpReward;

    // Nullable on purpose: the AI's targetValue can be absent/non-numeric (parseTarget -> null) and manual
    // challenges may omit it, so a NOT-NULL column would break those inserts. Both flows set it when they have it.
    private Integer targetValue;

    private String verificationSource;

    // Anas (Fitness/Charity): what the verification measures, e.g. "steps" or "charity_donation_sar".
    private String metricKey;

    // Shahad (Reading/Gaming): rule-based verification, e.g. BOOK_QUESTIONS / PLAYTIME / ACHIEVEMENT.
    private String verificationRule;

    private String verificationTarget;

    private String targetName;

    @Enumerated(EnumType.STRING)
    private ChallengeScope scope;

    @ManyToOne
    @JsonIgnore
    private Kingdom kingdom;

    @OneToMany(mappedBy = "challenge", cascade = CascadeType.ALL)
    private Set<Lobby> lobbies;

    @OneToMany(mappedBy = "challenge", cascade = CascadeType.ALL)
    private Set<ChallengeProgress> challengeProgresses;

    @OneToMany(mappedBy = "challenge", cascade = CascadeType.ALL)
    private Set<ChallengeQuestion> challengeQuestions;
}
