package com.cmrt.pfe.services;

import com.cmrt.pfe.exceptions.ApiException;
import com.cmrt.pfe.models.Reservation;
import com.cmrt.pfe.models.TestResource;
import com.cmrt.pfe.models.User;
import com.cmrt.pfe.models.enums.AuditAction;
import com.cmrt.pfe.models.enums.NotificationType;
import com.cmrt.pfe.models.enums.ReservationStatus;
import com.cmrt.pfe.models.enums.ResourceStatus;
import com.cmrt.pfe.models.enums.Role;
import com.cmrt.pfe.models.enums.Severity;
import com.cmrt.pfe.repositories.ProductRepository;
import com.cmrt.pfe.repositories.ReservationRepository;
import com.cmrt.pfe.repositories.ResourceRepository;
import com.cmrt.pfe.repositories.UserRepository;
import com.cmrt.pfe.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Booking of test benches and tooling.
 * <p>
 * The original brief asked for a button that notifies the people concerned. This adds
 * the part that makes the notification worth acting on: a real time slot, a check that
 * nobody else already holds the bench in that window, and an accept/refuse decision from
 * technical control.
 */
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ResourceRepository resourceRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    private static final Set<ReservationStatus> ACTIVE_STATUSES =
            EnumSet.of(ReservationStatus.PENDING, ReservationStatus.APPROVED, ReservationStatus.IN_PROGRESS);

    public List<Reservation> findAll() {
        return reservationRepository.findAll();
    }

    public List<Reservation> findByResource(String resourceId) {
        return reservationRepository.findByResourceId(resourceId);
    }

    public List<Reservation> findByRequester(String userId) {
        return reservationRepository.findByRequesterId(userId);
    }

    public List<Reservation> findPending() {
        return reservationRepository.findByStatus(ReservationStatus.PENDING);
    }

    public Reservation findById(String id) {
        return reservationRepository.findById(id).orElseThrow(() -> ApiException.notFound("Reservation"));
    }

    /** Books a slot, refusing overlaps and unusable assets up front. */
    public Reservation request(Reservation request, AuthPrincipal actor) {
        TestResource resource = resourceRepository.findById(request.getResourceId())
                .orElseThrow(() -> ApiException.notFound("Moyen de test"));

        LocalDateTime start = request.getStartDate();
        LocalDateTime end = request.getEndDate();
        if (start == null || end == null) {
            throw ApiException.badRequest("Les dates de debut et de fin sont obligatoires");
        }
        if (!end.isAfter(start)) {
            throw ApiException.badRequest("La date de fin doit etre posterieure a la date de debut");
        }
        if (start.isBefore(LocalDateTime.now().minusHours(1))) {
            throw ApiException.badRequest("Impossible de reserver un creneau dans le passe");
        }
        if (resource.getStatus() == ResourceStatus.OUT_OF_SERVICE
                || resource.getStatus() == ResourceStatus.MAINTENANCE) {
            throw ApiException.conflict("Le moyen " + resource.getCode() + " est indisponible ("
                    + resource.getStatus() + ")");
        }
        if (resource.isCalibrationExpired()) {
            throw ApiException.conflict("La calibration du moyen " + resource.getCode()
                    + " a expire le " + resource.getCalibrationExpiry());
        }

        List<Reservation> conflicts = reservationRepository
                .findByResourceIdAndStatusIn(resource.getId(), ACTIVE_STATUSES).stream()
                .filter(r -> r.overlaps(start, end))
                .toList();
        if (!conflicts.isEmpty()) {
            Reservation clash = conflicts.get(0);
            throw ApiException.conflict("Creneau deja reserve du "
                    + clash.getStartDate() + " au " + clash.getEndDate());
        }

        Reservation reservation = Reservation.builder()
                .resourceId(resource.getId())
                .requesterId(actor != null ? actor.userId() : request.getRequesterId())
                .productId(request.getProductId())
                .purpose(request.getPurpose())
                .startDate(start)
                .endDate(end)
                .status(ReservationStatus.PENDING)
                .build();

        Reservation saved = reservationRepository.save(reservation);
        auditService.record(actor, AuditAction.CREATE, "Reservation", saved.getId(), saved.getProductId(),
                "Demande de reservation du moyen " + resource.getCode());

        // Whoever owns the bench decides; if nobody owns it, the whole technical-control team is asked.
        String context = java.util.Optional.ofNullable(saved.getProductId())
                .flatMap(productRepository::findById)
                .map(p -> " pour " + p.getReference())
                .orElse("");
        String message = (actor != null ? actor.name() : "Un utilisateur")
                + " demande le moyen " + resource.getCode() + context
                + " du " + start + " au " + end
                + (saved.getPurpose() != null ? " - " + saved.getPurpose() : "");

        if (resource.getOwnerId() != null) {
            notificationService.notify(resource.getOwnerId(), NotificationType.RESERVATION_REQUESTED,
                    Severity.MAJOR, "Demande de reservation : " + resource.getCode(), message,
                    "/control", actor);
        } else {
            notificationService.notifyRoles(List.of(Role.CONTROLE_TECHNIQUE),
                    NotificationType.RESERVATION_REQUESTED, Severity.MAJOR,
                    "Demande de reservation : " + resource.getCode(), message, "/control", actor);
        }
        return saved;
    }

    public Reservation decide(String id, boolean approved, String comment, AuthPrincipal actor) {
        Reservation reservation = findById(id);
        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw ApiException.conflict("Cette demande a deja ete traitee");
        }
        // Re-check overlaps: another booking may have been approved since the request.
        if (approved) {
            boolean clash = reservationRepository
                    .findByResourceIdAndStatusIn(reservation.getResourceId(),
                            EnumSet.of(ReservationStatus.APPROVED, ReservationStatus.IN_PROGRESS)).stream()
                    .anyMatch(r -> !r.getId().equals(id)
                            && r.overlaps(reservation.getStartDate(), reservation.getEndDate()));
            if (clash) {
                throw ApiException.conflict("Le creneau a ete attribue entre-temps a une autre demande");
            }
        }

        reservation.setStatus(approved ? ReservationStatus.APPROVED : ReservationStatus.REJECTED);
        reservation.setDecidedBy(actor != null ? actor.userId() : null);
        reservation.setDecisionComment(comment);
        reservation.setDecidedAt(LocalDateTime.now());
        Reservation saved = reservationRepository.save(reservation);

        TestResource resource = resourceRepository.findById(reservation.getResourceId()).orElse(null);
        String code = resource != null ? resource.getCode() : "moyen";

        auditService.record(actor, approved ? AuditAction.APPROVE : AuditAction.REJECT,
                "Reservation", id, reservation.getProductId(),
                "Reservation du moyen " + code + (approved ? " acceptee" : " refusee"));
        notificationService.notify(reservation.getRequesterId(),
                approved ? NotificationType.RESERVATION_APPROVED : NotificationType.RESERVATION_REJECTED,
                approved ? Severity.MINOR : Severity.MAJOR,
                approved ? "Reservation acceptee : " + code : "Reservation refusee : " + code,
                (approved ? "Creneau confirme du " : "Demande refusee pour le creneau du ")
                        + reservation.getStartDate() + " au " + reservation.getEndDate()
                        + (comment != null ? " - " + comment : ""),
                "/control", actor);
        return saved;
    }

    /** Marks the bench as occupied when the slot actually starts. */
    public Reservation start(String id, AuthPrincipal actor) {
        Reservation reservation = findById(id);
        if (reservation.getStatus() != ReservationStatus.APPROVED) {
            throw ApiException.conflict("Seule une reservation acceptee peut demarrer");
        }
        reservation.setStatus(ReservationStatus.IN_PROGRESS);
        Reservation saved = reservationRepository.save(reservation);
        setResourceStatus(reservation.getResourceId(), ResourceStatus.IN_USE);
        auditService.record(actor, AuditAction.STATUS_CHANGE, "Reservation", id, reservation.getProductId(),
                "Debut d'utilisation du moyen");
        return saved;
    }

    public Reservation complete(String id, AuthPrincipal actor) {
        Reservation reservation = findById(id);
        reservation.setStatus(ReservationStatus.COMPLETED);
        Reservation saved = reservationRepository.save(reservation);
        setResourceStatus(reservation.getResourceId(), ResourceStatus.AVAILABLE);
        auditService.record(actor, AuditAction.STATUS_CHANGE, "Reservation", id, reservation.getProductId(),
                "Fin d'utilisation du moyen");
        return saved;
    }

    public Reservation cancel(String id, AuthPrincipal actor) {
        Reservation reservation = findById(id);
        boolean owner = actor != null && actor.userId().equals(reservation.getRequesterId());
        if (!owner && actor != null && !actor.isAdmin()
                && actor.roleEnum() != Role.CONTROLE_TECHNIQUE) {
            throw ApiException.forbidden("Seul le demandeur ou le controle technique peut annuler cette reservation");
        }
        if (reservation.getStatus() == ReservationStatus.COMPLETED) {
            throw ApiException.conflict("Une reservation terminee ne peut pas etre annulee");
        }
        reservation.setStatus(ReservationStatus.CANCELLED);
        Reservation saved = reservationRepository.save(reservation);
        auditService.record(actor, AuditAction.STATUS_CHANGE, "Reservation", id, reservation.getProductId(),
                "Reservation annulee");
        return saved;
    }

    private void setResourceStatus(String resourceId, ResourceStatus status) {
        resourceRepository.findById(resourceId).ifPresent(resource -> {
            resource.setStatus(status);
            resource.setUpdatedAt(LocalDateTime.now());
            resourceRepository.save(resource);
        });
    }

    /** Display name of a booking's requester, used by the calendar view. */
    public String requesterName(Reservation reservation) {
        return java.util.Optional.ofNullable(reservation.getRequesterId())
                .flatMap(userRepository::findById)
                .map(User::getFullName)
                .orElse("Utilisateur inconnu");
    }
}
