package com.multitenant.workspace.controller;

import com.multitenant.workspace.dto.TaskDtos;
import com.multitenant.workspace.security.UserPrincipal;
import com.multitenant.workspace.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public ResponseEntity<List<TaskDtos.TaskResponse>> getTasks(@RequestParam(required = false) Long projectId) {
        return ResponseEntity.ok(taskService.getTasks(projectId));
    }

    @PostMapping
    public ResponseEntity<TaskDtos.TaskResponse> createTask(
            @Valid @RequestBody TaskDtos.CreateTaskRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        return ResponseEntity.ok(taskService.createTask(
                request,
                userPrincipal.getId(),
                userPrincipal.getFullName()
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskDtos.TaskResponse> getTask(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.getTask(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TaskDtos.TaskResponse> updateTask(
            @PathVariable Long id,
            @Valid @RequestBody TaskDtos.UpdateTaskRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        return ResponseEntity.ok(taskService.updateTask(
                id,
                request,
                userPrincipal.getId(),
                userPrincipal.getFullName()
        ));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<TaskDtos.TaskResponse> updateTaskStatus(
            @PathVariable Long id,
            @Valid @RequestBody TaskDtos.UpdateTaskStatusRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        return ResponseEntity.ok(taskService.updateTaskStatus(
                id,
                request,
                userPrincipal.getId(),
                userPrincipal.getFullName()
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteTask(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        taskService.deleteTask(id, userPrincipal.getId(), userPrincipal.getFullName());
        return ResponseEntity.ok(Map.of("message", "Task deleted successfully"));
    }
}
