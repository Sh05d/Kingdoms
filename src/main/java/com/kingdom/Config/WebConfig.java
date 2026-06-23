package com.kingdom.Config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the {@link OwnershipInterceptor} on all API routes so {playerId}-scoped endpoints are limited to the
 * authenticated caller's own account. Runs after Spring Security (so authentication/role checks happen first).
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final VerificationInterceptor verificationInterceptor;
    private final OwnershipInterceptor ownershipInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Verification runs first: an unverified player is stopped before any ownership / business logic.
        registry.addInterceptor(verificationInterceptor).addPathPatterns("/api/v1/**");
        registry.addInterceptor(ownershipInterceptor).addPathPatterns("/api/v1/**");
    }
}
