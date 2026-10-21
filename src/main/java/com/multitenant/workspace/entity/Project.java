package com.multitenant.workspace.entity;

import com.multitenant.workspace.tenant.BaseTenantEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "projects", indexes = {
    @Index(name = "idx_project_tenant", columnList = "tenant_id")
})
public class Project extends BaseTenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(length = 24)
    private String color = "#3b82f6";

    @Column(nullable = false, length = 32)
    private String status = "ACTIVE"; // ACTIVE, ARCHIVED

    @Column(name = "created_by_id")
    private Long createdById;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Project() {}

    public Project(String name, String description, String color, Long createdById) {
        this.name = name;
        this.description = description;
        this.color = color != null && !color.isBlank() ? color : "#3b82f6";
        this.createdById = createdById;
        this.status = "ACTIVE";
    }

    @Override
    @PrePersist
    public void prePersist() {
        super.prePersist();
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getCreatedById() {
        return createdById;
    }

    public void setCreatedById(Long createdById) {
        this.createdById = createdById;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
