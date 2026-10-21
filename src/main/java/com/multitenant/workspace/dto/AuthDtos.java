package com.multitenant.workspace.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public class AuthDtos {

    public record RegisterRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Invalid email format")
            String email,

            @NotBlank(message = "Password is required")
            @Size(min = 6, message = "Password must be at least 6 characters")
            String password,

            @NotBlank(message = "Full name is required")
            String fullName,

            String workspaceName
    ) {}

    public record LoginRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Invalid email format")
            String email,

            @NotBlank(message = "Password is required")
            String password
    ) {}

    public record UserProfileResponse(
            Long id,
            String email,
            String fullName,
            String avatarColor
    ) {}

    public record AuthResponse(
            String token,
            UserProfileResponse user,
            List<WorkspaceDtos.WorkspaceResponse> workspaces,
            String defaultWorkspaceId
    ) {}
}
