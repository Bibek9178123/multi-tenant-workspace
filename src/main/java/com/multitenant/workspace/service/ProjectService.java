package com.multitenant.workspace.service;

import com.multitenant.workspace.dto.ProjectDtos;
import com.multitenant.workspace.entity.Project;
import com.multitenant.workspace.entity.Task;
import com.multitenant.workspace.repository.ProjectRepository;
import com.multitenant.workspace.repository.TaskRepository;
import com.multitenant.workspace.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final ActivityLogService activityLogService;
    private final com.multitenant.workspace.security.RbacService rbacService;

    public ProjectService(ProjectRepository projectRepository,
                          TaskRepository taskRepository,
                          ActivityLogService activityLogService,
                          com.multitenant.workspace.security.RbacService rbacService) {
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.activityLogService = activityLogService;
        this.rbacService = rbacService;
    }

    private String getRequiredTenantId() {
        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("Active tenant context is required");
        }
        return tenantId;
    }

    @Transactional(readOnly = true)
    public List<ProjectDtos.ProjectResponse> getProjects() {
        String tenantId = getRequiredTenantId();
        return projectRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(p -> new ProjectDtos.ProjectResponse(
                        p.getId(),
                        p.getName(),
                        p.getDescription(),
                        p.getColor(),
                        p.getStatus(),
                        p.getCreatedById(),
                        p.getCreatedAt(),
                        taskRepository.findByTenantIdAndProjectIdOrderByPositionAscCreatedAtDesc(tenantId, p.getId()).size()
                ))
                .toList();
    }

    @Transactional
    public ProjectDtos.ProjectResponse createProject(ProjectDtos.CreateProjectRequest request,
                                                     Long userId, String userName) {
        String tenantId = getRequiredTenantId();
        rbacService.assertPermission(userId, tenantId, com.multitenant.workspace.security.Permission.CREATE_PROJECTS);

        Project project = new Project(request.name(), request.description(), request.color(), userId);
        project.setTenantId(tenantId);
        project = projectRepository.save(project);

        activityLogService.log("PROJECT_CREATED", "Created project '" + project.getName() + "'", userId, userName);

        return new ProjectDtos.ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getColor(),
                project.getStatus(),
                project.getCreatedById(),
                project.getCreatedAt(),
                0
        );
    }

    @Transactional(readOnly = true)
    public ProjectDtos.ProjectResponse getProject(Long projectId) {
        String tenantId = getRequiredTenantId();
        Project p = projectRepository.findByIdAndTenantId(projectId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found in current workspace"));

        long taskCount = taskRepository.findByTenantIdAndProjectIdOrderByPositionAscCreatedAtDesc(tenantId, p.getId()).size();
        return new ProjectDtos.ProjectResponse(
                p.getId(),
                p.getName(),
                p.getDescription(),
                p.getColor(),
                p.getStatus(),
                p.getCreatedById(),
                p.getCreatedAt(),
                taskCount
        );
    }

    @Transactional
    public ProjectDtos.ProjectResponse updateProject(Long projectId, ProjectDtos.UpdateProjectRequest request,
                                                     Long userId, String userName) {
        String tenantId = getRequiredTenantId();
        Project p = projectRepository.findByIdAndTenantId(projectId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found in current workspace"));

        p.setName(request.name());
        p.setDescription(request.description());
        if (request.color() != null && !request.color().isBlank()) {
            p.setColor(request.color());
        }
        if (request.status() != null && !request.status().isBlank()) {
            p.setStatus(request.status());
        }
        projectRepository.save(p);

        activityLogService.log("PROJECT_UPDATED", "Updated project '" + p.getName() + "'", userId, userName);

        long taskCount = taskRepository.findByTenantIdAndProjectIdOrderByPositionAscCreatedAtDesc(tenantId, p.getId()).size();
        return new ProjectDtos.ProjectResponse(
                p.getId(),
                p.getName(),
                p.getDescription(),
                p.getColor(),
                p.getStatus(),
                p.getCreatedById(),
                p.getCreatedAt(),
                taskCount
        );
    }

    @Transactional
    public void deleteProject(Long projectId, Long userId, String userName) {
        String tenantId = getRequiredTenantId();
        rbacService.assertPermission(userId, tenantId, com.multitenant.workspace.security.Permission.DELETE_PROJECTS);

        Project p = projectRepository.findByIdAndTenantId(projectId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found in current workspace"));

        // Delete associated tasks
        List<Task> tasks = taskRepository.findByTenantIdAndProjectIdOrderByPositionAscCreatedAtDesc(tenantId, projectId);
        taskRepository.deleteAll(tasks);

        projectRepository.delete(p);
        activityLogService.log("PROJECT_DELETED", "Deleted project '" + p.getName() + "'", userId, userName);
    }
}
