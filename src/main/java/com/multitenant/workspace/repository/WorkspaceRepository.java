package com.multitenant.workspace.repository;

import com.multitenant.workspace.entity.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WorkspaceRepository extends JpaRepository<Workspace, String> {
    Optional<Workspace> findBySlug(String slug);
    boolean existsBySlug(String slug);
}
