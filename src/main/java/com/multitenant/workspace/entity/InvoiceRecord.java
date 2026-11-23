package com.multitenant.workspace.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Persists billing invoice records for a workspace/tenant.
 * Created whenever a Stripe payment event (checkout.session.completed or
 * invoice.payment_succeeded) is processed.
 */
@Entity
@Table(name = "invoice_records",
       indexes = {
           @Index(name = "idx_invoice_workspace", columnList = "workspace_id"),
           @Index(name = "idx_invoice_created", columnList = "created_at")
       })
public class InvoiceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Tenant / Workspace this invoice belongs to */
    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    /** Human-readable invoice number, e.g. INV-2026-00001 */
    @Column(name = "invoice_number", nullable = false, length = 40)
    private String invoiceNumber;

    /** Stripe invoice or session ID */
    @Column(name = "stripe_invoice_id", length = 100)
    private String stripeInvoiceId;

    /** Amount in USD (whole dollars) */
    @Column(name = "amount_usd", nullable = false)
    private long amountUsd;

    /** Currency code, default "USD" */
    @Column(name = "currency", length = 8)
    private String currency = "USD";

    /** e.g. PRO, ENTERPRISE */
    @Column(name = "plan", length = 32)
    private String plan;

    /** PAID, FAILED */
    @Column(name = "payment_status", length = 32)
    private String paymentStatus = "PAID";

    /** Source: CHECKOUT_COMPLETED, INVOICE_PAYMENT_SUCCEEDED */
    @Column(name = "event_type", length = 64)
    private String eventType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public InvoiceRecord() {}

    public InvoiceRecord(String workspaceId, String invoiceNumber, String stripeInvoiceId,
                         long amountUsd, String plan, String paymentStatus, String eventType) {
        this.workspaceId = workspaceId;
        this.invoiceNumber = invoiceNumber;
        this.stripeInvoiceId = stripeInvoiceId;
        this.amountUsd = amountUsd;
        this.plan = plan;
        this.paymentStatus = paymentStatus;
        this.eventType = eventType;
        this.createdAt = LocalDateTime.now();
    }

    @PrePersist
    public void onPrePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.currency == null) {
            this.currency = "USD";
        }
    }

    // --- Getters & Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }

    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }

    public String getStripeInvoiceId() { return stripeInvoiceId; }
    public void setStripeInvoiceId(String stripeInvoiceId) { this.stripeInvoiceId = stripeInvoiceId; }

    public long getAmountUsd() { return amountUsd; }
    public void setAmountUsd(long amountUsd) { this.amountUsd = amountUsd; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getPlan() { return plan; }
    public void setPlan(String plan) { this.plan = plan; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
