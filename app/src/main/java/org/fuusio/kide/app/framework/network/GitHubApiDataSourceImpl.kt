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
package org.fuusio.kide.app.framework.network

import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.fuusio.kide.app.adapter.project.ProjectRemoteDataSource
import org.fuusio.kide.app.domain.entity.Project
import org.fuusio.kide.framework.AbstractDataSource
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

import timber.log.Timber

@Serializable
data class GitHubSearchResponse(
    @SerialName("total_count") val totalCount: Int,
    @SerialName("items") val items: List<GitHubRepoItem>
)

@Serializable
data class GitHubRepoItem(
    val id: Long,
    val name: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("html_url") val htmlUrl: String,
    val description: String? = null,
    @SerialName("stargazers_count") val stargazersCount: Int,
    @SerialName("forks_count") val forksCount: Int,
    val language: String? = null,
    val license: GitHubLicenseItem? = null
)

@Serializable
data class GitHubLicenseItem(
    val key: String,
    val name: String,
    @SerialName("spdx_id") val spdxId: String? = null
)

class GitHubApiDataSourceImpl : AbstractDataSource(), ProjectRemoteDataSource {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    override suspend fun searchProjects(
        query: String,
        language: String?,
        license: String?
    ): List<Project> = withContext(dispatcher) {
        val qBuilder = StringBuilder(query)
        if (!language.isNullOrBlank()) {
            qBuilder.append(" language:\"").append(language).append("\"")
        }
        if (!license.isNullOrBlank()) {
            qBuilder.append(" license:").append(license)
        }

        val encodedQuery = URLEncoder.encode(qBuilder.toString(), "UTF-8")
        val urlString = "https://api.github.com/search/repositories?q=$encodedQuery"
        Timber.d("Searching projects on GitHub with URL: %s", urlString)

        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "GitKide-App")
        connection.connectTimeout = 10000
        connection.readTimeout = 10000

        val responseCode = connection.responseCode
        Timber.d("GitHub search response code: %d", responseCode)

        if (responseCode == HttpURLConnection.HTTP_OK) {
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val responseStr = reader.use { it.readText() }
            val parsed = json.decodeFromString<GitHubSearchResponse>(responseStr)
            parsed.items.map { item ->
                Project(
                    id = item.id,
                    name = item.name,
                    fullName = item.fullName,
                    description = item.description,
                    htmlUrl = item.htmlUrl,
                    language = item.language,
                    starsCount = item.stargazersCount,
                    forksCount = item.forksCount,
                    licenseSpdxId = item.license?.spdxId,
                    notes = null,
                    labels = emptyList(),
                    isSaved = false,
                    savedAt = null
                )
            }
        } else {
            val errorStream = connection.errorStream
            val errorStr = if (errorStream != null) {
                BufferedReader(InputStreamReader(errorStream)).use { it.readText() }
            } else {
                "Unknown connection error"
            }
            Timber.e("GitHub search failed with code %d. Error: %s", responseCode, errorStr)
            throw Exception("GitHub API Error ($responseCode): $errorStr")
        }
    }
}
