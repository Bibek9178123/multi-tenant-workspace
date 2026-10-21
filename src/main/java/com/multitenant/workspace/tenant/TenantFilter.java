package com.multitenant.workspace.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.multitenant.workspace.repository.WorkspaceMemberRepository;
import com.multitenant.workspace.security.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Component
public class TenantFilter extends OncePerRequestFilter {

    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TenantFilter(WorkspaceMemberRepository workspaceMemberRepository) {
        this.workspaceMemberRepository = workspaceMemberRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // For /api/mail/, allow public access but set TenantContext if X-Tenant-ID is present
        if (path.startsWith("/api/mail/")) {
            String mailTenantId = request.getHeader("X-Tenant-ID");
            if (!StringUtils.hasText(mailTenantId)) {
                mailTenantId = request.getParameter("tenantId");
            }
            if (StringUtils.hasText(mailTenantId)) {
                TenantContext.setCurrentTenant(mailTenantId.trim());
            }
            try {
                filterChain.doFilter(request, response);
            } finally {
                TenantContext.clear();
            }
            return;
        }

        // Skip tenant enforcement for public routes, auth routes, webhooks, and education endpoints
        if (path.startsWith("/api/auth/") ||
            path.startsWith("/api/webhooks/") ||
            path.startsWith("/api/education/") ||
            path.equals("/api/workspaces") && request.getMethod().equalsIgnoreCase("GET") ||
            path.equals("/api/workspaces") && request.getMethod().equalsIgnoreCase("POST") ||
            !path.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String tenantId = request.getHeader("X-Tenant-ID");
        if (!StringUtils.hasText(tenantId)) {
            tenantId = request.getParameter("tenantId");
        }

        try {
            if (StringUtils.hasText(tenantId)) {
                tenantId = tenantId.trim();

                // If user is authenticated, verify membership to this tenant
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal userPrincipal) {
                    Long userId = userPrincipal.getId();
                    boolean isMember = workspaceMemberRepository.existsByWorkspaceIdAndUserId(tenantId, userId);
                    if (!isMember) {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.setContentType("application/json");
                        response.getWriter().write(objectMapper.writeValueAsString(
                                Map.of("error", "Access denied: You are not a member of workspace '" + tenantId + "'")
                        ));
                        return;
                    }
                }

                TenantContext.setCurrentTenant(tenantId);
            }

            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
