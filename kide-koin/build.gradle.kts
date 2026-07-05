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
       namespace = "org.fuusio.kide.koin"
       compileSdk = libs.versions.compileSdk.get().toInt()
       minSdk = libs.versions.minSdk.get().toInt()

       withHostTest {}
   }

   sourceSets {
       commonMain.dependencies {
           implementation(project.dependencies.platform(libs.koin.bom))
           api(libs.koin.core)
       }

       commonTest.dependencies {
           implementation(libs.kotlin.test)
       }

       androidMain.dependencies {
           api(project(":kide-clean-architecture"))
       }
   }
}
