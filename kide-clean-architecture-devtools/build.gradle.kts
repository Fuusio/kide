plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.maven.publish)
}

kotlin {
    explicitApiWarning()

    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation()

    jvm()
    iosArm64()
    iosSimulatorArm64()

    android {
        namespace = "org.fuusio.kide.cleanarchitecture.devtools"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
        withHostTest {}
    }

    sourceSets {
        // Bridges two optional modules, which is the whole reason this is its own artifact:
        // kide-devtools must not drag the Clean Architecture layer into every debug build, and
        // kide-clean-architecture must not depend on debug tooling. Mirrors the split that
        // kide-clean-architecture-test already makes for test-only dependencies.
        commonMain.dependencies {
            api(project(":kide-clean-architecture"))
            api(project(":kide-devtools"))
        }

        jvmTest.dependencies {
            implementation(libs.kotest.runner.junit5)
            implementation(libs.kotest.assertions.core)
            implementation(libs.kotlinx.coroutines.test)
            runtimeOnly(libs.junit.platform.launcher)
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
