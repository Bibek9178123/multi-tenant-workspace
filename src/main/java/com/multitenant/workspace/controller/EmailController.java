package com.multitenant.workspace.controller;

import com.multitenant.workspace.service.EmailService;
import com.multitenant.workspace.tenant.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mail")
public class EmailController {

    private final EmailService emailService;

    public EmailController(EmailService emailService) {
        this.emailService = emailService;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getMailStatus(
            @RequestHeader(value = "X-Tenant-ID", required = false) String headerTenantId) {
        String tenantId = (headerTenantId != null && !headerTenantId.isBlank())
                ? headerTenantId.trim()
                : TenantContext.getCurrentTenant();
        return ResponseEntity.ok(emailService.getMailStatus(tenantId));
    }

    @GetMapping("/outbox")
    public ResponseEntity<List<EmailService.SentEmailRecord>> getRecentOutbox(
            @RequestHeader(value = "X-Tenant-ID", required = false) String headerTenantId) {
        String tenantId = (headerTenantId != null && !headerTenantId.isBlank())
                ? headerTenantId.trim()
                : TenantContext.getCurrentTenant();
        return ResponseEntity.ok(emailService.getRecentOutbox(tenantId));
    }

    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> sendTestEmail(
            @RequestHeader(value = "X-Tenant-ID", required = false) String headerTenantId,
            @RequestBody(required = false) Map<String, String> body) {
        String tenantId = (headerTenantId != null && !headerTenantId.isBlank())
                ? headerTenantId.trim()
                : TenantContext.getCurrentTenant();

        String toEmail = (body != null && body.containsKey("toEmail") && !body.get("toEmail").isBlank())
                ? body.get("toEmail").trim()
                : "bibekbibek9178@gmail.com";
        String overridePass = (body != null && body.containsKey("password") && !body.get("password").isBlank())
                ? body.get("password").trim()
                : null;

        EmailService.SentEmailRecord record = emailService.sendTestEmail(toEmail, overridePass, tenantId);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("message", "Test email processed");
        resp.put("emailId", record.id());
        resp.put("tenantId", record.tenantId());
        resp.put("workspaceName", record.workspaceName());
        resp.put("toEmail", record.toEmail());
        resp.put("subject", record.subject());
        resp.put("deliveryStatus", record.deliveryStatus());
        resp.put("errorMessage", record.errorMessage());
        resp.put("mode", record.deliveryStatus().equals("SENT_SMTP") ? "Live SMTP" : "Dev Simulation Outbox");

        return ResponseEntity.ok(resp);
    }

    @DeleteMapping("/outbox")
    public ResponseEntity<Map<String, String>> clearOutbox(
            @RequestHeader(value = "X-Tenant-ID", required = false) String headerTenantId) {
        String tenantId = (headerTenantId != null && !headerTenantId.isBlank())
                ? headerTenantId.trim()
                : TenantContext.getCurrentTenant();
        emailService.clearOutbox(tenantId);
        return ResponseEntity.ok(Map.of("message", "Dev email outbox cleared successfully for workspace"));
    }
}
