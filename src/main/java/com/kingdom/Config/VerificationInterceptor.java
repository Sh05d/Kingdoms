package com.kingdom.Config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Phone-verification gate. After register, the account exists but the phone is NOT verified. A player must verify
 * (POST /api/v1/auth/verify-otp with the WhatsApp code) before they can do anything. This blocks an authenticated
 * but UNVERIFIED player from every API route except /api/v1/auth/** (register / send-otp / verify-otp), so the
 * verify call itself stays reachable. Admins (no OTP flow) and already-verified players pass through. Runs after
 * Spring Security, so the principal is already set.
 */
@Component
public class VerificationInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Auth endpoints must stay open so an unverified user CAN register + verify.
        if (request.getRequestURI().startsWith("/api/v1/auth/")) {
            return true;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // Anonymous / public endpoint (webhooks, oauth) -> let Spring Security decide.
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails me)) {
            return true;
        }
        boolean isAdmin = me.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (isAdmin || me.isPhoneVerified()) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"message\":\"يجب تأكيد رقم هاتفك أولاً عبر رمز التحقق على واتساب قبل استخدام الممالك.\"}");
        return false;
    }
}
