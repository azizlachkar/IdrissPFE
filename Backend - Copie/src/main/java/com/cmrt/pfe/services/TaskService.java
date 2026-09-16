package com.cmrt.pfe.services;

import com.cmrt.pfe.exceptions.ApiException;
import com.cmrt.pfe.models.Task;
import com.cmrt.pfe.models.enums.AuditAction;
import com.cmrt.pfe.models.enums.NotificationType;
import com.cmrt.pfe.models.enums.Severity;
import com.cmrt.pfe.models.enums.TaskStatus;
import com.cmrt.pfe.repositories.ProductRepository;
import com.cmrt.pfe.repositories.TaskRepository;
import com.cmrt.pfe.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProductRepository productRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public static final Set<TaskStatus> CLOSED_STATUSES = EnumSet.of(TaskStatus.DONE, TaskStatus.CANCELLED);

    public List<Task> findAll() {
        return taskRepository.findAll();
    }

    public List<Task> findByProduct(String productId) {
        return taskRepository.findByProductId(productId);
    }

    public List<Task> findByAssignee(String userId) {
        return taskRepository.findByAssigneeId(userId);
    }

    /** The caller's open work, newest deadline first - the "my tasks" widget. */
    public List<Task> myOpenTasks(String userId) {
        return taskRepository.findByAssigneeIdAndStatusNotIn(userId, CLOSED_STATUSES).stream()
                .sorted((a, b) -> {
                    if (a.getDueDate() == null && b.getDueDate() == null) return 0;
                    if (a.getDueDate() == null) return 1;
                    if (b.getDueDate() == null) return -1;
                    return a.getDueDate().compareTo(b.getDueDate());
                })
                .toList();
    }

    public Task findById(String id) {
        return taskRepository.findById(id).orElseThrow(() -> ApiException.notFound("Tache"));
    }

    public Task create(Task request, AuthPrincipal actor) {
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw ApiException.badRequest("Le titre de la tache est obligatoire");
        }
        if (request.getProductId() != null && !productRepository.existsById(request.getProductId())) {
            throw ApiException.notFound("Produit");
        }

        Task task = Task.builder()
                .title(request.getTitle().trim())
                .description(request.getDescription())
                .productId(request.getProductId())
                .stageType(request.getStageType())
                .assigneeId(request.getAssigneeId())
                .reporterId(actor != null ? actor.userId() : null)
                .status(request.getStatus() != null ? request.getStatus() : TaskStatus.TODO)
                .priority(request.getPriority())
                .dueDate(request.getDueDate())
                .estimatedHours(request.getEstimatedHours())
                .checklist(request.getChecklist() != null ? request.getChecklist() : new ArrayList<>())
                .tags(request.getTags() != null ? request.getTags() : new ArrayList<>())
                .build();

        Task saved = taskRepository.save(task);
        auditService.record(actor, AuditAction.CREATE, "Task", saved.getId(), saved.getProductId(),
                "Creation de la tache : " + saved.getTitle());
        notificationService.notify(saved.getAssigneeId(), NotificationType.TASK_ASSIGNED, Severity.MINOR,
                "Nouvelle tache affectee", saved.getTitle()
                        + (saved.getDueDate() != null ? " (echeance : " + saved.getDueDate() + ")" : ""),
                "/tasks", actor);
        return saved;
    }

    public Task update(String id, Task changes, AuthPrincipal actor) {
        Task task = findById(id);
        String previousAssignee = task.getAssigneeId();

        if (changes.getTitle() != null) task.setTitle(changes.getTitle());
        if (changes.getDescription() != null) task.setDescription(changes.getDescription());
        if (changes.getAssigneeId() != null) task.setAssigneeId(changes.getAssigneeId());
        if (changes.getPriority() != null) task.setPriority(changes.getPriority());
        if (changes.getDueDate() != null) task.setDueDate(changes.getDueDate());
        if (changes.getStageType() != null) task.setStageType(changes.getStageType());
        if (changes.getEstimatedHours() != null) task.setEstimatedHours(changes.getEstimatedHours());
        if (changes.getSpentHours() != null) task.setSpentHours(changes.getSpentHours());
        if (changes.getChecklist() != null) task.setChecklist(changes.getChecklist());
        if (changes.getTags() != null) task.setTags(changes.getTags());
        task.setUpdatedAt(LocalDateTime.now());

        Task saved = taskRepository.save(task);
        auditService.record(actor, AuditAction.UPDATE, "Task", id, task.getProductId(),
                "Mise a jour de la tache : " + task.getTitle());

        if (changes.getAssigneeId() != null && !changes.getAssigneeId().equals(previousAssignee)) {
            notificationService.notify(changes.getAssigneeId(), NotificationType.TASK_ASSIGNED, Severity.MINOR,
                    "Tache qui vous est affectee", task.getTitle(), "/tasks", actor);
        }
        return saved;
    }

    /** Kanban drag-and-drop lands here. */
    public Task changeStatus(String id, TaskStatus status, AuthPrincipal actor) {
        Task task = findById(id);
        TaskStatus previous = task.getStatus();
        task.setStatus(status);
        task.setUpdatedAt(LocalDateTime.now());
        task.setCompletedAt(status == TaskStatus.DONE ? LocalDateTime.now() : null);

        Task saved = taskRepository.save(task);
        auditService.record(actor, AuditAction.STATUS_CHANGE, "Task", id, task.getProductId(),
                "Tache " + task.getTitle() + " : " + previous + " -> " + status,
                previous != null ? previous.name() : null, status.name());

        if (status == TaskStatus.DONE && task.getReporterId() != null) {
            notificationService.notify(task.getReporterId(), NotificationType.GENERIC, Severity.MINOR,
                    "Tache terminee", task.getTitle() + " a ete cloturee.", "/tasks", actor);
        }
        return saved;
    }

    public Task toggleChecklistItem(String id, int index, AuthPrincipal actor) {
        Task task = findById(id);
        if (task.getChecklist() == null || index < 0 || index >= task.getChecklist().size()) {
            throw ApiException.badRequest("Element de checklist introuvable");
        }
        Task.ChecklistItem item = task.getChecklist().get(index);
        item.setDone(!item.isDone());
        task.setUpdatedAt(LocalDateTime.now());
        return taskRepository.save(task);
    }

    public void delete(String id, AuthPrincipal actor) {
        Task task = findById(id);
        taskRepository.deleteById(id);
        auditService.record(actor, AuditAction.DELETE, "Task", id, task.getProductId(),
                "Suppression de la tache : " + task.getTitle());
    }

    public List<Task> overdue() {
        return taskRepository.findByDueDateBeforeAndStatusNotIn(LocalDate.now(), CLOSED_STATUSES);
    }
}
