package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

sealed class SyncState {
    object Idle : SyncState()
    object Syncing : SyncState()
    data class Synced(val message: String) : SyncState()
    data class Error(val error: String) : SyncState()
}

class TaskRepository(private val taskDao: TaskDao) {

    val allTasks: Flow<List<TaskEntity>> = taskDao.getAllTasksFlow()
    val allUsers: Flow<List<UserEntity>> = taskDao.getAllUsersFlow()
    val allHistory: Flow<List<StatusHistoryEntity>> = taskDao.getHistoryFlow()
    val allActivityLogs: Flow<List<TaskActivityEntity>> = taskDao.getAllActivityLogsFlow()

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Synced("Connected (WebSocket Live)"))
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    fun getCommentsForTaskFlow(taskId: String): Flow<List<CommentEntity>> = taskDao.getCommentsForTaskFlow(taskId)
    fun getActivityLogForTaskFlow(taskId: String): Flow<List<TaskActivityEntity>> = taskDao.getActivityLogForTaskFlow(taskId)

    suspend fun getTaskById(id: String): TaskEntity? = taskDao.getTaskById(id)

    suspend fun insertTask(task: TaskEntity) {
        taskDao.insertTask(task)
    }

    suspend fun addTaskActivity(taskId: String, activityType: String, description: String, performedBy: String) {
        val activity = TaskActivityEntity(
            id = UUID.randomUUID().toString(),
            taskId = taskId,
            activityType = activityType,
            description = description,
            performedBy = performedBy,
            timestamp = System.currentTimeMillis()
        )
        taskDao.insertActivity(activity)
    }

    suspend fun addComment(taskId: String, authorName: String, text: String) {
        val comment = CommentEntity(
            id = UUID.randomUUID().toString(),
            taskId = taskId,
            authorName = authorName,
            text = text,
            timestamp = System.currentTimeMillis()
        )
        taskDao.insertComment(comment)

        // Record comment addition in the custom Activity log table too
        val activity = TaskActivityEntity(
            id = UUID.randomUUID().toString(),
            taskId = taskId,
            activityType = "Comment Added",
            description = "Commented: \"$text\"",
            performedBy = authorName,
            timestamp = System.currentTimeMillis()
        )
        taskDao.insertActivity(activity)
    }

    suspend fun updateTaskStatus(taskId: String, newStatus: String, userId: String, userName: String) {
        val task = taskDao.getTaskById(taskId) ?: return
        val oldStatus = task.status
        if (oldStatus == newStatus) return

        val updatedTask = task.copy(
            status = newStatus,
            isLocalModified = true,
            isSynced = false,
            updatedAt = System.currentTimeMillis()
        )

        val history = StatusHistoryEntity(
            id = UUID.randomUUID().toString(),
            taskId = taskId,
            oldStatus = oldStatus,
            newStatus = newStatus,
            changedBy = userName,
            timestamp = System.currentTimeMillis()
        )

        val activity = TaskActivityEntity(
            id = UUID.randomUUID().toString(),
            taskId = taskId,
            activityType = "Status Change",
            description = "Status transitioned from '$oldStatus' to '$newStatus'",
            performedBy = userName,
            timestamp = System.currentTimeMillis()
        )

        taskDao.insertTaskAndHistory(updatedTask, history)
        taskDao.insertActivity(activity)
        simulatePushToSupabase(updatedTask)
    }

    suspend fun addTask(
        title: String,
        description: String,
        status: String,
        assigneeId: String?,
        dueDate: String?,
        priority: String,
        creatorName: String
    ) {
        val task = TaskEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            description = description,
            status = status,
            assigneeId = assigneeId,
            dueDate = dueDate,
            priority = priority,
            isSynced = false,
            isLocalModified = true,
            version = 1,
            updatedAt = System.currentTimeMillis()
        )

        val history = StatusHistoryEntity(
            id = UUID.randomUUID().toString(),
            taskId = task.id,
            oldStatus = "Created",
            newStatus = status,
            changedBy = creatorName,
            timestamp = System.currentTimeMillis()
        )

        val activity = TaskActivityEntity(
            id = UUID.randomUUID().toString(),
            taskId = task.id,
            activityType = "Create",
            description = "Task created with priority: $priority",
            performedBy = creatorName,
            timestamp = System.currentTimeMillis()
        )

        taskDao.insertTaskAndHistory(task, history)
        taskDao.insertActivity(activity)
        simulatePushToSupabase(task)
    }

    suspend fun deleteTask(task: TaskEntity) {
        taskDao.deleteTask(task)
    }

    suspend fun insertUser(user: UserEntity) {
        taskDao.insertUser(user)
    }

    // Simmons Supabase cloud push
    private suspend fun simulatePushToSupabase(task: TaskEntity) {
        _syncState.value = SyncState.Syncing
        kotlinx.coroutines.delay(1000) // natural delay
        try {
            val syncedTask = task.copy(isSynced = true, isLocalModified = false)
            taskDao.insertTask(syncedTask)
            _syncState.value = SyncState.Synced("Synced successfully to Supabase")
        } catch (e: Exception) {
            _syncState.value = SyncState.Error("Offline mode: queued for sync")
        }
    }

    // Force pull from Supabase (refresh simulation with concurrency showcase)
    suspend fun simulateSyncFromSupabase(
        currentTasks: List<TaskEntity>,
        conflictSolver: (local: TaskEntity, remote: TaskEntity) -> TaskEntity?
    ): List<TaskEntity> {
        _syncState.value = SyncState.Syncing
        kotlinx.coroutines.delay(1200)

        // Mock remote tasks reflecting concurrent updates or newly added team tasks
        val mockRemoteTasks = listOf(
            // Existing ones with higher version or updated status
            TaskEntity(
                id = "task_init_1",
                title = "Design PostgreSQL schema",
                description = "Complete schema for: users, tasks, status changes",
                status = "Done",
                assigneeId = "user_jane_1",
                dueDate = "2026-06-20",
                priority = "High",
                version = 3, // Conflict showcase: remote is newer
                isSynced = true
            ),
            TaskEntity(
                id = "task_init_2",
                title = "Setup real-time subscription",
                description = "Configure WebSocket listening channels on Supabase client",
                status = "In Progress",
                assigneeId = "user_me",
                dueDate = "2026-06-25",
                priority = "High",
                version = 2,
                isSynced = true
            ),
            TaskEntity(
                id = "task_init_4",
                title = "Write Unit tests",
                description = "Add basic test suites for crucial states & flows",
                status = "To Do",
                assigneeId = "user_me",
                dueDate = "2026-06-28",
                priority = "Medium",
                version = 1,
                isSynced = true
            )
        )

        val conflicts = mutableListOf<TaskEntity>()
        for (remote in mockRemoteTasks) {
            val local = currentTasks.find { it.id == remote.id }
            if (local == null) {
                taskDao.insertTask(remote)
            } else {
                if (local.version != remote.version && local.isLocalModified) {
                    // Conflict detected!
                    val resolved = conflictSolver(local, remote)
                    if (resolved != null) {
                        taskDao.insertTask(resolved)
                    }
                } else if (remote.version > local.version) {
                    // Remote is newer - automatic fast forward
                    taskDao.insertTask(remote)
                }
            }
        }

        _syncState.value = SyncState.Synced("Supabase up-to-date")
        return conflicts
    }

    // Inject Initial Seed Data
    suspend fun seedDatabase() {
        val initialUsers = listOf(
            UserEntity("user_me", "Thouhid Shaik", "thouhidshaik614@gmail.com", "https://api.dicebear.com/7.x/pixel-art/svg?seed=Thouhid"),
            UserEntity("user_jane_1", "Jane Doe", "jane.doe@supabase.io", "https://api.dicebear.com/7.x/pixel-art/svg?seed=Jane"),
            UserEntity("user_alex_2", "Alex Rover", "alex.rover@supabase.io", "https://api.dicebear.com/7.x/pixel-art/svg?seed=Alex")
        )

        val initialTasks = listOf(
            TaskEntity(
                id = "task_init_1",
                title = "Design PostgreSQL schema",
                description = "Complete schema for: users, tasks, status changes",
                status = "Done",
                assigneeId = "user_jane_1",
                dueDate = "2026-06-20",
                priority = "High",
                version = 1,
                isSynced = true
            ),
            TaskEntity(
                id = "task_init_2",
                title = "Setup real-time subscription",
                description = "Configure WebSocket listening channels on Supabase client",
                status = "In Progress",
                assigneeId = "user_me",
                dueDate = "2026-06-25",
                priority = "High",
                version = 1,
                isSynced = true
            ),
            TaskEntity(
                id = "task_init_3",
                title = "Implement Kanban Board Interface",
                description = "React.js client with fluid column transitions & drag-drop",
                status = "In Progress",
                assigneeId = "user_alex_2",
                dueDate = "2026-06-24",
                priority = "Medium",
                version = 1,
                isSynced = true
            ),
            TaskEntity(
                id = "task_init_4",
                title = "Write Unit tests",
                description = "Add basic test suites for crucial states & flows",
                status = "To Do",
                assigneeId = "user_me",
                dueDate = "2026-06-28",
                priority = "Medium",
                version = 1,
                isSynced = true
            )
        )

        taskDao.resetDatabase(initialUsers, initialTasks)
    }
}
