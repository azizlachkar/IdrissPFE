package com.cmrt.pfe.services;

import com.cmrt.pfe.dto.AuthDtos;
import com.cmrt.pfe.exceptions.ApiException;
import com.cmrt.pfe.models.User;
import com.cmrt.pfe.models.enums.AuditAction;
import com.cmrt.pfe.models.enums.Role;
import com.cmrt.pfe.repositories.UserRepository;
import com.cmrt.pfe.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final JwtService jwtService;
    private final AuditService auditService;

    /** At least 12 characters with a letter, a digit and a symbol. */
    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{12,}$");

    private static final String PASSWORD_RULE =
            "Le mot de passe doit comporter au moins 12 caracteres et contenir une lettre, un chiffre et un symbole.";

    public AuthDtos.UserProfile register(AuthDtos.SignupRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.badRequest("Un compte existe deja avec cet email");
        }
        validatePassword(request.password());

        User user = User.builder()
                .nom(request.nom().trim())
                .prenom(request.prenom().trim())
                .email(email)
                .matricule(request.matricule())
                .telephone(request.telephone())
                .departement(request.departement())
                .poste(request.poste())
                .serviceUnit(request.serviceUnit())
                // Self-service signup never grants ADMIN; an administrator promotes afterwards.
                .role(request.role() == null || request.role() == Role.ADMIN ? Role.VIEWER : request.role())
                .password(BCrypt.hashpw(request.password(), BCrypt.gensalt()))
                .enabled(false)
                .active(true)
                .verificationToken(UUID.randomUUID().toString())
                .build();

        User saved = userRepository.save(user);
        emailService.sendVerificationEmail(saved.getEmail(), saved.getPrenom(), saved.getNom(), saved.getVerificationToken());
        auditService.record(null, AuditAction.CREATE, "User", saved.getId(), null,
                "Inscription de " + saved.getFullName());
        return AuthDtos.UserProfile.from(saved);
    }

    public AuthDtos.AuthResponse authenticate(AuthDtos.LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                // Same message for unknown email and wrong password: don't reveal which accounts exist.
                .orElseThrow(() -> ApiException.unauthorized("Email ou mot de passe incorrect"));

        if (user.getPassword() == null || !BCrypt.checkpw(request.password(), user.getPassword())) {
            throw ApiException.unauthorized("Email ou mot de passe incorrect");
        }
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw ApiException.forbidden("Veuillez verifier votre adresse email avant de vous connecter");
        }
        if (!Boolean.TRUE.equals(user.getActive())) {
            throw ApiException.forbidden("Votre compte a ete desactive. Contactez un administrateur.");
        }

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        String token = jwtService.generateToken(user);
        return new AuthDtos.AuthResponse(token, jwtService.getExpirationMs(), AuthDtos.UserProfile.from(user));
    }

    public void verifyEmail(String token) {
        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> ApiException.badRequest("Jeton de verification invalide ou deja utilise"));
        user.setEnabled(true);
        user.setVerificationToken(null);
        userRepository.save(user);
    }

    public void changePassword(String userId, AuthDtos.ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Utilisateur"));
        if (!BCrypt.checkpw(request.currentPassword(), user.getPassword())) {
            throw ApiException.badRequest("Mot de passe actuel incorrect");
        }
        validatePassword(request.newPassword());
        user.setPassword(BCrypt.hashpw(request.newPassword(), BCrypt.gensalt()));
        userRepository.save(user);
    }

    /**
     * Always succeeds from the caller's point of view - responding differently for
     * unknown addresses would turn this endpoint into an account-enumeration oracle.
     */
    public void requestPasswordReset(String email) {
        userRepository.findByEmailIgnoreCase(email.trim()).ifPresent(user -> {
            user.setResetToken(UUID.randomUUID().toString());
            user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(30));
            userRepository.save(user);
            emailService.sendPasswordResetEmail(user.getEmail(), user.getPrenom(), user.getResetToken());
        });
    }

    public void resetPassword(AuthDtos.ResetPasswordRequest request) {
        User user = userRepository.findByResetToken(request.token())
                .orElseThrow(() -> ApiException.badRequest("Lien de reinitialisation invalide"));
        if (user.getResetTokenExpiry() == null || user.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            throw ApiException.badRequest("Lien de reinitialisation expire");
        }
        validatePassword(request.newPassword());
        user.setPassword(BCrypt.hashpw(request.newPassword(), BCrypt.gensalt()));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);
    }

    private void validatePassword(String password) {
        if (password == null || !PASSWORD_PATTERN.matcher(password).matches()) {
            throw ApiException.badRequest(PASSWORD_RULE);
        }
    }
}
