/**
 * Main Application Controller for NexSpace Multi-Tenant SaaS
 */

const state = {
    user: null,
    workspaces: [],
    activeWorkspace: null,
    activeTab: 'dashboard',
    projects: [],
    tasks: [],
    members: [],
    activities: [],
    filterProjectId: '',
    searchQuery: '',
    draggedTaskId: null,
    outboxCache: [],
    kanbanGroupBy: 'status',
    kanbanSearch: '',
    currentTaskAttachments: []
};

// -----------------------------------------------------------------------------
// App Initialization
// -----------------------------------------------------------------------------
document.addEventListener('DOMContentLoaded', async () => {
    initEventListeners();
    await checkAuthAndLoad();
});

async function checkAuthAndLoad() {
    const token = API.getToken();
    if (!token) {
        showAuthModal();
        return;
    }

    try {
        const user = await API.getProfile();
        state.user = user;
        updateUserUI(user);

        // Load workspaces
        const workspaces = await API.getWorkspaces();
        state.workspaces = workspaces;

        if (workspaces.length === 0) {
            showToast('No workspaces found. Please create one.', 'info');
            openNewWorkspaceModal();
            return;
        }

        // Determine active workspace
        const savedTenantId = API.getActiveTenantId();
        let current = workspaces.find(w => w.id === savedTenantId);
        if (!current) {
            current = workspaces[0];
            API.setActiveTenantId(current.id);
        }
        state.activeWorkspace = current;

        hideAuthModal();
        updateWorkspaceUI(current);
        renderWorkspaceList();

        // Load data for active workspace
        await loadWorkspaceData();
    } catch (error) {
        console.warn('Initial session check failed:', error);
        showAuthModal();
    }
}

// -----------------------------------------------------------------------------
// Workspace Switching (Multi-Tenancy Context)
// -----------------------------------------------------------------------------
async function switchWorkspace(workspaceId) {
    const target = state.workspaces.find(w => w.id === workspaceId);
    if (!target) return;

    state.activeWorkspace = target;
    API.setActiveTenantId(target.id);

    closeWorkspaceMenu();
    updateWorkspaceUI(target);
    renderWorkspaceList();

    showToast(`Switched to workspace: ${target.name}`, 'info');

    // Reload all tenant-isolated data
    await loadWorkspaceData();
}

async function loadWorkspaceData() {
    if (!state.activeWorkspace) return;

    try {
        // Parallel load of tenant isolated resources
        const [projects, tasks, summary, members, activities] = await Promise.all([
            API.getProjects(),
            API.getTasks(),
            API.getSummary().catch(() => null),
            API.getMembers(state.activeWorkspace.id).catch(() => []),
            API.getActivities().catch(() => [])
        ]);

        state.projects = projects;
        state.tasks = tasks;
        state.members = members;
        state.activities = activities;

        // Render notifications in topbar
        renderNotifications();

        // Populate selects
        populateProjectSelects();
        populateAssigneeSelects();

        // Update Nav counts
        document.getElementById('nav-task-count').textContent = tasks.length;
        document.getElementById('nav-project-count').textContent = projects.length;
        document.getElementById('nav-member-count').textContent = members.length;

        // Update email count
        API.getMailOutbox().then(emails => {
            state.outboxCache = emails || [];
            const navEmail = document.getElementById('nav-email-count');
            if (navEmail) navEmail.textContent = state.outboxCache.length;
        }).catch(() => {});

        // Render current active tab
        renderActiveView(summary);
    } catch (error) {
        console.error('Failed to load workspace data:', error);
        showToast(error.message, 'error');
    }
}

function updateWorkspaceUI(ws) {
    document.getElementById('current-ws-name').textContent = ws.name;
    document.getElementById('current-ws-avatar').textContent = ws.name.charAt(0).toUpperCase();
    document.getElementById('current-ws-role-pill').textContent = ws.role || 'MEMBER';

    document.getElementById('topbar-ws-slug').textContent = ws.slug || ws.id;
    document.getElementById('footer-tenant-id').textContent = ws.id;
    const planBadge = document.getElementById('nav-plan-badge');
    if (planBadge) planBadge.textContent = ws.plan || 'FREE';

    // Fill Settings inputs
    document.getElementById('setting-ws-name').value = ws.name;
    document.getElementById('setting-ws-slug').value = ws.slug || ws.id;
    document.getElementById('setting-ws-desc').value = ws.description || '';
    document.getElementById('setting-ws-plan').value = ws.plan || 'FREE';

    // Show/hide danger zone based on role
    const dangerZone = document.getElementById('danger-zone-card');
    if (ws.role === 'OWNER') {
        dangerZone.style.display = 'block';
    } else {
        dangerZone.style.display = 'none';
    }
}

function renderWorkspaceList() {
    const container = document.getElementById('workspace-list-container');
    container.innerHTML = '';

    state.workspaces.forEach(ws => {
        const item = document.createElement('button');
        item.className = `workspace-item ${ws.id === state.activeWorkspace?.id ? 'active' : ''}`;
        item.onclick = () => switchWorkspace(ws.id);

        item.innerHTML = `
            <div class="ws-item-avatar">${ws.name.charAt(0).toUpperCase()}</div>
            <div class="ws-item-info">
                <div class="ws-item-name">${escapeHtml(ws.name)}</div>
                <div class="ws-item-sub">${ws.role} • ${ws.memberCount || 1} member(s)</div>
            </div>
            ${ws.id === state.activeWorkspace?.id ? '<i class="fa-solid fa-check" style="color: var(--primary); font-size: 0.8rem;"></i>' : ''}
        `;
        container.appendChild(item);
    });
}

function toggleWorkspaceMenu(e) {
    e.stopPropagation();
    const menu = document.getElementById('workspace-menu');
    menu.classList.toggle('hidden');
}

function closeWorkspaceMenu() {
    document.getElementById('workspace-menu').classList.add('hidden');
}

// -----------------------------------------------------------------------------
// Tabs Navigation
// -----------------------------------------------------------------------------
function switchTab(tabName) {
    state.activeTab = tabName;
    closeMobileSidebar();

    // Update nav buttons
    document.querySelectorAll('.sidebar-nav .nav-item').forEach(btn => {
        if (btn.dataset.tab === tabName) {
            btn.classList.add('active');
        } else {
            btn.classList.remove('active');
        }
    });

    // Update page title
    const titles = {
        dashboard: 'Overview',
        kanban: 'Kanban Board',
        projects: 'Projects',
        members: 'Team & Members',
        billing: 'Stripe Billing & Subscriptions',
        outbox: 'Email Delivery Outbox & SMTP Tester',
        lab: 'Architecture & RBAC Learning Lab',
        settings: 'Workspace Settings'
    };
    document.getElementById('page-title').textContent = titles[tabName] || 'Overview';

    // Toggle content views
    document.querySelectorAll('.content-view').forEach(view => {
        view.classList.remove('active');
    });
    const targetView = document.getElementById(`view-${tabName}`);
    if (targetView) targetView.classList.add('active');

    renderActiveView();
}

function renderActiveView(summaryData = null) {
    if (state.activeTab === 'dashboard') {
        renderDashboard(summaryData);
    } else if (state.activeTab === 'kanban') {
        renderKanban();
    } else if (state.activeTab === 'projects') {
        renderProjects();
    } else if (state.activeTab === 'members') {
        renderMembers();
    } else if (state.activeTab === 'billing') {
        renderBilling();
    } else if (state.activeTab === 'outbox') {
        renderOutbox();
    } else if (state.activeTab === 'lab') {
        renderLab();
    }
}

// -----------------------------------------------------------------------------
// View 1: Dashboard Rendering
// -----------------------------------------------------------------------------
async function renderDashboard(summaryData) {
    let summary = summaryData;
    if (!summary) {
        try {
            summary = await API.getSummary();
        } catch (e) {
            summary = {
                totalProjects: state.projects.length,
                totalTasks: state.tasks.length,
                completedTasks: state.tasks.filter(t => t.status === 'DONE').length,
                inProgressTasks: state.tasks.filter(t => t.status === 'IN_PROGRESS').length,
                todoTasks: state.tasks.filter(t => t.status === 'TODO').length,
                inReviewTasks: state.tasks.filter(t => t.status === 'IN_REVIEW').length,
                totalMembers: state.members.length,
                tasksByPriority: {}
            };
        }
    }

    // Top metrics
    document.getElementById('metric-projects').textContent = summary.totalProjects;
    document.getElementById('metric-tasks').textContent = summary.totalTasks;
    document.getElementById('metric-completed').textContent = summary.completedTasks;
    document.getElementById('metric-members').textContent = summary.totalMembers;

    const rate = summary.totalTasks > 0 ? Math.round((summary.completedTasks / summary.totalTasks) * 100) : 0;
    document.getElementById('metric-completion-rate').textContent = `${rate}% completion rate`;
    document.getElementById('metric-plan-name').textContent = `${state.activeWorkspace?.plan || 'FREE'} Plan`;

    // Status bars
    const total = summary.totalTasks || 1;
    document.getElementById('bar-cnt-todo').textContent = summary.todoTasks;
    document.getElementById('bar-fill-todo').style.width = `${(summary.todoTasks / total) * 100}%`;

    document.getElementById('bar-cnt-progress').textContent = summary.inProgressTasks;
    document.getElementById('bar-fill-progress').style.width = `${(summary.inProgressTasks / total) * 100}%`;

    document.getElementById('bar-cnt-review').textContent = summary.inReviewTasks;
    document.getElementById('bar-fill-review').style.width = `${(summary.inReviewTasks / total) * 100}%`;

    document.getElementById('bar-cnt-done').textContent = summary.completedTasks;
    document.getElementById('bar-fill-done').style.width = `${(summary.completedTasks / total) * 100}%`;

    // Priority legend
    const pCounts = { URGENT: 0, HIGH: 0, MEDIUM: 0, LOW: 0 };
    state.tasks.forEach(t => {
        if (pCounts[t.priority] !== undefined) pCounts[t.priority]++;
    });
    document.getElementById('lg-urgent').textContent = pCounts.URGENT;
    document.getElementById('lg-high').textContent = pCounts.HIGH;
    document.getElementById('lg-medium').textContent = pCounts.MEDIUM;
    document.getElementById('lg-low').textContent = pCounts.LOW;

    // Render activity feed
    const feed = document.getElementById('activity-feed');
    if (state.activities.length === 0) {
        feed.innerHTML = '<div class="empty-state">No recent activities in this workspace.</div>';
    } else {
        feed.innerHTML = state.activities.map(act => `
            <div class="activity-item">
                <div class="activity-icon"><i class="fa-solid fa-bolt"></i></div>
                <div class="activity-content">
                    <div class="activity-text"><strong>${escapeHtml(act.userName || 'User')}</strong> ${escapeHtml(act.details || act.action)}</div>
                    <div class="activity-time">${formatDate(act.createdAt)}</div>
                </div>
            </div>
        `).join('');
    }
}

// -----------------------------------------------------------------------------
// View 2: Dynamic Kanban Board & Swimlanes Rendering
// -----------------------------------------------------------------------------
function changeKanbanGrouping(groupBy) {
    state.kanbanGroupBy = groupBy;
    renderKanban();
}

function handleKanbanSearch(val) {
    state.kanbanSearch = val.trim().toLowerCase();
    renderKanban();
}

function renderKanban() {
    const board = document.getElementById('kanban-board-container');
    if (!board) return;
    board.innerHTML = '';

    const projectMap = new Map(state.projects.map(p => [p.id, p]));

    // Filter tasks
    let filtered = state.tasks;
    if (state.filterProjectId) {
        filtered = filtered.filter(t => String(t.projectId) === String(state.filterProjectId));
    }
    if (state.searchQuery) {
        const q = state.searchQuery.toLowerCase();
        filtered = filtered.filter(t => t.title.toLowerCase().includes(q) || (t.description && t.description.toLowerCase().includes(q)));
    }
    if (state.kanbanSearch) {
        const q = state.kanbanSearch.toLowerCase();
        filtered = filtered.filter(t =>
            t.title.toLowerCase().includes(q) ||
            (t.description && t.description.toLowerCase().includes(q)) ||
            (t.assigneeName && t.assigneeName.toLowerCase().includes(q)) ||
            (t.priority && t.priority.toLowerCase().includes(q))
        );
    }

    const groupBy = state.kanbanGroupBy || 'status';

    // Toggle horizontal scroll class for wide multi-swimlanes
    if (groupBy === 'status') {
        board.classList.remove('swimlanes-scroll');
    } else {
        board.classList.add('swimlanes-scroll');
    }

    let columnConfigs = [];

    if (groupBy === 'status') {
        columnConfigs = [
            { key: 'TODO', title: 'To Do', indicatorClass: 'todo' },
            { key: 'IN_PROGRESS', title: 'In Progress', indicatorClass: 'in_progress' },
            { key: 'IN_REVIEW', title: 'In Review', indicatorClass: 'in_review' },
            { key: 'DONE', title: 'Done', indicatorClass: 'done' }
        ];
    } else if (groupBy === 'priority') {
        columnConfigs = [
            { key: 'URGENT', title: 'Urgent Priority', indicatorClass: 'urgent' },
            { key: 'HIGH', title: 'High Priority', indicatorClass: 'high' },
            { key: 'MEDIUM', title: 'Medium Priority', indicatorClass: 'medium' },
            { key: 'LOW', title: 'Low Priority', indicatorClass: 'low' }
        ];
    } else if (groupBy === 'assignee') {
        columnConfigs = state.members.map(m => ({
            key: String(m.userId),
            title: m.fullName || m.email,
            avatar: (m.fullName || m.email).charAt(0).toUpperCase()
        }));
        columnConfigs.push({
            key: 'unassigned',
            title: 'Unassigned',
            avatar: '?'
        });
    } else if (groupBy === 'project') {
        columnConfigs = state.projects.map(p => ({
            key: String(p.id),
            title: p.name,
            color: p.color
        }));
        if (columnConfigs.length === 0) {
            columnConfigs.push({
                key: 'no_projects',
                title: 'No Projects Created'
            });
        }
    }

    columnConfigs.forEach(col => {
        // Collect column tasks
        let colTasks = [];
        if (groupBy === 'status') {
            colTasks = filtered.filter(t => t.status === col.key);
        } else if (groupBy === 'priority') {
            colTasks = filtered.filter(t => t.priority === col.key);
        } else if (groupBy === 'assignee') {
            if (col.key === 'unassigned') {
                colTasks = filtered.filter(t => !t.assigneeId);
            } else {
                colTasks = filtered.filter(t => String(t.assigneeId) === col.key);
            }
        } else if (groupBy === 'project') {
            colTasks = filtered.filter(t => String(t.projectId) === col.key);
        }

        const colDiv = document.createElement('div');
        colDiv.className = 'kanban-column';
        colDiv.dataset.groupKey = col.key;
        colDiv.dataset.groupBy = groupBy;

        colDiv.ondragover = allowDrop;
        colDiv.ondrop = (e) => handleDynamicDrop(e, col.key, groupBy);

        let indicatorHtml = '';
        if (col.indicatorClass) {
            indicatorHtml = `<span class="status-indicator ${col.indicatorClass}"></span>`;
        } else if (col.color) {
            indicatorHtml = `<span class="status-indicator" style="background:${col.color};"></span>`;
        } else if (col.avatar) {
            indicatorHtml = `<span class="status-indicator" style="background:#818cf8;"></span>`;
        }

        colDiv.innerHTML = `
            <div class="column-header">
                <div class="column-title">
                    ${indicatorHtml}
                    <h3>${escapeHtml(col.title)}</h3>
                </div>
                <span class="column-count">${colTasks.length}</span>
            </div>
            <div class="kanban-cards-container"></div>
            <button class="btn-add-card" onclick="openNewTaskForColumn('${escapeHtml(col.key)}', '${groupBy}')">
                <i class="fa-solid fa-plus"></i> Add Task
            </button>
        `;

        const cardsContainer = colDiv.querySelector('.kanban-cards-container');

        colTasks.forEach(task => {
            const project = projectMap.get(task.projectId);
            const card = document.createElement('div');
            card.className = 'task-card';
            card.draggable = true;
            card.dataset.taskId = task.id;

            card.ondragstart = (e) => handleDragStart(e, task.id);
            card.ondragend = handleDragEnd;

            // Check attachments
            let attachments = [];
            try {
                if (task.attachmentsJson) {
                    attachments = JSON.parse(task.attachmentsJson);
                }
            } catch (err) {}

            const attBadge = attachments && attachments.length > 0 ? `
                <div style="margin-top: 4px;">
                    <span class="card-attachment-chip" onclick="previewTaskAttachments(event, ${task.id})" title="${attachments.length} attachment(s) - click to preview">
                        <i class="fa-solid fa-paperclip"></i> ${attachments.length} file${attachments.length > 1 ? 's' : ''}
                    </span>
                </div>
            ` : '';

            card.innerHTML = `
                <div style="display:flex; justify-content:space-between; align-items:center;">
                    <span class="card-project-pill" style="background: ${project ? project.color + '22' : '#374151'}; color: ${project ? project.color : '#9ca3af'};">
                        <i class="fa-solid fa-folder" style="font-size: 0.65rem;"></i>
                        ${escapeHtml(project ? project.name : 'Project')}
                    </span>
                    <div class="card-quick-actions">
                        <button class="btn-card-action" title="Edit Task" onclick="openEditTaskModal(${task.id})"><i class="fa-solid fa-pencil"></i></button>
                        <button class="btn-card-action" title="Delete Task" onclick="handleDeleteTask(${task.id})"><i class="fa-solid fa-trash-can"></i></button>
                    </div>
                </div>
                <div class="card-title">${escapeHtml(task.title)}</div>
                ${task.description ? `<div class="card-desc">${escapeHtml(task.description)}</div>` : ''}
                ${attBadge}
                <div class="card-meta-row">
                    <span class="priority-badge ${task.priority.toLowerCase()}">${task.priority}</span>
                    <div class="card-assignee-date">
                        ${task.dueDate ? `<span class="due-date-chip"><i class="fa-regular fa-calendar"></i> ${task.dueDate}</span>` : ''}
                        <div class="assignee-avatar" title="${escapeHtml(task.assigneeName || 'Unassigned')}">
                            ${task.assigneeName ? task.assigneeName.charAt(0).toUpperCase() : '?'}
                        </div>
                    </div>
                </div>
            `;
            cardsContainer.appendChild(card);
        });

        board.appendChild(colDiv);
    });
}

function openNewTaskForColumn(colKey, groupBy) {
    let defaultStatus = 'TODO';
    let defaultPriority = 'MEDIUM';
    let defaultAssigneeId = null;
    let defaultProjectId = null;

    if (groupBy === 'status') {
        defaultStatus = colKey;
    } else if (groupBy === 'priority') {
        defaultPriority = colKey;
    } else if (groupBy === 'assignee') {
        if (colKey !== 'unassigned') {
            defaultAssigneeId = Number(colKey);
        }
    } else if (groupBy === 'project') {
        if (colKey !== 'no_projects') {
            defaultProjectId = Number(colKey);
        }
    }

    openNewTaskModal(defaultStatus, defaultPriority, defaultAssigneeId, defaultProjectId);
}

function handleDragStart(e, taskId) {
    state.draggedTaskId = taskId;
    e.target.classList.add('dragging');
    e.dataTransfer.setData('text/plain', taskId);
    e.dataTransfer.effectAllowed = 'move';
}

function handleDragEnd(e) {
    e.target.classList.remove('dragging');
    document.querySelectorAll('.kanban-column').forEach(col => col.classList.remove('drag-over'));
}

function allowDrop(e) {
    e.preventDefault();
    const col = e.target.closest('.kanban-column');
    if (col) col.classList.add('drag-over');
}

async function handleDynamicDrop(e, targetKey, groupBy) {
    e.preventDefault();
    const col = e.target.closest('.kanban-column');
    if (col) col.classList.remove('drag-over');

    const taskId = state.draggedTaskId || e.dataTransfer.getData('text/plain');
    if (!taskId) return;

    const task = state.tasks.find(t => String(t.id) === String(taskId));
    if (!task) return;

    if (groupBy === 'status') {
        if (task.status === targetKey) return;
        task.status = targetKey;
        renderKanban();
        try {
            await API.updateTaskStatus(taskId, targetKey);
            showToast(`Moved to ${targetKey.replace('_', ' ')}`, 'success');
            API.getSummary().then(s => renderDashboard(s));
            loadWorkspaceActivities();
        } catch (error) {
            showToast(error.message, 'error');
            await loadWorkspaceData();
        }
    } else if (groupBy === 'priority') {
        if (task.priority === targetKey) return;
        task.priority = targetKey;
        renderKanban();
        try {
            await API.updateTask(taskId, {
                projectId: task.projectId,
                title: task.title,
                description: task.description,
                status: task.status,
                priority: targetKey,
                assigneeId: task.assigneeId,
                assigneeName: task.assigneeName,
                dueDate: task.dueDate,
                attachmentsJson: task.attachmentsJson
            });
            showToast(`Priority updated to ${targetKey}`, 'success');
            loadWorkspaceActivities();
        } catch (error) {
            showToast(error.message, 'error');
            await loadWorkspaceData();
        }
    } else if (groupBy === 'assignee') {
        const newAssigneeId = targetKey === 'unassigned' ? null : Number(targetKey);
        if (task.assigneeId === newAssigneeId) return;
        const targetMember = state.members.find(m => String(m.userId) === targetKey);
        task.assigneeId = newAssigneeId;
        task.assigneeName = targetMember ? (targetMember.fullName || targetMember.email) : null;
        renderKanban();
        try {
            await API.updateTask(taskId, {
                projectId: task.projectId,
                title: task.title,
                description: task.description,
                status: task.status,
                priority: task.priority,
                assigneeId: newAssigneeId,
                assigneeName: task.assigneeName,
                dueDate: task.dueDate,
                attachmentsJson: task.attachmentsJson
            });
            showToast(targetMember ? `Assigned to ${targetMember.fullName || targetMember.email}` : 'Task unassigned', 'success');
            loadWorkspaceActivities();
        } catch (error) {
            showToast(error.message, 'error');
            await loadWorkspaceData();
        }
    } else if (groupBy === 'project') {
        if (targetKey === 'no_projects') return;
        const newProjectId = Number(targetKey);
        if (task.projectId === newProjectId) return;
        task.projectId = newProjectId;
        renderKanban();
        try {
            await API.updateTask(taskId, {
                projectId: newProjectId,
                title: task.title,
                description: task.description,
                status: task.status,
                priority: task.priority,
                assigneeId: task.assigneeId,
                assigneeName: task.assigneeName,
                dueDate: task.dueDate,
                attachmentsJson: task.attachmentsJson
            });
            showToast('Moved to project', 'success');
            loadWorkspaceActivities();
        } catch (error) {
            showToast(error.message, 'error');
            await loadWorkspaceData();
        }
    }
}

// -----------------------------------------------------------------------------
// View 3: Projects Rendering
// -----------------------------------------------------------------------------
function renderProjects() {
    const grid = document.getElementById('projects-grid');
    grid.innerHTML = '';

    if (state.projects.length === 0) {
        grid.innerHTML = '<div class="empty-state" style="grid-column: 1 / -1;">No projects created yet. Click "Create Project" above!</div>';
        return;
    }

    state.projects.forEach(project => {
        const card = document.createElement('div');
        card.className = 'project-card';
        card.innerHTML = `
            <div class="proj-top">
                <span class="proj-color-tag" style="background: ${project.color}; box-shadow: 0 0 8px ${project.color};"></span>
                <span class="proj-badge">${project.status}</span>
            </div>
            <div class="proj-title">${escapeHtml(project.name)}</div>
            <div class="proj-desc">${escapeHtml(project.description || 'No description provided')}</div>
            <div class="proj-footer">
                <span class="proj-badge"><i class="fa-solid fa-list-check"></i> ${project.taskCount || 0} tasks</span>
                <button class="btn btn-secondary" style="padding: 4px 10px; font-size: 0.75rem;" onclick="filterAndOpenKanban(${project.id})">
                    View Tasks <i class="fa-solid fa-arrow-right"></i>
                </button>
            </div>
        `;
        grid.appendChild(card);
    });
}

function filterAndOpenKanban(projectId) {
    state.filterProjectId = projectId;
    document.getElementById('kanban-project-filter').value = projectId;
    switchTab('kanban');
}

// -----------------------------------------------------------------------------
// View 4: Members Table Rendering
// -----------------------------------------------------------------------------
function renderMembers() {
    const tbody = document.getElementById('members-table-body');
    tbody.innerHTML = '';

    if (state.members.length === 0) {
        tbody.innerHTML = '<tr><td colspan="5" class="empty-state">No members found.</td></tr>';
        return;
    }

    const isWorkspaceOwner = state.activeWorkspace?.role === 'OWNER';
    const isWorkspaceAdmin = state.activeWorkspace?.role === 'ADMIN' || isWorkspaceOwner;

    state.members.forEach(member => {
        const tr = document.createElement('tr');
        const isSelf = member.userId === state.user?.id;
        const isTargetOwner = member.role === 'OWNER';

        tr.innerHTML = `
            <td>
                <div class="member-cell">
                    <div class="member-avatar">${member.fullName ? member.fullName.charAt(0).toUpperCase() : member.email.charAt(0).toUpperCase()}</div>
                    <div class="member-info">
                        <span class="member-name">${escapeHtml(member.fullName || member.email.split('@')[0])} ${isSelf ? '<span style="color:var(--primary); font-size:0.75rem;">(You)</span>' : ''}</span>
                        <span class="member-email">${escapeHtml(member.email)}</span>
                    </div>
                </div>
            </td>
            <td>
                <span class="role-badge ${member.role.toLowerCase()}">${member.role}</span>
            </td>
            <td>
                <span class="status-badge ${member.status.toLowerCase()}">
                    <i class="fa-solid ${member.status === 'ACTIVE' ? 'fa-circle-check' : 'fa-clock'}"></i>
                    ${member.status}
                </span>
            </td>
            <td>${formatDate(member.joinedAt)}</td>
            <td class="text-right">
                ${isWorkspaceAdmin && !isTargetOwner ? `
                    <button class="btn btn-secondary" style="padding: 4px 8px; font-size: 0.75rem;" onclick="handlePromptChangeRole(${member.id}, '${member.role}')" title="Change Role">
                        <i class="fa-solid fa-user-shield"></i> Role
                    </button>
                    <button class="btn btn-danger" style="padding: 4px 8px; font-size: 0.75rem;" onclick="handleRemoveMember(${member.id}, '${escapeHtml(member.email)}')" title="Remove">
                        <i class="fa-solid fa-trash-can"></i>
                    </button>
                ` : isSelf && !isTargetOwner ? `
                    <button class="btn btn-secondary" style="padding: 4px 8px; font-size: 0.75rem;" onclick="handleRemoveMember(${member.id}, 'yourself')">
                        Leave Workspace
                    </button>
                ` : '<span style="color: var(--text-muted); font-size: 0.75rem;">No actions</span>'}
            </td>
        `;
        tbody.appendChild(tr);
    });
}

// -----------------------------------------------------------------------------
// Member Actions
// -----------------------------------------------------------------------------
async function handleInviteMember(e) {
    e.preventDefault();
    const email = document.getElementById('invite-email').value.trim();
    const role = document.getElementById('invite-role').value;

    try {
        await API.inviteMember(state.activeWorkspace.id, email, role);
        showToast(`Invitation sent to ${email}`, 'success');
        closeModal('modal-invite');
        document.getElementById('invite-email').value = '';

        // Reload members
        state.members = await API.getMembers(state.activeWorkspace.id);
        renderMembers();
    } catch (error) {
        showToast(error.message, 'error');
    }
}

async function handlePromptChangeRole(memberId, currentRole) {
    const roles = ['ADMIN', 'MEMBER', 'VIEWER'];
    const newRole = prompt(`Enter new role for member (${roles.join(', ')}):`, currentRole);
    if (!newRole || !roles.includes(newRole.toUpperCase())) return;

    try {
        await API.updateMemberRole(state.activeWorkspace.id, memberId, newRole.toUpperCase());
        showToast('Member role updated', 'success');
        state.members = await API.getMembers(state.activeWorkspace.id);
        renderMembers();
    } catch (error) {
        showToast(error.message, 'error');
    }
}

async function handleRemoveMember(memberId, email) {
    if (!confirm(`Are you sure you want to remove ${email} from this workspace?`)) return;

    try {
        await API.removeMember(state.activeWorkspace.id, memberId);
        showToast('Member removed from workspace', 'info');
        state.members = await API.getMembers(state.activeWorkspace.id);
        renderMembers();
    } catch (error) {
        showToast(error.message, 'error');
    }
}

// -----------------------------------------------------------------------------
// Project & Task Actions
// -----------------------------------------------------------------------------
async function handleCreateProject(e) {
    e.preventDefault();
    const name = document.getElementById('new-proj-name').value.trim();
    const description = document.getElementById('new-proj-desc').value.trim();
    const colorRadio = document.querySelector('input[name="proj-color"]:checked');
    const color = colorRadio ? colorRadio.value : '#6366f1';

    try {
        const newProj = await API.createProject(name, description, color);
        showToast(`Project '${newProj.name}' created`, 'success');
        closeModal('modal-new-project');
        document.getElementById('new-proj-name').value = '';
        document.getElementById('new-proj-desc').value = '';

        state.projects = await API.getProjects();
        populateProjectSelects();
        renderProjects();
        renderDashboard();
    } catch (error) {
        showToast(error.message, 'error');
    }
}

async function handleSaveTask(e) {
    e.preventDefault();
    const editId = document.getElementById('task-edit-id').value;
    const projectId = document.getElementById('task-project').value;
    const title = document.getElementById('task-title').value.trim();
    const description = document.getElementById('task-desc').value.trim();
    const status = document.getElementById('task-status').value;
    const priority = document.getElementById('task-priority').value;
    const assigneeSelect = document.getElementById('task-assignee');
    const assigneeId = assigneeSelect.value ? Number(assigneeSelect.value) : null;
    const assigneeName = assigneeSelect.selectedOptions[0]?.text || null;
    const dueDate = document.getElementById('task-due-date').value || null;
    const attachmentsJson = JSON.stringify(state.currentTaskAttachments || []);

    const payload = {
        projectId: Number(projectId),
        title,
        description,
        status,
        priority,
        assigneeId,
        assigneeName: assigneeId ? assigneeName : null,
        dueDate,
        attachmentsJson
    };

    try {
        if (editId) {
            await API.updateTask(editId, payload);
            showToast('Task updated successfully', 'success');
        } else {
            await API.createTask(payload);
            showToast('Task created successfully', 'success');
        }

        closeModal('modal-task');
        state.tasks = await API.getTasks();
        renderKanban();
        renderDashboard();
        loadWorkspaceActivities();
    } catch (error) {
        showToast(error.message, 'error');
    }
}

function openNewTaskModal(defaultStatus = 'TODO', defaultPriority = 'MEDIUM', defaultAssigneeId = null, defaultProjectId = null) {
    if (state.projects.length === 0) {
        showToast('Please create a project first before adding tasks.', 'info');
        openNewProjectModal();
        return;
    }

    document.getElementById('task-form').reset();
    document.getElementById('task-edit-id').value = '';
    document.getElementById('modal-task-title').innerHTML = '<i class="fa-solid fa-list-check"></i> Add New Task';
    document.getElementById('btn-save-task').textContent = 'Create Task';
    document.getElementById('task-status').value = defaultStatus;
    document.getElementById('task-priority').value = defaultPriority;

    if (defaultAssigneeId) {
        document.getElementById('task-assignee').value = defaultAssigneeId;
    } else {
        document.getElementById('task-assignee').value = '';
    }

    if (defaultProjectId) {
        document.getElementById('task-project').value = defaultProjectId;
    } else if (state.filterProjectId) {
        document.getElementById('task-project').value = state.filterProjectId;
    }

    // Reset attachments
    state.currentTaskAttachments = [];
    renderTaskAttachmentsList();

    openModal('modal-task');
}

function openEditTaskModal(taskId) {
    const task = state.tasks.find(t => t.id === taskId);
    if (!task) return;

    document.getElementById('task-edit-id').value = task.id;
    document.getElementById('task-title').value = task.title;
    document.getElementById('task-project').value = task.projectId;
    document.getElementById('task-desc').value = task.description || '';
    document.getElementById('task-status').value = task.status;
    document.getElementById('task-priority').value = task.priority;
    document.getElementById('task-assignee').value = task.assigneeId || '';
    document.getElementById('task-due-date').value = task.dueDate || '';

    // Load attachments
    try {
        state.currentTaskAttachments = task.attachmentsJson ? JSON.parse(task.attachmentsJson) : [];
    } catch {
        state.currentTaskAttachments = [];
    }
    renderTaskAttachmentsList();

    document.getElementById('modal-task-title').innerHTML = '<i class="fa-solid fa-pencil"></i> Edit Task';
    document.getElementById('btn-save-task').textContent = 'Update Task';

    openModal('modal-task');
}

// -----------------------------------------------------------------------------
// File Attachments Management
// -----------------------------------------------------------------------------
function handleTaskFileSelect(e) {
    const files = e.target.files || (e.dataTransfer && e.dataTransfer.files);
    if (!files || files.length === 0) return;

    Array.from(files).forEach(file => {
        if (file.size > 10 * 1024 * 1024) {
            showToast(`File '${file.name}' is too large (max 10MB)`, 'error');
            return;
        }

        const reader = new FileReader();
        reader.onload = (uploadEvent) => {
            const att = {
                id: 'att_' + Date.now() + '_' + Math.random().toString(36).substring(2, 7),
                fileName: file.name,
                fileSize: file.size,
                fileType: file.type || 'application/octet-stream',
                dataUrl: uploadEvent.target.result,
                uploadedAt: new Date().toISOString()
            };
            state.currentTaskAttachments.push(att);
            renderTaskAttachmentsList();
        };
        reader.readAsDataURL(file);
    });

    if (e.target && e.target.value !== undefined) {
        e.target.value = '';
    }
}

function formatFileSize(bytes) {
    if (!bytes || bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
}

function getFileIcon(fileType, fileName) {
    const fn = (fileName || '').toLowerCase();
    const ft = (fileType || '').toLowerCase();
    if (ft.startsWith('image/') || fn.endsWith('.png') || fn.endsWith('.jpg') || fn.endsWith('.jpeg') || fn.endsWith('.gif') || fn.endsWith('.webp')) return 'fa-regular fa-file-image';
    if (ft.includes('pdf') || fn.endsWith('.pdf')) return 'fa-regular fa-file-pdf';
    if (ft.includes('word') || fn.endsWith('.doc') || fn.endsWith('.docx')) return 'fa-regular fa-file-word';
    if (ft.includes('excel') || ft.includes('spreadsheet') || fn.endsWith('.xlsx') || fn.endsWith('.csv')) return 'fa-regular fa-file-excel';
    if (ft.includes('text') || fn.endsWith('.txt') || fn.endsWith('.md') || fn.endsWith('.json') || fn.endsWith('.yml')) return 'fa-regular fa-file-lines';
    return 'fa-regular fa-file';
}

function renderTaskAttachmentsList() {
    const list = document.getElementById('task-attachments-list');
    const countText = document.getElementById('task-att-count-text');
    if (!list) return;

    if (countText) {
        countText.textContent = `${state.currentTaskAttachments.length} attached`;
    }

    if (state.currentTaskAttachments.length === 0) {
        list.innerHTML = '';
        return;
    }

    list.innerHTML = state.currentTaskAttachments.map(att => `
        <div class="attachment-item">
            <div class="attachment-info">
                <i class="${getFileIcon(att.fileType, att.fileName)}"></i>
                <div style="min-width:0;">
                    <div class="attachment-name" title="${escapeHtml(att.fileName)}">${escapeHtml(att.fileName)}</div>
                    <div class="attachment-size">${formatFileSize(att.fileSize)}</div>
                </div>
            </div>
            <div class="attachment-actions">
                <button type="button" class="btn-att-action" title="Download" onclick="downloadAttachment('${att.id}')">
                    <i class="fa-solid fa-download"></i>
                </button>
                <button type="button" class="btn-att-action delete" title="Remove" onclick="removeTaskAttachment('${att.id}')">
                    <i class="fa-solid fa-trash-can"></i>
                </button>
            </div>
        </div>
    `).join('');
}

function removeTaskAttachment(attId) {
    state.currentTaskAttachments = state.currentTaskAttachments.filter(a => a.id !== attId);
    renderTaskAttachmentsList();
}

function downloadAttachment(attId) {
    const att = state.currentTaskAttachments.find(a => a.id === attId);
    if (!att) return;
    const a = document.createElement('a');
    a.href = att.dataUrl;
    a.download = att.fileName;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
}

function previewTaskAttachments(e, taskId) {
    e.stopPropagation();
    const task = state.tasks.find(t => t.id === taskId);
    if (!task) return;

    let attachments = [];
    try {
        attachments = task.attachmentsJson ? JSON.parse(task.attachmentsJson) : [];
    } catch {
        attachments = [];
    }

    const titleEl = document.getElementById('modal-att-title');
    const container = document.getElementById('modal-att-content');
    if (titleEl) {
        titleEl.innerHTML = `<i class="fa-solid fa-paperclip"></i> Attachments: ${escapeHtml(task.title)}`;
    }

    if (!attachments || attachments.length === 0) {
        container.innerHTML = '<div class="empty-state" style="grid-column: 1 / -1;">No files attached to this task.</div>';
    } else {
        container.innerHTML = attachments.map(att => {
            const isImage = (att.fileType || '').startsWith('image/');
            const thumbHtml = isImage
                ? `<img src="${att.dataUrl}" alt="${escapeHtml(att.fileName)}">`
                : `<i class="${getFileIcon(att.fileType, att.fileName)} file-icon-placeholder"></i>`;

            return `
                <div class="att-preview-card">
                    <div class="att-preview-thumb">
                        ${thumbHtml}
                    </div>
                    <div class="att-preview-meta">
                        <div class="att-preview-name" title="${escapeHtml(att.fileName)}">${escapeHtml(att.fileName)}</div>
                        <div class="att-preview-sub">${formatFileSize(att.fileSize)} • ${formatDate(att.uploadedAt)}</div>
                    </div>
                    <div class="att-preview-actions">
                        <a href="${att.dataUrl}" download="${escapeHtml(att.fileName)}" class="btn btn-secondary" style="font-size: 0.75rem; padding: 4px 10px;">
                            <i class="fa-solid fa-download"></i> Download
                        </a>
                        ${isImage ? `
                            <a href="${att.dataUrl}" target="_blank" class="btn btn-primary" style="font-size: 0.75rem; padding: 4px 10px;">
                                <i class="fa-regular fa-eye"></i> View Full
                            </a>
                        ` : ''}
                    </div>
                </div>
            `;
        }).join('');
    }

    openModal('modal-attachments-preview');
}

async function handleDeleteTask(taskId) {
    if (!confirm('Are you sure you want to delete this task?')) return;

    try {
        await API.deleteTask(taskId);
        showToast('Task deleted', 'info');
        state.tasks = state.tasks.filter(t => t.id !== taskId);
        renderKanban();
        renderDashboard();
        loadWorkspaceActivities();
    } catch (error) {
        showToast(error.message, 'error');
    }
}

function handleQuickCreate() {
    openNewTaskModal('TODO');
}

// -----------------------------------------------------------------------------
// Workspace Management & Settings
// -----------------------------------------------------------------------------
async function handleCreateWorkspace(e) {
    e.preventDefault();
    const name = document.getElementById('new-ws-name').value.trim();
    const slug = document.getElementById('new-ws-slug').value.trim();
    const desc = document.getElementById('new-ws-desc').value.trim();

    try {
        const newWs = await API.createWorkspace(name, slug, desc);
        showToast(`Workspace '${newWs.name}' created!`, 'success');
        closeModal('modal-new-workspace');

        state.workspaces = await API.getWorkspaces();
        await switchWorkspace(newWs.id);
    } catch (error) {
        showToast(error.message, 'error');
    }
}

async function handleSaveWorkspaceSettings(e) {
    e.preventDefault();
    const name = document.getElementById('setting-ws-name').value.trim();
    const desc = document.getElementById('setting-ws-desc').value.trim();
    const plan = document.getElementById('setting-ws-plan').value;

    try {
        const updated = await API.updateWorkspace(state.activeWorkspace.id, {
            name,
            description: desc,
            plan
        });
        showToast('Workspace settings saved', 'success');
        state.activeWorkspace = updated;
        updateWorkspaceUI(updated);

        // Update in list
        state.workspaces = await API.getWorkspaces();
        renderWorkspaceList();
    } catch (error) {
        showToast(error.message, 'error');
    }
}

async function handleDeleteWorkspace() {
    const ws = state.activeWorkspace;
    const confirmName = prompt(`To confirm deletion, type the workspace name: "${ws.name}"`);
    if (confirmName !== ws.name) {
        showToast('Workspace name did not match. Deletion cancelled.', 'info');
        return;
    }

    try {
        await API.deleteWorkspace(ws.id);
        showToast('Workspace deleted permanently.', 'info');

        state.workspaces = await API.getWorkspaces();
        if (state.workspaces.length > 0) {
            await switchWorkspace(state.workspaces[0].id);
        } else {
            openNewWorkspaceModal();
        }
    } catch (error) {
        showToast(error.message, 'error');
    }
}

// -----------------------------------------------------------------------------
// View 8: Email Delivery Outbox & Live SMTP Monitor
// -----------------------------------------------------------------------------
async function renderOutbox() {
    try {
        const [status, emails] = await Promise.all([
            API.getMailStatus().catch(() => null),
            API.getMailOutbox().catch(() => [])
        ]);

        state.outboxCache = emails || [];

        // Update status badge
        const badge = document.getElementById('email-mode-badge');
        const hostPill = document.getElementById('email-host-pill');
        const fromPill = document.getElementById('email-from-pill');
        const countBadge = document.getElementById('outbox-count-badge');
        const navCount = document.getElementById('nav-email-count');

        if (status) {
            const isLive = status.mode === 'LIVE_SMTP';
            if (badge) {
                badge.className = `badge-status-mode ${isLive ? 'live' : 'dev'}`;
                badge.innerHTML = isLive
                    ? '<i class="fa-solid fa-circle-check"></i> LIVE SMTP ACTIVE'
                    : '<i class="fa-solid fa-satellite-dish"></i> DEV SIMULATION OUTBOX';
            }
            if (hostPill) hostPill.innerHTML = `<i class="fa-solid fa-server"></i> Host: ${escapeHtml(status.host)}:${status.port}`;
            if (fromPill) fromPill.innerHTML = `<i class="fa-solid fa-at"></i> From: ${escapeHtml(status.fromAddress)}`;
        }

        const count = state.outboxCache.length;
        if (countBadge) countBadge.textContent = `${count} messages`;
        if (navCount) navCount.textContent = count;

        const tbody = document.getElementById('outbox-table-body');
        if (!tbody) return;

        if (state.outboxCache.length === 0) {
            tbody.innerHTML = `
                <tr>
                    <td colspan="6" class="text-center empty-state-cell">
                        <i class="fa-regular fa-envelope" style="font-size: 1.5rem; opacity: 0.5; margin-bottom: 8px; display: block;"></i>
                        No emails sent yet. Invite a team member, register a user, or send a test email above!
                    </td>
                </tr>
            `;
            return;
        }

        tbody.innerHTML = state.outboxCache.map(msg => {
            const isSmtp = msg.deliveryStatus === 'SENT_SMTP';
            const statusBadge = isSmtp
                ? '<span class="status-badge active"><i class="fa-solid fa-paper-plane"></i> Sent (SMTP)</span>'
                : '<span class="status-badge review"><i class="fa-solid fa-inbox"></i> Captured (Outbox)</span>';

            const timeStr = formatTimestamp(msg.sentAt);

            return `
                <tr>
                    <td>${statusBadge}</td>
                    <td><strong>${escapeHtml(msg.toEmail)}</strong></td>
                    <td><span class="badge-email-type ${escapeHtml(msg.emailType)}">${escapeHtml(msg.emailType)}</span></td>
                    <td>${escapeHtml(msg.subject)}</td>
                    <td style="font-size: 0.8rem; color: var(--text-muted);">${timeStr}</td>
                    <td class="text-right">
                        <button class="btn btn-secondary btn-sm" onclick="previewEmail('${escapeHtml(msg.id)}')">
                            <i class="fa-regular fa-eye"></i> View HTML
                        </button>
                    </td>
                </tr>
            `;
        }).join('');
    } catch (error) {
        console.error('Failed to load email outbox:', error);
    }
}

async function refreshOutbox() {
    await renderOutbox();
    showToast('Outbox refreshed', 'info');
}

async function handleSendTestEmail(e) {
    e.preventDefault();
    const input = document.getElementById('test-email-input');
    const toEmail = input.value.trim();
    if (!toEmail) return;

    const btn = document.getElementById('btn-send-test-mail');
    btn.disabled = true;

    try {
        const res = await API.sendTestEmail(toEmail);
        showToast(`Test email processed for ${toEmail} (${res.mode})`, 'success');
        input.value = '';
        await renderOutbox();
    } catch (err) {
        showToast(err.message, 'error');
    } finally {
        btn.disabled = false;
    }
}

async function handleClearOutbox() {
    if (!confirm('Clear all recorded emails from the development outbox?')) return;
    try {
        await API.clearMailOutbox();
        showToast('Dev outbox cleared', 'info');
        await renderOutbox();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

function toggleEmailGuide() {
    const guide = document.getElementById('email-guide-content');
    if (!guide) return;
    guide.classList.toggle('collapsed');
    const btn = document.getElementById('btn-toggle-guide');
    if (btn) {
        const isCollapsed = guide.classList.contains('collapsed');
        btn.innerHTML = isCollapsed
            ? '<i class="fa-solid fa-chevron-right"></i> Guide'
            : '<i class="fa-solid fa-chevron-down"></i> Guide';
    }
}

function previewEmail(emailId) {
    const msg = state.outboxCache.find(m => m.id === emailId);
    if (!msg) {
        showToast('Email record not found', 'error');
        return;
    }

    document.getElementById('modal-email-preview-subject').innerHTML = `<i class="fa-solid fa-envelope-open-text"></i> ${escapeHtml(msg.subject)}`;
    document.getElementById('modal-email-preview-meta').textContent = `To: ${msg.toEmail} | Type: ${msg.emailType} | Status: ${msg.deliveryStatus} | Sent: ${formatTimestamp(msg.sentAt)}`;

    const previewContainer = document.getElementById('email-preview-content');
    previewContainer.innerHTML = msg.bodyHtml;

    openModal('modal-email-preview');
}

// -----------------------------------------------------------------------------
// Auth Handlers (Sign In / Register)
// -----------------------------------------------------------------------------
async function handleLogin(e) {
    e.preventDefault();
    const email = document.getElementById('login-email').value.trim();
    const password = document.getElementById('login-password').value;

    const btn = document.getElementById('login-submit-btn');
    btn.disabled = true;

    try {
        let authResponse;
        try {
            authResponse = await API.login(email, password);
        } catch (err) {
            // Auto register demo accounts if user doesn't exist
            if (email === 'alex@example.com') {
                authResponse = await API.register('Alex Morgan', email, password, 'Acme Studio');
            } else {
                throw err;
            }
        }

        API.setToken(authResponse.token);
        if (authResponse.defaultWorkspaceId) {
            API.setActiveTenantId(authResponse.defaultWorkspaceId);
        }

        showToast(`Welcome back, ${authResponse.user.fullName}!`, 'success');
        await checkAuthAndLoad();
    } catch (error) {
        showToast(error.message, 'error');
    } finally {
        btn.disabled = false;
    }
}

async function handleRegister(e) {
    e.preventDefault();
    const fullName = document.getElementById('reg-fullname').value.trim();
    const email = document.getElementById('reg-email').value.trim();
    const password = document.getElementById('reg-password').value;
    const workspaceName = document.getElementById('reg-workspace').value.trim();

    const btn = document.getElementById('register-submit-btn');
    btn.disabled = true;

    try {
        const authResponse = await API.register(fullName, email, password, workspaceName);
        API.setToken(authResponse.token);
        if (authResponse.defaultWorkspaceId) {
            API.setActiveTenantId(authResponse.defaultWorkspaceId);
        }

        showToast(`Account and workspace created! Welcome ${fullName}!`, 'success');
        await checkAuthAndLoad();
    } catch (error) {
        showToast(error.message, 'error');
    } finally {
        btn.disabled = false;
    }
}

function handleLogout() {
    API.setToken(null);
    API.setActiveTenantId(null);
    state.user = null;
    state.workspaces = [];
    state.activeWorkspace = null;
    showToast('Signed out successfully', 'info');
    showAuthModal();
}

function showAuthTab(tab) {
    const loginForm = document.getElementById('login-form');
    const registerForm = document.getElementById('register-form');
    const loginBtn = document.getElementById('tab-login-btn');
    const registerBtn = document.getElementById('tab-register-btn');

    if (tab === 'login') {
        loginForm.classList.remove('hidden');
        registerForm.classList.add('hidden');
        loginBtn.classList.add('active');
        registerBtn.classList.remove('active');
    } else {
        loginForm.classList.add('hidden');
        registerForm.classList.remove('hidden');
        loginBtn.classList.remove('active');
        registerBtn.classList.add('active');
    }
}

function showAuthModal() {
    document.getElementById('auth-overlay').classList.remove('hidden');
}

function hideAuthModal() {
    document.getElementById('auth-overlay').classList.add('hidden');
}

function updateUserUI(user) {
    document.getElementById('current-user-name').textContent = user.fullName;
    document.getElementById('current-user-email').textContent = user.email;
    const avatar = document.getElementById('current-user-avatar');
    avatar.textContent = user.fullName.charAt(0).toUpperCase();
    if (user.avatarColor) {
        avatar.style.background = user.avatarColor;
    }
}

// -----------------------------------------------------------------------------
// Filters, Search & Helpers
// -----------------------------------------------------------------------------
function filterKanbanByProject(projectId) {
    state.filterProjectId = projectId;
    renderKanban();
}

function handleSearch(val) {
    state.searchQuery = val.trim();
    renderKanban();
}

function populateProjectSelects() {
    const kSelect = document.getElementById('kanban-project-filter');
    const tSelect = document.getElementById('task-project');

    // Filter select
    kSelect.innerHTML = '<option value="">All Projects</option>' +
        state.projects.map(p => `<option value="${p.id}" ${String(state.filterProjectId) === String(p.id) ? 'selected' : ''}>${escapeHtml(p.name)}</option>`).join('');

    // Modal select
    tSelect.innerHTML = state.projects.map(p => `<option value="${p.id}">${escapeHtml(p.name)}</option>`).join('');
}

function populateAssigneeSelects() {
    const aSelect = document.getElementById('task-assignee');
    aSelect.innerHTML = '<option value="">Unassigned</option>' +
        state.members.map(m => `<option value="${m.userId || ''}">${escapeHtml(m.fullName || m.email)}</option>`).join('');
}

function autoGenerateSlug(val) {
    const slug = val.toLowerCase().replace(/[^a-z0-9]/g, '-').replace(/-+/g, '-').replace(/^-|-$/g, '');
    document.getElementById('new-ws-slug').value = slug;
}

function openModal(id) {
    document.getElementById(id).classList.remove('hidden');
}

function closeModal(id) {
    document.getElementById(id).classList.add('hidden');
}

function openNewWorkspaceModal() {
    closeWorkspaceMenu();
    document.getElementById('new-ws-name').value = '';
    document.getElementById('new-ws-slug').value = '';
    document.getElementById('new-ws-desc').value = '';
    openModal('modal-new-workspace');
}

function openNewProjectModal() {
    document.getElementById('new-proj-name').value = '';
    document.getElementById('new-proj-desc').value = '';
    openModal('modal-new-project');
}

function openInviteModal() {
    document.getElementById('invite-email').value = '';
    document.getElementById('invite-role').value = 'MEMBER';
    openModal('modal-invite');
}

function showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;

    let icon = 'fa-circle-info';
    if (type === 'success') icon = 'fa-circle-check';
    if (type === 'error') icon = 'fa-triangle-exclamation';

    toast.innerHTML = `<i class="fa-solid ${icon}"></i> <span>${escapeHtml(message)}</span>`;
    container.appendChild(toast);

    setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transform = 'translateX(20px)';
        toast.style.transition = 'all 0.25s ease';
        setTimeout(() => toast.remove(), 250);
    }, 3500);
}

function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#039;");
}

function formatDate(isoStr) {
    if (!isoStr) return '-';
    try {
        const d = new Date(isoStr);
        return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
    } catch {
        return isoStr;
    }
}

function formatTimestamp(isoStr) {
    if (!isoStr) return '-';
    try {
        const d = new Date(isoStr);
        return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit' });
    } catch {
        return isoStr;
    }
}

// -----------------------------------------------------------------------------
// View 6: Stripe Billing & Subscriptions
// -----------------------------------------------------------------------------
async function renderBilling() {
    try {
        const info = await API.getBillingInfo();

        const statusEl = document.getElementById('company-sub-status');
        if (statusEl) {
            statusEl.textContent = info.subscriptionStatus;
            const banner = document.getElementById('billing-status-banner');
            if (banner) {
                banner.className = 'billing-status-banner ' + info.subscriptionStatus.toLowerCase();
            }
        }

        const activePlanEl = document.getElementById('bill-active-plan');
        if (activePlanEl) activePlanEl.textContent = info.plan;

        const prices = { FREE: '$0 / month', PRO: '$29 / month', ENTERPRISE: '$99 / month' };
        const priceEl = document.getElementById('bill-plan-price');
        if (priceEl) priceEl.textContent = prices[info.plan] || '$0 / month';

        const custEl = document.getElementById('bill-stripe-customer');
        if (custEl) custEl.textContent = info.stripeCustomerId || 'none';

        const subEl = document.getElementById('bill-stripe-sub');
        if (subEl) subEl.textContent = info.stripeSubscriptionId || 'none';

        const dateEl = document.getElementById('bill-renewal-date');
        if (dateEl) dateEl.textContent = formatDate(info.currentPeriodEnd);

        // Update plan buttons
        ['FREE', 'PRO', 'ENTERPRISE'].forEach(p => {
            const btn = document.getElementById(`btn-plan-${p}`);
            if (btn) {
                if (p === info.plan) {
                    btn.disabled = true;
                    btn.className = 'btn btn-secondary btn-block plan-action-btn';
                    btn.innerHTML = '<i class="fa-solid fa-check"></i> Current Plan';
                } else {
                    btn.disabled = false;
                    btn.className = 'btn btn-primary btn-block plan-action-btn';
                    btn.innerHTML = `<i class="fa-brands fa-stripe"></i> Upgrade to ${p} (${prices[p].split(' ')[0]}/mo)`;
                }
            }
        });
    } catch (error) {
        console.error('Failed to load billing info:', error);
        showToast(error.message, 'error');
    }
}

async function handleCheckout(targetPlan) {
    try {
        showToast(`Connecting to Stripe Checkout for ${targetPlan} plan...`, 'info');
        const checkoutData = await API.createCheckout(targetPlan);

        // Simulate instant payment confirmation in development environment
        showToast(`Stripe Checkout Session created (${checkoutData.sessionId}). Simulating payment confirmation webhook...`, 'info');

        const outcome = await API.simulateWebhook('checkout.session.completed', targetPlan);
        showToast(`🎉 Payment verified! Company successfully upgraded to ${targetPlan} Plan via Stripe Webhook!`, 'success');

        // Reload state
        state.workspaces = await API.getWorkspaces();
        if (state.activeWorkspace) {
            const updated = state.workspaces.find(w => w.id === state.activeWorkspace.id);
            if (updated) {
                state.activeWorkspace = updated;
                updateWorkspaceUI(updated);
            }
        }
        await renderBilling();
        renderDashboard();
    } catch (error) {
        showToast(error.message, 'error');
    }
}

// -----------------------------------------------------------------------------
// View 7: Architecture Learning Lab
// -----------------------------------------------------------------------------
async function renderLab() {
    await Promise.all([
        loadRbacMatrix(),
        refreshArchitectureSummary()
    ]);
}

async function loadRbacMatrix() {
    try {
        const matrix = await API.getRbacMatrix();
        const tbody = document.getElementById('rbac-matrix-tbody');
        if (!tbody) return;
        tbody.innerHTML = '';

        const roles = ['OWNER', 'ADMIN', 'BILLING_ADMIN', 'MEMBER', 'VIEWER'];
        const permissions = matrix.permissions || [];
        const roleMap = matrix.roles || {};

        permissions.forEach(perm => {
            const tr = document.createElement('tr');
            let cols = `
                <td>
                    <strong>${perm.name}</strong>
                    <div style="font-size:0.75rem; color:var(--text-muted);">${escapeHtml(perm.description)}</div>
                </td>
            `;

            roles.forEach(r => {
                const hasPerm = (roleMap[r] || []).includes(perm.name);
                cols += `
                    <td class="text-center">
                        ${hasPerm ? '<i class="fa-solid fa-check rbac-check"></i>' : '<i class="fa-solid fa-minus rbac-cross"></i>'}
                    </td>
                `;
            });

            tr.innerHTML = cols;
            tbody.appendChild(tr);
        });
    } catch (error) {
        console.error('Failed to load RBAC matrix:', error);
    }
}

async function handleTestRbacPermission() {
    const role = document.getElementById('test-role-select').value;
    const permission = document.getElementById('test-perm-select').value;

    try {
        const result = await API.testRbacPermission(role, permission);
        const box = document.getElementById('test-result-box');
        const badge = document.getElementById('test-result-badge');
        const text = document.getElementById('test-result-text');

        box.classList.remove('hidden');
        if (result.granted) {
            badge.className = 'test-result-badge allowed';
            badge.textContent = 'ALLOWED';
        } else {
            badge.className = 'test-result-badge denied';
            badge.textContent = 'ACCESS DENIED';
        }

        text.textContent = result.explanation;
    } catch (error) {
        showToast(error.message, 'error');
    }
}

async function handleSimulateWebhook(eventType, plan = 'PRO') {
    const consoleStatus = document.getElementById('webhook-console-status');
    const consoleOutput = document.getElementById('webhook-console-output');

    if (consoleStatus) consoleStatus.textContent = `Dispatching '${eventType}'...`;

    try {
        const outcome = await API.simulateWebhook(eventType, plan);

        if (consoleStatus) consoleStatus.textContent = 'Processed 200 OK (Idempotent Event Stamped)';
        if (consoleOutput) {
            consoleOutput.textContent = outcome.rawPayload || JSON.stringify(outcome, null, 2);
        }

        showToast(`Stripe Webhook '${eventType}' processed!`, 'success');

        // Reload tenant data
        state.workspaces = await API.getWorkspaces();
        if (state.activeWorkspace) {
            const updated = state.workspaces.find(w => w.id === state.activeWorkspace.id);
            if (updated) {
                state.activeWorkspace = updated;
                updateWorkspaceUI(updated);
            }
        }
        await renderBilling();
        renderDashboard();
    } catch (error) {
        if (consoleStatus) consoleStatus.textContent = 'Failed: ' + error.message;
        showToast(error.message, 'error');
    }
}

async function refreshArchitectureSummary() {
    const outputEl = document.getElementById('arch-summary-output');
    if (!outputEl) return;

    try {
        const summary = await API.getArchitectureSummary();
        const activeTenant = API.getActiveTenantId();
        const telemetry = {
            activeTenantId: activeTenant,
            activeWorkspaceName: state.activeWorkspace?.name || 'none',
            currentUserEmail: state.user?.email || 'none',
            currentUserRole: state.activeWorkspace?.role || 'none',
            transportHeaders: {
                "Authorization": "Bearer eyJhbGciOiJIUzI1NiJ9...",
                "X-Tenant-ID": activeTenant
            },
            threadLocalContext: `TenantContext.getCurrentTenant() == "${activeTenant}"`,
            sqlIsolationPattern: `SELECT * FROM {table} WHERE tenant_id = '${activeTenant}'`,
            cleanupGuarantee: "TenantContext.clear() executed in TenantFilter finally block",
            ...summary
        };

        outputEl.textContent = JSON.stringify(telemetry, null, 2);
    } catch (error) {
        outputEl.textContent = '// Failed to fetch telemetry: ' + error.message;
    }
}

// -----------------------------------------------------------------------------
// Real-Time Activity Notifications Controller
// -----------------------------------------------------------------------------
function toggleNotificationsMenu(e) {
    e.stopPropagation();
    const menu = document.getElementById('notifications-menu');
    if (!menu) return;
    const isHidden = menu.classList.contains('hidden');
    if (isHidden) {
        renderNotifications();
        menu.classList.remove('hidden');
    } else {
        menu.classList.add('hidden');
    }
}

function closeNotificationsMenu() {
    const menu = document.getElementById('notifications-menu');
    if (menu) menu.classList.add('hidden');
}

function getReadNotificationIds() {
    if (!state.activeWorkspace) return [];
    try {
        const stored = localStorage.getItem('nexspace_read_notifs_' + state.activeWorkspace.id);
        return stored ? JSON.parse(stored) : [];
    } catch {
        return [];
    }
}

function markAllNotificationsAsRead(e) {
    if (e) e.stopPropagation();
    if (!state.activeWorkspace) return;
    const allIds = state.activities.map(a => a.id);
    localStorage.setItem('nexspace_read_notifs_' + state.activeWorkspace.id, JSON.stringify(allIds));
    renderNotifications();
    showToast('All notifications marked as read', 'info');
}

function getActivityIconClass(action) {
    if (!action) return 'fa-solid fa-bolt';
    if (action.includes('TASK_CREATED')) return 'fa-solid fa-plus';
    if (action.includes('TASK_STATUS')) return 'fa-solid fa-arrows-split-up-and-left';
    if (action.includes('TASK_UPDATED')) return 'fa-solid fa-pen';
    if (action.includes('TASK_DELETED')) return 'fa-solid fa-trash';
    if (action.includes('MEMBER')) return 'fa-solid fa-user-plus';
    if (action.includes('PROJECT')) return 'fa-solid fa-folder';
    if (action.includes('BILLING') || action.includes('SUBSCRIPTION')) return 'fa-solid fa-credit-card';
    return 'fa-solid fa-bolt';
}

function renderNotifications() {
    const badge = document.getElementById('notification-badge');
    const list = document.getElementById('notifications-list');
    if (!badge || !list) return;

    const readIds = new Set(getReadNotificationIds());
    const unreadActivities = state.activities.filter(a => !readIds.has(a.id));
    const unreadCount = unreadActivities.length;

    badge.textContent = unreadCount;
    if (unreadCount === 0) {
        badge.classList.add('hidden-badge');
    } else {
        badge.classList.remove('hidden-badge');
    }

    if (state.activities.length === 0) {
        list.innerHTML = `
            <div class="notifications-empty">
                <i class="fa-regular fa-bell-slash"></i>
                <span>No workspace notifications yet</span>
            </div>
        `;
        return;
    }

    list.innerHTML = state.activities.slice(0, 20).map(act => {
        const isUnread = !readIds.has(act.id);
        return `
            <div class="notification-item ${isUnread ? 'unread' : ''}" onclick="handleNotificationClick(${act.id})">
                <div class="notif-icon"><i class="${getActivityIconClass(act.action)}"></i></div>
                <div class="notif-content">
                    <div class="notif-text"><strong>${escapeHtml(act.userName || 'User')}</strong> ${escapeHtml(act.details || act.action)}</div>
                    <div class="notif-time">${formatDate(act.createdAt)}</div>
                </div>
            </div>
        `;
    }).join('');
}

function handleNotificationClick(actId) {
    if (!state.activeWorkspace) return;
    const readIds = getReadNotificationIds();
    if (!readIds.includes(actId)) {
        readIds.push(actId);
        localStorage.setItem('nexspace_read_notifs_' + state.activeWorkspace.id, JSON.stringify(readIds));
        renderNotifications();
    }
    closeNotificationsMenu();
}

async function loadWorkspaceActivities() {
    try {
        state.activities = await API.getActivities().catch(() => []);
        renderNotifications();
        const feed = document.getElementById('activity-feed');
        if (feed) {
            feed.innerHTML = state.activities.map(act => `
                <div class="activity-item">
                    <div class="activity-icon"><i class="${getActivityIconClass(act.action)}"></i></div>
                    <div class="activity-content">
                        <div class="activity-text"><strong>${escapeHtml(act.userName || 'User')}</strong> ${escapeHtml(act.details || act.action)}</div>
                        <div class="activity-time">${formatDate(act.createdAt)}</div>
                    </div>
                </div>
            `).join('');
        }
    } catch {}
}

function setupDropzoneListeners() {
    const dropzone = document.getElementById('task-dropzone');
    if (!dropzone) return;

    ['dragenter', 'dragover'].forEach(eventName => {
        dropzone.addEventListener(eventName, (e) => {
            e.preventDefault();
            e.stopPropagation();
            dropzone.classList.add('dragover');
        }, false);
    });

    ['dragleave', 'drop'].forEach(eventName => {
        dropzone.addEventListener(eventName, (e) => {
            e.preventDefault();
            e.stopPropagation();
            dropzone.classList.remove('dragover');
        }, false);
    });

    dropzone.addEventListener('drop', (e) => {
        const dt = e.dataTransfer;
        if (dt && dt.files && dt.files.length > 0) {
            handleTaskFileSelect({ target: { files: dt.files }, dataTransfer: dt });
        }
    }, false);
}

// -----------------------------------------------------------------------------
// Mobile Sidebar Navigation Controller
// -----------------------------------------------------------------------------
function toggleMobileSidebar() {
    const sidebar = document.getElementById('app-sidebar');
    if (!sidebar) return;
    
    if (sidebar.classList.contains('mobile-open')) {
        closeMobileSidebar();
    } else {
        openMobileSidebar();
    }
}

function openMobileSidebar() {
    const sidebar = document.getElementById('app-sidebar');
    const backdrop = document.getElementById('sidebar-backdrop');
    if (sidebar) sidebar.classList.add('mobile-open');
    if (backdrop) backdrop.classList.add('active');
    document.body.classList.add('sidebar-active-body');
}

function closeMobileSidebar() {
    const sidebar = document.getElementById('app-sidebar');
    const backdrop = document.getElementById('sidebar-backdrop');
    if (sidebar) sidebar.classList.remove('mobile-open');
    if (backdrop) backdrop.classList.remove('active');
    document.body.classList.remove('sidebar-active-body');
}

function initEventListeners() {
    // Close dropdowns & mobile drawer when clicking outside
    document.addEventListener('click', (e) => {
        if (!e.target.closest('.workspace-selector-widget')) {
            closeWorkspaceMenu();
        }
        if (!e.target.closest('.notifications-dropdown-container')) {
            closeNotificationsMenu();
        }
        const sidebar = document.getElementById('app-sidebar');
        const toggleBtn = document.getElementById('btn-mobile-menu');
        if (sidebar && sidebar.classList.contains('mobile-open')) {
            if (!sidebar.contains(e.target) && !toggleBtn?.contains(e.target)) {
                closeMobileSidebar();
            }
        }
    });

    // Keyboard shortcuts: Escape closes modal/menu, / focuses global search
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') {
            document.querySelectorAll('.modal-overlay').forEach(m => m.classList.add('hidden'));
            closeMobileSidebar();
            closeNotificationsMenu();
            closeWorkspaceMenu();
        }
        if (e.key === '/' && !['INPUT', 'TEXTAREA', 'SELECT'].includes(document.activeElement?.tagName)) {
            e.preventDefault();
            const searchInput = document.getElementById('global-search');
            if (searchInput) searchInput.focus();
        }
    });

    // Setup drag & drop file upload
    setupDropzoneListeners();
}
