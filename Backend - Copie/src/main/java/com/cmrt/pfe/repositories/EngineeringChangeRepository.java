package com.cmrt.pfe.repositories;

import com.cmrt.pfe.models.EngineeringChange;
import com.cmrt.pfe.models.enums.ChangeStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface EngineeringChangeRepository extends MongoRepository<EngineeringChange, String> {
    List<EngineeringChange> findByProductId(String productId);
    List<EngineeringChange> findByStatus(ChangeStatus status);
    List<EngineeringChange> findByStatusIn(Collection<ChangeStatus> statuses);
    List<EngineeringChange> findByRequesterId(String requesterId);
    long countByStatus(ChangeStatus status);
    void deleteByProductId(String productId);
}
