package com.kingdom.Config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

/**
 * Ownership guard. Many endpoints take a {playerId} in the path; role rules alone let one logged-in PLAYER pass
 * another player's id and read/modify their data. This interceptor forces the {playerId} path variable to equal
 * the authenticated caller's own id, read straight from {@link CustomUserDetails} (no DB hit).
 *
 * - ADMINs bypass (they manage everyone).
 * - Paths with no {playerId} variable (or a non-numeric one) are left untouched — Spring Security still applies.
 * - Other player-identifying vars ({hostPlayerId}, {invitedPlayerId}, {kingdomMembershipId}, {userId}) are NOT
 *   enforced here — their "who is allowed" rule differs per endpoint and is checked in the service layer.
 */
@Component
public class OwnershipInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // Not one of our authenticated app users (anonymous / public endpoint) -> let Spring Security decide.
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails me)) {
            return true;
        }
        // ADMINs can act on any player.
        boolean isAdmin = me.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (isAdmin) {
            return true;
        }
        @SuppressWarnings("unchecked")
        Map<String, String> vars = (Map<String, String>)
                request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (vars == null || !vars.containsKey("playerId")) {
            return true; // nothing player-scoped to enforce
        }
        Integer pathPlayerId;
        try {
            pathPlayerId = Integer.valueOf(vars.get("playerId"));
        } catch (NumberFormatException e) {
            return true; // not a numeric id
        }
        if (me.getId() != null && me.getId().equals(pathPlayerId)) {
            return true; // acting on own account
        }
        response.sendError(HttpServletResponse.SC_FORBIDDEN,
                "You can only act on your own player account.");
        return false;
    }
}
