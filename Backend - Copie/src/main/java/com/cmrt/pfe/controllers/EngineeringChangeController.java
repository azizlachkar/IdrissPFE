package com.cmrt.pfe.controllers;

import com.cmrt.pfe.models.EngineeringChange;
import com.cmrt.pfe.models.enums.Role;
import com.cmrt.pfe.security.AuthPrincipal;
import com.cmrt.pfe.security.CurrentUser;
import com.cmrt.pfe.security.RequireRole;
import com.cmrt.pfe.services.EngineeringChangeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Engineering change control (ECR / ECO). */
@RestController
@RequestMapping("/api/changes")
@RequiredArgsConstructor
public class EngineeringChangeController {

    private final EngineeringChangeService changeService;

    @GetMapping
    public ResponseEntity<List<EngineeringChange>> all(@RequestParam(required = false) String productId) {
        return ResponseEntity.ok(productId == null
                ? changeService.findAll()
                : changeService.findByProduct(productId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EngineeringChange> byId(@PathVariable String id) {
        return ResponseEntity.ok(changeService.findById(id));
    }

    @RequireRole({Role.ADMIN, Role.CHEF_PROJET, Role.METHODISTE, Role.QUALITICIEN, Role.CONTROLE_TECHNIQUE})
    @PostMapping
    public ResponseEntity<EngineeringChange> create(@RequestBody EngineeringChange request,
                                                    @CurrentUser AuthPrincipal principal) {
        return ResponseEntity.ok(changeService.create(request, principal));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EngineeringChange> update(@PathVariable String id,
                                                    @RequestBody EngineeringChange request,
                                                    @CurrentUser AuthPrincipal principal) {
        return ResponseEntity.ok(changeService.update(id, request, principal));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<EngineeringChange> submit(@PathVariable String id,
                                                    @CurrentUser AuthPrincipal principal) {
        return ResponseEntity.ok(changeService.submit(id, principal));
    }

    @RequireRole({Role.ADMIN, Role.CHEF_PROJET, Role.METHODISTE, Role.QUALITICIEN, Role.CONTROLE_TECHNIQUE})
    @PostMapping("/{id}/approve")
    public ResponseEntity<EngineeringChange> approve(@PathVariable String id,
                                                     @RequestBody(required = false) Map<String, String> body,
                                                     @CurrentUser AuthPrincipal principal) {
        return ResponseEntity.ok(changeService.approve(id, body != null ? body.get("comment") : null, principal));
    }

    @RequireRole({Role.ADMIN, Role.CHEF_PROJET, Role.METHODISTE, Role.QUALITICIEN, Role.CONTROLE_TECHNIQUE})
    @PostMapping("/{id}/reject")
    public ResponseEntity<EngineeringChange> reject(@PathVariable String id,
                                                    @RequestBody Map<String, String> body,
                                                    @CurrentUser AuthPrincipal principal) {
        return ResponseEntity.ok(changeService.reject(id, body.get("comment"), principal));
    }

    @PostMapping("/{id}/implement")
    public ResponseEntity<EngineeringChange> implement(@PathVariable String id,
                                                       @CurrentUser AuthPrincipal principal) {
        return ResponseEntity.ok(changeService.markImplemented(id, principal));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<EngineeringChange> close(@PathVariable String id,
                                                   @CurrentUser AuthPrincipal principal) {
        return ResponseEntity.ok(changeService.close(id, principal));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, @CurrentUser AuthPrincipal principal) {
        changeService.delete(id, principal);
        return ResponseEntity.noContent().build();
    }
}
