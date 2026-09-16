package com.cmrt.pfe.services;

import com.cmrt.pfe.dto.DashboardDtos;
import com.cmrt.pfe.models.AuditLog;
import com.cmrt.pfe.models.EngineeringChange;
import com.cmrt.pfe.models.Issue;
import com.cmrt.pfe.models.Product;
import com.cmrt.pfe.models.ProjectStage;
import com.cmrt.pfe.models.Reservation;
import com.cmrt.pfe.models.Task;
import com.cmrt.pfe.models.TestResource;
import com.cmrt.pfe.models.User;
import com.cmrt.pfe.models.enums.ChangeStatus;
import com.cmrt.pfe.models.enums.IssueStatus;
import com.cmrt.pfe.models.enums.ProductStatus;
import com.cmrt.pfe.models.enums.ProjectType;
import com.cmrt.pfe.models.enums.ReservationStatus;
import com.cmrt.pfe.models.enums.ResourceStatus;
import com.cmrt.pfe.models.enums.Severity;
import com.cmrt.pfe.models.enums.StageStatus;
import com.cmrt.pfe.models.enums.TaskStatus;
import com.cmrt.pfe.repositories.DocumentRepository;
import com.cmrt.pfe.repositories.EngineeringChangeRepository;
import com.cmrt.pfe.repositories.IssueRepository;
import com.cmrt.pfe.repositories.ProductRepository;
import com.cmrt.pfe.repositories.ProjectStageRepository;
import com.cmrt.pfe.repositories.ReservationRepository;
import com.cmrt.pfe.repositories.ResourceRepository;
import com.cmrt.pfe.repositories.TaskRepository;
import com.cmrt.pfe.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Computes the indicators shown on the dashboards. Everything is derived here so the
 * client stays a rendering layer and the same numbers appear on every screen.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ProductRepository productRepository;
    private final ProjectStageRepository stageRepository;
    private final IssueRepository issueRepository;
    private final TaskRepository taskRepository;
    private final ResourceRepository resourceRepository;
    private final ReservationRepository reservationRepository;
    private final EngineeringChangeRepository changeRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    private static final Set<IssueStatus> OPEN_ISSUES =
            EnumSet.of(IssueStatus.OPEN, IssueStatus.ACKNOWLEDGED, IssueStatus.IN_PROGRESS);
    private static final Set<TaskStatus> CLOSED_TASKS =
            EnumSet.of(TaskStatus.DONE, TaskStatus.CANCELLED);

    public DashboardDtos.DashboardResponse overview() {
        List<Product> products = productRepository.findAll();
        List<ProjectStage> stages = stageRepository.findAll();
        List<Issue> issues = issueRepository.findAll();
        List<Task> tasks = taskRepository.findAll();
        List<TestResource> resources = resourceRepository.findAll();
        List<Reservation> reservations = reservationRepository.findAll();
        List<EngineeringChange> changes = changeRepository.findAll();

        return new DashboardDtos.DashboardResponse(
                buildKpis(products, stages, issues, tasks, resources, reservations, changes),
                productsByStage(products),
                countBy(products, p -> p.getStatus() == null ? "INCONNU" : p.getStatus().name()),
                countBy(products, p -> p.getCustomer() == null ? "AUTRE" : p.getCustomer().name()),
                countBy(issues.stream().filter(i -> OPEN_ISSUES.contains(i.getStatus())).toList(),
                        i -> i.getSeverity() == null ? "MAJOR" : i.getSeverity().name()),
                countBy(issues.stream().filter(i -> OPEN_ISSUES.contains(i.getStatus())).toList(),
                        i -> i.getCategory() == null ? "AUTRE" : i.getCategory().name()),
                countBy(tasks, t -> t.getStatus() == null ? "TODO" : t.getStatus().name()),
                countBy(resources, r -> r.getStatus() == null ? "AVAILABLE" : r.getStatus().name()),
                monthlyThroughput(stages),
                buildAlerts(products, stages, issues, tasks, reservations),
                recentActivity());
    }

    private DashboardDtos.KpiSummary buildKpis(List<Product> products, List<ProjectStage> stages,
                                               List<Issue> issues, List<Task> tasks,
                                               List<TestResource> resources, List<Reservation> reservations,
                                               List<EngineeringChange> changes) {
        List<Issue> openIssues = issues.stream().filter(i -> OPEN_ISSUES.contains(i.getStatus())).toList();
        List<Task> openTasks = tasks.stream().filter(t -> !CLOSED_TASKS.contains(t.getStatus())).toList();

        // Average time to resolve, over issues that actually closed.
        double avgResolution = issues.stream()
                .map(Issue::getResolutionHours)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .average()
                .orElse(0);

        // Share of closed gates that finished on or before their planned date.
        List<ProjectStage> closedStages = stages.stream()
                .filter(s -> s.getStatus() == StageStatus.COMPLETED && s.getActualEnd() != null && s.getPlannedEnd() != null)
                .toList();
        double onTime = closedStages.isEmpty() ? 100 : 100.0 * closedStages.stream()
                .filter(s -> !s.getActualEnd().toLocalDate().isAfter(s.getPlannedEnd()))
                .count() / closedStages.size();

        double avgChangeCycle = changes.stream()
                .map(EngineeringChange::getCycleTimeDays)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .average()
                .orElse(0);

        long busyResources = resources.stream()
                .filter(r -> r.getStatus() == ResourceStatus.IN_USE || r.getStatus() == ResourceStatus.RESERVED)
                .count();

        long atRisk = products.stream().filter(p -> {
            boolean lateStage = stages.stream()
                    .anyMatch(s -> s.getProductId().equals(p.getId()) && s.isOverdue());
            boolean lateSop = p.getTargetSopDate() != null && p.getActualSopDate() == null
                    && p.getTargetSopDate().isBefore(LocalDate.now());
            return lateStage || lateSop;
        }).count();

        return new DashboardDtos.KpiSummary(
                products.size(),
                products.stream().filter(p -> p.getProjectType() == ProjectType.NPI).count(),
                products.stream().filter(p -> p.getProjectType() == ProjectType.PRODUCTION).count(),
                products.stream().filter(p -> p.getStatus() == ProductStatus.MASS_PRODUCTION).count(),
                products.stream().filter(Product::isBlocked).count(),
                atRisk,
                round(products.stream().mapToInt(Product::getProgressPercent).average().orElse(0)),
                openIssues.size(),
                openIssues.stream()
                        .filter(i -> i.getSeverity() == Severity.CRITICAL || i.getSeverity() == Severity.BLOCKING)
                        .count(),
                openIssues.stream().filter(Issue::isSlaBreached).count(),
                round(avgResolution),
                openTasks.size(),
                openTasks.stream().filter(Task::isOverdue).count(),
                stages.stream().filter(ProjectStage::isOverdue).count(),
                stages.stream().filter(s -> s.getStatus() == StageStatus.PENDING_APPROVAL).count(),
                reservations.stream().filter(r -> r.getStatus() == ReservationStatus.PENDING).count(),
                resources.stream().filter(r -> r.getStatus() == ResourceStatus.AVAILABLE).count(),
                resources.size(),
                resources.isEmpty() ? 0 : round(100.0 * busyResources / resources.size()),
                changes.stream()
                        .filter(c -> c.getStatus() != ChangeStatus.CLOSED && c.getStatus() != ChangeStatus.REJECTED)
                        .count(),
                round(avgChangeCycle),
                round(onTime));
    }

    /** How many products currently sit at each gate - the pipeline funnel. */
    private List<DashboardDtos.Metric> productsByStage(List<Product> products) {
        Map<String, Long> counts = products.stream()
                .filter(p -> p.getCurrentStage() != null)
                .collect(Collectors.groupingBy(p -> p.getCurrentStage().name(), Collectors.counting()));

        return com.cmrt.pfe.models.enums.StageType.pipeline().stream()
                .map(stage -> new DashboardDtos.Metric(
                        stage.name(), stage.getLabel(), counts.getOrDefault(stage.name(), 0L)))
                .toList();
    }

    /** Gates closed per month over the last six months - delivery throughput. */
    private List<DashboardDtos.Metric> monthlyThroughput(List<ProjectStage> stages) {
        List<DashboardDtos.Metric> metrics = new ArrayList<>();
        YearMonth now = YearMonth.now();
        for (int i = 5; i >= 0; i--) {
            YearMonth month = now.minusMonths(i);
            long closed = stages.stream()
                    .filter(s -> s.getActualEnd() != null)
                    .filter(s -> YearMonth.from(s.getActualEnd()).equals(month))
                    .count();
            metrics.add(new DashboardDtos.Metric(month.toString(),
                    String.format("%02d/%d", month.getMonthValue(), month.getYear()), closed));
        }
        return metrics;
    }

    /** The "needs attention" panel: overdue gates, breached SLAs and waiting decisions. */
    private List<DashboardDtos.AlertItem> buildAlerts(List<Product> products, List<ProjectStage> stages,
                                                      List<Issue> issues, List<Task> tasks,
                                                      List<Reservation> reservations) {
        Map<String, Product> byId = products.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity(), (a, b) -> a));
        List<DashboardDtos.AlertItem> alerts = new ArrayList<>();

        stages.stream().filter(ProjectStage::isOverdue).forEach(stage -> {
            Product product = byId.get(stage.getProductId());
            alerts.add(new DashboardDtos.AlertItem("STAGE_OVERDUE", "CRITICAL",
                    "Jalon en retard : " + stage.getStageType().getLabel(),
                    (product != null ? product.getReference() : "Produit")
                            + " - echeance depassee depuis le " + stage.getPlannedEnd(),
                    "/products/" + stage.getProductId(),
                    stage.getPlannedEnd() != null ? stage.getPlannedEnd().toString() : null));
        });

        issues.stream()
                .filter(i -> OPEN_ISSUES.contains(i.getStatus()))
                .filter(Issue::isSlaBreached)
                .forEach(issue -> {
                    Product product = byId.get(issue.getProductId());
                    alerts.add(new DashboardDtos.AlertItem("SLA_BREACH", "CRITICAL",
                            "SLA depasse : " + issue.getReference(),
                            (product != null ? product.getReference() + " - " : "") + issue.getTitle(),
                            "/issues/" + issue.getId(),
                            issue.getCreatedAt() != null ? issue.getCreatedAt().toString() : null));
                });

        stages.stream()
                .filter(s -> s.getStatus() == StageStatus.PENDING_APPROVAL)
                .forEach(stage -> {
                    Product product = byId.get(stage.getProductId());
                    alerts.add(new DashboardDtos.AlertItem("APPROVAL_PENDING", "MAJOR",
                            "Validation en attente : " + stage.getStageType().getLabel(),
                            (product != null ? product.getReference() : "Produit") + " attend une approbation",
                            "/products/" + stage.getProductId(),
                            stage.getUpdatedAt() != null ? stage.getUpdatedAt().toString() : null));
                });

        reservations.stream()
                .filter(r -> r.getStatus() == ReservationStatus.PENDING)
                .forEach(reservation -> alerts.add(new DashboardDtos.AlertItem("RESERVATION_PENDING", "MAJOR",
                        "Reservation a traiter",
                        "Creneau demande du " + reservation.getStartDate() + " au " + reservation.getEndDate(),
                        "/control",
                        reservation.getCreatedAt() != null ? reservation.getCreatedAt().toString() : null)));

        tasks.stream()
                .filter(Task::isOverdue)
                .forEach(task -> alerts.add(new DashboardDtos.AlertItem("TASK_OVERDUE", "MAJOR",
                        "Tache en retard : " + task.getTitle(),
                        "Echeance " + task.getDueDate(),
                        "/tasks",
                        task.getDueDate() != null ? task.getDueDate().toString() : null)));

        // Most urgent first, then most recent.
        return alerts.stream()
                .sorted(Comparator
                        .comparing((DashboardDtos.AlertItem a) -> "CRITICAL".equals(a.severity()) ? 0 : 1)
                        .thenComparing(a -> a.date() == null ? "" : a.date(), Comparator.reverseOrder()))
                .limit(20)
                .toList();
    }

    private List<DashboardDtos.ActivityItem> recentActivity() {
        return auditService.recent(15).stream()
                .map(this::toActivity)
                .toList();
    }

    private DashboardDtos.ActivityItem toActivity(AuditLog log) {
        return new DashboardDtos.ActivityItem(
                log.getActorName(),
                log.getAction() != null ? log.getAction().name() : null,
                log.getSummary(),
                log.getEntityType(),
                log.getProductId(),
                log.getTimestamp() != null ? log.getTimestamp().toString() : null);
    }

    /** Open workload per active user, so a lead can see who is saturated. */
    public List<DashboardDtos.WorkloadItem> workload() {
        List<User> users = userRepository.findByActiveTrue();
        List<Task> tasks = taskRepository.findAll();
        List<Issue> issues = issueRepository.findAll();
        List<ProjectStage> stages = stageRepository.findAll();
        List<Product> products = productRepository.findAll();

        return users.stream().map(user -> {
            List<Task> userTasks = tasks.stream()
                    .filter(t -> user.getId().equals(t.getAssigneeId()))
                    .filter(t -> !CLOSED_TASKS.contains(t.getStatus()))
                    .toList();
            return new DashboardDtos.WorkloadItem(
                    user.getId(),
                    user.getFullName(),
                    user.getRole() != null ? user.getRole().name() : null,
                    userTasks.size(),
                    userTasks.stream().filter(Task::isOverdue).count(),
                    issues.stream()
                            .filter(i -> user.getId().equals(i.getAssigneeId()))
                            .filter(i -> OPEN_ISSUES.contains(i.getStatus()))
                            .count(),
                    stages.stream()
                            .filter(s -> user.getId().equals(s.getOwnerId()))
                            .filter(s -> !s.isTerminal())
                            .count(),
                    products.stream()
                            .filter(p -> user.getId().equals(p.getMethodisteId())
                                    || user.getId().equals(p.getQualiticienId())
                                    || user.getId().equals(p.getChefProjetId()))
                            .count());
        }).sorted(Comparator.comparingLong(DashboardDtos.WorkloadItem::openTasks).reversed()).toList();
    }

    /** Counts a collection by a derived key, preserving a stable order for the charts. */
    private <T> List<DashboardDtos.Metric> countBy(List<T> items, Function<T, String> classifier) {
        Map<String, Long> counts = items.stream()
                .collect(Collectors.groupingBy(classifier, LinkedHashMap::new, Collectors.counting()));
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new DashboardDtos.Metric(e.getKey(), e.getKey(), e.getValue()))
                .toList();
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    /** Total document count, exposed for the admin overview. */
    public long documentCount() {
        return documentRepository.count();
    }
}
