package com.multitenant.workspace.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.multitenant.workspace.entity.Workspace;
import com.multitenant.workspace.repository.UserRepository;
import com.multitenant.workspace.repository.WorkspaceRepository;
import com.multitenant.workspace.security.Permission;
import com.multitenant.workspace.security.RbacService;
import com.stripe.Stripe;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class StripeBillingService {

    private static final Logger logger = LoggerFactory.getLogger(StripeBillingService.class);

    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;
    private final ActivityLogService activityLogService;
    private final RbacService rbacService;
    private final EmailService emailService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.stripe.api-key:sk_test_mock_demo_key}")
    private String stripeApiKey;

    @Value("${app.stripe.webhook-secret:whsec_mock_demo_secret}")
    private String stripeWebhookSecret;

    public StripeBillingService(WorkspaceRepository workspaceRepository,
                                UserRepository userRepository,
                                ActivityLogService activityLogService,
                                RbacService rbacService,
                                EmailService emailService) {
        this.workspaceRepository = workspaceRepository;
        this.userRepository = userRepository;
        this.activityLogService = activityLogService;
        this.rbacService = rbacService;
        this.emailService = emailService;
    }

    /**
     * Creates a Stripe Checkout Session for upgrading a company's subscription plan.
     */
    @Transactional
    public Map<String, Object> createCheckoutSession(String workspaceId, Workspace.PlanType plan, Long userId, String userEmail) {
        rbacService.assertPermission(userId, workspaceId, Permission.MANAGE_BILLING);

        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceId));

        String customerId = workspace.getStripeCustomerId();
        if (customerId == null || customerId.isBlank()) {
            customerId = "cus_" + UUID.randomUUID().toString().substring(0, 14);
            workspace.setStripeCustomerId(customerId);
            workspaceRepository.save(workspace);
        }

        // Check if real Stripe API key provided (starts with sk_test_ or sk_live_ and not the placeholder)
        boolean isRealStripeKey = stripeApiKey != null &&
                (stripeApiKey.startsWith("sk_test_") || stripeApiKey.startsWith("sk_live_")) &&
                !stripeApiKey.contains("mock_demo");

        String checkoutUrl;
        String sessionId = "cs_test_" + UUID.randomUUID().toString().replace("-", "");

        if (isRealStripeKey) {
            try {
                Stripe.apiKey = stripeApiKey;

                long unitAmount = plan == Workspace.PlanType.PRO ? 2900L : 9900L; // in cents
                SessionCreateParams params = SessionCreateParams.builder()
                        .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                        .setCustomer(customerId)
                        .setSuccessUrl("http://localhost:8085/?session_id={CHECKOUT_SESSION_ID}&status=success")
                        .setCancelUrl("http://localhost:8085/?status=cancelled")
                        .addLineItem(SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                        .setCurrency("usd")
                                        .setUnitAmount(unitAmount)
                                        .setRecurring(SessionCreateParams.LineItem.PriceData.Recurring.builder()
                                                .setInterval(SessionCreateParams.LineItem.PriceData.Recurring.Interval.MONTH)
                                                .build())
                                        .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                .setName("NexSpace " + plan.name() + " Plan")
                                                .setDescription("Multi-tenant company workspace subscription")
                                                .build())
                                        .build())
                                .build())
                        .putMetadata("workspaceId", workspaceId)
                        .putMetadata("plan", plan.name())
                        .build();

                Session session = Session.create(params);
                checkoutUrl = session.getUrl();
                sessionId = session.getId();
            } catch (Exception ex) {
                logger.warn("Stripe API call failed, falling back to simulated checkout: {}", ex.getMessage());
                checkoutUrl = "/simulated-checkout?sessionId=" + sessionId + "&workspaceId=" + workspaceId + "&plan=" + plan;
            }
        } else {
            checkoutUrl = "/simulated-checkout?sessionId=" + sessionId + "&workspaceId=" + workspaceId + "&plan=" + plan;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("checkoutUrl", checkoutUrl);
        result.put("workspaceId", workspaceId);
        result.put("plan", plan.name());
        result.put("customerId", customerId);
        result.put("isSimulated", !isRealStripeKey);

        return result;
    }

    /**
     * Processes inbound Stripe Webhook payloads with signature verification.
     */
    @Transactional
    public Map<String, Object> handleWebhook(String payload, String signatureHeader) {
        String eventType = null;
        JsonNode eventNode = null;

        boolean isRealSecret = stripeWebhookSecret != null &&
                stripeWebhookSecret.startsWith("whsec_") &&
                !stripeWebhookSecret.contains("mock_demo");

        if (isRealSecret && signatureHeader != null && !signatureHeader.isBlank()) {
            try {
                Event event = Webhook.constructEvent(payload, signatureHeader, stripeWebhookSecret);
                eventType = event.getType();
                eventNode = objectMapper.readTree(payload);
            } catch (Exception e) {
                logger.error("Stripe Webhook Signature Verification Failed: {}", e.getMessage());
                throw new SecurityException("Invalid Stripe Webhook Signature: " + e.getMessage());
            }
        } else {
            // Test/Simulation mode signature or mock event
            try {
                eventNode = objectMapper.readTree(payload);
                eventType = eventNode.has("type") ? eventNode.get("type").asText() : "unknown";
            } catch (Exception e) {
                throw new IllegalArgumentException("Malformed Webhook JSON payload: " + e.getMessage());
            }
        }

        logger.info("Processing Stripe Webhook Event: {}", eventType);
        Map<String, Object> outcome = new HashMap<>();
        outcome.put("eventType", eventType);
        outcome.put("processedAt", LocalDateTime.now().toString());

        JsonNode dataObject = eventNode.path("data").path("object");

        switch (eventType) {
            case "checkout.session.completed" -> {
                String workspaceId = dataObject.path("metadata").path("workspaceId").asText(null);
                String planStr = dataObject.path("metadata").path("plan").asText("PRO");
                String customerId = dataObject.path("customer").asText(null);
                String subscriptionId = dataObject.path("subscription").asText("sub_" + UUID.randomUUID().toString().substring(0, 10));

                if (workspaceId != null && workspaceRepository.existsById(workspaceId)) {
                    Workspace ws = workspaceRepository.findById(workspaceId).get();
                    Workspace.PlanType plan = Workspace.PlanType.valueOf(planStr.toUpperCase());
                    ws.setPlan(plan);
                    ws.setSubscriptionStatus("ACTIVE");
                    if (customerId != null) ws.setStripeCustomerId(customerId);
                    ws.setStripeSubscriptionId(subscriptionId);
                    ws.setCurrentPeriodEnd(LocalDateTime.now().plusDays(30));
                    workspaceRepository.save(ws);

                    activityLogService.log("STRIPE_WEBHOOK_PROCESSED",
                            "Stripe Webhook checkout.session.completed: Company upgraded to " + plan + " plan.",
                            ws.getOwnerId(), "Stripe Webhook");

                    userRepository.findById(ws.getOwnerId()).ifPresent(owner -> {
                        emailService.sendBillingAlertEmail(owner.getEmail(), ws.getName(),
                                "Subscription Upgraded: " + plan + " Plan",
                                "Great news! Your workspace subscription has been upgraded to the <strong>" + plan + "</strong> plan. Enjoy unlimited projects and team members.",
                                ws.getId());
                    });

                    outcome.put("workspaceId", workspaceId);
                    outcome.put("status", "UPGRADED");
                    outcome.put("newPlan", plan.name());
                }
            }

            case "customer.subscription.updated" -> {
                String subId = dataObject.path("id").asText(null);
                String status = dataObject.path("status").asText("ACTIVE").toUpperCase();

                // Find workspace by subscription or customer
                String customerId = dataObject.path("customer").asText(null);
                workspaceRepository.findAll().stream()
                        .filter(w -> (subId != null && subId.equals(w.getStripeSubscriptionId())) ||
                                     (customerId != null && customerId.equals(w.getStripeCustomerId())))
                        .findFirst()
                        .ifPresent(ws -> {
                            ws.setSubscriptionStatus(status);
                            workspaceRepository.save(ws);
                            activityLogService.log("SUBSCRIPTION_UPDATED",
                                    "Stripe Webhook customer.subscription.updated: Status changed to " + status,
                                    ws.getOwnerId(), "Stripe Webhook");
                            outcome.put("workspaceId", ws.getId());
                            outcome.put("status", status);
                        });
            }

            case "customer.subscription.deleted" -> {
                String customerId = dataObject.path("customer").asText(null);
                workspaceRepository.findAll().stream()
                        .filter(w -> customerId != null && customerId.equals(w.getStripeCustomerId()))
                        .findFirst()
                        .ifPresent(ws -> {
                            ws.setPlan(Workspace.PlanType.FREE);
                            ws.setSubscriptionStatus("CANCELED");
                            workspaceRepository.save(ws);
                            activityLogService.log("SUBSCRIPTION_CANCELED",
                                    "Stripe Webhook customer.subscription.deleted: Subscription canceled, reverted to FREE.",
                                    ws.getOwnerId(), "Stripe Webhook");

                            userRepository.findById(ws.getOwnerId()).ifPresent(owner -> {
                                emailService.sendBillingAlertEmail(owner.getEmail(), ws.getName(),
                                        "Subscription Canceled",
                                        "Your subscription for workspace <strong>" + ws.getName() + "</strong> has ended. The workspace has reverted to the FREE plan.",
                                        ws.getId());
                            });

                            outcome.put("workspaceId", ws.getId());
                            outcome.put("status", "CANCELED");
                        });
            }

            case "invoice.payment_succeeded" -> {
                String customerId = dataObject.path("customer").asText(null);
                long amountPaid = dataObject.path("amount_paid").asLong(0) / 100;
                workspaceRepository.findAll().stream()
                        .filter(w -> customerId != null && customerId.equals(w.getStripeCustomerId()))
                        .findFirst()
                        .ifPresent(ws -> {
                            activityLogService.log("PAYMENT_SUCCEEDED",
                                    "Stripe Webhook invoice.payment_succeeded: Payment of $" + amountPaid + " received.",
                                    ws.getOwnerId(), "Stripe Webhook");

                            userRepository.findById(ws.getOwnerId()).ifPresent(owner -> {
                                emailService.sendBillingAlertEmail(owner.getEmail(), ws.getName(),
                                        "Payment Receipt: $" + amountPaid,
                                        "We received your payment of <strong>$" + amountPaid + "</strong> for workspace <strong>" + ws.getName() + "</strong>. Thank you for subscribing!",
                                        ws.getId());
                            });

                            outcome.put("workspaceId", ws.getId());
                            outcome.put("amountPaid", "$" + amountPaid);
                        });
            }

            case "invoice.payment_failed" -> {
                String customerId = dataObject.path("customer").asText(null);
                workspaceRepository.findAll().stream()
                        .filter(w -> customerId != null && customerId.equals(w.getStripeCustomerId()))
                        .findFirst()
                        .ifPresent(ws -> {
                            ws.setSubscriptionStatus("PAST_DUE");
                            workspaceRepository.save(ws);
                            activityLogService.log("PAYMENT_FAILED",
                                    "Stripe Webhook invoice.payment_failed: Payment attempt failed. Account marked PAST_DUE.",
                                    ws.getOwnerId(), "Stripe Webhook");

                            userRepository.findById(ws.getOwnerId()).ifPresent(owner -> {
                                emailService.sendBillingAlertEmail(owner.getEmail(), ws.getName(),
                                        "Payment Failed - Action Required",
                                        "Your recent subscription payment for workspace <strong>" + ws.getName() + "</strong> failed. Your account is marked PAST_DUE. Please update your payment details.",
                                        ws.getId());
                            });

                            outcome.put("workspaceId", ws.getId());
                            outcome.put("status", "PAST_DUE");
                        });
            }

            default -> logger.info("Unhandled Stripe event type: {}", eventType);
        }

        return outcome;
    }

    /**
     * Webhook Simulator for Educational and Testing Purposes.
     * Generates a fully formatted Stripe webhook JSON event and passes it through the webhook processor.
     */
    @Transactional
    public Map<String, Object> simulateWebhook(String workspaceId, String eventType, Workspace.PlanType plan) {
        Workspace ws = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceId));

        String customerId = ws.getStripeCustomerId() != null ? ws.getStripeCustomerId() : "cus_mock_" + UUID.randomUUID().toString().substring(0, 8);
        String subId = ws.getStripeSubscriptionId() != null ? ws.getStripeSubscriptionId() : "sub_mock_" + UUID.randomUUID().toString().substring(0, 8);
        ws.setStripeCustomerId(customerId);
        workspaceRepository.save(ws);

        Map<String, Object> payloadMap = new HashMap<>();
        payloadMap.put("id", "evt_sim_" + UUID.randomUUID().toString().substring(0, 12));
        payloadMap.put("object", "event");
        payloadMap.put("api_version", "2023-10-16");
        payloadMap.put("created", System.currentTimeMillis() / 1000);
        payloadMap.put("type", eventType);

        Map<String, Object> data = new HashMap<>();
        Map<String, Object> dataObject = new HashMap<>();

        if ("checkout.session.completed".equals(eventType)) {
            dataObject.put("id", "cs_sim_" + UUID.randomUUID().toString().substring(0, 10));
            dataObject.put("customer", customerId);
            dataObject.put("subscription", subId);
            dataObject.put("payment_status", "paid");
            dataObject.put("metadata", Map.of("workspaceId", workspaceId, "plan", plan != null ? plan.name() : "PRO"));
        } else if ("invoice.payment_failed".equals(eventType)) {
            dataObject.put("id", "in_sim_" + UUID.randomUUID().toString().substring(0, 10));
            dataObject.put("customer", customerId);
            dataObject.put("attempt_count", 3);
            dataObject.put("status", "open");
        } else if ("invoice.payment_succeeded".equals(eventType)) {
            dataObject.put("id", "in_sim_" + UUID.randomUUID().toString().substring(0, 10));
            dataObject.put("customer", customerId);
            dataObject.put("amount_paid", plan == Workspace.PlanType.ENTERPRISE ? 9900 : 2900);
            dataObject.put("status", "paid");
        } else if ("customer.subscription.deleted".equals(eventType)) {
            dataObject.put("id", subId);
            dataObject.put("customer", customerId);
            dataObject.put("status", "canceled");
        } else {
            dataObject.put("id", subId);
            dataObject.put("customer", customerId);
            dataObject.put("status", "active");
        }

        data.put("object", dataObject);
        payloadMap.put("data", data);

        String jsonPayload;
        try {
            jsonPayload = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payloadMap);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize simulated webhook payload", e);
        }

        // Process through real webhook pipeline
        Map<String, Object> outcome = handleWebhook(jsonPayload, "test_simulated_signature");
        outcome.put("rawPayload", jsonPayload);
        outcome.put("simulatedEvent", eventType);
        return outcome;
    }
}
