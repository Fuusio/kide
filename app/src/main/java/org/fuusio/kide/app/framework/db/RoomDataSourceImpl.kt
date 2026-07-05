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
package org.fuusio.kide.app.framework.db

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.fuusio.kide.app.adapter.project.ProjectLocalDataSource
import org.fuusio.kide.app.domain.entity.Label
import org.fuusio.kide.app.domain.entity.Project
import org.fuusio.kide.app.framework.db.ProjectLabelCrossRef
import org.fuusio.kide.framework.AbstractDataSource
import timber.log.Timber

class RoomDataSourceImpl(private val projectDao: ProjectDao) :
    AbstractDataSource(), ProjectLocalDataSource {

    override fun getSavedProjects(): Flow<List<Project>> =
        projectDao.getSavedProjectsFlow().map { list ->
            list.map { it.toDomain() }
        }

    override suspend fun getProjectDetails(projectId: Long): Project? = withContext(dispatcher) {
        projectDao.getProjectWithLabels(projectId)?.toDomain()
    }

    override suspend fun saveProject(project: Project) = withContext(dispatcher) {
        Timber.d("Room DB: saving project %s (ID: %d)", project.name, project.id)
        val savedAt = project.savedAt ?: System.currentTimeMillis()
        projectDao.insertProject(project.toEntity(savedAt))
        project.labels.forEach { label ->
            projectDao.insertLabel(label.toEntity())
            projectDao.insertProjectLabelCrossRef(ProjectLabelCrossRef(project.id, label.name))
        }
    }

    override suspend fun deleteProject(projectId: Long) = withContext(dispatcher) {
        Timber.d("Room DB: deleting project with ID: %d", projectId)
        projectDao.deleteProject(projectId)
        projectDao.deleteProjectLabelCrossRefsForProject(projectId)
    }

    override suspend fun updateProjectNotes(projectId: Long, notes: String) = withContext(dispatcher) {
        Timber.d("Room DB: updating notes for project ID %d", projectId)
        projectDao.updateProjectNotes(projectId, notes)
    }

    override suspend fun addLabelToProject(projectId: Long, labelName: String) = withContext(dispatcher) {
        Timber.d("Room DB: adding label %s to project ID %d", labelName, projectId)
        projectDao.insertProjectLabelCrossRef(ProjectLabelCrossRef(projectId, labelName))
    }

    override suspend fun removeLabelFromProject(projectId: Long, labelName: String) = withContext(dispatcher) {
        Timber.d("Room DB: removing label %s from project ID %d", labelName, projectId)
        projectDao.deleteProjectLabelCrossRef(projectId, labelName)
    }

    override fun getLabels(): Flow<List<Label>> =
        projectDao.getLabelsFlow().map { list ->
            list.map { it.toDomain() }
        }

    override suspend fun saveLabel(label: Label) = withContext(dispatcher) {
        projectDao.insertLabel(label.toEntity())
    }

    override suspend fun deleteLabel(labelName: String) = withContext(dispatcher) {
        projectDao.deleteLabel(labelName)
    }
}

fun LabelEntity.toDomain() = Label(name = name, colorHex = colorHex)
fun Label.toEntity() = LabelEntity(name = name, colorHex = colorHex)

fun ProjectWithLabels.toDomain() = Project(
    id = project.id,
    name = project.name,
    fullName = project.fullName,
    description = project.description,
    htmlUrl = project.htmlUrl,
    language = project.language,
    starsCount = project.starsCount,
    forksCount = project.forksCount,
    licenseSpdxId = project.licenseSpdxId,
    notes = project.notes,
    labels = labels.map { it.toDomain() },
    isSaved = true,
    savedAt = project.savedAt
)

fun Project.toEntity(savedAt: Long) = ProjectEntity(
    id = id,
    name = name,
    fullName = fullName,
    description = description,
    htmlUrl = htmlUrl,
    language = language,
    starsCount = starsCount,
    forksCount = forksCount,
    licenseSpdxId = licenseSpdxId,
    notes = notes,
    savedAt = savedAt
)
