package com.multitenant.workspace.security;

/**
 * Fine-grained permissions defining isolated data and action boundaries within a tenant company.
 */
public enum Permission {
    MANAGE_COMPANY("Change company settings, domain, or permanently delete the tenant"),
    MANAGE_BILLING("Upgrade/downgrade Stripe subscription, access invoices, change plans"),
    INVITE_MEMBERS("Invite new users and teammates to this tenant"),
    MANAGE_ROLES("Promote, demote, or change role assignments of other members"),
    CREATE_PROJECTS("Create new projects in the company workspace"),
    DELETE_PROJECTS("Permanently delete projects and their associated tasks"),
    MANAGE_TASKS("Create, update, move on Kanban board, and delete tasks"),
    VIEW_AUDIT_LOGS("Inspect the tenant's security and activity audit trail"),
    TRIGGER_WEBHOOKS("Simulate and process incoming Stripe payment webhook events");

    private final String description;

    Permission(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
