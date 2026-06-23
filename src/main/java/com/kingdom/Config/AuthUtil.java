package com.kingdom.Config;

import com.kingdom.API.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Service-layer ownership helper. Reads the authenticated caller straight from the SecurityContext
 * ({@link CustomUserDetails}) so a method can require that an id it was handed (a hostPlayerId, or a player id
 * derived from the loaded entity) actually belongs to the caller. ADMINs bypass. Used for endpoints whose
 * owner id is NOT a {playerId} path variable (so {@link OwnershipInterceptor} cannot cover them):
 * lobby host actions, invites, and a challenge run's owning player.
 */
public final class AuthUtil {

    private AuthUtil() {
    }

    /** The authenticated caller's id (== their playerId via @MapsId), or null if not an app user. */
    public static Integer currentUserId() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof CustomUserDetails me) {
            return me.getId();
        }
        return null;
    }

    public static boolean isAdmin() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getAuthorities().stream()
                .anyMatch(x -> "ROLE_ADMIN".equals(x.getAuthority()));
    }

    /** Allow only if the caller is an ADMIN or is acting on their own id; otherwise block the action. */
    public static void requireSelfOrAdmin(Integer ownerPlayerId) {
        if (isAdmin()) {
            return;
        }
        Integer me = currentUserId();
        if (me == null || !me.equals(ownerPlayerId)) {
            throw new ApiException("You can only perform this action on your own resources");
        }
    }
}
