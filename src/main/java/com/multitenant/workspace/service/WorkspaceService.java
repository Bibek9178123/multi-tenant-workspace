package com.multitenant.workspace.service;

import com.multitenant.workspace.dto.WorkspaceDtos;
import com.multitenant.workspace.entity.Project;
import com.multitenant.workspace.entity.Task;
import com.multitenant.workspace.entity.Workspace;
import com.multitenant.workspace.entity.WorkspaceMember;
import com.multitenant.workspace.repository.*;
import com.multitenant.workspace.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final ActivityLogRepository activityLogRepository;
    private final ActivityLogService activityLogService;

    public WorkspaceService(WorkspaceRepository workspaceRepository,
                            WorkspaceMemberRepository workspaceMemberRepository,
                            ProjectRepository projectRepository,
                            TaskRepository taskRepository,
                            ActivityLogRepository activityLogRepository,
                            ActivityLogService activityLogService) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.activityLogRepository = activityLogRepository;
        this.activityLogService = activityLogService;
    }

    @Transactional(readOnly = true)
    public List<WorkspaceDtos.WorkspaceResponse> getUserWorkspaces(Long userId) {
        List<WorkspaceMember> memberships = workspaceMemberRepository.findByUserIdAndStatus(
                userId, WorkspaceMember.MemberStatus.ACTIVE);

        List<WorkspaceDtos.WorkspaceResponse> responses = new ArrayList<>();
        for (WorkspaceMember member : memberships) {
            workspaceRepository.findById(member.getWorkspaceId()).ifPresent(ws -> {
                long count = workspaceMemberRepository.findByWorkspaceId(ws.getId()).size();
                responses.add(new WorkspaceDtos.WorkspaceResponse(
                        ws.getId(),
                        ws.getName(),
                        ws.getSlug(),
                        ws.getDescription(),
                        ws.getPlan(),
                        member.getRole().name(),
                        count,
                        ws.getOwnerId()
                ));
            });
        }
        return responses;
    }

    @Transactional
    public WorkspaceDtos.WorkspaceResponse createWorkspace(WorkspaceDtos.CreateWorkspaceRequest request,
                                                           Long userId, String userEmail, String userName) {
        String baseSlug = request.slug();
        if (baseSlug == null || baseSlug.isBlank()) {
            baseSlug = request.name().toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9]", "-")
                    .replaceAll("-+", "-")
                    .replaceAll("^-|-$", "");
            if (baseSlug.isBlank()) {
                baseSlug = "workspace";
            }
        }

        String finalSlug = baseSlug;
        int suffix = 1;
        while (workspaceRepository.existsBySlug(finalSlug)) {
            finalSlug = baseSlug + "-" + suffix++;
        }

        String workspaceId = "ws_" + UUID.randomUUID().toString().substring(0, 8);

        Workspace workspace = new Workspace(
                workspaceId,
                request.name(),
                finalSlug,
                request.description(),
                userId,
                Workspace.PlanType.FREE
        );
        workspaceRepository.save(workspace);

        // Add creator as OWNER
        WorkspaceMember ownerMember = new WorkspaceMember(
                workspaceId,
                userId,
                userEmail,
                userName,
                WorkspaceMember.MemberRole.OWNER,
                WorkspaceMember.MemberStatus.ACTIVE
        );
        workspaceMemberRepository.save(ownerMember);

        // Seed initial project & tasks in new tenant context
        String prevTenant = TenantContext.getCurrentTenant();
        try {
            TenantContext.setCurrentTenant(workspaceId);

            Project defaultProject = new Project("Product Roadmap", "Central workspace project for sprints & roadmaps", "#6366f1", userId);
            defaultProject.setTenantId(workspaceId);
            defaultProject = projectRepository.save(defaultProject);

            Task task1 = new Task(defaultProject.getId(), "Set up team & invite collaborators",
                    "Add team members to this workspace and assign roles (Admin, Member, Viewer).",
                    Task.TaskStatus.DONE, Task.TaskPriority.HIGH, userId, userName, userId, LocalDate.now());
            task1.setTenantId(workspaceId);
            taskRepository.save(task1);

            Task task2 = new Task(defaultProject.getId(), "Define workspace OKRs and project milestones",
                    "Break down the quarterly deliverables into actionable tasks.",
                    Task.TaskStatus.IN_PROGRESS, Task.TaskPriority.MEDIUM, userId, userName, userId, LocalDate.now().plusDays(7));
            task2.setTenantId(workspaceId);
            taskRepository.save(task2);

            Task task3 = new Task(defaultProject.getId(), "Review multi-tenant database partitioning",
                    "Evaluate discriminator column indexing and tenant row-level queries.",
                    Task.TaskStatus.TODO, Task.TaskPriority.HIGH, null, "Unassigned", userId, LocalDate.now().plusDays(14));
            task3.setTenantId(workspaceId);
            taskRepository.save(task3);

            activityLogService.log("WORKSPACE_CREATED", "Workspace '" + workspace.getName() + "' created.", userId, userName);
        } finally {
            if (prevTenant != null) {
                TenantContext.setCurrentTenant(prevTenant);
            } else {
                TenantContext.clear();
            }
        }

        return new WorkspaceDtos.WorkspaceResponse(
                workspace.getId(),
                workspace.getName(),
                workspace.getSlug(),
                workspace.getDescription(),
                workspace.getPlan(),
                WorkspaceMember.MemberRole.OWNER.name(),
                1,
                workspace.getOwnerId()
        );
    }

    @Transactional(readOnly = true)
    public WorkspaceDtos.WorkspaceResponse getWorkspace(String workspaceId, Long userId) {
        Workspace ws = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceId));

        WorkspaceMember member = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new SecurityException("You do not have access to this workspace"));

        long count = workspaceMemberRepository.findByWorkspaceId(workspaceId).size();

        return new WorkspaceDtos.WorkspaceResponse(
                ws.getId(),
                ws.getName(),
                ws.getSlug(),
                ws.getDescription(),
                ws.getPlan(),
                member.getRole().name(),
                count,
                ws.getOwnerId()
        );
    }

    @Transactional
    public WorkspaceDtos.WorkspaceResponse updateWorkspace(String workspaceId,
                                                           WorkspaceDtos.UpdateWorkspaceRequest request,
                                                           Long userId, String userName) {
        Workspace ws = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceId));

        WorkspaceMember member = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new SecurityException("You do not have access to this workspace"));

        if (member.getRole() != WorkspaceMember.MemberRole.OWNER && member.getRole() != WorkspaceMember.MemberRole.ADMIN) {
            throw new SecurityException("Only workspace OWNER or ADMIN can modify settings");
        }

        ws.setName(request.name());
        if (request.description() != null) {
            ws.setDescription(request.description());
        }
        if (request.plan() != null && member.getRole() == WorkspaceMember.MemberRole.OWNER) {
            ws.setPlan(request.plan());
        }
        workspaceRepository.save(ws);

        activityLogService.log("WORKSPACE_UPDATED", "Workspace settings updated by " + userName, userId, userName);

        long count = workspaceMemberRepository.findByWorkspaceId(workspaceId).size();
        return new WorkspaceDtos.WorkspaceResponse(
                ws.getId(),
                ws.getName(),
                ws.getSlug(),
                ws.getDescription(),
                ws.getPlan(),
                member.getRole().name(),
                count,
                ws.getOwnerId()
        );
    }

    @Transactional
    public void deleteWorkspace(String workspaceId, Long userId) {
        Workspace ws = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceId));

        if (!ws.getOwnerId().equals(userId)) {
            throw new SecurityException("Only the workspace OWNER can delete this workspace");
        }

        // Clean up tenant resources
        taskRepository.deleteByTenantId(workspaceId);
        projectRepository.deleteByTenantId(workspaceId);
        activityLogRepository.deleteByTenantId(workspaceId);
        workspaceMemberRepository.deleteByWorkspaceId(workspaceId);
        workspaceRepository.delete(ws);
    }
}
