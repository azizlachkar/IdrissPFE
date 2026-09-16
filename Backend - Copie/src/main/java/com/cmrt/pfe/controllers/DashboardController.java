package com.cmrt.pfe.controllers;

import com.cmrt.pfe.dto.DashboardDtos;
import com.cmrt.pfe.models.AuditLog;
import com.cmrt.pfe.models.enums.Role;
import com.cmrt.pfe.security.RequireRole;
import com.cmrt.pfe.services.AuditService;
import com.cmrt.pfe.services.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final AuditService auditService;

    /** Every indicator, chart series and alert in one payload. */
    @GetMapping
    public ResponseEntity<DashboardDtos.DashboardResponse> overview() {
        return ResponseEntity.ok(dashboardService.overview());
    }

    @RequireRole({Role.ADMIN, Role.CHEF_PROJET, Role.RESPONSABLE_PRODUCTION})
    @GetMapping("/workload")
    public ResponseEntity<List<DashboardDtos.WorkloadItem>> workload() {
        return ResponseEntity.ok(dashboardService.workload());
    }

    /** Global traceability feed. */
    @RequireRole({Role.ADMIN, Role.CHEF_PROJET, Role.QUALITICIEN})
    @GetMapping("/activity")
    public ResponseEntity<List<AuditLog>> activity(@RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(auditService.recent(Math.min(limit, 200)));
    }
}
