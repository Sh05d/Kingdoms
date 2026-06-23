package com.kingdom.Service;

import com.kingdom.API.ApiException;
import com.kingdom.DTO.IN.UserIn;
import com.kingdom.Enums.UserRole;
import com.kingdom.Model.Player;
import com.kingdom.Model.User;
import com.kingdom.Repository.PlayerRepository;
import com.kingdom.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;

/**
 * INTERIM self-service registration (Anas): create the account (User + Player) AND send the verification OTP in
 * one step — the "OTP on sign-up" Anas asked for. Registration does NOT auto-join any kingdom: the new player
 * joins them via /api/v1/kingdom-membership/join, which enforces the free 2-kingdom cap for non-subscribers.
 *
 * Lives in the auth-interim space (Maysun owns the final register). When her register lands, replace this and
 * simply call {@code otpService.sendOtp(phone)} after she creates the user. The password is interim-hashed
 * (SHA-256, not plaintext); the prepared BCryptPasswordEncoder in SecurityConfig takes over with the final
 * Spring Security flow.
 */
@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final UserRepository userRepository;
    private final PlayerRepository playerRepository;
    private final OtpService otpService;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    /** Create the User + Player, then send the OTP. Returns the new player. No kingdoms joined yet. */
    @Transactional
    public Player register(UserIn req) {
        String username = req.getUsername() == null ? "" : req.getUsername().trim();
        String phone = req.getPhoneNumber() == null ? "" : req.getPhoneNumber().trim();
        String email = req.getEmail() == null ? "" : req.getEmail().trim();

        // Clean uniqueness messages instead of a raw DB constraint error.
        if (userRepository.findUserByUsername(username) != null) {
            throw new ApiException("That username is already taken");
        }
        if (userRepository.findUserByPhoneNumber(phone) != null) {
            throw new ApiException("That phone number is already registered");
        }
        for (User existing : userRepository.findAll()) {
            if (email.equalsIgnoreCase(existing.getEmail())) {
                throw new ApiException("That email is already registered");
            }
        }

        // 1) account
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPhoneNumber(phone);
        user.setPasswordHash(passwordEncoder.encode(req.getPassword())); // BCrypt (Spring Security)
        user.setRole(UserRole.PLAYER);
        user = userRepository.save(user);

        // 2) player — joins NO kingdoms yet; the player picks them via /kingdom-membership/join (free 2-cap).
        Player player = new Player();
        player.setDisplayName(username);
        player.setInterests("fitness,charity,volunteering,reading,gaming,faith");
        player.setJoinedAt(LocalDateTime.now());
        player.setUser(user);
        player = playerRepository.save(player);

        // 3) the whole point: send the verification OTP on creation.
        otpService.sendOtp(phone);
        return player;
    }

    // INTERIM password hash (SHA-256 hex) — NOT plaintext. Replaced by the prepared BCryptPasswordEncoder
    // (SecurityConfig) when the final Spring Security auth is switched on.
    private String hash(String password) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((password == null ? "" : password).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new ApiException("Could not hash the password");
        }
    }
}
