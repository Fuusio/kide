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

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Transaction
    @Query("SELECT * FROM projects ORDER BY savedAt DESC")
    fun getSavedProjectsFlow(): Flow<List<ProjectWithLabels>>

    @Transaction
    @Query("SELECT * FROM projects WHERE id = :projectId")
    suspend fun getProjectWithLabels(projectId: Long): ProjectWithLabels?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :projectId")
    suspend fun deleteProject(projectId: Long)

    @Query("UPDATE projects SET notes = :notes WHERE id = :projectId")
    suspend fun updateProjectNotes(projectId: Long, notes: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLabel(label: LabelEntity)

    @Query("DELETE FROM labels WHERE name = :labelName")
    suspend fun deleteLabel(labelName: String)

    @Query("SELECT * FROM labels ORDER BY name ASC")
    fun getLabelsFlow(): Flow<List<LabelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProjectLabelCrossRef(crossRef: ProjectLabelCrossRef)

    @Query("DELETE FROM project_label_cross_ref WHERE projectId = :projectId AND labelName = :labelName")
    suspend fun deleteProjectLabelCrossRef(projectId: Long, labelName: String)

    @Query("DELETE FROM project_label_cross_ref WHERE projectId = :projectId")
    suspend fun deleteProjectLabelCrossRefsForProject(projectId: Long)
}
