package com.kingdom.Config;

import com.kingdom.Enums.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * The authenticated principal for the app. Implements Spring's {@link UserDetails} (so HTTP Basic + the role
 * rules work) but also carries the app-level {@code id} and {@code role}. Controllers can inject it with
 * {@code @AuthenticationPrincipal CustomUserDetails me} and read {@code me.getId()} — identity comes from the
 * login, not from a path/body field. Because Player uses @MapsId on User, this id IS the player's id.
 */
public class CustomUserDetails implements UserDetails {

    private final Integer id;
    private final String username;
    private final String password;
    private final UserRole role;
    private final boolean enabled;
    private final boolean phoneVerified;

    public CustomUserDetails(Integer id, String username, String password, UserRole role, boolean enabled, boolean phoneVerified) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.role = role;
        this.enabled = enabled;
        this.phoneVerified = phoneVerified;
    }

    public Integer getId() {
        return id;
    }

    public UserRole getRole() {
        return role;
    }

    public boolean isPhoneVerified() {
        return phoneVerified;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return enabled;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
}
