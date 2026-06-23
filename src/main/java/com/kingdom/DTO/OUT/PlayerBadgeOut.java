package com.kingdom.DTO.OUT;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class PlayerBadgeOut {
    private String badgeName;
    private String badgeDescription;
    private String kingdomName;
    private LocalDateTime earnDate;
}
