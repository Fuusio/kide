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
    iosX64()

    android {
        namespace = "org.fuusio.kide.test"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":kide"))
            api(libs.kotlinx.coroutines.test)
            api(libs.turbine)
            api(libs.kotlin.test)
        }
    }
}
