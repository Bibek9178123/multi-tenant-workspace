package com.multitenant.workspace.repository;

import com.multitenant.workspace.entity.WorkspaceMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, Long> {
    List<WorkspaceMember> findByUserIdAndStatus(Long userId, WorkspaceMember.MemberStatus status);
    List<WorkspaceMember> findByWorkspaceId(String workspaceId);
    Optional<WorkspaceMember> findByWorkspaceIdAndUserId(String workspaceId, Long userId);
    Optional<WorkspaceMember> findByWorkspaceIdAndEmail(String workspaceId, String email);
    boolean existsByWorkspaceIdAndUserId(String workspaceId, Long userId);
    void deleteByWorkspaceId(String workspaceId);
}
