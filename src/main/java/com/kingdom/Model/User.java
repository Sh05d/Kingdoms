package com.kingdom.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.kingdom.Enums.UserRole;
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
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(unique = true, nullable = false)
    private String phoneNumber;

    @Column(nullable = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) // never expose the password hash in API responses
    private String passwordHash;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private UserRole role;

    private Boolean banned = false;

    // Phone-OTP gate: false until the player verifies via /auth/verify-otp; VerificationInterceptor blocks
    // unverified players from every non-/auth route. New registrations start false; admins/demo seed are true.
    private Boolean phoneVerified = false;

    @Column(columnDefinition = "datetime")
    private LocalDateTime createdAt;

    @OneToOne(mappedBy= "user", cascade= CascadeType.ALL, orphanRemoval= true)
    @JsonIgnore
    private Player player;
}
