package com.multitenant.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public class ProjectDtos {

    public record CreateProjectRequest(
            @NotBlank(message = "Project name is required")
            @Size(max = 120)
            String name,

            @Size(max = 1000)
            String description,

            String color
    ) {}

    public record UpdateProjectRequest(
            @NotBlank(message = "Project name is required")
            @Size(max = 120)
            String name,

            @Size(max = 1000)
            String description,

            String color,

            String status
    ) {}

    public record ProjectResponse(
            Long id,
            String name,
            String description,
            String color,
            String status,
            Long createdById,
            LocalDateTime createdAt,
            long taskCount
    ) {}
}
