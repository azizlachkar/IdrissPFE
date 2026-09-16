package com.cmrt.pfe.security;

import com.cmrt.pfe.exceptions.ApiException;
import com.cmrt.pfe.models.enums.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Authenticates every request from its bearer token and enforces {@link RequireRole}.
 * <p>
 * The API is stateless and consumed only by the Angular SPA, so a single interceptor is
 * clearer here than a full Spring Security filter chain: the rules live next to the
 * endpoints they protect.
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    /** Request attribute the argument resolver reads the principal back from. */
    public static final String PRINCIPAL_ATTRIBUTE = "cmrt.principal";

    private final JwtService jwtService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // CORS preflight carries no Authorization header by design.
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }

        AuthPrincipal principal = resolvePrincipal(request);
        if (principal != null) {
            request.setAttribute(PRINCIPAL_ATTRIBUTE, principal);
        }

        if (isPublic(method)) {
            return true;
        }
        if (principal == null) {
            throw ApiException.unauthorized("Authentification requise");
        }

        RequireRole requirement = method.getMethodAnnotation(RequireRole.class);
        if (requirement == null) {
            requirement = method.getBeanType().getAnnotation(RequireRole.class);
        }
        if (requirement != null && !principal.isAdmin() && !principal.hasAnyRole(requirement.value())) {
            throw ApiException.forbidden("Votre role ne permet pas cette action");
        }
        return true;
    }

    private boolean isPublic(HandlerMethod method) {
        return method.getMethodAnnotation(PublicEndpoint.class) != null
                || method.getBeanType().getAnnotation(PublicEndpoint.class) != null;
    }

    private AuthPrincipal resolvePrincipal(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        return jwtService.parse(header.substring(7).trim());
    }

    /** Convenience for services that need the caller without it being a method parameter. */
    public static AuthPrincipal current(HttpServletRequest request) {
        Object attr = request.getAttribute(PRINCIPAL_ATTRIBUTE);
        return attr instanceof AuthPrincipal p ? p : null;
    }

    /** Roles allowed to sign off engineering gates, used by several controllers. */
    public static final Role[] APPROVER_ROLES = {
            Role.ADMIN, Role.CHEF_PROJET, Role.METHODISTE,
            Role.QUALITICIEN, Role.RESPONSABLE_PRODUCTION, Role.CONTROLE_TECHNIQUE
    };
}
