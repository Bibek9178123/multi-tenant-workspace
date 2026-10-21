package com.multitenant.workspace.controller;

import com.multitenant.workspace.dto.MemberDtos;
import com.multitenant.workspace.security.UserPrincipal;
import com.multitenant.workspace.service.MemberService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    public ResponseEntity<List<MemberDtos.MemberResponse>> getMembers(@PathVariable String workspaceId) {
        return ResponseEntity.ok(memberService.getMembers(workspaceId));
    }

    @PostMapping("/invite")
    public ResponseEntity<MemberDtos.MemberResponse> inviteMember(
            @PathVariable String workspaceId,
            @Valid @RequestBody MemberDtos.InviteMemberRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        return ResponseEntity.ok(memberService.inviteMember(
                workspaceId,
                request,
                userPrincipal.getId(),
                userPrincipal.getFullName()
        ));
    }

    @PutMapping("/{memberId}/role")
    public ResponseEntity<MemberDtos.MemberResponse> updateRole(
            @PathVariable String workspaceId,
            @PathVariable Long memberId,
            @Valid @RequestBody MemberDtos.UpdateRoleRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        return ResponseEntity.ok(memberService.updateRole(
                workspaceId,
                memberId,
                request.role(),
                userPrincipal.getId(),
                userPrincipal.getFullName()
        ));
    }

    @DeleteMapping("/{memberId}")
    public ResponseEntity<Map<String, String>> removeMember(
            @PathVariable String workspaceId,
            @PathVariable Long memberId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        memberService.removeMember(workspaceId, memberId, userPrincipal.getId(), userPrincipal.getFullName());
        return ResponseEntity.ok(Map.of("message", "Member removed successfully"));
    }
}
