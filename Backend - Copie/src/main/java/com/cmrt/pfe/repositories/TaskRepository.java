package com.cmrt.pfe.repositories;

import com.cmrt.pfe.models.Task;
import com.cmrt.pfe.models.enums.TaskStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public interface TaskRepository extends MongoRepository<Task, String> {
    List<Task> findByProductId(String productId);
    List<Task> findByAssigneeId(String assigneeId);
    List<Task> findByStatus(TaskStatus status);
    List<Task> findByAssigneeIdAndStatusNotIn(String assigneeId, Collection<TaskStatus> excluded);
    List<Task> findByDueDateBeforeAndStatusNotIn(LocalDate date, Collection<TaskStatus> excluded);
    long countByStatus(TaskStatus status);
    void deleteByProductId(String productId);
}
