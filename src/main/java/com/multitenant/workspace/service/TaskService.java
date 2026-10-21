package com.multitenant.workspace.service;

import com.multitenant.workspace.dto.TaskDtos;
import com.multitenant.workspace.entity.Project;
import com.multitenant.workspace.entity.Task;
import com.multitenant.workspace.repository.ProjectRepository;
import com.multitenant.workspace.repository.TaskRepository;
import com.multitenant.workspace.repository.UserRepository;
import com.multitenant.workspace.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ActivityLogService activityLogService;
    private final com.multitenant.workspace.security.RbacService rbacService;
    private final EmailService emailService;

    public TaskService(TaskRepository taskRepository,
                       ProjectRepository projectRepository,
                       UserRepository userRepository,
                       ActivityLogService activityLogService,
                       com.multitenant.workspace.security.RbacService rbacService,
                       EmailService emailService) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.activityLogService = activityLogService;
        this.rbacService = rbacService;
        this.emailService = emailService;
    }

    private String getRequiredTenantId() {
        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("Active tenant context is required");
        }
        return tenantId;
    }

    @Transactional(readOnly = true)
    public List<TaskDtos.TaskResponse> getTasks(Long projectId) {
        String tenantId = getRequiredTenantId();
        List<Task> tasks;
        if (projectId != null) {
            tasks = taskRepository.findByTenantIdAndProjectIdOrderByPositionAscCreatedAtDesc(tenantId, projectId);
        } else {
            tasks = taskRepository.findByTenantIdOrderByPositionAscCreatedAtDesc(tenantId);
        }
        return tasks.stream().map(this::mapToResponse).toList();
    }

    @Transactional
    public TaskDtos.TaskResponse createTask(TaskDtos.CreateTaskRequest request, Long userId, String userName) {
        String tenantId = getRequiredTenantId();
        rbacService.assertPermission(userId, tenantId, com.multitenant.workspace.security.Permission.MANAGE_TASKS);

        // Verify project belongs to current tenant
        projectRepository.findByIdAndTenantId(request.projectId(), tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Project does not exist in this workspace"));

        Task task = new Task(
                request.projectId(),
                request.title(),
                request.description(),
                request.status(),
                request.priority(),
                request.assigneeId(),
                request.assigneeName(),
                userId,
                request.dueDate()
        );
        task.setTenantId(tenantId);
        if (request.attachmentsJson() != null) {
            task.setAttachmentsJson(request.attachmentsJson());
        }
        task = taskRepository.save(task);

        activityLogService.log("TASK_CREATED", "Created task '" + task.getTitle() + "'", userId, userName);

        if (request.assigneeId() != null) {
            final Task finalTask = task;
            userRepository.findById(request.assigneeId()).ifPresent(assignee -> {
                String projectName = projectRepository.findByIdAndTenantId(request.projectId(), tenantId)
                        .map(Project::getName).orElse("Workspace Project");
                emailService.sendTaskAssignmentEmail(assignee.getEmail(), assignee.getFullName(), finalTask.getTitle(), projectName, userName, tenantId);
            });
        }

        return mapToResponse(task);
    }

    @Transactional(readOnly = true)
    public TaskDtos.TaskResponse getTask(Long taskId) {
        String tenantId = getRequiredTenantId();
        Task task = taskRepository.findByIdAndTenantId(taskId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found in current workspace"));
        return mapToResponse(task);
    }

    @Transactional
    public TaskDtos.TaskResponse updateTask(Long taskId, TaskDtos.UpdateTaskRequest request,
                                            Long userId, String userName) {
        String tenantId = getRequiredTenantId();
        rbacService.assertPermission(userId, tenantId, com.multitenant.workspace.security.Permission.MANAGE_TASKS);

        Task task = taskRepository.findByIdAndTenantId(taskId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found in current workspace"));

        Long oldAssigneeId = task.getAssigneeId();

        if (request.projectId() != null && !request.projectId().equals(task.getProjectId())) {
            projectRepository.findByIdAndTenantId(request.projectId(), tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Target project does not exist in workspace"));
            task.setProjectId(request.projectId());
        }

        task.setTitle(request.title());
        task.setDescription(request.description());
        if (request.status() != null) {
            task.setStatus(request.status());
        }
        if (request.priority() != null) {
            task.setPriority(request.priority());
        }
        task.setAssigneeId(request.assigneeId());
        task.setAssigneeName(request.assigneeName());
        task.setDueDate(request.dueDate());

        if (request.attachmentsJson() != null) {
            task.setAttachmentsJson(request.attachmentsJson());
        }

        taskRepository.save(task);
        activityLogService.log("TASK_UPDATED", "Updated task '" + task.getTitle() + "'", userId, userName);

        if (request.assigneeId() != null && !request.assigneeId().equals(oldAssigneeId)) {
            final Task finalTask = task;
            userRepository.findById(request.assigneeId()).ifPresent(assignee -> {
                String projectName = projectRepository.findByIdAndTenantId(finalTask.getProjectId(), tenantId)
                        .map(Project::getName).orElse("Workspace Project");
                emailService.sendTaskAssignmentEmail(assignee.getEmail(), assignee.getFullName(), finalTask.getTitle(), projectName, userName, tenantId);
            });
        }

        return mapToResponse(task);
    }

    @Transactional
    public TaskDtos.TaskResponse updateTaskStatus(Long taskId, TaskDtos.UpdateTaskStatusRequest request,
                                                  Long userId, String userName) {
        String tenantId = getRequiredTenantId();
        rbacService.assertPermission(userId, tenantId, com.multitenant.workspace.security.Permission.MANAGE_TASKS);

        Task task = taskRepository.findByIdAndTenantId(taskId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found in current workspace"));

        Task.TaskStatus oldStatus = task.getStatus();
        task.setStatus(request.status());
        if (request.position() != null) {
            task.setPosition(request.position());
        }
        taskRepository.save(task);

        if (oldStatus != request.status()) {
            activityLogService.log("TASK_STATUS_CHANGED", "Moved '" + task.getTitle() + "' from " + oldStatus + " to " + request.status(), userId, userName);
        }

        return mapToResponse(task);
    }

    @Transactional
    public void deleteTask(Long taskId, Long userId, String userName) {
        String tenantId = getRequiredTenantId();
        rbacService.assertPermission(userId, tenantId, com.multitenant.workspace.security.Permission.MANAGE_TASKS);

        Task task = taskRepository.findByIdAndTenantId(taskId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found in current workspace"));

        taskRepository.delete(task);
        activityLogService.log("TASK_DELETED", "Deleted task '" + task.getTitle() + "'", userId, userName);
    }

    private TaskDtos.TaskResponse mapToResponse(Task t) {
        return new TaskDtos.TaskResponse(
                t.getId(),
                t.getProjectId(),
                t.getTitle(),
                t.getDescription(),
                t.getStatus(),
                t.getPriority(),
                t.getAssigneeId(),
                t.getAssigneeName(),
                t.getCreatedById(),
                t.getDueDate(),
                t.getPosition(),
                t.getAttachmentsJson(),
                t.getCreatedAt(),
                t.getUpdatedAt()
        );
    }
}
