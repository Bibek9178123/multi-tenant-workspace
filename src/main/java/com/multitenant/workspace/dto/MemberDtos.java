package com.multitenant.workspace.dto;

import com.multitenant.workspace.entity.WorkspaceMember;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public class MemberDtos {

    public record InviteMemberRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Valid email is required")
            String email,

            @NotNull(message = "Role is required")
            WorkspaceMember.MemberRole role
    ) {}

    public record UpdateRoleRequest(
            @NotNull(message = "Role is required")
            WorkspaceMember.MemberRole role
    ) {}

    public record MemberResponse(
            Long id,
            Long userId,
            String email,
            String fullName,
            WorkspaceMember.MemberRole role,
            WorkspaceMember.MemberStatus status,
            LocalDateTime joinedAt
    ) {}
}
