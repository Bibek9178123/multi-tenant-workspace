package com.multitenant.workspace.controller;

import com.multitenant.workspace.entity.Workspace;
import com.multitenant.workspace.repository.WorkspaceRepository;
import com.multitenant.workspace.security.Permission;
import com.multitenant.workspace.security.RbacService;
import com.multitenant.workspace.security.UserPrincipal;
import com.multitenant.workspace.service.StripeBillingService;
import com.multitenant.workspace.tenant.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class StripeWebhookController {

    private final StripeBillingService stripeBillingService;
    private final WorkspaceRepository workspaceRepository;
    private final RbacService rbacService;

    public StripeWebhookController(StripeBillingService stripeBillingService,
                                   WorkspaceRepository workspaceRepository,
                                   RbacService rbacService) {
        this.stripeBillingService = stripeBillingService;
        this.workspaceRepository = workspaceRepository;
        this.rbacService = rbacService;
    }

    /**
     * Public Stripe Webhook Receiver Endpoint.
     * Called directly by Stripe's webhook delivery system.
     */
    @PostMapping("/webhooks/stripe")
    public ResponseEntity<Map<String, Object>> handleStripeWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signatureHeader) {
        Map<String, Object> result = stripeBillingService.handleWebhook(rawPayload, signatureHeader);
        return ResponseEntity.ok(result);
    }

    /**
     * Create Checkout Session for the current tenant company.
     */
    @PostMapping("/billing/checkout")
    public ResponseEntity<Map<String, Object>> createCheckout(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("Active tenant context is required");
        }

        String planStr = body.getOrDefault("plan", "PRO").toUpperCase();
        Workspace.PlanType plan = Workspace.PlanType.valueOf(planStr);

        Map<String, Object> checkoutData = stripeBillingService.createCheckoutSession(
                tenantId, plan, userPrincipal.getId(), userPrincipal.getUsername()
        );
        return ResponseEntity.ok(checkoutData);
    }

    /**
     * Webhook Simulator: Enables one-click testing of payment webhooks directly from the UI!
     */
    @PostMapping("/billing/simulate-webhook")
    public ResponseEntity<Map<String, Object>> simulateWebhook(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("Active tenant context is required");
        }

        rbacService.assertPermission(userPrincipal.getId(), tenantId, Permission.TRIGGER_WEBHOOKS);

        String eventType = body.getOrDefault("eventType", "checkout.session.completed");
        String planStr = body.getOrDefault("plan", "PRO").toUpperCase();
        Workspace.PlanType plan = Workspace.PlanType.valueOf(planStr);

        Map<String, Object> outcome = stripeBillingService.simulateWebhook(tenantId, eventType, plan);
        return ResponseEntity.ok(outcome);
    }

    /**
     * Fetch billing and subscription details for active workspace.
     */
    @GetMapping("/billing/info")
    public ResponseEntity<Map<String, Object>> getBillingInfo(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("Active tenant context is required");
        }

        Workspace ws = workspaceRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + tenantId));

        boolean canManageBilling = rbacService.hasPermission(userPrincipal.getId(), tenantId, Permission.MANAGE_BILLING);

        Map<String, Object> info = Map.of(
                "workspaceId", ws.getId(),
                "companyName", ws.getName(),
                "plan", ws.getPlan().name(),
                "subscriptionStatus", ws.getSubscriptionStatus() != null ? ws.getSubscriptionStatus() : "ACTIVE",
                "stripeCustomerId", ws.getStripeCustomerId() != null ? ws.getStripeCustomerId() : "none",
                "stripeSubscriptionId", ws.getStripeSubscriptionId() != null ? ws.getStripeSubscriptionId() : "none",
                "currentPeriodEnd", ws.getCurrentPeriodEnd() != null ? ws.getCurrentPeriodEnd().toString() : "N/A",
                "canManageBilling", canManageBilling
        );

        return ResponseEntity.ok(info);
    }
}
