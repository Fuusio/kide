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
package org.fuusio.kide.app.domain.adapter.project

import kotlinx.coroutines.flow.Flow
import org.fuusio.kide.adapter.Repository
import org.fuusio.kide.app.domain.entity.Label
import org.fuusio.kide.app.domain.entity.Project

interface ProjectRepository : Repository {
    suspend fun searchProjects(query: String, language: String?, license: String?): List<Project>
    fun getSavedProjects(): Flow<List<Project>>
    suspend fun getProjectDetails(projectId: Long): Project?
    suspend fun saveProject(project: Project)
    suspend fun deleteProject(projectId: Long)
    suspend fun updateProjectNotes(projectId: Long, notes: String)
    suspend fun addLabelToProject(projectId: Long, labelName: String)
    suspend fun removeLabelFromProject(projectId: Long, labelName: String)
    fun getLabels(): Flow<List<Label>>
    suspend fun saveLabel(label: Label)
    suspend fun deleteLabel(labelName: String)
}
