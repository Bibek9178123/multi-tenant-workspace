package com.multitenant.workspace.repository;

import com.multitenant.workspace.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByTenantIdOrderByCreatedAtDesc(String tenantId);
    Optional<Project> findByIdAndTenantId(Long id, String tenantId);
    long countByTenantId(String tenantId);
    void deleteByTenantId(String tenantId);
}
