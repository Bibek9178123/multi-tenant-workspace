package com.multitenant.workspace.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "workspace_members", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"workspace_id", "user_id"}),
    @UniqueConstraint(columnNames = {"workspace_id", "email"})
})
public class WorkspaceMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "user_id")
    private Long userId; // null if pending invite

    @Column(nullable = false, length = 120)
    private String email;

    @Column(name = "full_name", length = 100)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MemberRole role = MemberRole.MEMBER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MemberStatus status = MemberStatus.ACTIVE;

    @Column(name = "joined_at")
    private LocalDateTime joinedAt;

    public enum MemberRole {
        OWNER,
        ADMIN,
        BILLING_ADMIN,
        MEMBER,
        VIEWER
    }

    public enum MemberStatus {
        ACTIVE,
        INVITED
    }

    public WorkspaceMember() {}

    public WorkspaceMember(String workspaceId, Long userId, String email, String fullName, MemberRole role, MemberStatus status) {
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.email = email;
        this.fullName = fullName;
        this.role = role != null ? role : MemberRole.MEMBER;
        this.status = status != null ? status : MemberStatus.ACTIVE;
        this.joinedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public MemberRole getRole() {
        return role;
    }

    public void setRole(MemberRole role) {
        this.role = role;
    }

    public MemberStatus getStatus() {
        return status;
    }

    public void setStatus(MemberStatus status) {
        this.status = status;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(LocalDateTime joinedAt) {
        this.joinedAt = joinedAt;
    }
}
