package com.example.data.repository

import com.example.data.local.dao.NlveDao
import com.example.data.local.entity.CommandLogEntity
import com.example.data.local.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow

class VideoEditorRepository(private val dao: NlveDao) {
    val allProjects: Flow<List<ProjectEntity>> = dao.getAllProjects()

    suspend fun saveProject(project: ProjectEntity) {
        dao.insertProject(project)
    }

    suspend fun getProject(id: String): ProjectEntity? {
        return dao.getProjectById(id)
    }

    suspend fun deleteProject(id: String) {
        dao.deleteProject(id)
        dao.clearLogsForProject(id)
    }

    fun getLogsForProject(projectId: String): Flow<List<CommandLogEntity>> {
        return dao.getCommandLogsForProject(projectId)
    }

    suspend fun logCommand(
        projectId: String,
        rawPrompt: String,
        parsedSummary: String,
        ffmpegCommand: String,
        isApplied: Boolean = true
    ): Long {
        val entity = CommandLogEntity(
            projectId = projectId,
            rawPrompt = rawPrompt,
            parsedSummary = parsedSummary,
            ffmpegCommand = ffmpegCommand,
            timestamp = System.currentTimeMillis(),
            isApplied = isApplied
        )
        return dao.insertCommandLog(entity)
    }

    suspend fun updateCommandStatus(log: CommandLogEntity) {
        dao.updateCommandLog(log)
    }
}
