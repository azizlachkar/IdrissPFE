package com.cmrt.pfe.services;

import com.cmrt.pfe.models.Notification;
import com.cmrt.pfe.models.User;
import com.cmrt.pfe.models.enums.NotificationType;
import com.cmrt.pfe.models.enums.Role;
import com.cmrt.pfe.models.enums.Severity;
import com.cmrt.pfe.repositories.NotificationRepository;
import com.cmrt.pfe.repositories.UserRepository;
import com.cmrt.pfe.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Single entry point for telling people something happened. Every notification is
 * persisted for the in-app inbox; high-severity ones are mirrored by email.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    public Notification notify(String recipientId, NotificationType type, Severity severity,
                               String title, String message, String link, AuthPrincipal actor) {
        if (recipientId == null || recipientId.isBlank()) {
            return null;
        }
        // Don't ping people about their own actions.
        if (actor != null && recipientId.equals(actor.userId())) {
            return null;
        }

        Notification notification = notificationRepository.save(Notification.builder()
                .recipientId(recipientId)
                .type(type)
                .severity(severity)
                .title(title)
                .message(message)
                .link(link)
                .actorId(actor != null ? actor.userId() : null)
                .actorName(actor != null ? actor.name() : "Systeme")
                .build());

        if (severity == Severity.CRITICAL || severity == Severity.BLOCKING) {
            userRepository.findById(recipientId)
                    .filter(u -> Boolean.TRUE.equals(u.getEnabled()) && u.getEmail() != null)
                    .ifPresent(u -> emailService.sendNotificationEmail(u.getEmail(), title, message, link));
        }
        return notification;
    }

    /** Notify several people at once, ignoring nulls and duplicates. */
    public void notifyAll(Collection<String> recipientIds, NotificationType type, Severity severity,
                          String title, String message, String link, AuthPrincipal actor) {
        Set<String> unique = new LinkedHashSet<>();
        if (recipientIds != null) {
            recipientIds.stream().filter(Objects::nonNull).forEach(unique::add);
        }
        unique.forEach(id -> notify(id, type, severity, title, message, link, actor));
    }

    /** Notify everyone holding one of the given roles - used for approval requests. */
    public void notifyRoles(Collection<Role> roles, NotificationType type, Severity severity,
                            String title, String message, String link, AuthPrincipal actor) {
        if (roles == null || roles.isEmpty()) return;
        List<String> ids = userRepository.findByRoleIn(roles).stream()
                .filter(u -> Boolean.TRUE.equals(u.getActive()))
                .map(User::getId)
                .toList();
        notifyAll(ids, type, severity, title, message, link, actor);
    }

    public List<Notification> inbox(String userId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId);
    }

    public List<Notification> unread(String userId) {
        return notificationRepository.findByRecipientIdAndReadFalseOrderByCreatedAtDesc(userId);
    }

    public long unreadCount(String userId) {
        return notificationRepository.countByRecipientIdAndReadFalse(userId);
    }

    public void markRead(String notificationId, String userId) {
        notificationRepository.findById(notificationId)
                .filter(n -> userId.equals(n.getRecipientId()))
                .ifPresent(n -> {
                    n.setRead(true);
                    n.setReadAt(LocalDateTime.now());
                    notificationRepository.save(n);
                });
    }

    public void markAllRead(String userId) {
        List<Notification> unread = notificationRepository.findByRecipientIdAndReadFalseOrderByCreatedAtDesc(userId);
        LocalDateTime now = LocalDateTime.now();
        unread.forEach(n -> {
            n.setRead(true);
            n.setReadAt(now);
        });
        notificationRepository.saveAll(unread);
    }
}
