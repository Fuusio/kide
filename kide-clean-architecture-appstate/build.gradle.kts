plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.maven.publish) apply false
}

// Experimental, like kide-appstate, and published only together with it: see the comment in
// kide-appstate/build.gradle.kts and .github/workflows/publish-appstate.yml.
if (providers.gradleProperty("kide.publishExperimental").isPresent) {
    apply(plugin = "com.vanniktech.maven.publish")
}

kotlin {
    explicitApiWarning()

    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation()

    // Android and JVM only, following kide-appstate.
    jvm()

    android {
        namespace = "org.fuusio.kide.cleanarchitecture.appstate"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()

        // Host tests cover persistence: statestore-datastore is published for Android
        // alone, so a persisted key cannot be exercised from jvmTest.
        withHostTest {
            // The Compose runtime calls android.os.Trace, which the host android.jar only
            // stubs; without this the composition behind syncToDataStore throws.
            isReturnDefaultValues = true
        }
    }

    sourceSets {
        // Bridges two optional modules, which is why it is its own artifact: kide-appstate
        // must not drag the Clean Architecture layer or StateStore into an application that
        // only wants transformFlow, and kide-clean-architecture must not depend on AppState.
        commonMain.dependencies {
            api(project(":kide-clean-architecture"))
            api(project(":kide-appstate"))
            api(libs.androidx.appstate.statestore)
        }

        named("androidHostTest") {
            dependencies {
                implementation(libs.androidx.appstate.statestore.datastore)
                implementation(libs.kotlinx.serialization.core)
                implementation(libs.kotest.runner.junit5)
                implementation(libs.kotest.assertions.core)
                implementation(libs.kotlinx.coroutines.test)
                runtimeOnly(libs.junit.platform.launcher)
            }
        }

        jvmTest.dependencies {
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
    // The StateStore probes report their findings on standard output.
    testLogging { showStandardStreams = true }
}
