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
package org.fuusio.kide.app.feature.about.presentation

/**
 * Represents a semantic versioning object consisting of major, minor, and patch numbers,
 * along with a release status.
 *
 * @property major The major version number, incremented for incompatible API changes.
 * @property minor The minor version number, incremented for adding functionality in a backwards-compatible manner.
 * @property patch The patch version number, incremented for backwards-compatible bug fixes.
 * @property releaseStatus The [ReleaseStatus] indicating the stability level of the version.
 */
data class Version(
    val major: Int = 0,
    val minor: Int = 0,
    val patch: Int = 1,
    val releaseStatus: ReleaseStatus = ReleaseStatus.ALPHA
) {

    override fun toString(): String {
        return "$major.$minor.$patch"
    }

    @Suppress("unused")
    enum class ReleaseStatus {
        PROTOTYPE,
        ALPHA,
        BETA,
        RELEASE,
    }

    companion object {

        /**
         * Creates a [Version] instance from a string representation in the format "major.minor.patch".
         *
         * @param versionString The string to parse into a [Version] object.
         */
        fun fromString(versionString: String): Version {
            val parts = versionString.trim().split(".")
            val major = parts[0].toInt()
            val minor = parts[1].toInt()
            val patch = parts[2].toInt()
            return Version(major, minor, patch)
        }
    }
}