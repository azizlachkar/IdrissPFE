package com.cmrt.pfe.repositories;

import com.cmrt.pfe.models.ProjectStage;
import com.cmrt.pfe.models.enums.StageStatus;
import com.cmrt.pfe.models.enums.StageType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectStageRepository extends MongoRepository<ProjectStage, String> {
    List<ProjectStage> findByProductIdOrderByOrderAsc(String productId);
    Optional<ProjectStage> findByProductIdAndStageType(String productId, StageType stageType);
    List<ProjectStage> findByStatus(StageStatus status);
    List<ProjectStage> findByStatusIn(Collection<StageStatus> statuses);
    List<ProjectStage> findByOwnerId(String ownerId);
    List<ProjectStage> findByStageType(StageType stageType);
    List<ProjectStage> findByPlannedEndBeforeAndStatusNotIn(LocalDate date, Collection<StageStatus> excluded);
    void deleteByProductId(String productId);
}
