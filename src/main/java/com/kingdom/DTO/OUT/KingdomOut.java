package com.kingdom.DTO.OUT;

import com.kingdom.Enums.KingdomType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class KingdomOut {
    private String name;
    private String description;
    private KingdomType type;
}
