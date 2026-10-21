package com.multitenant.workspace.repository;

import com.multitenant.workspace.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByTenantIdOrderByPositionAscCreatedAtDesc(String tenantId);
    List<Task> findByTenantIdAndProjectIdOrderByPositionAscCreatedAtDesc(String tenantId, Long projectId);
    Optional<Task> findByIdAndTenantId(Long id, String tenantId);
    long countByTenantId(String tenantId);
    long countByTenantIdAndStatus(String tenantId, Task.TaskStatus status);
    void deleteByTenantId(String tenantId);
}
