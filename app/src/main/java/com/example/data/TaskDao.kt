package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY updatedAt DESC")
    fun getAllTasksFlow(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: String): TaskEntity?

    @Query("SELECT * FROM users")
    fun getAllUsersFlow(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getUserById(id: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaskAndHistory(task: TaskEntity, history: StatusHistoryEntity) {
        insertTask(task)
        insertHistory(history)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: StatusHistoryEntity)

    @Query("SELECT * FROM status_history ORDER BY timestamp DESC")
    fun getHistoryFlow(): Flow<List<StatusHistoryEntity>>

    @Query("SELECT * FROM status_history WHERE taskId = :taskId ORDER BY timestamp DESC")
    fun getHistoryForTaskFlow(taskId: String): Flow<List<StatusHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComment(comment: CommentEntity)

    @Query("SELECT * FROM task_comments WHERE taskId = :taskId ORDER BY timestamp DESC")
    fun getCommentsForTaskFlow(taskId: String): Flow<List<CommentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivity(activity: TaskActivityEntity)

    @Query("SELECT * FROM task_activity_log WHERE taskId = :taskId ORDER BY timestamp DESC")
    fun getActivityLogForTaskFlow(taskId: String): Flow<List<TaskActivityEntity>>

    @Query("SELECT * FROM task_activity_log ORDER BY timestamp DESC")
    fun getAllActivityLogsFlow(): Flow<List<TaskActivityEntity>>

    @Query("DELETE FROM task_comments")
    suspend fun clearComments()

    @Query("DELETE FROM task_activity_log")
    suspend fun clearActivityLogs()

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query("DELETE FROM tasks")
    suspend fun clearAllTasks()

    @Query("DELETE FROM users")
    suspend fun clearAllUsers()

    @Transaction
    suspend fun resetDatabase(users: List<UserEntity>, tasks: List<TaskEntity>) {
        clearAllTasks()
        clearAllUsers()
        clearComments()
        clearActivityLogs()
        users.forEach { insertUser(it) }
        tasks.forEach { insertTask(it) }
    }
}
