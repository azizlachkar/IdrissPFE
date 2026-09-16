package com.cmrt.pfe.dto;

import com.cmrt.pfe.models.User;
import com.cmrt.pfe.models.enums.Departement;
import com.cmrt.pfe.models.enums.Poste;
import com.cmrt.pfe.models.enums.Role;
import com.cmrt.pfe.models.enums.ServiceUnit;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Request and response shapes for the authentication endpoints. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "L'email est obligatoire") @Email(message = "Email invalide") String email,
            @NotBlank(message = "Le mot de passe est obligatoire") String password) {
    }

    public record SignupRequest(
            @NotBlank(message = "Le nom est obligatoire") String nom,
            @NotBlank(message = "Le prenom est obligatoire") String prenom,
            @NotBlank(message = "L'email est obligatoire") @Email(message = "Email invalide") String email,
            @NotBlank(message = "Le mot de passe est obligatoire") String password,
            String matricule,
            String telephone,
            Departement departement,
            Poste poste,
            ServiceUnit serviceUnit,
            Role role) {
    }

    /** Token plus the profile the SPA needs to render its shell. */
    public record AuthResponse(String token, long expiresInMs, UserProfile user) {
    }

    public record UserProfile(
            String id,
            String matricule,
            String nom,
            String prenom,
            String fullName,
            String initials,
            String email,
            String telephone,
            Departement departement,
            Poste poste,
            ServiceUnit serviceUnit,
            Role role,
            Boolean enabled,
            Boolean active) {

        public static UserProfile from(User user) {
            return new UserProfile(
                    user.getId(), user.getMatricule(), user.getNom(), user.getPrenom(),
                    user.getFullName(), user.getInitials(), user.getEmail(), user.getTelephone(),
                    user.getDepartement(), user.getPoste(), user.getServiceUnit(), user.getRole(),
                    user.getEnabled(), user.getActive());
        }
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank String newPassword) {
    }

    public record ForgotPasswordRequest(@NotBlank @Email String email) {
    }

    public record ResetPasswordRequest(@NotBlank String token, @NotBlank String newPassword) {
    }

    public record TokenRequest(@NotBlank String token) {
    }
}
