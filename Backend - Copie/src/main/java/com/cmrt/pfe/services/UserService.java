package com.cmrt.pfe.services;

import com.cmrt.pfe.dto.AuthDtos;
import com.cmrt.pfe.exceptions.ApiException;
import com.cmrt.pfe.models.User;
import com.cmrt.pfe.models.enums.AuditAction;
import com.cmrt.pfe.models.enums.Role;
import com.cmrt.pfe.repositories.UserRepository;
import com.cmrt.pfe.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final AuditService auditService;

    public List<AuthDtos.UserProfile> findAll() {
        return userRepository.findAll().stream().map(AuthDtos.UserProfile::from).toList();
    }

    public List<AuthDtos.UserProfile> findByRole(Role role) {
        return userRepository.findByRole(role).stream().map(AuthDtos.UserProfile::from).toList();
    }

    public User findEntity(String id) {
        return userRepository.findById(id).orElseThrow(() -> ApiException.notFound("Utilisateur"));
    }

    public AuthDtos.UserProfile findById(String id) {
        return AuthDtos.UserProfile.from(findEntity(id));
    }

    /**
     * Resolves several user ids in one query and returns a lookup keyed by id. Used by
     * list endpoints that need to show owner names without an N+1 read per row.
     */
    public Map<String, AuthDtos.UserProfile> resolve(Collection<String> ids) {
        List<String> clean = ids.stream().filter(id -> id != null && !id.isBlank()).distinct().toList();
        if (clean.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(clean).stream()
                .collect(Collectors.toMap(User::getId, AuthDtos.UserProfile::from, (a, b) -> a));
    }

    /** Display name for an id, or a placeholder when the user was removed. */
    public String nameOf(String userId) {
        if (userId == null) return "Non affecte";
        return userRepository.findById(userId).map(User::getFullName).orElse("Utilisateur inconnu");
    }

    public AuthDtos.UserProfile updateProfile(String userId, User changes) {
        User user = findEntity(userId);
        if (changes.getNom() != null) user.setNom(changes.getNom());
        if (changes.getPrenom() != null) user.setPrenom(changes.getPrenom());
        if (changes.getTelephone() != null) user.setTelephone(changes.getTelephone());
        if (changes.getMatricule() != null) user.setMatricule(changes.getMatricule());
        if (changes.getDepartement() != null) user.setDepartement(changes.getDepartement());
        if (changes.getPoste() != null) user.setPoste(changes.getPoste());
        if (changes.getServiceUnit() != null) user.setServiceUnit(changes.getServiceUnit());
        return AuthDtos.UserProfile.from(userRepository.save(user));
    }

    /** Administrator action: change someone's role. */
    public AuthDtos.UserProfile changeRole(String userId, Role role, AuthPrincipal actor) {
        User user = findEntity(userId);
        Role previous = user.getRole();
        user.setRole(role);
        User saved = userRepository.save(user);
        auditService.record(actor, AuditAction.UPDATE, "User", userId, null,
                "Role de " + user.getFullName() + " modifie",
                previous != null ? previous.name() : null, role.name());
        return AuthDtos.UserProfile.from(saved);
    }

    /** Administrator action: enable or suspend an account. Accounts are never hard-deleted. */
    public AuthDtos.UserProfile setActive(String userId, boolean active, AuthPrincipal actor) {
        if (actor != null && userId.equals(actor.userId()) && !active) {
            throw ApiException.badRequest("Vous ne pouvez pas desactiver votre propre compte");
        }
        User user = findEntity(userId);
        user.setActive(active);
        User saved = userRepository.save(user);
        auditService.record(actor, AuditAction.STATUS_CHANGE, "User", userId, null,
                (active ? "Activation" : "Desactivation") + " du compte " + user.getFullName());
        return AuthDtos.UserProfile.from(saved);
    }

    /** Administrator action: validate an account without waiting for the email link. */
    public AuthDtos.UserProfile confirmAccount(String userId, AuthPrincipal actor) {
        User user = findEntity(userId);
        user.setEnabled(true);
        user.setVerificationToken(null);
        User saved = userRepository.save(user);
        auditService.record(actor, AuditAction.STATUS_CHANGE, "User", userId, null,
                "Compte " + user.getFullName() + " valide par un administrateur");
        return AuthDtos.UserProfile.from(saved);
    }

    /** Users grouped by role, for the assignment dropdowns in the product forms. */
    public Map<Role, List<AuthDtos.UserProfile>> directory() {
        return userRepository.findByActiveTrue().stream()
                .filter(u -> u.getRole() != null)
                .collect(Collectors.groupingBy(User::getRole,
                        Collectors.mapping(AuthDtos.UserProfile::from, Collectors.toList())));
    }

    /** Active users holding any of the given roles - the approver pool for a gate. */
    public List<User> activeWithRoles(Collection<Role> roles) {
        return userRepository.findByRoleIn(roles).stream()
                .filter(u -> Boolean.TRUE.equals(u.getActive()))
                .toList();
    }

    public Map<String, String> nameLookup(Collection<String> ids) {
        return resolve(ids).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().fullName(), (a, b) -> a));
    }

    public Function<String, String> nameResolver(Collection<String> ids) {
        Map<String, String> lookup = nameLookup(ids);
        return id -> id == null ? null : lookup.getOrDefault(id, "Utilisateur inconnu");
    }
}
