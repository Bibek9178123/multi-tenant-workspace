package com.multitenant.workspace.controller;

import com.multitenant.workspace.service.EmailService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<Map<String, Object>> getMailStatus() {
        return ResponseEntity.ok(emailService.getMailStatus());
    }

    @GetMapping("/outbox")
    public ResponseEntity<List<EmailService.SentEmailRecord>> getRecentOutbox() {
        return ResponseEntity.ok(emailService.getRecentOutbox());
    }

    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> sendTestEmail(@RequestBody(required = false) Map<String, String> body) {
        String toEmail = (body != null && body.containsKey("toEmail") && !body.get("toEmail").isBlank())
                ? body.get("toEmail").trim()
                : "developer@nexspace.com";

        EmailService.SentEmailRecord record = emailService.sendTestEmail(toEmail);

        return ResponseEntity.ok(Map.of(
                "message", "Test email processed successfully",
                "emailId", record.id(),
                "toEmail", record.toEmail(),
                "subject", record.subject(),
                "deliveryStatus", record.deliveryStatus(),
                "mode", record.deliveryStatus().equals("SENT_SMTP") ? "Live SMTP" : "Dev Simulation Outbox"
        ));
    }

    @DeleteMapping("/outbox")
    public ResponseEntity<Map<String, String>> clearOutbox() {
        emailService.clearOutbox();
        return ResponseEntity.ok(Map.of("message", "Dev email outbox cleared successfully"));
    }
}
