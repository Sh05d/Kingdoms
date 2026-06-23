package com.kingdom.DTO.IN;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class KingdomRecommendationIn {
    @NotEmpty(message = "Interest is required")
    private String interests;
}
