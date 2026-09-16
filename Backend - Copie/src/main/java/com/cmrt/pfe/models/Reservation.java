package com.cmrt.pfe.models;

import com.cmrt.pfe.models.enums.ReservationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * A time-boxed booking of a {@link TestResource}. Bookings carry a real slot rather than
 * a single request date, which is what lets the service layer reject overlaps before
 * two teams turn up at the same bench.
 */
@Document(collection = "reservations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Reservation {

    @Id
    private String id;

    @Indexed
    private String resourceId;

    @Indexed
    private String requesterId;

    /** Product the bench is being booked for, when the booking relates to a project. */
    private String productId;

    private String purpose;

    private LocalDateTime startDate;

    private LocalDateTime endDate;

    @Builder.Default
    private ReservationStatus status = ReservationStatus.PENDING;

    /** Technical-control user who accepted or refused the booking. */
    private String decidedBy;

    private String decisionComment;

    private LocalDateTime decidedAt;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /** Slot length in hours, used for utilisation KPIs. */
    public Double getDurationHours() {
        if (startDate == null || endDate == null) return null;
        return Duration.between(startDate, endDate).toMinutes() / 60.0;
    }

    /** True when this booking occupies the bench at any point during the given window. */
    public boolean overlaps(LocalDateTime otherStart, LocalDateTime otherEnd) {
        if (startDate == null || endDate == null || otherStart == null || otherEnd == null) {
            return false;
        }
        return startDate.isBefore(otherEnd) && otherStart.isBefore(endDate);
    }

    /** Only these states actually hold the asset; rejected and cancelled slots are free. */
    public boolean holdsResource() {
        return status == ReservationStatus.PENDING
                || status == ReservationStatus.APPROVED
                || status == ReservationStatus.IN_PROGRESS;
    }
}
