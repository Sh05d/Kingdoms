package com.kingdom.Model;

import com.kingdom.Enums.Period;
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
public class PeriodScore {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Period period;

    @Column(columnDefinition = "datetime")
    private LocalDateTime startDate;

    @Column(columnDefinition = "datetime")
    private LocalDateTime endDate;

    @Column(columnDefinition = "int not null")
    private Integer seasonalXp;

    @ManyToOne
    @JsonIgnore
    @JoinColumn(name = "kingdom_membership_id")
    private KingdomMembership kingdomMembership;
}
