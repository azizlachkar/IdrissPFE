package com.cmrt.pfe.converters;

import com.cmrt.pfe.models.enums.Role;
import org.springframework.core.convert.converter.Converter;

public class RoleReadConverter implements Converter<String, Role> {

    @Override
    public Role convert(String source) {
        if (source == null) return null;
        String s = normalize(source);
        for (Role r : Role.values()) {
            if (r.name().equalsIgnoreCase(s)) return r;
        }
        try {
            return Role.valueOf(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String normalize(String s) {
        return s.trim().replaceAll("[^A-Za-z0-9_]", "").toUpperCase();
    }
}
