package com.cmrt.pfe.services;

import com.cmrt.pfe.models.Issue;
import com.cmrt.pfe.models.Product;
import com.cmrt.pfe.models.ProjectStage;
import com.cmrt.pfe.models.Task;
import com.cmrt.pfe.models.enums.NotificationType;
import com.cmrt.pfe.models.enums.Severity;
import com.cmrt.pfe.repositories.ProductRepository;
import com.cmrt.pfe.repositories.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The automatic side of the platform: a daily sweep that warns owners about deadlines
 * before they are missed and escalates the ones already breached. Without it the
 * indicators would be accurate but nobody would be told.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeadlineMonitor {

    private final WorkflowService workflowService;
    private final TaskRepository taskRepository;
    private final ProductRepository productRepository;
    private final IssueService issueService;
    private final NotificationService notificationService;

    /** Every working morning at 07:00. */
    @Scheduled(cron = "${app.scheduling.deadline-cron:0 0 7 * * MON-FRI}")
    public void sweep() {
        log.info("Balayage des echeances demarre");
        int alerts = 0;
        alerts += alertOverdueStages();
        alerts += alertTasks();
        alerts += alertSlaBreaches();
        log.info("Balayage des echeances termine : {} alerte(s) emise(s)", alerts);
    }

    private int alertOverdueStages() {
        List<ProjectStage> overdue = workflowService.overdueStages();
        if (overdue.isEmpty()) {
            return 0;
        }
        Map<String, Product> products = productRepository
                .findAllById(overdue.stream().map(ProjectStage::getProductId).distinct().toList())
                .stream().collect(Collectors.toMap(Product::getId, Function.identity(), (a, b) -> a));

        overdue.forEach(stage -> {
            Product product = products.get(stage.getProductId());
            String reference = product != null ? product.getReference() : "Produit";
            notificationService.notify(stage.getOwnerId(), NotificationType.DEADLINE_ALERT, Severity.CRITICAL,
                    "Jalon en retard : " + stage.getStageType().getLabel(),
                    reference + " - echeance du " + stage.getPlannedEnd() + " depassee.",
                    "/products/" + stage.getProductId(), null);
            // Escalate to the project lead so a slip is visible above the owner.
            if (product != null && product.getChefProjetId() != null) {
                notificationService.notify(product.getChefProjetId(), NotificationType.DEADLINE_ALERT,
                        Severity.MAJOR, "Retard sur " + reference,
                        "Le jalon " + stage.getStageType().getLabel() + " a depasse son echeance.",
                        "/products/" + stage.getProductId(), null);
            }
        });
        return overdue.size();
    }

    private int alertTasks() {
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusDays(2);
        int count = 0;

        for (Task task : taskRepository.findAll()) {
            if (task.getDueDate() == null || TaskService.CLOSED_STATUSES.contains(task.getStatus())) {
                continue;
            }
            if (task.getDueDate().isBefore(today)) {
                notificationService.notify(task.getAssigneeId(), NotificationType.TASK_OVERDUE, Severity.MAJOR,
                        "Tache en retard", task.getTitle() + " (echeance " + task.getDueDate() + ")",
                        "/tasks", null);
                count++;
            } else if (!task.getDueDate().isAfter(horizon)) {
                notificationService.notify(task.getAssigneeId(), NotificationType.TASK_DUE_SOON, Severity.MINOR,
                        "Echeance proche", task.getTitle() + " est due le " + task.getDueDate(),
                        "/tasks", null);
                count++;
            }
        }
        return count;
    }

    private int alertSlaBreaches() {
        List<Issue> breached = issueService.findOpen().stream()
                .filter(Issue::isSlaBreached)
                .filter(i -> Objects.nonNull(i.getAssigneeId()))
                .toList();

        breached.forEach(issue -> notificationService.notify(issue.getAssigneeId(),
                NotificationType.DEADLINE_ALERT, Severity.CRITICAL,
                "SLA depasse : " + issue.getReference(),
                issue.getTitle() + " - delai de reponse de " + issue.getSlaHours() + "h depasse.",
                "/issues/" + issue.getId(), null));
        return breached.size();
    }
}
