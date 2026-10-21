package com.multitenant.workspace.dto;

import com.multitenant.workspace.entity.Workspace;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class WorkspaceDtos {

    public record CreateWorkspaceRequest(
            @NotBlank(message = "Workspace name is required")
            @Size(max = 100)
            String name,

            @Pattern(regexp = "^[a-z0-9-]+$", message = "Slug must contain only lowercase letters, numbers, and hyphens")
            String slug,

            @Size(max = 500)
            String description
    ) {}

    public record UpdateWorkspaceRequest(
            @NotBlank(message = "Workspace name is required")
            @Size(max = 100)
            String name,

            @Size(max = 500)
            String description,

            Workspace.PlanType plan
    ) {}

    public record WorkspaceResponse(
            String id,
            String name,
            String slug,
            String description,
            Workspace.PlanType plan,
            String role,
            long memberCount,
            Long ownerId
    ) {}
}
