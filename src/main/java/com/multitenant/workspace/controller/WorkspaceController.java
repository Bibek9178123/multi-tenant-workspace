package com.multitenant.workspace.controller;

import com.multitenant.workspace.dto.WorkspaceDtos;
import com.multitenant.workspace.security.UserPrincipal;
import com.multitenant.workspace.service.WorkspaceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping
    public ResponseEntity<List<WorkspaceDtos.WorkspaceResponse>> getWorkspaces(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        return ResponseEntity.ok(workspaceService.getUserWorkspaces(userPrincipal.getId()));
    }

    @PostMapping
    public ResponseEntity<WorkspaceDtos.WorkspaceResponse> createWorkspace(
            @Valid @RequestBody WorkspaceDtos.CreateWorkspaceRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        return ResponseEntity.ok(workspaceService.createWorkspace(
                request,
                userPrincipal.getId(),
                userPrincipal.getUsername(),
                userPrincipal.getFullName()
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkspaceDtos.WorkspaceResponse> getWorkspace(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        return ResponseEntity.ok(workspaceService.getWorkspace(id, userPrincipal.getId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<WorkspaceDtos.WorkspaceResponse> updateWorkspace(
            @PathVariable String id,
            @Valid @RequestBody WorkspaceDtos.UpdateWorkspaceRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        return ResponseEntity.ok(workspaceService.updateWorkspace(
                id,
                request,
                userPrincipal.getId(),
                userPrincipal.getFullName()
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteWorkspace(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        workspaceService.deleteWorkspace(id, userPrincipal.getId());
        return ResponseEntity.ok(Map.of("message", "Workspace and all associated tenant data deleted successfully"));
    }
}
