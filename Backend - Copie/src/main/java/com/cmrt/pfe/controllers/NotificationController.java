package com.cmrt.pfe.controllers;

import com.cmrt.pfe.models.Notification;
import com.cmrt.pfe.security.AuthPrincipal;
import com.cmrt.pfe.security.CurrentUser;
import com.cmrt.pfe.services.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** The notification inbox. Scoped to the caller - nobody reads someone else's. */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<List<Notification>> inbox(@CurrentUser AuthPrincipal principal,
                                                    @RequestParam(required = false) Boolean unreadOnly) {
        return ResponseEntity.ok(Boolean.TRUE.equals(unreadOnly)
                ? notificationService.unread(principal.userId())
                : notificationService.inbox(principal.userId()));
    }

    /** Polled by the navbar badge. */
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(@CurrentUser AuthPrincipal principal) {
        return ResponseEntity.ok(Map.of("count", notificationService.unreadCount(principal.userId())));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable String id, @CurrentUser AuthPrincipal principal) {
        notificationService.markRead(id, principal.userId());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@CurrentUser AuthPrincipal principal) {
        notificationService.markAllRead(principal.userId());
        return ResponseEntity.noContent().build();
    }
}
