package com.multitenant.workspace.service;

import com.multitenant.workspace.dto.AnalyticsDtos;
import com.multitenant.workspace.entity.Task;
import com.multitenant.workspace.repository.ProjectRepository;
import com.multitenant.workspace.repository.TaskRepository;
import com.multitenant.workspace.repository.WorkspaceMemberRepository;
import com.multitenant.workspace.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
public class AnalyticsService {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    public AnalyticsService(ProjectRepository projectRepository,
                            TaskRepository taskRepository,
                            WorkspaceMemberRepository workspaceMemberRepository) {
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
    }

    @Transactional(readOnly = true)
    public AnalyticsDtos.WorkspaceSummaryResponse getSummary() {
        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("Active tenant context is required");
        }

        long totalProjects = projectRepository.countByTenantId(tenantId);
        long totalTasks = taskRepository.countByTenantId(tenantId);
        long completedTasks = taskRepository.countByTenantIdAndStatus(tenantId, Task.TaskStatus.DONE);
        long inProgressTasks = taskRepository.countByTenantIdAndStatus(tenantId, Task.TaskStatus.IN_PROGRESS);
        long todoTasks = taskRepository.countByTenantIdAndStatus(tenantId, Task.TaskStatus.TODO);
        long inReviewTasks = taskRepository.countByTenantIdAndStatus(tenantId, Task.TaskStatus.IN_REVIEW);
        long totalMembers = workspaceMemberRepository.findByWorkspaceId(tenantId).size();

        Map<String, Long> priorityMap = new HashMap<>();
        for (Task.TaskPriority p : Task.TaskPriority.values()) {
            long count = taskRepository.findByTenantIdOrderByPositionAscCreatedAtDesc(tenantId).stream()
                    .filter(t -> t.getPriority() == p)
                    .count();
            priorityMap.put(p.name(), count);
        }

        return new AnalyticsDtos.WorkspaceSummaryResponse(
                totalProjects,
                totalTasks,
                completedTasks,
                inProgressTasks,
                todoTasks,
                inReviewTasks,
                totalMembers,
                priorityMap
        );
    }
}
