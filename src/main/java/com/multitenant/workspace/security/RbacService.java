package com.multitenant.workspace.security;

import com.multitenant.workspace.entity.WorkspaceMember;
import com.multitenant.workspace.repository.WorkspaceMemberRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class RbacService {

    private final WorkspaceMemberRepository workspaceMemberRepository;

    private static final Map<WorkspaceMember.MemberRole, Set<Permission>> ROLE_PERMISSIONS = new EnumMap<>(WorkspaceMember.MemberRole.class);

    static {
        // OWNER: Unrestricted permissions across all modules
        ROLE_PERMISSIONS.put(WorkspaceMember.MemberRole.OWNER, EnumSet.allOf(Permission.class));

        // ADMIN: All operational permissions except company deletion/ownership transfer
        ROLE_PERMISSIONS.put(WorkspaceMember.MemberRole.ADMIN, EnumSet.of(
                Permission.MANAGE_COMPANY,
                Permission.MANAGE_BILLING,
                Permission.INVITE_MEMBERS,
                Permission.MANAGE_ROLES,
                Permission.CREATE_PROJECTS,
                Permission.DELETE_PROJECTS,
                Permission.MANAGE_TASKS,
                Permission.VIEW_AUDIT_LOGS,
                Permission.TRIGGER_WEBHOOKS
        ));

        // BILLING_ADMIN: Financial and billing permissions only, isolated from project data manipulation
        ROLE_PERMISSIONS.put(WorkspaceMember.MemberRole.BILLING_ADMIN, EnumSet.of(
                Permission.MANAGE_BILLING,
                Permission.VIEW_AUDIT_LOGS,
                Permission.TRIGGER_WEBHOOKS
        ));

        // MEMBER: Standard project and task execution role
        ROLE_PERMISSIONS.put(WorkspaceMember.MemberRole.MEMBER, EnumSet.of(
                Permission.CREATE_PROJECTS,
                Permission.MANAGE_TASKS,
                Permission.VIEW_AUDIT_LOGS
        ));

        // VIEWER: Read-only access to audit logs and boards
        ROLE_PERMISSIONS.put(WorkspaceMember.MemberRole.VIEWER, EnumSet.of(
                Permission.VIEW_AUDIT_LOGS
        ));
    }

    public RbacService(WorkspaceMemberRepository workspaceMemberRepository) {
        this.workspaceMemberRepository = workspaceMemberRepository;
    }

    public boolean hasPermission(Long userId, String workspaceId, Permission permission) {
        if (userId == null || workspaceId == null) {
            return false;
        }

        return workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .map(m -> {
                    Set<Permission> granted = ROLE_PERMISSIONS.getOrDefault(m.getRole(), Collections.emptySet());
                    return granted.contains(permission);
                })
                .orElse(false);
    }

    public void assertPermission(Long userId, String workspaceId, Permission permission) {
        if (!hasPermission(userId, workspaceId, permission)) {
            String roleName = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                    .map(m -> m.getRole().name())
                    .orElse("NONE");
            throw new SecurityException("RBAC Access Denied: Your role '" + roleName +
                    "' does not have permission '" + permission.name() + "' (" + permission.getDescription() + ").");
        }
    }

    public Map<String, Object> getRbacTeachingMatrix() {
        Map<String, Object> matrix = new LinkedHashMap<>();
        Map<String, List<String>> roleMap = new LinkedHashMap<>();

        for (WorkspaceMember.MemberRole role : WorkspaceMember.MemberRole.values()) {
            Set<Permission> perms = ROLE_PERMISSIONS.getOrDefault(role, Collections.emptySet());
            roleMap.put(role.name(), perms.stream().map(Enum::name).toList());
        }

        List<Map<String, String>> permList = Arrays.stream(Permission.values())
                .map(p -> Map.of("name", p.name(), "description", p.getDescription()))
                .toList();

        matrix.put("roles", roleMap);
        matrix.put("permissions", permList);
        return matrix;
    }
}
