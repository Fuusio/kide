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
        namespace = "org.fuusio.kide.decompose"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()

        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":kide"))
            api(libs.essenty.instance.keeper)
            api(libs.essenty.state.keeper)
            api(libs.kotlinx.serialization.core)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
