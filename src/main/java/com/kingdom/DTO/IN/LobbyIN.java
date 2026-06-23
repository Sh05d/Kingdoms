package com.kingdom.DTO.IN;
import com.kingdom.Enums.LobbyVisibility;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;

import java.time.LocalDateTime;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class LobbyIN {
    @NotEmpty(message = "name cannot be empty")
    private String name;

    @NotEmpty(message = "description cannot be empty")
    private String description;

    @NotNull(message = "visibility is PRIVATE or PUBLIC")
  //  @Pattern(regexp = "^(PRIVATE|PUBLIC)$", message = "Invalid lobby type")
    private LobbyVisibility visibility;

    @NotNull(message = "start date is required")
    private LocalDateTime startsAt;
    @NotNull(message = "end date is required")
    private LocalDateTime endsAt;

}