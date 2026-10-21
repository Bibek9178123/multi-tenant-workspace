package com.multitenant.workspace.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> checkHealth() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("service", "NexSpace Multi-Tenant SaaS");
        health.put("timestamp", Instant.now().toString());

        boolean dbOk = false;
        try (Connection conn = dataSource.getConnection()) {
            dbOk = conn.isValid(2);
            health.put("database", dbOk ? "CONNECTED" : "UNHEALTHY");
        } catch (Exception e) {
            health.put("database", "DOWN (" + e.getMessage() + ")");
        }

        return dbOk ? ResponseEntity.ok(health) : ResponseEntity.status(503).body(health);
    }
}
