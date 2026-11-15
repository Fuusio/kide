plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.maven.publish) apply false
}

// Experimental module with its own version (see gradle.properties) and its own release
// tag. Publishing is opt-in so that the root `publishAndReleaseToMavenCentral`, which a
// stable `v*` release runs, never publishes it: without this property the module has no
// publishing tasks at all. Its own releases, and local `publishToMavenLocal`, pass
// `-Pkide.publishExperimental`. See .github/workflows/publish-appstate.yml.
if (providers.gradleProperty("kide.publishExperimental").isPresent) {
    apply(plugin = "com.vanniktech.maven.publish")
}

kotlin {
    explicitApiWarning()

    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation()

    // Android and JVM only until AppState on iOS has been verified
    // (docs/proposal-appstate.md, phase 4).
    jvm()

    android {
        namespace = "org.fuusio.kide.appstate"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":kide"))
            api(libs.androidx.appstate.transform)
            api(compose.runtime)
        }

        jvmTest.dependencies {
            implementation(libs.androidx.appstate.transform.testing)
            implementation(libs.kotest.runner.junit5)
            implementation(libs.kotest.assertions.core)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
            runtimeOnly(libs.junit.platform.launcher)
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
