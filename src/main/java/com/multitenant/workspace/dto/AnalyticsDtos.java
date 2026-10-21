package com.multitenant.workspace.dto;

import java.util.Map;

public class AnalyticsDtos {

    public record WorkspaceSummaryResponse(
            long totalProjects,
            long totalTasks,
            long completedTasks,
            long inProgressTasks,
            long todoTasks,
            long inReviewTasks,
            long totalMembers,
            Map<String, Long> tasksByPriority
    ) {}
}
