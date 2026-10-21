package com.multitenant.workspace.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;

/**
 * Base mapped superclass for all multi-tenant entities.
 * Automatically stamps the record with the active TenantContext ID on persist.
 */
@MappedSuperclass
public abstract class BaseTenantEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false, length = 64)
    private String tenantId;

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    @PrePersist
    public void prePersist() {
        if (this.tenantId == null || this.tenantId.trim().isEmpty()) {
            this.tenantId = TenantContext.getCurrentTenant();
        }
    }
}
