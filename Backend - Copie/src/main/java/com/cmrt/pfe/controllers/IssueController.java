package com.cmrt.pfe.controllers;

import com.cmrt.pfe.models.Issue;
import com.cmrt.pfe.models.enums.IssueStatus;
import com.cmrt.pfe.models.enums.Role;
import com.cmrt.pfe.security.AuthPrincipal;
import com.cmrt.pfe.security.CurrentUser;
import com.cmrt.pfe.security.RequireRole;
import com.cmrt.pfe.services.IssueService;
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

/** Blockage tracking - the "Reclamation" workflow. */
@RestController
@RequestMapping("/api/issues")
@RequiredArgsConstructor
public class IssueController {

    private final IssueService issueService;

    @GetMapping
    public ResponseEntity<List<Issue>> all(@RequestParam(required = false) String productId,
                                           @RequestParam(required = false) Boolean openOnly) {
        if (productId != null) {
            return ResponseEntity.ok(issueService.findByProduct(productId));
        }
        return ResponseEntity.ok(Boolean.TRUE.equals(openOnly) ? issueService.findOpen() : issueService.findAll());
    }

    /** Blockages assigned to the caller. */
    @GetMapping("/mine")
    public ResponseEntity<List<Issue>> mine(@CurrentUser AuthPrincipal principal) {
        return ResponseEntity.ok(issueService.findAssignedTo(principal.userId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Issue> byId(@PathVariable String id) {
        return ResponseEntity.ok(issueService.findById(id));
    }

    /** Anyone signed in may raise a blockage - that is the point of the button. */
    @PostMapping
    public ResponseEntity<Issue> create(@RequestBody Issue request, @CurrentUser AuthPrincipal principal) {
        return ResponseEntity.ok(issueService.create(request, principal));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Issue> update(@PathVariable String id, @RequestBody Issue request,
                                        @CurrentUser AuthPrincipal principal) {
        return ResponseEntity.ok(issueService.update(id, request, principal));
    }

    @PatchMapping("/{id}/assign")
    public ResponseEntity<Issue> assign(@PathVariable String id, @RequestBody Map<String, String> body,
                                        @CurrentUser AuthPrincipal principal) {
        return ResponseEntity.ok(issueService.assign(id, body.get("assigneeId"), principal));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Issue> changeStatus(@PathVariable String id, @RequestBody Map<String, String> body,
                                              @CurrentUser AuthPrincipal principal) {
        IssueStatus status = IssueStatus.valueOf(body.get("status"));
        return ResponseEntity.ok(issueService.changeStatus(id, status, body.get("comment"), principal));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<Issue> comment(@PathVariable String id, @RequestBody Map<String, String> body,
                                         @CurrentUser AuthPrincipal principal) {
        return ResponseEntity.ok(issueService.addComment(id, body.get("message"), principal));
    }

    @RequireRole({Role.ADMIN, Role.CHEF_PROJET})
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, @CurrentUser AuthPrincipal principal) {
        issueService.delete(id, principal);
        return ResponseEntity.noContent().build();
    }
}
