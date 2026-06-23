package com.kingdom.DTO.IN;

import com.kingdom.Enums.BadgeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BadgeIN {
    @NotBlank(message = "badge name is required")
    private String name;

    @NotBlank(message = "badge description is required")
    private String description;

    @NotNull(message = "badge type is required")
    private BadgeType type;

    @NotNull(message = "required value is required")
    @PositiveOrZero(message = "required value must be zero or greater")
    private Integer requiredValue;
}
