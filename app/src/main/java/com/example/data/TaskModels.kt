package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String,
    val email: String,
    val avatarUrl: String? = null
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val status: String, // "To Do", "In Progress", "Done"
    val assigneeId: String?,
    val dueDate: String?,
    val priority: String, // "Low", "Medium", "High"
    val version: Int = 1,
    val isSynced: Boolean = true,
    val isLocalModified: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "status_history")
data class StatusHistoryEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val oldStatus: String,
    val newStatus: String,
    val changedBy: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "task_comments")
data class CommentEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val authorName: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "task_activity_log")
data class TaskActivityEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val activityType: String, // "Create", "Status Change", "Comment Added", "Priority Changed"
    val description: String,
    val performedBy: String,
    val timestamp: Long = System.currentTimeMillis()
)
