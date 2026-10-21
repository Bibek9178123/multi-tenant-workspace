package com.multitenant.workspace.controller;

import com.multitenant.workspace.dto.ProjectDtos;
import com.multitenant.workspace.security.UserPrincipal;
import com.multitenant.workspace.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public ResponseEntity<List<ProjectDtos.ProjectResponse>> getProjects() {
        return ResponseEntity.ok(projectService.getProjects());
    }

    @PostMapping
    public ResponseEntity<ProjectDtos.ProjectResponse> createProject(
            @Valid @RequestBody ProjectDtos.CreateProjectRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        return ResponseEntity.ok(projectService.createProject(
                request,
                userPrincipal.getId(),
                userPrincipal.getFullName()
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectDtos.ProjectResponse> getProject(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.getProject(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProjectDtos.ProjectResponse> updateProject(
            @PathVariable Long id,
            @Valid @RequestBody ProjectDtos.UpdateProjectRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        return ResponseEntity.ok(projectService.updateProject(
                id,
                request,
                userPrincipal.getId(),
                userPrincipal.getFullName()
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteProject(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        projectService.deleteProject(id, userPrincipal.getId(), userPrincipal.getFullName());
        return ResponseEntity.ok(Map.of("message", "Project and its tasks deleted successfully"));
    }
}
