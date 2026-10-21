package com.multitenant.workspace.controller;

import com.multitenant.workspace.dto.AnalyticsDtos;
import com.multitenant.workspace.entity.ActivityLog;
import com.multitenant.workspace.service.ActivityLogService;
import com.multitenant.workspace.service.AnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final ActivityLogService activityLogService;

    public AnalyticsController(AnalyticsService analyticsService, ActivityLogService activityLogService) {
        this.analyticsService = analyticsService;
        this.activityLogService = activityLogService;
    }

    @GetMapping("/summary")
    public ResponseEntity<AnalyticsDtos.WorkspaceSummaryResponse> getSummary() {
        return ResponseEntity.ok(analyticsService.getSummary());
    }

    @GetMapping("/activities")
    public ResponseEntity<List<ActivityLog>> getRecentActivities() {
        return ResponseEntity.ok(activityLogService.getRecentActivities());
    }
}
