package com.kingdom.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Real Spring Security (HTTP Basic, no JWT). Authentication = username + BCrypt password via
 * {@link CustomUserDetailsService}. Authorization is role-based (ROLE_PLAYER / ROLE_ADMIN):
 *   - PUBLIC: sign-up / OTP + external webhooks & OAuth callbacks (caller has no app account yet).
 *   - ADMIN: the admin panel + every privileged op (catalog, grants, manual verification, whole-table reads).
 *   - PLAYER: gameplay actions (join / finish / submit, lobby, invites, billing) — admins do not play.
 *   - AUTHENTICATED: shared reads (profiles, leaderboards, badges) for any logged-in player or admin.
 *
 * Ownership: endpoints that take a {playerId} in the path are additionally guarded by {@link OwnershipInterceptor},
 * which forces that id to equal the authenticated caller's own id (ADMINs bypass) — so a logged-in player cannot
 * read or modify another player's resource by changing the id.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // PUBLIC: sign-up/login flow + external webhooks & OAuth callbacks (no app user yet)
                        .requestMatchers(
                                "/api/v1/auth/**",
                                "/api/v1/payment/webhook",
                                "/api/v1/whatsapp/webhook",
                                "/api/v1/challenge-question/whatsapp/webhook",
                                "/api/v1/verify/volunteer/whatsapp",
                                "/api/v1/verify/fitness/callback",
                                "/oauth/**",
                                "/error"
                        ).permitAll()
                        // ADMIN: admin panel + n8n automation feeds (they return all players' phone/email)
                        .requestMatchers("/api/v1/user/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/lobby/winners", "/api/v1/player/churn-risk",
                                "/api/v1/player/weekly-reports", "/api/v1/subscription/subscriptions-expiring").hasRole("ADMIN")
                        // ADMIN: n8n push test (fires the backend -> n8n webhook on demand)
                        .requestMatchers("/api/v1/n8n/**").hasRole("ADMIN")
                        // ADMIN: whole-table "list everything" reads (would dump every other player's data)
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/challenge-progress/get",
                                "/api/v1/challenge-question/get",
                                "/api/v1/connecte/get",
                                "/api/v1/invite/get",
                                "/api/v1/kingdom-membership/get",
                                "/api/v1/lobby-member/get",
                                "/api/v1/lobby/get",
                                "/api/v1/period-score/get",
                                "/api/v1/player-badge/get",
                                "/api/v1/subscription/premium-active",
                                "/api/v1/verify/test-welcome-email").hasRole("ADMIN")
                        // ADMIN: creates + privileged actions (catalog, grants, manual verification, global jobs)
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/challenge/add/**",
                                "/api/v1/challenge/generate",
                                "/api/v1/kingdom/add",
                                "/api/v1/badge/add/**",
                                "/api/v1/challenge-progress/add",
                                "/api/v1/challenge-question/add",
                                "/api/v1/connecte/add",
                                "/api/v1/period-score/add/**",
                                "/api/v1/player-badge/add/**",
                                "/api/v1/subscription/add/**",
                                "/api/v1/verify/charity/donate",
                                "/api/v1/verify/charity/manual-donate/**",
                                "/api/v1/verify/streak/run",
                                "/api/v1/verify/streak/warn/**").hasRole("ADMIN")
                        // ADMIN: updates (catalog + records that drive XP / division / premium / badges)
                        .requestMatchers(HttpMethod.PUT,
                                "/api/v1/challenge/**",
                                "/api/v1/kingdom/**",
                                "/api/v1/badge/**",
                                "/api/v1/challenge-progress/update/**",
                                "/api/v1/challenge-question/update/**",
                                "/api/v1/connecte/update/**",
                                "/api/v1/invite/update-status/**",
                                "/api/v1/kingdom-membership/update/**",
                                "/api/v1/lobby-member/update-role/**",
                                "/api/v1/period-score/update/**",
                                "/api/v1/player-badge/update/**",
                                "/api/v1/subscription/update/**").hasRole("ADMIN")
                        // ADMIN: deletes (catalog + global records)
                        .requestMatchers(HttpMethod.DELETE,
                                "/api/v1/challenge/**",
                                "/api/v1/kingdom/**",
                                "/api/v1/badge/**",
                                "/api/v1/challenge-progress/delete/**",
                                "/api/v1/challenge-question/delete/**",
                                "/api/v1/connecte/delete/**",
                                "/api/v1/invite/delete/**",
                                "/api/v1/lobby-member/delete/**",
                                "/api/v1/lobby/delete/**",
                                "/api/v1/period-score/delete/**",
                                "/api/v1/player-badge/delete/**").hasRole("ADMIN")
                        // PLAYER-ONLY gameplay actions (admins run the system; they do not play)
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/challenge-progress/join/**",
                                "/api/v1/challenge-progress/finish/**",
                                "/api/v1/challenge-progress/cancel/**",
                                "/api/v1/challenge-progress/submit-image/**",
                                "/api/v1/challenge-progress/submit-github/**",
                                "/api/v1/kingdom-membership/join/**",
                                "/api/v1/kingdom/ai-recommendation/**",
                                "/api/v1/lobby/create/**",
                                "/api/v1/lobby/finish/**",
                                "/api/v1/lobby-member/join/**",
                                "/api/v1/lobby-member/join-private/**",
                                "/api/v1/invite/send/**",
                                "/api/v1/invite/resend/**",
                                "/api/v1/payment/checkout/**",
                                "/api/v1/payment/renew/**",
                                "/api/v1/connecte/link/**",
                                "/api/v1/player/connect-wakatime/**",
                                "/api/v1/player/ai-report/**",
                                "/api/v1/subscription/cancel/**",
                                "/api/v1/verify/volunteer/upload").hasRole("PLAYER")
                        .requestMatchers(HttpMethod.PUT,
                                "/api/v1/player/update/**",
                                "/api/v1/invite/reject/**").hasRole("PLAYER")
                        .requestMatchers(HttpMethod.DELETE,
                                "/api/v1/kingdom-membership/leave/**",
                                "/api/v1/lobby/cancel/**",
                                "/api/v1/lobby-member/leave/**",
                                "/api/v1/lobby-member/kick/**",
                                "/api/v1/connecte/disconnect/**",
                                "/api/v1/player/delete/**",
                                "/api/v1/subscription/delete/**").hasRole("PLAYER")
                        // EVERYTHING ELSE: any authenticated PLAYER or ADMIN.
                        // {playerId}-scoped paths are further restricted to the caller's own id by OwnershipInterceptor.
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
