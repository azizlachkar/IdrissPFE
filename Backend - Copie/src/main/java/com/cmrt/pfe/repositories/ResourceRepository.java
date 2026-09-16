package com.cmrt.pfe.repositories;

import com.cmrt.pfe.models.TestResource;
import com.cmrt.pfe.models.enums.ResourceStatus;
import com.cmrt.pfe.models.enums.ResourceType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResourceRepository extends MongoRepository<TestResource, String> {
    List<TestResource> findByStatus(ResourceStatus status);
    List<TestResource> findByType(ResourceType type);
    Optional<TestResource> findByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCase(String code);
    long countByStatus(ResourceStatus status);
}
