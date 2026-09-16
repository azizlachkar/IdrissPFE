package com.cmrt.pfe.controllers;

import com.cmrt.pfe.dto.AuthDtos;
import com.cmrt.pfe.security.AuthPrincipal;
import com.cmrt.pfe.security.CurrentUser;
import com.cmrt.pfe.security.PublicEndpoint;
import com.cmrt.pfe.services.AuthService;
import com.cmrt.pfe.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @PublicEndpoint
    @PostMapping("/signup")
    public ResponseEntity<AuthDtos.UserProfile> signup(@Valid @RequestBody AuthDtos.SignupRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PublicEndpoint
    @PostMapping("/login")
    public ResponseEntity<AuthDtos.AuthResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return ResponseEntity.ok(authService.authenticate(request));
    }

    @PublicEndpoint
    @PostMapping("/verify")
    public ResponseEntity<Map<String, String>> verify(@Valid @RequestBody AuthDtos.TokenRequest request) {
        authService.verifyEmail(request.token());
        return ResponseEntity.ok(Map.of("message", "Compte valide avec succes"));
    }

    @PublicEndpoint
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody AuthDtos.ForgotPasswordRequest request) {
        authService.requestPasswordReset(request.email());
        // Deliberately identical whether or not the address exists.
        return ResponseEntity.ok(Map.of("message",
                "Si un compte existe pour cette adresse, un lien de reinitialisation vient d'etre envoye"));
    }

    @PublicEndpoint
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody AuthDtos.ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(Map.of("message", "Mot de passe mis a jour"));
    }

    /** Lets the SPA rehydrate its session on reload and confirm the token is still valid. */
    @GetMapping("/me")
    public ResponseEntity<AuthDtos.UserProfile> me(@CurrentUser AuthPrincipal principal) {
        return ResponseEntity.ok(userService.findById(principal.userId()));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(@CurrentUser AuthPrincipal principal,
                                                              @Valid @RequestBody AuthDtos.ChangePasswordRequest request) {
        authService.changePassword(principal.userId(), request);
        return ResponseEntity.ok(Map.of("message", "Mot de passe modifie"));
    }
}
