package com.cmrt.pfe.security;

import com.cmrt.pfe.models.enums.Role;

/**
 * The authenticated caller, rebuilt from the JWT on each request and injected into
 * controller methods with {@link CurrentUser}.
 */
public record AuthPrincipal(String userId, String email, String role, String name) {

    public Role roleEnum() {
        try {
            return role == null ? null : Role.valueOf(role);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public boolean isAdmin() {
        return Role.ADMIN == roleEnum();
    }

    public boolean hasAnyRole(Role... roles) {
        Role mine = roleEnum();
        if (mine == null) return false;
        for (Role r : roles) {
            if (r == mine) return true;
        }
        return false;
    }
}
