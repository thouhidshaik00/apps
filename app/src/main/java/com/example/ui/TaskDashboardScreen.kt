package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.*
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDashboardScreen(
    viewModel: TaskViewModel,
    modifier: Modifier = Modifier
) {
    val tasks by viewModel.tasks.collectAsState()
    val users by viewModel.users.collectAsState()
    val history by viewModel.history.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()
    val conflictPair by viewModel.conflictTask.collectAsState()
    val notificationsLog by viewModel.notificationsLog.collectAsState()

    var activeTab by remember { mutableStateOf(0) } // 0: Web Board, 1: Mobile My Tasks, 2: Realtime Logs
    var showAddTaskDialog by remember { mutableStateOf(false) }
    var selectedTaskDetail by remember { mutableStateOf<TaskEntity?>(null) }

    val activeUser = users.find { it.id == currentUserId }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF3ECF8E)) // Supabase signature green
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "TaskSync Board",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    // Manual trigger simulating real-time replication pull from cloud database
                    IconButton(
                        onClick = { viewModel.forceSyncFromSupabaseCloud() },
                        modifier = Modifier.testTag("sync_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sync from remote Supabase DB",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { viewModel.forceResetData() }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Reset database state",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddTaskDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("add_task_fab")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add New Task")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Live Status Indicator Banner
            SyncIndicatorBanner(syncState = syncState)

            // Interactive Assignee Selector
            AssigneeSelectorRow(
                users = users,
                selectedUserId = currentUserId,
                onUserSelected = { viewModel.selectUser(it) }
            )

            // Modern Navigation Tabs
            ScrollableTabRow(
                selectedTabIndex = activeTab,
                containerColor = Color.Transparent,
                edgePadding = 16.dp,
                divider = {}
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = { Text("Web Kanban", fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text("My Tasks (${tasks.count { it.assigneeId == currentUserId && it.status != "Done" }})", fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    text = { Text("History Feed", fontWeight = FontWeight.SemiBold) }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Active Tab Content Viewport
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                when (activeTab) {
                    0 -> KanbanBoardView(
                        tasks = tasks,
                        users = users,
                        onStatusChanged = { taskId, nextStatus ->
                            viewModel.updateTaskStatus(taskId, nextStatus)
                        },
                        onDeleteTask = { viewModel.deleteTask(it) },
                        onTaskClick = { selectedTaskDetail = it }
                    )
                    1 -> MyTasksListView(
                        tasks = tasks.filter { it.assigneeId == currentUserId },
                        onToggleComplete = { viewModel.toggleTaskCompletion(it) },
                        onDeleteTask = { viewModel.deleteTask(it) },
                        onTaskClick = { selectedTaskDetail = it }
                    )
                    2 -> AuditHistoryFeedView(history = history)
                }
            }

            // Reminders Notification simulator log showing toast history
            NotificationToastLogger(logs = notificationsLog)
        }
    }

    // Modal forms
    if (showAddTaskDialog) {
        AddTaskDialog(
            users = users,
            onDismiss = { showAddTaskDialog = false },
            onConfirm = { title, desc, prio, assignee, due ->
                viewModel.addTask(title, desc, prio, assignee, due)
                showAddTaskDialog = false
            }
        )
    }

    conflictPair?.let { pair ->
        SyncConflictDialog(
            conflictPair = pair,
            onDismiss = { viewModel.dismissConflict() },
            onResolve = { viewModel.resolveConflict(it) }
        )
    }

    selectedTaskDetail?.let { task ->
        val freshTask = tasks.find { it.id == task.id } ?: task
        TaskDetailDialog(
            task = freshTask,
            users = users,
            viewModel = viewModel,
            onDismiss = { selectedTaskDetail = null }
        )
    }
}

@Composable
fun SyncIndicatorBanner(syncState: SyncState) {
    val bgColor: Color
    val labelText: String
    val iconColor: Color

    when (syncState) {
        is SyncState.Syncing -> {
            bgColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            labelText = "Pulsing changes to Supabase cloud database..."
            iconColor = MaterialTheme.colorScheme.primary
        }
        is SyncState.Error -> {
            bgColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
            labelText = "Offline local queue: Connection failure cached"
            iconColor = MaterialTheme.colorScheme.error
        }
        is SyncState.Synced -> {
            bgColor = Color(0xFF102A1E)
            labelText = syncState.message
            iconColor = Color(0xFF3ECF8E)
        }
        else -> {
            bgColor = MaterialTheme.colorScheme.surfaceVariant
            labelText = "Standby"
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(10.dp),
        color = bgColor,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Sync State Indicator",
                tint = iconColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = labelText,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = if (syncState is SyncState.Synced) Color(0xFFD4F7DF) else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun AssigneeSelectorRow(
    users: List<UserEntity>,
    selectedUserId: String,
    onUserSelected: (String) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            text = "Switch active identity (Simulate assignee workspace):",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(users) { user ->
                val isSelected = user.id == selectedUserId
                val borderCol = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                val bgCol = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface

                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onUserSelected(user.id) }
                        .border(1.dp, borderCol, RoundedCornerShape(20.dp)),
                    color = bgCol,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                user.name.take(2).uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (user.id == "user_me") "${user.name} (Me)" else user.name,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun KanbanBoardView(
    tasks: List<TaskEntity>,
    users: List<UserEntity>,
    onStatusChanged: (String, String) -> Unit,
    onDeleteTask: (TaskEntity) -> Unit,
    onTaskClick: (TaskEntity) -> Unit
) {
    val statuses = listOf("To Do", "In Progress", "Done")

    LazyRow(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(statuses) { colStatus ->
            val colTasks = tasks.filter { it.status == colStatus }
            KanbanColumnCard(
                statusName = colStatus,
                tasks = colTasks,
                users = users,
                onStatusChanged = onStatusChanged,
                onDeleteTask = onDeleteTask,
                onTaskClick = onTaskClick
            )
        }
    }
}

@Composable
fun KanbanColumnCard(
    statusName: String,
    tasks: List<TaskEntity>,
    users: List<UserEntity>,
    onStatusChanged: (String, String) -> Unit,
    onDeleteTask: (TaskEntity) -> Unit,
    onTaskClick: (TaskEntity) -> Unit
) {
    val colHeaderColor = when (statusName) {
        "To Do" -> Color(0xFF7A869A)
        "In Progress" -> Color(0xFF0052CC)
        else -> Color(0xFF36B37E)
    }

    Surface(
        modifier = Modifier
            .width(280.dp)
            .fillMaxHeight(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            // Column Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(colHeaderColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = statusName,
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Badge(
                    containerColor = colHeaderColor.copy(alpha = 0.2f),
                    contentColor = colHeaderColor
                ) {
                    Text(text = tasks.size.toString(), modifier = Modifier.padding(2.dp))
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (tasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No Tasks in $statusName",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(tasks) { task ->
                        KanbanTaskItem(
                            task = task,
                            users = users,
                            onStatusChanged = onStatusChanged,
                            onDelete = { onDeleteTask(task) },
                            onTaskClick = { onTaskClick(task) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun KanbanTaskItem(
    task: TaskEntity,
    users: List<UserEntity>,
    onStatusChanged: (String, String) -> Unit,
    onDelete: () -> Unit,
    onTaskClick: () -> Unit
) {
    val attendee = users.find { it.id == task.assigneeId }

    val prioColor = when (task.priority) {
        "High" -> PriorityHigh
        "Medium" -> PriorityMedium
        else -> PriorityLow
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTaskClick() }
            .testTag("task_card_${task.id}"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Task header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(prioColor.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = task.priority,
                        color = prioColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!task.isSynced) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Unsynced local state",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier
                                .size(14.dp)
                                .padding(end = 4.dp)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete task",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onDelete() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Title
            Text(
                text = task.title,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Desc
            if (task.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = task.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Footer row (dueDate and assignee initials)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Due Date",
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = task.dueDate ?: "No due date",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                // Initial circle
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = attendee?.name?.take(2)?.uppercase() ?: "US",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Transition Arrows
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val statuses = listOf("To Do", "In Progress", "Done")
                val currentIndex = statuses.indexOf(task.status)

                IconButton(
                    onClick = {
                        if (currentIndex > 0) {
                            onStatusChanged(task.id, statuses[currentIndex - 1])
                        }
                    },
                    enabled = currentIndex > 0,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Move column left",
                        modifier = Modifier.size(16.dp)
                    )
                }

                Text(
                    text = "Shift column",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )

                IconButton(
                    onClick = {
                        if (currentIndex < 2) {
                            onStatusChanged(task.id, statuses[currentIndex + 1])
                        }
                    },
                    enabled = currentIndex < 2,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Move column right",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MyTasksListView(
    tasks: List<TaskEntity>,
    onToggleComplete: (String) -> Unit,
    onDeleteTask: (TaskEntity) -> Unit,
    onTaskClick: (TaskEntity) -> Unit
) {
    if (tasks.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "No tasks assigned",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No personal tasks assigned yet",
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Try adding a task or switching identities above",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tasks) { task ->
                val isCompleted = task.status == "Done"
                val textDecoration = if (isCompleted) FontWeight.SemiBold else FontWeight.Normal

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTaskClick(task) }
                        .testTag("checklist_task_${task.id}"),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCompleted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isCompleted,
                            onCheckedChange = { onToggleComplete(task.id) },
                            modifier = Modifier.testTag("checkbox_${task.id}")
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = task.title,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                // Priority badge
                                val priColor = when (task.priority) {
                                    "High" -> PriorityHigh
                                    "Medium" -> PriorityMedium
                                    else -> PriorityLow
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(priColor.copy(alpha = 0.12f))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(task.priority, color = priColor, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (task.description.isNotEmpty()) {
                                Text(
                                    text = task.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Bucket: ${task.status}", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                if (task.dueDate != null) {
                                    Text("• Due: ${task.dueDate}", fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                        }

                        IconButton(onClick = { onDeleteTask(task) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete personal item",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AuditHistoryFeedView(history: List<StatusHistoryEntity>) {
    if (history.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("No recent sync transactions listed.")
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(history) { log ->
                val timeLabel = java.text.SimpleDateFormat(
                    "HH:mm:ss", java.util.Locale.getDefault()
                ).format(log.timestamp)

                 Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Sync audit",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "${log.changedBy} state shift:",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Moved task column from \"${log.oldStatus}\" ➔ \"${log.newStatus}\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Sync timestamp: $timeLabel (Replicated via WebSocket)",
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationToastLogger(logs: List<String>) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 100.dp)
            .padding(16.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                "Simulated Push & Local Notification Alerts:",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(2.dp))
            if (logs.isEmpty()) {
                Text("Standby. Perform operations to trigger native system callback logs.", fontSize = 9.sp)
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(logs) { log ->
                        Text(
                            text = log,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddTaskDialog(
    users: List<UserEntity>,
    onDismiss: () -> Unit,
    onConfirm: (title: String, desc: String, priority: String, assigneeId: String?, dueDate: String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("Medium") }
    var assigneeId by remember { mutableStateOf<String?>(users.firstOrNull()?.id) }
    var dueDate by remember { mutableStateOf("2026-06-18") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.width(340.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Quick Task Setup",
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleMedium
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task Title *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_task_title_input")
                )

                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Details & Objectives") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                // Select priority
                Column {
                    Text("Task Severity Grade", style = MaterialTheme.typography.bodySmall)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("Low", "Medium", "High").forEach { level ->
                            val currentChoice = priority == level
                            val activeBadgeColor = when (level) {
                                "High" -> PriorityHigh
                                "Medium" -> PriorityMedium
                                else -> PriorityLow
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { priority = level }
                                    .background(
                                        if (currentChoice) activeBadgeColor.copy(alpha = 0.25f)
                                        else MaterialTheme.colorScheme.background
                                    )
                                    .border(
                                        width = if (currentChoice) 2.dp else 1.dp,
                                        color = if (currentChoice) activeBadgeColor else MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(level, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = if (currentChoice) activeBadgeColor else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }

                // Select Assignee
                Column {
                    Text("Select Resource Assignee", style = MaterialTheme.typography.bodySmall)
                    ScrollableTabRow(
                        selectedTabIndex = users.indexOfFirst { it.id == assigneeId }.coerceAtLeast(0),
                        containerColor = Color.Transparent,
                        divider = {},
                        edgePadding = 0.dp
                    ) {
                        users.forEach { user ->
                            Tab(
                                selected = assigneeId == user.id,
                                onClick = { assigneeId = user.id },
                                text = { Text(user.name.take(10), fontSize = 11.sp) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = dueDate,
                    onValueChange = { dueDate = it },
                    label = { Text("Due Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Dismiss")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (title.isNotBlank()) {
                                onConfirm(title, desc, priority, assigneeId, dueDate)
                            }
                        },
                        enabled = title.isNotBlank(),
                        modifier = Modifier.testTag("confirm_add_task_button")
                    ) {
                        Text("Add to DB")
                    }
                }
            }
        }
    }
}

@Composable
fun SyncConflictDialog(
    conflictPair: TaskViewModel.ConflictPair,
    onDismiss: () -> Unit,
    onResolve: (TaskEntity) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.width(360.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Concurrent Clash warning",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Supabase Collision Detected!",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Text(
                    text = "A teammate modified \"${conflictPair.local.title}\" on the cloud Supabase database concurrently. Select reconciliation strategy below:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Comparison Cards
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Local state version card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onResolve(conflictPair.local) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Keep Your Local Version (Override)", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            Text("Status: ${conflictPair.local.status}", fontSize = 10.sp)
                            Text("Modified locally, version code v${conflictPair.local.version}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Remote version card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onResolve(conflictPair.remote) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Sync Remote Cloud Version (Accept)", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                            Text("Status: ${conflictPair.remote.status}", fontSize = 10.sp)
                            Text("Modified remotely, version code v${conflictPair.remote.version}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Hold update")
                    }
                }
            }
        }
    }
}

@Composable
fun TaskDetailDialog(
    task: TaskEntity,
    users: List<UserEntity>,
    viewModel: TaskViewModel,
    onDismiss: () -> Unit
) {
    val comments by viewModel.getCommentsForTask(task.id).collectAsState(initial = emptyList())
    val activities by viewModel.getActivityLogForTask(task.id).collectAsState(initial = emptyList())
    val assignee = users.find { it.id == task.assigneeId }
    var commentText by remember { mutableStateOf("") }
    var priorityDropdownExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .width(428.dp)
                .fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxSize()
            ) {
                // Header with custom title & close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Task Workspace",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close screen")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Scrollable main body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Task descriptive card
                    Column {
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = task.description.ifEmpty { "No description added yet." },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Priority display & management controls
                    Column {
                        Text(
                            text = "Task Attributes",
                            style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Attributes Grid/Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Priority Selector button
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Priority Level", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                Box {
                                    val currentPrioColor = when (task.priority) {
                                        "High" -> PriorityHigh
                                        "Medium" -> PriorityMedium
                                        else -> PriorityLow
                                    }
                                    OutlinedButton(
                                        onClick = { priorityDropdownExpanded = true },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(containerColor = currentPrioColor.copy(alpha = 0.08f)),
                                        border = BorderStroke(1.dp, currentPrioColor.copy(alpha = 0.5f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                task.priority,
                                                color = currentPrioColor,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Icon(
                                                imageVector = Icons.Default.ArrowDropDown,
                                                contentDescription = "Expand prioritization drop menu",
                                                tint = currentPrioColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = priorityDropdownExpanded,
                                        onDismissRequest = { priorityDropdownExpanded = false }
                                    ) {
                                        listOf("Low", "Medium", "High").forEach { level ->
                                            DropdownMenuItem(
                                                text = { Text(level, fontWeight = FontWeight.Bold) },
                                                onClick = {
                                                    viewModel.updateTaskPriority(task.id, level)
                                                    priorityDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // Assignee Info box
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Assignee", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = assignee?.name?.take(2)?.uppercase() ?: "US",
                                                textDecoration = androidx.compose.ui.text.style.TextDecoration.None,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Text(
                                            text = assignee?.name ?: "Unassigned",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Comments section
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Team Comments (${comments.size})",
                            style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.primary)
                        )

                        // Comment text field input row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = commentText,
                                onValueChange = { commentText = it },
                                placeholder = { Text("Add project thoughts...", fontSize = 11.sp) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("comment_input_box"),
                                textStyle = MaterialTheme.typography.bodySmall,
                                shape = RoundedCornerShape(12.dp),
                                maxLines = 2,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            )
                            IconButton(
                                onClick = {
                                    if (commentText.isNotBlank()) {
                                        viewModel.addComment(task.id, commentText)
                                        commentText = ""
                                    }
                                },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .size(38.dp)
                                    .testTag("send_comment_button"),
                                enabled = commentText.isNotBlank()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "Submit comment",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // List of comments
                        if (comments.isEmpty()) {
                            Text(
                                "No comments posted yet. Start the conversation!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                comments.forEach { comment ->
                                    val timeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(comment.timestamp)
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(16.dp)
                                                            .clip(CircleShape)
                                                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = comment.authorName.take(2).uppercase(),
                                                            fontSize = 7.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.secondary
                                                        )
                                                    }
                                                    Text(
                                                        comment.authorName,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                                Text(
                                                    timeStr,
                                                    fontSize = 9.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                comment.text,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Real-time task-specific activity log timeline
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = "Activity log section icon",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Supabase Realtime Activity Log",
                                style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.primary)
                            )
                        }

                        if (activities.isEmpty()) {
                            Text(
                                "No audit history logged.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                activities.forEach { act ->
                                    val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(act.timestamp)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .padding(top = 2.dp)
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.outline)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${act.performedBy} executed '${act.activityType}'",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = act.description,
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            timeStr,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

