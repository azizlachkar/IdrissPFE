package com.cmrt.pfe.controllers;

import com.cmrt.pfe.models.Task;
import com.cmrt.pfe.models.enums.TaskStatus;
import com.cmrt.pfe.security.AuthPrincipal;
import com.cmrt.pfe.security.CurrentUser;
import com.cmrt.pfe.services.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @GetMapping
    public ResponseEntity<List<Task>> all(@RequestParam(required = false) String productId,
                                          @RequestParam(required = false) String assigneeId) {
        if (productId != null) return ResponseEntity.ok(taskService.findByProduct(productId));
        if (assigneeId != null) return ResponseEntity.ok(taskService.findByAssignee(assigneeId));
        return ResponseEntity.ok(taskService.findAll());
    }

    @GetMapping("/mine")
    public ResponseEntity<List<Task>> mine(@CurrentUser AuthPrincipal principal) {
        return ResponseEntity.ok(taskService.myOpenTasks(principal.userId()));
    }

    @GetMapping("/overdue")
    public ResponseEntity<List<Task>> overdue() {
        return ResponseEntity.ok(taskService.overdue());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Task> byId(@PathVariable String id) {
        return ResponseEntity.ok(taskService.findById(id));
    }

    @PostMapping
    public ResponseEntity<Task> create(@RequestBody Task request, @CurrentUser AuthPrincipal principal) {
        return ResponseEntity.ok(taskService.create(request, principal));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Task> update(@PathVariable String id, @RequestBody Task request,
                                       @CurrentUser AuthPrincipal principal) {
        return ResponseEntity.ok(taskService.update(id, request, principal));
    }

    /** Kanban column moves. */
    @PatchMapping("/{id}/status")
    public ResponseEntity<Task> changeStatus(@PathVariable String id, @RequestBody Map<String, String> body,
                                             @CurrentUser AuthPrincipal principal) {
        return ResponseEntity.ok(taskService.changeStatus(id, TaskStatus.valueOf(body.get("status")), principal));
    }

    @PatchMapping("/{id}/checklist/{index}")
    public ResponseEntity<Task> toggleChecklist(@PathVariable String id, @PathVariable int index,
                                                @CurrentUser AuthPrincipal principal) {
        return ResponseEntity.ok(taskService.toggleChecklistItem(id, index, principal));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, @CurrentUser AuthPrincipal principal) {
        taskService.delete(id, principal);
        return ResponseEntity.noContent().build();
    }
}
