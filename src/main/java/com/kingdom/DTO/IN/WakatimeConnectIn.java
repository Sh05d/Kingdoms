package com.kingdom.DTO.IN;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WakatimeConnectIn {
    @NotEmpty(message = "wakaTime API key is required")
    private String wakatimeApiKey;
}