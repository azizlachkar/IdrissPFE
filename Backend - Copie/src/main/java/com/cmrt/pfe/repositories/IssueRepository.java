package com.cmrt.pfe.repositories;

import com.cmrt.pfe.models.Issue;
import com.cmrt.pfe.models.enums.IssueCategory;
import com.cmrt.pfe.models.enums.IssueStatus;
import com.cmrt.pfe.models.enums.Severity;
import com.cmrt.pfe.models.enums.StageType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface IssueRepository extends MongoRepository<Issue, String> {
    List<Issue> findByProductId(String productId);
    List<Issue> findByProductIdAndStatusIn(String productId, Collection<IssueStatus> statuses);
    List<Issue> findByStatus(IssueStatus status);
    List<Issue> findByStatusIn(Collection<IssueStatus> statuses);
    List<Issue> findByAssigneeId(String assigneeId);
    List<Issue> findBySeverity(Severity severity);
    List<Issue> findByCategory(IssueCategory category);
    List<Issue> findByProductIdAndStageTypeAndStatusIn(String productId, StageType stageType, Collection<IssueStatus> statuses);
    long countByStatusIn(Collection<IssueStatus> statuses);
    void deleteByProductId(String productId);
}
