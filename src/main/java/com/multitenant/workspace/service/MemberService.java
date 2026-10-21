package com.multitenant.workspace.service;

import com.multitenant.workspace.dto.MemberDtos;
import com.multitenant.workspace.entity.User;
import com.multitenant.workspace.entity.Workspace;
import com.multitenant.workspace.entity.WorkspaceMember;
import com.multitenant.workspace.repository.UserRepository;
import com.multitenant.workspace.repository.WorkspaceMemberRepository;
import com.multitenant.workspace.repository.WorkspaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class MemberService {

    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;
    private final ActivityLogService activityLogService;
    private final com.multitenant.workspace.security.RbacService rbacService;
    private final EmailService emailService;

    public MemberService(WorkspaceMemberRepository workspaceMemberRepository,
                         WorkspaceRepository workspaceRepository,
                         UserRepository userRepository,
                         ActivityLogService activityLogService,
                         com.multitenant.workspace.security.RbacService rbacService,
                         EmailService emailService) {
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.workspaceRepository = workspaceRepository;
        this.userRepository = userRepository;
        this.activityLogService = activityLogService;
        this.rbacService = rbacService;
        this.emailService = emailService;
    }

    @Transactional(readOnly = true)
    public List<MemberDtos.MemberResponse> getMembers(String workspaceId) {
        return workspaceMemberRepository.findByWorkspaceId(workspaceId).stream()
                .map(m -> new MemberDtos.MemberResponse(
                        m.getId(),
                        m.getUserId(),
                        m.getEmail(),
                        m.getFullName(),
                        m.getRole(),
                        m.getStatus(),
                        m.getJoinedAt()
                ))
                .toList();
    }

    @Transactional
    public MemberDtos.MemberResponse inviteMember(String workspaceId, MemberDtos.InviteMemberRequest request,
                                                 Long actingUserId, String actingUserName) {
        // Enforce RBAC permission
        rbacService.assertPermission(actingUserId, workspaceId, com.multitenant.workspace.security.Permission.INVITE_MEMBERS);

        Optional<WorkspaceMember> existing = workspaceMemberRepository.findByWorkspaceIdAndEmail(workspaceId, request.email());
        if (existing.isPresent()) {
            throw new IllegalArgumentException("User with email '" + request.email() + "' is already a member or invited");
        }

        // Check if user already registered in system
        Optional<User> registeredUser = userRepository.findByEmail(request.email());

        WorkspaceMember member = new WorkspaceMember(
                workspaceId,
                registeredUser.map(User::getId).orElse(null),
                request.email(),
                registeredUser.map(User::getFullName).orElse(request.email().split("@")[0]),
                request.role(),
                WorkspaceMember.MemberStatus.ACTIVE // Directly active for ease of use
        );

        member = workspaceMemberRepository.save(member);
        activityLogService.log("MEMBER_INVITED", "Invited " + request.email() + " as " + request.role(), actingUserId, actingUserName);

        String wsName = workspaceRepository.findById(workspaceId).map(Workspace::getName).orElse("Workspace " + workspaceId);
        emailService.sendInvitationEmail(request.email(), actingUserName, wsName, request.role().name(), null, workspaceId);

        return new MemberDtos.MemberResponse(
                member.getId(),
                member.getUserId(),
                member.getEmail(),
                member.getFullName(),
                member.getRole(),
                member.getStatus(),
                member.getJoinedAt()
        );
    }

    @Transactional
    public MemberDtos.MemberResponse updateRole(String workspaceId, Long memberId,
                                                WorkspaceMember.MemberRole newRole,
                                                Long actingUserId, String actingUserName) {
        rbacService.assertPermission(actingUserId, workspaceId, com.multitenant.workspace.security.Permission.MANAGE_ROLES);
        WorkspaceMember caller = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, actingUserId)
                .orElseThrow(() -> new SecurityException("Access denied"));

        WorkspaceMember target = workspaceMemberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found"));

        if (!target.getWorkspaceId().equals(workspaceId)) {
            throw new IllegalArgumentException("Member does not belong to this workspace");
        }

        if (target.getRole() == WorkspaceMember.MemberRole.OWNER && caller.getRole() != WorkspaceMember.MemberRole.OWNER) {
            throw new SecurityException("Cannot modify workspace OWNER role");
        }

        target.setRole(newRole);
        workspaceMemberRepository.save(target);

        activityLogService.log("MEMBER_ROLE_UPDATED", "Updated role for " + target.getEmail() + " to " + newRole, actingUserId, actingUserName);

        return new MemberDtos.MemberResponse(
                target.getId(),
                target.getUserId(),
                target.getEmail(),
                target.getFullName(),
                target.getRole(),
                target.getStatus(),
                target.getJoinedAt()
        );
    }

    @Transactional
    public void removeMember(String workspaceId, Long memberId, Long actingUserId, String actingUserName) {
        WorkspaceMember caller = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, actingUserId)
                .orElseThrow(() -> new SecurityException("Access denied"));

        WorkspaceMember target = workspaceMemberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found"));

        if (!target.getWorkspaceId().equals(workspaceId)) {
            throw new IllegalArgumentException("Member does not belong to this workspace");
        }

        if (target.getRole() == WorkspaceMember.MemberRole.OWNER) {
            throw new SecurityException("Cannot remove the workspace OWNER");
        }

        // Allow user to leave themselves, or OWNER/ADMIN to remove others
        boolean isSelf = target.getUserId() != null && target.getUserId().equals(actingUserId);
        if (!isSelf && caller.getRole() != WorkspaceMember.MemberRole.OWNER && caller.getRole() != WorkspaceMember.MemberRole.ADMIN) {
            throw new SecurityException("Only OWNER or ADMIN can remove other members");
        }

        workspaceMemberRepository.delete(target);
        activityLogService.log("MEMBER_REMOVED", "Removed " + target.getEmail() + " from workspace", actingUserId, actingUserName);
    }
}
