package com.meridian.confirmiq.security;

import com.meridian.confirmiq.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** The authenticated principal, resolved from the JWT claims of the current request. */
public record CurrentUser(String username, String fullName, Role role, Long counterpartyId) {

    public static final CurrentUser SYSTEM = new CurrentUser("system", "ConfirmIQ System", null, null);

    public static CurrentUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        Number cpty = jwt.getClaim("cpty");
        return new CurrentUser(jwt.getSubject(), jwt.getClaimAsString("name"),
                Role.valueOf(jwt.getClaimAsString("role")), cpty == null ? null : cpty.longValue());
    }

    /** Current user, or {@link #SYSTEM} when running outside a request (scheduled jobs, seeding). */
    public static CurrentUser getOrSystem() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof Jwt ? get() : SYSTEM;
    }

    public String roleName() {
        return role == null ? "SYSTEM" : role.name();
    }
}
