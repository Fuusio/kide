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
        namespace = "org.fuusio.kide.voyager"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()

        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":kide"))
            api(libs.voyager.screenmodel)
            implementation(compose.runtime)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
