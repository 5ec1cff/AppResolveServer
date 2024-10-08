import android.databinding.tool.ext.capitalizeUS
import com.android.build.gradle.tasks.PackageAndroidArtifact

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.rikka.refine)
}

android {
    namespace = "io.github.a13e300.appresolveserver"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.a13e300.appresolveserver"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        debug {
            signingConfig = null
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = true
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    packaging {
        resources {
            excludes += "**"
        }
    }
    tasks.withType<PackageAndroidArtifact> {
        doFirst { appMetadata.asFile.orNull?.writeText("") }
    }
}

dependencies {
    compileOnly(libs.annotation)
    compileOnly(project(":stub"))
    implementation(libs.commons.cli)
    implementation(libs.gson)
}

afterEvaluate {
    android.applicationVariants.forEach { variant ->
        val variantLowered = variant.name.lowercase()
        val variantCapped = variant.name.capitalizeUS()
        val apkPath =
            layout.buildDirectory.file("outputs/apk/$variantLowered/app-$variantLowered-unsigned.apk")
                .get().asFile.absolutePath
        val shPath = layout.projectDirectory.file("scripts/ars.sh").asFile.absolutePath
        task<Task>("pushByADB$variantCapped") {
            group = "push"
            dependsOn("assemble$variantCapped")
            doLast {
                exec {
                    commandLine(
                        "adb",
                        "shell",
                        "rm /data/local/tmp/ars.apk /data/local/tmp/ars || su -c 'rm /data/local/tmp/ars.apk /data/local/tmp/ars'"
                    )
                    isIgnoreExitValue = true
                }
                exec {
                    commandLine("adb", "push", apkPath, "/data/local/tmp/ars.apk")
                }
                exec {
                    commandLine("adb", "push", shPath, "/data/local/tmp/ars")
                }
                exec {
                    commandLine("adb", "shell", "chmod +x /data/local/tmp/ars")
                }
            }

        }
    }
}