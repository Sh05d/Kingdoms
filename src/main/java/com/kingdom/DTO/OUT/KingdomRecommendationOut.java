package com.kingdom.DTO.OUT;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class KingdomRecommendationOut {
    private Integer kingdomId;
    private String kingdomName;
    private String reason;
}
