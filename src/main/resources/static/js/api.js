/**
 * API Service for NexSpace Multi-Tenant SaaS
 * Handles JWT Authorization and X-Tenant-ID Header propagation.
 */

const API = {
    BASE_URL: '/api',

    getToken() {
        return localStorage.getItem('nexspace_token');
    },

    setToken(token) {
        if (token) {
            localStorage.setItem('nexspace_token', token);
        } else {
            localStorage.removeItem('nexspace_token');
        }
    },

    getActiveTenantId() {
        return localStorage.getItem('nexspace_tenant_id');
    },

    setActiveTenantId(tenantId) {
        if (tenantId) {
            localStorage.setItem('nexspace_tenant_id', tenantId);
        } else {
            localStorage.removeItem('nexspace_tenant_id');
        }
    },

    async request(endpoint, options = {}) {
        const headers = {
            'Content-Type': 'application/json',
            ...(options.headers || {})
        };

        const token = this.getToken();
        if (token) {
            headers['Authorization'] = `Bearer ${token}`;
        }

        const tenantId = this.getActiveTenantId();
        if (tenantId) {
            headers['X-Tenant-ID'] = tenantId;
        }

        const config = {
            ...options,
            headers
        };

        try {
            const response = await fetch(`${this.BASE_URL}${endpoint}`, config);

            // Handle unauthorized (session expired)
            if (response.status === 401) {
                this.setToken(null);
                if (window.showAuthModal) {
                    window.showAuthModal();
                }
                throw new Error('Session expired. Please sign in again.');
            }

            // Handle forbidden
            if (response.status === 403) {
                const errData = await response.json().catch(() => ({}));
                throw new Error(errData.error || 'Access forbidden: You do not have permission for this workspace.');
            }

            // Other error statuses
            if (!response.ok) {
                const errData = await response.json().catch(() => ({}));
                const message = errData.error || Object.values(errData)[0] || `Request failed with status ${response.status}`;
                throw new Error(message);
            }

            // Return json if available, else empty object
            const contentType = response.headers.get('content-type');
            if (contentType && contentType.includes('application/json')) {
                return await response.json();
            }
            return {};
        } catch (error) {
            console.error(`API Error [${options.method || 'GET'} ${endpoint}]:`, error);
            throw error;
        }
    },

    // Auth endpoints
    login(email, password) {
        return this.request('/auth/login', {
            method: 'POST',
            body: JSON.stringify({ email, password })
        });
    },

    register(fullName, email, password, workspaceName) {
        return this.request('/auth/register', {
            method: 'POST',
            body: JSON.stringify({ fullName, email, password, workspaceName })
        });
    },

    getProfile() {
        return this.request('/auth/me');
    },

    // Workspaces
    getWorkspaces() {
        return this.request('/workspaces');
    },

    createWorkspace(name, slug, description) {
        return this.request('/workspaces', {
            method: 'POST',
            body: JSON.stringify({ name, slug, description })
        });
    },

    getWorkspace(id) {
        return this.request(`/workspaces/${id}`);
    },

    updateWorkspace(id, data) {
        return this.request(`/workspaces/${id}`, {
            method: 'PUT',
            body: JSON.stringify(data)
        });
    },

    deleteWorkspace(id) {
        return this.request(`/workspaces/${id}`, {
            method: 'DELETE'
        });
    },

    // Workspace Members
    getMembers(workspaceId) {
        return this.request(`/workspaces/${workspaceId}/members`);
    },

    inviteMember(workspaceId, email, role) {
        return this.request(`/workspaces/${workspaceId}/members/invite`, {
            method: 'POST',
            body: JSON.stringify({ email, role })
        });
    },

    updateMemberRole(workspaceId, memberId, role) {
        return this.request(`/workspaces/${workspaceId}/members/${memberId}/role`, {
            method: 'PUT',
            body: JSON.stringify({ role })
        });
    },

    removeMember(workspaceId, memberId) {
        return this.request(`/workspaces/${workspaceId}/members/${memberId}`, {
            method: 'DELETE'
        });
    },

    // Projects
    getProjects() {
        return this.request('/projects');
    },

    createProject(name, description, color) {
        return this.request('/projects', {
            method: 'POST',
            body: JSON.stringify({ name, description, color })
        });
    },

    updateProject(id, data) {
        return this.request(`/projects/${id}`, {
            method: 'PUT',
            body: JSON.stringify(data)
        });
    },

    deleteProject(id) {
        return this.request(`/projects/${id}`, {
            method: 'DELETE'
        });
    },

    // Tasks & Kanban
    getTasks(projectId = null) {
        const query = projectId ? `?projectId=${projectId}` : '';
        return this.request(`/tasks${query}`);
    },

    createTask(data) {
        return this.request('/tasks', {
            method: 'POST',
            body: JSON.stringify(data)
        });
    },

    updateTask(id, data) {
        return this.request(`/tasks/${id}`, {
            method: 'PUT',
            body: JSON.stringify(data)
        });
    },

    updateTaskStatus(id, status, position = 0) {
        return this.request(`/tasks/${id}/status`, {
            method: 'PATCH',
            body: JSON.stringify({ status, position })
        });
    },

    deleteTask(id) {
        return this.request(`/tasks/${id}`, {
            method: 'DELETE'
        });
    },

    // Analytics & Activity
    getSummary() {
        return this.request('/analytics/summary');
    },

    getActivities() {
        return this.request('/analytics/activities');
    },

    // Stripe Billing & Webhook Simulation
    getBillingInfo() {
        return this.request('/billing/info');
    },

    createCheckout(plan) {
        return this.request('/billing/checkout', {
            method: 'POST',
            body: JSON.stringify({ plan })
        });
    },

    simulateWebhook(eventType, plan = 'PRO') {
        return this.request('/billing/simulate-webhook', {
            method: 'POST',
            body: JSON.stringify({ eventType, plan })
        });
    },

    // Architecture & RBAC Educational Endpoints
    getRbacMatrix() {
        return this.request('/education/rbac-matrix');
    },

    testRbacPermission(role, permission) {
        return this.request('/education/test-permission', {
            method: 'POST',
            body: JSON.stringify({ role, permission })
        });
    },

    getArchitectureSummary() {
        return this.request('/education/architecture-summary');
    },

    // Mail Service & Dev Outbox
    getMailStatus() {
        return this.request('/mail/status');
    },

    getMailOutbox() {
        return this.request('/mail/outbox');
    },

    sendTestEmail(toEmail) {
        return this.request('/mail/test', {
            method: 'POST',
            body: JSON.stringify({ toEmail })
        });
    },

    clearMailOutbox() {
        return this.request('/mail/outbox', {
            method: 'DELETE'
        });
    }
};
