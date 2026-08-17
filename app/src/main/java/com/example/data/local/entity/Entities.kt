package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val videoSource: String,
    val totalDurationMs: Long,
    val resolution: String,
    val fps: Int,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "command_logs")
data class CommandLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: String,
    val rawPrompt: String,
    val parsedSummary: String,
    val ffmpegCommand: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isApplied: Boolean = true
)
