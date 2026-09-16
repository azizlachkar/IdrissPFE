package com.cmrt.pfe.controllers;

import com.cmrt.pfe.models.TestResource;
import com.cmrt.pfe.models.enums.ResourceStatus;
import com.cmrt.pfe.models.enums.Role;
import com.cmrt.pfe.security.AuthPrincipal;
import com.cmrt.pfe.security.CurrentUser;
import com.cmrt.pfe.security.RequireRole;
import com.cmrt.pfe.services.ResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Test benches, build boards, interfaces and tooling. */
@RestController
@RequestMapping("/api/resources")
@RequiredArgsConstructor
public class ResourceController {

    private final ResourceService resourceService;

    @GetMapping
    public ResponseEntity<List<TestResource>> all(@RequestParam(required = false) Boolean availableOnly) {
        return ResponseEntity.ok(Boolean.TRUE.equals(availableOnly)
                ? resourceService.findAvailable()
                : resourceService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TestResource> byId(@PathVariable String id) {
        return ResponseEntity.ok(resourceService.findById(id));
    }

    @RequireRole({Role.ADMIN, Role.CONTROLE_TECHNIQUE})
    @PostMapping
    public ResponseEntity<TestResource> create(@RequestBody TestResource request,
                                               @CurrentUser AuthPrincipal principal) {
        return ResponseEntity.ok(resourceService.create(request, principal));
    }

    @RequireRole({Role.ADMIN, Role.CONTROLE_TECHNIQUE})
    @PutMapping("/{id}")
    public ResponseEntity<TestResource> update(@PathVariable String id, @RequestBody TestResource request,
                                               @CurrentUser AuthPrincipal principal) {
        return ResponseEntity.ok(resourceService.update(id, request, principal));
    }

    @RequireRole({Role.ADMIN, Role.CONTROLE_TECHNIQUE})
    @PatchMapping("/{id}/status")
    public ResponseEntity<TestResource> changeStatus(@PathVariable String id, @RequestBody Map<String, String> body,
                                                     @CurrentUser AuthPrincipal principal) {
        return ResponseEntity.ok(resourceService.changeStatus(id,
                ResourceStatus.valueOf(body.get("status")), principal));
    }

    @RequireRole(Role.ADMIN)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, @CurrentUser AuthPrincipal principal) {
        resourceService.delete(id, principal);
        return ResponseEntity.noContent().build();
    }
}
