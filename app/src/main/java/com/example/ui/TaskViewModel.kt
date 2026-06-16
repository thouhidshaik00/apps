package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class TaskViewModel(application: Application) : AndroidViewModel(application) {

    private val db = TaskDatabase.getDatabase(application)
    private val repository = TaskRepository(db.taskDao())

    val tasks: StateFlow<List<TaskEntity>> = repository.allTasks.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val users: StateFlow<List<UserEntity>> = repository.allUsers.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val history: StateFlow<List<StatusHistoryEntity>> = repository.allHistory.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allActivityLogs: StateFlow<List<TaskActivityEntity>> = repository.allActivityLogs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val syncState: StateFlow<SyncState> = repository.syncState

    fun getCommentsForTask(taskId: String): Flow<List<CommentEntity>> = repository.getCommentsForTaskFlow(taskId)
    fun getActivityLogForTask(taskId: String): Flow<List<TaskActivityEntity>> = repository.getActivityLogForTaskFlow(taskId)

    fun addComment(taskId: String, text: String) {
        viewModelScope.launch {
            if (text.isBlank()) return@launch
            val activeUser = users.value.find { it.id == currentUserId.value }
            val authorName = activeUser?.name ?: "Thouhid Shaik"
            repository.addComment(taskId, authorName, text)
            triggerNotificationSimulation("New Comment", "\"$authorName\" commented on task.")
        }
    }

    private val _currentUserId = MutableStateFlow("user_me")
    val currentUserId: StateFlow<String> = _currentUserId.asStateFlow()

    // Conflict resolution holding state
    private val _conflictTask = MutableStateFlow<ConflictPair?>(null)
    val conflictTask: StateFlow<ConflictPair?> = _conflictTask.asStateFlow()

    // Notification Trigger Simulator State
    private val _notificationsLog = MutableStateFlow<List<String>>(emptyList())
    val notificationsLog: StateFlow<List<String>> = _notificationsLog.asStateFlow()

    data class ConflictPair(val local: TaskEntity, val remote: TaskEntity)

    init {
        // Seed initial values to make first-launch look rich and highly interactive
        viewModelScope.launch {
            repository.allUsers.first().let { currentList ->
                if (currentList.isEmpty()) {
                    repository.seedDatabase()
                }
            }
        }
    }

    fun selectUser(userId: String) {
        _currentUserId.value = userId
    }

    fun addTask(title: String, description: String, priority: String, assigneeId: String?, dueDate: String?) {
        viewModelScope.launch {
            val creator = users.value.find { it.id == currentUserId.value }?.name ?: "Unknown"
            repository.addTask(
                title = title,
                description = description,
                status = "To Do",
                assigneeId = assigneeId,
                dueDate = dueDate,
                priority = priority,
                creatorName = creator
            )
            triggerNotificationSimulation("Task Created", "New task dynamic: \"$title\" is registered, queued to sync.")
        }
    }

    fun updateTaskStatus(taskId: String, newStatus: String) {
        viewModelScope.launch {
            val activeUser = users.value.find { it.id == currentUserId.value }
            val userId = activeUser?.id ?: "unknown"
            val userName = activeUser?.name ?: "A Teammate"
            repository.updateTaskStatus(taskId, newStatus, userId, userName)
            triggerNotificationSimulation("Task Shifted", "Task moved to progress state: $newStatus")
        }
    }

    fun toggleTaskCompletion(taskId: String) {
        viewModelScope.launch {
            val task = tasks.value.find { it.id == taskId } ?: return@launch
            val nextStatus = if (task.status == "Done") "To Do" else "Done"
            val activeUser = users.value.find { it.id == currentUserId.value }
            repository.updateTaskStatus(taskId, nextStatus, activeUser?.id ?: "unknown", activeUser?.name ?: "Thouhid Shaik")
            triggerNotificationSimulation(
                "Task Complete", 
                if (nextStatus == "Done") "Congratulations! Task completed: ${task.title}" else "Task marked incomplete"
            )
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            repository.deleteTask(task)
            triggerNotificationSimulation("Task Deleted", "\"${task.title}\" has been removed.")
        }
    }

    fun updateTaskPriority(taskId: String, newPriority: String) {
        viewModelScope.launch {
            val task = tasks.value.find { it.id == taskId } ?: return@launch
            if (task.priority == newPriority) return@launch
            val updatedTask = task.copy(
                priority = newPriority,
                isLocalModified = true,
                isSynced = false,
                updatedAt = System.currentTimeMillis()
            )
            repository.insertTask(updatedTask)

            val activeUser = users.value.find { it.id == currentUserId.value }
            val userName = activeUser?.name ?: "Thouhid Shaik"
            repository.addTaskActivity(
                taskId = taskId,
                activityType = "Priority Changed",
                description = "Priority changed from '${task.priority}' to '$newPriority'",
                performedBy = userName
            )

            triggerNotificationSimulation("Priority Changed", "\"${task.title}\" priority set to $newPriority")
        }
    }

    fun triggerNotificationSimulation(title: String, body: String) {
        val currentLog = _notificationsLog.value.toMutableList()
        currentLog.add(0, "🔔 [$title] $body - ${System.currentTimeMillis().let { java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(it) }}")
        _notificationsLog.value = currentLog
    }

    // Pull database updates with conflict resolution demonstration
    fun forceSyncFromSupabaseCloud() {
        viewModelScope.launch {
            repository.simulateSyncFromSupabase(tasks.value) { local, remote ->
                // Conflict Callback! Let's pause and ask the user inside the dialog
                _conflictTask.value = ConflictPair(local, remote)
                null // Return null temporarily so we hold until choice is confirmed
            }
        }
    }

    fun resolveConflict(resolvedTask: TaskEntity) {
        viewModelScope.launch {
            repository.insertTask(resolvedTask.copy(isSynced = true, isLocalModified = false, version = resolvedTask.version + 1))
            _conflictTask.value = null
        }
    }

    fun dismissConflict() {
        _conflictTask.value = null
    }

    fun forceResetData() {
        viewModelScope.launch {
            repository.seedDatabase()
            triggerNotificationSimulation("System Reset", "Successfully re-seeded schema with model tasks from Supabase.")
        }
    }
}
