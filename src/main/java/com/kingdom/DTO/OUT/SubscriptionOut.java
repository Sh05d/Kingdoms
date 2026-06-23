package com.kingdom.DTO.OUT;

import com.kingdom.Enums.SubscriptionPlan;
import com.kingdom.Enums.SubscriptionStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionOut {

    private Integer id;

    private String lemonSubscriptionId;

    private SubscriptionPlan plan;

    private SubscriptionStatus status;

    private LocalDateTime startDate;

    private LocalDateTime expiresAt;

    private boolean autoRenew;
}