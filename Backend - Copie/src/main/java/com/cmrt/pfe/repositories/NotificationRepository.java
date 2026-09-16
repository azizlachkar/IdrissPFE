package com.cmrt.pfe.repositories;

import com.cmrt.pfe.models.Notification;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends MongoRepository<Notification, String> {
    List<Notification> findByRecipientIdOrderByCreatedAtDesc(String recipientId);
    List<Notification> findByRecipientIdAndReadFalseOrderByCreatedAtDesc(String recipientId);
    long countByRecipientIdAndReadFalse(String recipientId);
}
