package com.multitenant.workspace.controller;

import com.multitenant.workspace.entity.WorkspaceMember;
import com.multitenant.workspace.security.Permission;
import com.multitenant.workspace.security.RbacService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/education")
public class RbacEducationController {

    private final RbacService rbacService;

    public RbacEducationController(RbacService rbacService) {
        this.rbacService = rbacService;
    }

    @GetMapping("/rbac-matrix")
    public ResponseEntity<Map<String, Object>> getRbacMatrix() {
        return ResponseEntity.ok(rbacService.getRbacTeachingMatrix());
    }

    @PostMapping("/test-permission")
    public ResponseEntity<Map<String, Object>> testPermission(@RequestBody Map<String, String> body) {
        String roleStr = body.getOrDefault("role", "MEMBER").toUpperCase();
        String permStr = body.getOrDefault("permission", "MANAGE_TASKS").toUpperCase();

        try {
            WorkspaceMember.MemberRole role = WorkspaceMember.MemberRole.valueOf(roleStr);
            Permission perm = Permission.valueOf(permStr);

            Map<String, Object> matrix = rbacService.getRbacTeachingMatrix();
            @SuppressWarnings("unchecked")
            Map<String, java.util.List<String>> roleMap = (Map<String, java.util.List<String>>) matrix.get("roles");
            boolean granted = roleMap.getOrDefault(role.name(), java.util.List.of()).contains(perm.name());

            String explanation;
            if (granted) {
                explanation = "ALLOWED: Role '" + role + "' has permission to execute '" + perm + "' (" + perm.getDescription() + ").";
            } else {
                explanation = "DENIED: Role '" + role + "' is isolated from '" + perm + "'. " +
                        (role == WorkspaceMember.MemberRole.BILLING_ADMIN ?
                                "Notice: Financial/Billing admins cannot manipulate or delete project/engineering data." :
                                "Only higher-tier administrative roles possess this capability.");
            }

            return ResponseEntity.ok(Map.of(
                    "role", role.name(),
                    "permission", perm.name(),
                    "granted", granted,
                    "explanation", explanation
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/architecture-summary")
    public ResponseEntity<Map<String, Object>> getArchitectureSummary() {
        return ResponseEntity.ok(Map.of(
                "strategy", "Shared Database with Discriminator Column (tenant_id)",
                "isolationMechanism", "TenantFilter -> ThreadLocal TenantContext -> JPA @PrePersist -> SQL WHERE tenant_id = :id",
                "rbacModel", "5 Distinct Roles (OWNER, ADMIN, BILLING_ADMIN, MEMBER, VIEWER)",
                "billingEngine", "Stripe API + Inbound Webhooks with Signature Verification",
                "supportedWebhookEvents", java.util.List.of(
                        "checkout.session.completed",
                        "customer.subscription.updated",
                        "customer.subscription.deleted",
                        "invoice.payment_succeeded",
                        "invoice.payment_failed"
                )
        ));
    }
}
