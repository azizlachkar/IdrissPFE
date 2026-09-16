package com.cmrt.pfe.converters;

import com.cmrt.pfe.models.enums.Departement;
import org.springframework.core.convert.converter.Converter;

public class DepartementReadConverter implements Converter<String, Departement> {

    @Override
    public Departement convert(String source) {
        if (source == null) return null;
        String s = normalize(source);
        for (Departement d : Departement.values()) {
            if (d.name().equalsIgnoreCase(s)) return d;
        }
        try {
            return Departement.valueOf(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String normalize(String s) {
        // Trim, remove non-alphanumeric/underscore characters and upper-case
        return s.trim().replaceAll("[^A-Za-z0-9_]", "").toUpperCase();
    }
}
