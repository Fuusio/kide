plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.maven.publish)
}

kotlin {
    explicitApiWarning()

    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation()

    jvm()
    iosArm64()
    iosSimulatorArm64()
    iosX64()

    android {
        namespace = "org.fuusio.kide.navigation"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":kide"))
            api(compose.runtime)
            api(libs.jetbrains.lifecycle.viewmodel)
            api(libs.jetbrains.lifecycle.viewmodel.compose)
            api(libs.jetbrains.lifecycle.viewmodel.navigation3)
            api(libs.jetbrains.navigation3.ui)
            api(libs.kotlinx.serialization.core)
            api(libs.jetbrains.lifecycle.viewmodel.savedstate)
        }

        jvmTest.dependencies {
            implementation(libs.kotest.runner.junit5)
            implementation(libs.kotest.assertions.core)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.kotlinx.serialization.json)
            runtimeOnly(libs.junit.platform.launcher)
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
