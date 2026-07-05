/* Kide
 *
 * Copyright 2025 - 2026 Marko Salmela.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fuusio.kide.app.adapter.project

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import org.fuusio.kide.adapter.AbstractRepository
import org.fuusio.kide.app.domain.adapter.project.ProjectRepository
import org.fuusio.kide.app.domain.entity.Label
import org.fuusio.kide.app.domain.entity.Project

class ProjectRepositoryImpl(
    private val localDataSource: ProjectLocalDataSource,
    private val remoteDataSource: ProjectRemoteDataSource
) : AbstractRepository(), ProjectRepository {

    override suspend fun searchProjects(
        query: String,
        language: String?,
        license: String?
    ): List<Project> = withContext(dispatcher) {
        val remoteProjects = remoteDataSource.searchProjects(query, language, license)
        val savedProjectIds = localDataSource.getSavedProjects().firstOrNull()?.map { it.id }?.toSet() ?: emptySet()
        remoteProjects.map { project ->
            if (savedProjectIds.contains(project.id)) {
                val savedDetails = localDataSource.getProjectDetails(project.id)
                project.copy(
                    isSaved = true,
                    notes = savedDetails?.notes,
                    labels = savedDetails?.labels ?: emptyList(),
                    savedAt = savedDetails?.savedAt
                )
            } else {
                project
            }
        }
    }

    override fun getSavedProjects(): Flow<List<Project>> = localDataSource.getSavedProjects()

    override suspend fun getProjectDetails(projectId: Long): Project? = withContext(dispatcher) {
        localDataSource.getProjectDetails(projectId)
    }

    override suspend fun saveProject(project: Project) = withContext(dispatcher) {
        localDataSource.saveProject(project)
    }

    override suspend fun deleteProject(projectId: Long) = withContext(dispatcher) {
        localDataSource.deleteProject(projectId)
    }

    override suspend fun updateProjectNotes(projectId: Long, notes: String) = withContext(dispatcher) {
        localDataSource.updateProjectNotes(projectId, notes)
    }

    override suspend fun addLabelToProject(projectId: Long, labelName: String) = withContext(dispatcher) {
        localDataSource.addLabelToProject(projectId, labelName)
    }

    override suspend fun removeLabelFromProject(projectId: Long, labelName: String) = withContext(dispatcher) {
        localDataSource.removeLabelFromProject(projectId, labelName)
    }

    override fun getLabels(): Flow<List<Label>> = localDataSource.getLabels()

    override suspend fun saveLabel(label: Label) = withContext(dispatcher) {
        localDataSource.saveLabel(label)
    }

    override suspend fun deleteLabel(labelName: String) = withContext(dispatcher) {
        localDataSource.deleteLabel(labelName)
    }
}
