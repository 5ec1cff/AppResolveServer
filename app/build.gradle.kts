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
        release {
            isMinifyEnabled = false
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
}

dependencies {
    compileOnly(libs.annotation)
    compileOnly(project(":stub"))
    implementation(libs.commons.cli)
    implementation(libs.gson)
}

afterEvaluate {
    val apkPath = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk").get().asFile.absolutePath
    val shPath = layout.projectDirectory.file("scripts/ars.sh").asFile.absolutePath
    task<Task>("pushByADB") {
        group = "push"
        dependsOn("assembleDebug")
        doLast {
            exec {
                commandLine("adb", "shell", "rm /data/local/tmp/ars.apk /data/local/tmp/ars || su -c 'rm /data/local/tmp/ars.apk /data/local/tmp/ars'")
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