package com.multitenant.workspace.service;

import com.multitenant.workspace.entity.ActivityLog;
import com.multitenant.workspace.repository.ActivityLogRepository;
import com.multitenant.workspace.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;

    public ActivityLogService(ActivityLogRepository activityLogRepository) {
        this.activityLogRepository = activityLogRepository;
    }

    @Transactional
    public void log(String action, String details, Long userId, String userName) {
        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null || tenantId.isBlank()) {
            return;
        }
        ActivityLog log = new ActivityLog(userId, userName, action, details);
        activityLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public List<ActivityLog> getRecentActivities() {
        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            return List.of();
        }
        return activityLogRepository.findTop20ByTenantIdOrderByCreatedAtDesc(tenantId);
    }
}
