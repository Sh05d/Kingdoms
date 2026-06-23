package com.kingdom.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.kingdom.Enums.SubscriptionPlan;
import com.kingdom.Enums.SubscriptionStatus;
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
public class Subscription {
    @Id
    private Integer id;

    private String lemonSubscriptionId;

    @Column(columnDefinition = "datetime")
    private LocalDateTime startDate;

    @Enumerated(EnumType.STRING)
    private SubscriptionStatus status;

    @Column(columnDefinition = "datetime")
    private LocalDateTime expiresAt;

    @Column(columnDefinition = "boolean not null")
    private boolean autoRenew;
    @Enumerated(EnumType.STRING)
    private SubscriptionPlan plan;

    @OneToOne
    @MapsId
    @JoinColumn(name= "id")
    @JsonIgnore
    private Player player;
}
