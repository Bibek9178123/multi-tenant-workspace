package com.multitenant.workspace.dto;

import com.multitenant.workspace.entity.Task;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class TaskDtos {

    public record CreateTaskRequest(
            @NotNull(message = "Project ID is required")
            Long projectId,

            @NotBlank(message = "Task title is required")
            @Size(max = 200)
            String title,

            @Size(max = 2000)
            String description,

            Task.TaskStatus status,

            Task.TaskPriority priority,

            Long assigneeId,

            String assigneeName,

            LocalDate dueDate,

            String attachmentsJson
    ) {}

    public record UpdateTaskRequest(
            Long projectId,

            @NotBlank(message = "Task title is required")
            @Size(max = 200)
            String title,

            @Size(max = 2000)
            String description,

            Task.TaskStatus status,

            Task.TaskPriority priority,

            Long assigneeId,

            String assigneeName,

            LocalDate dueDate,

            String attachmentsJson
    ) {}

    public record UpdateTaskStatusRequest(
            @NotNull(message = "Status is required")
            Task.TaskStatus status,

            Integer position
    ) {}

    public record TaskResponse(
            Long id,
            Long projectId,
            String title,
            String description,
            Task.TaskStatus status,
            Task.TaskPriority priority,
            Long assigneeId,
            String assigneeName,
            Long createdById,
            LocalDate dueDate,
            Integer position,
            String attachmentsJson,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}
}
