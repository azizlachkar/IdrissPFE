package com.cmrt.pfe.controllers;

import com.cmrt.pfe.models.Reservation;
import com.cmrt.pfe.models.enums.Role;
import com.cmrt.pfe.security.AuthPrincipal;
import com.cmrt.pfe.security.CurrentUser;
import com.cmrt.pfe.security.RequireRole;
import com.cmrt.pfe.services.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Booking of test benches and tooling. */
@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @GetMapping
    public ResponseEntity<List<Reservation>> all(@RequestParam(required = false) String resourceId,
                                                 @RequestParam(required = false) Boolean pendingOnly) {
        if (resourceId != null) return ResponseEntity.ok(reservationService.findByResource(resourceId));
        if (Boolean.TRUE.equals(pendingOnly)) return ResponseEntity.ok(reservationService.findPending());
        return ResponseEntity.ok(reservationService.findAll());
    }

    @GetMapping("/mine")
    public ResponseEntity<List<Reservation>> mine(@CurrentUser AuthPrincipal principal) {
        return ResponseEntity.ok(reservationService.findByRequester(principal.userId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Reservation> byId(@PathVariable String id) {
        return ResponseEntity.ok(reservationService.findById(id));
    }

    /** Anyone signed in may request a bench; technical control decides. */
    @PostMapping
    public ResponseEntity<Reservation> request(@RequestBody Reservation request,
                                               @CurrentUser AuthPrincipal principal) {
        return ResponseEntity.ok(reservationService.request(request, principal));
    }

    @RequireRole({Role.ADMIN, Role.CONTROLE_TECHNIQUE})
    @PostMapping("/{id}/approve")
    public ResponseEntity<Reservation> approve(@PathVariable String id,
                                               @RequestBody(required = false) Map<String, String> body,
                                               @CurrentUser AuthPrincipal principal) {
        return ResponseEntity.ok(reservationService.decide(id, true,
                body != null ? body.get("comment") : null, principal));
    }

    @RequireRole({Role.ADMIN, Role.CONTROLE_TECHNIQUE})
    @PostMapping("/{id}/reject")
    public ResponseEntity<Reservation> reject(@PathVariable String id,
                                              @RequestBody Map<String, String> body,
                                              @CurrentUser AuthPrincipal principal) {
        return ResponseEntity.ok(reservationService.decide(id, false, body.get("comment"), principal));
    }

    @RequireRole({Role.ADMIN, Role.CONTROLE_TECHNIQUE, Role.TECHNICIEN})
    @PostMapping("/{id}/start")
    public ResponseEntity<Reservation> start(@PathVariable String id, @CurrentUser AuthPrincipal principal) {
        return ResponseEntity.ok(reservationService.start(id, principal));
    }

    @RequireRole({Role.ADMIN, Role.CONTROLE_TECHNIQUE, Role.TECHNICIEN})
    @PostMapping("/{id}/complete")
    public ResponseEntity<Reservation> complete(@PathVariable String id, @CurrentUser AuthPrincipal principal) {
        return ResponseEntity.ok(reservationService.complete(id, principal));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Reservation> cancel(@PathVariable String id, @CurrentUser AuthPrincipal principal) {
        return ResponseEntity.ok(reservationService.cancel(id, principal));
    }
}
