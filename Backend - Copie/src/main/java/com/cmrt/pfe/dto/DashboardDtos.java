package com.cmrt.pfe.dto;

import java.util.List;

/** Aggregated figures behind the dashboards. */
public final class DashboardDtos {

    private DashboardDtos() {
    }

    /** A named value, the shape every chart on the client consumes. */
    public record Metric(String key, String label, double value) {
    }

    public record KpiSummary(
            long totalProducts,
            long npiProducts,
            long productionProducts,
            long productsInMassProduction,
            long blockedProducts,
            long productsAtRisk,
            double averageProgress,
            long openIssues,
            long criticalIssues,
            long slaBreaches,
            double averageResolutionHours,
            long openTasks,
            long overdueTasks,
            long overdueStages,
            long pendingApprovals,
            long pendingReservations,
            long availableResources,
            long totalResources,
            double resourceUtilisation,
            long openChanges,
            double averageChangeCycleDays,
            double onTimeStageRate) {
    }

    /** Everything the dashboard screen needs, in one call. */
    public record DashboardResponse(
            KpiSummary kpis,
            List<Metric> productsByStage,
            List<Metric> productsByStatus,
            List<Metric> productsByCustomer,
            List<Metric> issuesBySeverity,
            List<Metric> issuesByCategory,
            List<Metric> tasksByStatus,
            List<Metric> resourcesByStatus,
            List<Metric> monthlyThroughput,
            List<AlertItem> alerts,
            List<ActivityItem> recentActivity) {
    }

    /** A row in the "needs attention" panel. */
    public record AlertItem(
            String type,
            String severity,
            String title,
            String detail,
            String link,
            String date) {
    }

    public record ActivityItem(
            String actorName,
            String action,
            String summary,
            String entityType,
            String productId,
            String timestamp) {
    }

    /** Per-person workload, for the team view. */
    public record WorkloadItem(
            String userId,
            String name,
            String role,
            long openTasks,
            long overdueTasks,
            long openIssues,
            long ownedStages,
            long products) {
    }
}
