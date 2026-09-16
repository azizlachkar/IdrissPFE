package com.cmrt.pfe.models;

import com.cmrt.pfe.models.enums.Departement;
import com.cmrt.pfe.models.enums.Poste;
import com.cmrt.pfe.models.enums.Role;
import com.cmrt.pfe.models.enums.ServiceUnit;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    private String id;

    private String matricule;

    private String nom;

    private String prenom;

    @Indexed(unique = true)
    private String email;

    @JsonIgnore
    private String password;

    private String telephone;

    private Departement departement;

    private Poste poste;

    private ServiceUnit serviceUnit;

    private Role role;

    @Builder.Default
    private Boolean enabled = false;

    @Builder.Default
    private Boolean active = true;

    @JsonIgnore
    private String verificationToken;

    @JsonIgnore
    private String resetToken;

    @JsonIgnore
    private LocalDateTime resetTokenExpiry;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime lastLoginAt;

    public String getFullName() {
        String p = prenom == null ? "" : prenom;
        String n = nom == null ? "" : nom;
        return (p + " " + n).trim();
    }

    public String getInitials() {
        StringBuilder sb = new StringBuilder();
        if (prenom != null && !prenom.isBlank()) sb.append(Character.toUpperCase(prenom.charAt(0)));
        if (nom != null && !nom.isBlank()) sb.append(Character.toUpperCase(nom.charAt(0)));
        return sb.toString();
    }
}
