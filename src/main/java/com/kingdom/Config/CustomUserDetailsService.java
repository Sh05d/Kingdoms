package com.kingdom.Config;

import com.kingdom.Model.User;
import com.kingdom.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads the app's User by username for Spring Security (HTTP Basic). The stored passwordHash MUST be BCrypt
 * (RegistrationService + AdminService + DemoSeeder all encode with the BCrypt PasswordEncoder). The role maps
 * to a Spring authority "ROLE_PLAYER" / "ROLE_ADMIN"; a banned user is marked disabled so they cannot log in.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) {
        User user = userRepository.findUserByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("No user with username: " + username);
        }
        boolean enabled = !Boolean.TRUE.equals(user.getBanned());
        boolean phoneVerified = Boolean.TRUE.equals(user.getPhoneVerified());
        return new CustomUserDetails(
                user.getId(), user.getUsername(), user.getPasswordHash(), user.getRole(), enabled, phoneVerified);
    }
}
