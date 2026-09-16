package com.cmrt.pfe.repositories;

import com.cmrt.pfe.models.Reservation;
import com.cmrt.pfe.models.enums.ReservationStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface ReservationRepository extends MongoRepository<Reservation, String> {
    List<Reservation> findByResourceId(String resourceId);
    List<Reservation> findByResourceIdAndStatusIn(String resourceId, Collection<ReservationStatus> statuses);
    List<Reservation> findByRequesterId(String requesterId);
    List<Reservation> findByStatus(ReservationStatus status);
    List<Reservation> findByStatusIn(Collection<ReservationStatus> statuses);
    List<Reservation> findByStartDateBetween(LocalDateTime from, LocalDateTime to);
    long countByStatus(ReservationStatus status);
}
