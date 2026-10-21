package com.multitenant.workspace.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final Optional<JavaMailSender> mailSender;

    @Value("${spring.mail.host:smtp.gmail.com}")
    private String mailHost;

    @Value("${spring.mail.port:587}")
    private int mailPort;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @Value("${app.mail.from:noreply@nexspace.com}")
    private String mailFrom;

    @Value("${app.mail.from-name:NexSpace Workspace}")
    private String mailFromName;

    @Value("${app.mail.app-url:http://localhost:8085}")
    private String appUrl;

    // In-memory Outbox for development inspection, testing, and UI preview
    private final List<SentEmailRecord> outbox = new CopyOnWriteArrayList<>();

    public record SentEmailRecord(
            String id,
            String toEmail,
            String subject,
            String bodyHtml,
            String emailType,
            String deliveryStatus, // SENT_SMTP or SIMULATED_OUTBOX
            LocalDateTime sentAt
    ) {}

    public EmailService(Optional<JavaMailSender> mailSender) {
        this.mailSender = mailSender;
    }

    public boolean isSmtpConfigured() {
        return mailUsername != null && !mailUsername.isBlank() && !mailUsername.contains("your-email")
                && mailPassword != null && !mailPassword.isBlank() && !mailPassword.contains("YOUR_");
    }

    public Map<String, Object> getMailStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("smtpConfigured", isSmtpConfigured());
        status.put("host", mailHost);
        status.put("port", mailPort);
        status.put("username", (mailUsername != null && !mailUsername.isBlank()) ? mailUsername : "none");
        status.put("hasPassword", mailPassword != null && !mailPassword.isBlank());
        status.put("fromAddress", mailFrom);
        status.put("mode", isSmtpConfigured() ? "LIVE_SMTP" : "DEV_SIMULATION_OUTBOX");
        status.put("totalEmailsSent", outbox.size());
        return status;
    }

    public List<SentEmailRecord> getRecentOutbox() {
        List<SentEmailRecord> list = new ArrayList<>(outbox);
        Collections.reverse(list);
        if (list.size() > 30) {
            return list.subList(0, 30);
        }
        return list;
    }

    public void clearOutbox() {
        outbox.clear();
    }

    /**
     * Welcome Email for New Registered Users.
     */
    @Async
    public void sendWelcomeEmail(String toEmail, String userName, String companyName) {
        String subject = "Welcome to NexSpace | " + companyName;
        String html = """
            <div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; max-width: 580px; margin: 0 auto; background: #0f172a; border-radius: 12px; border: 1px solid #1e293b; overflow: hidden; color: #f8fafc;">
                <div style="background: linear-gradient(135deg, #6366f1, #4f46e5); padding: 32px 24px; text-align: center;">
                    <h1 style="color: white; margin: 0; font-size: 24px;">Welcome to NexSpace</h1>
                    <p style="color: #e0e7ff; margin-top: 8px; font-size: 14px;">Multi-Tenant Team Collaboration Platform</p>
                </div>
                <div style="padding: 32px 24px;">
                    <p style="font-size: 16px; line-height: 1.6;">Hello <strong>%s</strong>,</p>
                    <p style="color: #94a3b8; line-height: 1.6;">Your multi-tenant workspace for <strong>%s</strong> is ready. Your team now has isolated access to projects, tasks, Kanban boards, and RBAC security boundaries.</p>
                    <div style="margin: 28px 0; text-align: center;">
                        <a href="%s" style="background: #6366f1; color: white; padding: 12px 28px; text-decoration: none; border-radius: 6px; font-weight: 600; display: inline-block;">Open Workspace</a>
                    </div>
                    <p style="font-size: 13px; color: #64748b; margin-top: 24px;">Need help? Reply directly to this email or visit your Architecture Learning Lab.</p>
                </div>
            </div>
            """.formatted(userName, companyName, appUrl);

        dispatchEmail(toEmail, subject, html, "WELCOME_EMAIL");
    }

    /**
     * Workspace Member Invitation Email.
     */
    @Async
    public void sendInvitationEmail(String toEmail, String inviterName, String companyName, String role, String inviteLink) {
        String subject = inviterName + " invited you to join " + companyName + " on NexSpace";
        String html = """
            <div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; max-width: 580px; margin: 0 auto; background: #0f172a; border-radius: 12px; border: 1px solid #1e293b; overflow: hidden; color: #f8fafc;">
                <div style="background: linear-gradient(135deg, #3b82f6, #1d4ed8); padding: 32px 24px; text-align: center;">
                    <h1 style="color: white; margin: 0; font-size: 24px;">Team Invitation</h1>
                    <p style="color: #dbeafe; margin-top: 8px; font-size: 14px;">Join %s on NexSpace</p>
                </div>
                <div style="padding: 32px 24px;">
                    <p style="font-size: 16px; line-height: 1.6;">Hello,</p>
                    <p style="color: #94a3b8; line-height: 1.6;"><strong>%s</strong> has invited you to collaborate in the <strong>%s</strong> workspace with the role of <span style="background: #334155; color: #38bdf8; padding: 3px 8px; border-radius: 4px; font-weight: 700;">%s</span>.</p>
                    <div style="margin: 28px 0; text-align: center;">
                        <a href="%s" style="background: #3b82f6; color: white; padding: 12px 28px; text-decoration: none; border-radius: 6px; font-weight: 600; display: inline-block;">Accept Invitation &amp; Join</a>
                    </div>
                    <p style="font-size: 13px; color: #64748b;">This invitation grants you isolated tenant access strictly bounded to %s.</p>
                </div>
            </div>
            """.formatted(companyName, inviterName, companyName, role, inviteLink != null ? inviteLink : appUrl, companyName);

        dispatchEmail(toEmail, subject, html, "MEMBER_INVITATION");
    }

    /**
     * Stripe Billing & Payment Notification Email.
     */
    @Async
    public void sendBillingAlertEmail(String toEmail, String companyName, String subject, String messageContent) {
        String html = """
            <div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; max-width: 580px; margin: 0 auto; background: #0f172a; border-radius: 12px; border: 1px solid #1e293b; overflow: hidden; color: #f8fafc;">
                <div style="background: linear-gradient(135deg, #6366f1, #8b5cf6); padding: 32px 24px; text-align: center;">
                    <h1 style="color: white; margin: 0; font-size: 22px;">Stripe Billing Notification</h1>
                    <p style="color: #e0e7ff; margin-top: 6px; font-size: 14px;">%s</p>
                </div>
                <div style="padding: 32px 24px;">
                    <p style="font-size: 15px; color: #cbd5e1; line-height: 1.6;">%s</p>
                    <div style="margin: 28px 0; text-align: center;">
                        <a href="%s" style="background: #6366f1; color: white; padding: 10px 24px; text-decoration: none; border-radius: 6px; font-weight: 600; display: inline-block;">View Billing Dashboard</a>
                    </div>
                </div>
            </div>
            """.formatted(companyName, messageContent, appUrl);

        dispatchEmail(toEmail, subject, html, "BILLING_NOTIFICATION");
    }

    /**
     * Task Assignment Notification Email.
     */
    @Async
    public void sendTaskAssignmentEmail(String toEmail, String assigneeName, String taskTitle, String projectName, String assignerName) {
        String subject = "New Task Assigned: " + taskTitle;
        String html = """
            <div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; max-width: 580px; margin: 0 auto; background: #0f172a; border-radius: 12px; border: 1px solid #1e293b; overflow: hidden; color: #f8fafc;">
                <div style="padding: 24px;">
                    <h2 style="color: #38bdf8; margin: 0 0 12px 0; font-size: 20px;">Task Assignment</h2>
                    <p style="color: #e2e8f0; font-size: 15px;">Hello %s,</p>
                    <p style="color: #94a3b8; font-size: 14px;"><strong>%s</strong> assigned you to a task in project <strong>%s</strong>:</p>
                    <div style="background: #1e293b; padding: 14px 18px; border-radius: 8px; border-left: 4px solid #6366f1; margin: 16px 0;">
                        <div style="font-size: 15px; font-weight: 700; color: #f8fafc;">%s</div>
                    </div>
                    <div style="margin: 20px 0; text-align: center;">
                        <a href="%s" style="background: #6366f1; color: white; padding: 9px 20px; text-decoration: none; border-radius: 6px; font-weight: 600; display: inline-block;">Open Task on Kanban</a>
                    </div>
                </div>
            </div>
            """.formatted(assigneeName != null ? assigneeName : "Teammate", assignerName, projectName, taskTitle, appUrl);

        dispatchEmail(toEmail, subject, html, "TASK_ASSIGNMENT");
    }

    /**
     * Test Email for verification.
     */
    public SentEmailRecord sendTestEmail(String toEmail) {
        String subject = "NexSpace Mail Service Test: Connection Successful";
        String html = """
            <div style="font-family: sans-serif; padding: 24px; background: #0f172a; color: white; border-radius: 8px;">
                <h2 style="color: #10b981;">SMTP Connection Verified!</h2>
                <p>This email confirms that your NexSpace mail delivery pipeline is working properly.</p>
                <p style="color: #94a3b8;">Sent via: %s (%s)</p>
            </div>
            """.formatted(mailHost, isSmtpConfigured() ? "Live SMTP" : "Virtual Dev Outbox");

        return dispatchEmail(toEmail, subject, html, "TEST_EMAIL");
    }

    private SentEmailRecord dispatchEmail(String toEmail, String subject, String bodyHtml, String emailType) {
        String emailId = "msg_" + UUID.randomUUID().toString().substring(0, 8);
        String deliveryStatus = "SIMULATED_OUTBOX";

        if (isSmtpConfigured() && mailSender.isPresent()) {
            try {
                MimeMessage message = mailSender.get().createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(mailFrom, mailFromName);
                helper.setTo(toEmail);
                helper.setSubject(subject);
                helper.setText(bodyHtml, true);

                mailSender.get().send(message);
                deliveryStatus = "SENT_SMTP";
                logger.info("[EmailService] Successfully sent live SMTP email [{}] to {}", emailType, toEmail);
            } catch (Exception ex) {
                logger.warn("[EmailService] Live SMTP send failed ({}), recording to Dev Outbox: {}", ex.getMessage(), ex.getClass().getSimpleName());
                deliveryStatus = "FAILED_SMTP_FALLBACK_OUTBOX";
            }
        } else {
            logger.info("[EmailService:DevOutbox] [{}] To: {} | Subject: '{}'", emailType, toEmail, subject);
        }

        SentEmailRecord record = new SentEmailRecord(
                emailId,
                toEmail,
                subject,
                bodyHtml,
                emailType,
                deliveryStatus,
                LocalDateTime.now()
        );

        outbox.add(record);
        return record;
    }
}
