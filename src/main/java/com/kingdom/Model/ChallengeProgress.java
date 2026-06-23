package com.kingdom.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.kingdom.Enums.ProgressStatus;
import com.kingdom.Enums.RejectionReason;
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
public class ChallengeProgress {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ProgressStatus status;

    @Column(columnDefinition = "datetime")
    private LocalDateTime startAt;

    @Column(columnDefinition = "datetime")
    private LocalDateTime finishedAt;

    @Enumerated(EnumType.STRING)
    private RejectionReason rejectionReason;

    private Integer verifiedValue;

    private Integer currentQuestionIndex;

    @ManyToOne
    @JsonIgnore
    private KingdomMembership kingdomMembership;

    @ManyToOne
    @JsonIgnore
    private Challenge challenge;
}
