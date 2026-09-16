package com.cmrt.pfe.security;

import com.cmrt.pfe.models.enums.Role;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Restricts an endpoint (or a whole controller) to the listed roles. ADMIN always
 * passes. Enforced by {@link AuthInterceptor}.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {
    Role[] value();
}
