package com.cmrt.pfe.controllers;

import com.cmrt.pfe.dto.AuthDtos;
import com.cmrt.pfe.models.User;
import com.cmrt.pfe.models.enums.Role;
import com.cmrt.pfe.security.AuthPrincipal;
import com.cmrt.pfe.security.CurrentUser;
import com.cmrt.pfe.security.RequireRole;
import com.cmrt.pfe.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<AuthDtos.UserProfile>> all(@RequestParam(required = false) Role role) {
        return ResponseEntity.ok(role == null ? userService.findAll() : userService.findByRole(role));
    }

    /** Active users grouped by role - feeds the assignment dropdowns. */
    @GetMapping("/directory")
    public ResponseEntity<Map<Role, List<AuthDtos.UserProfile>>> directory() {
        return ResponseEntity.ok(userService.directory());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AuthDtos.UserProfile> byId(@PathVariable String id) {
        return ResponseEntity.ok(userService.findById(id));
    }

    /** Anyone may edit their own profile; only an administrator may edit someone else's. */
    @PutMapping("/{id}")
    public ResponseEntity<AuthDtos.UserProfile> update(@PathVariable String id,
                                                       @RequestBody User changes,
                                                       @CurrentUser AuthPrincipal principal) {
        if (!principal.userId().equals(id) && !principal.isAdmin()) {
            throw com.cmrt.pfe.exceptions.ApiException.forbidden("Vous ne pouvez modifier que votre profil");
        }
        return ResponseEntity.ok(userService.updateProfile(id, changes));
    }

    @RequireRole(Role.ADMIN)
    @PatchMapping("/{id}/role")
    public ResponseEntity<AuthDtos.UserProfile> changeRole(@PathVariable String id,
                                                           @RequestBody Map<String, String> body,
                                                           @CurrentUser AuthPrincipal principal) {
        return ResponseEntity.ok(userService.changeRole(id, Role.valueOf(body.get("role")), principal));
    }

    @RequireRole(Role.ADMIN)
    @PatchMapping("/{id}/active")
    public ResponseEntity<AuthDtos.UserProfile> setActive(@PathVariable String id,
                                                          @RequestBody Map<String, Boolean> body,
                                                          @CurrentUser AuthPrincipal principal) {
        return ResponseEntity.ok(userService.setActive(id, Boolean.TRUE.equals(body.get("active")), principal));
    }

    @RequireRole(Role.ADMIN)
    @PatchMapping("/{id}/confirm")
    public ResponseEntity<AuthDtos.UserProfile> confirm(@PathVariable String id,
                                                        @CurrentUser AuthPrincipal principal) {
        return ResponseEntity.ok(userService.confirmAccount(id, principal));
    }
}
