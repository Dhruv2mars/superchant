import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Load local.properties for signing config
val localPropsFile = rootProject.file("local.properties")
val localProps = Properties().apply {
    if (localPropsFile.exists()) {
        FileInputStream(localPropsFile).use { load(it) }
    }
}

android {
    namespace = "com.superchant"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.superchant"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val storeFilePath = localProps.getProperty("RELEASE_STORE_FILE")
            if (storeFilePath != null) {
                storeFile = file(storeFilePath)
                storePassword = localProps.getProperty("RELEASE_STORE_PASSWORD", "")
                keyAlias = localProps.getProperty("RELEASE_KEY_ALIAS", "")
                keyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD", "")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
            // No applicationIdSuffix - keeps same package name as release for consistent testing
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    
    // Bundle configuration for AAB
    bundle {
        language {
            // Don't split by language - include all in one APK
            enableSplit = false
        }
        density {
            // Don't split by density for universal APK
            enableSplit = false
        }
        abi {
            // Don't split by ABI for universal APK
            enableSplit = false
        }
    }
}

// Task to build release APK with proper naming
tasks.register("buildReleaseApk") {
    dependsOn("assembleRelease")
    doLast {
        val versionName = android.defaultConfig.versionName
        val apkDir = layout.buildDirectory.dir("outputs/apk/release").get().asFile
        val apkFile = apkDir.listFiles()?.find { it.name.endsWith("-release.apk") }
        if (apkFile != null) {
            val destFile = File(rootProject.rootDir, "SuperChant-v$versionName.apk")
            apkFile.copyTo(destFile, overwrite = true)
            println("Release APK copied to: ${destFile.absolutePath}")
        }
    }
}

// Task to build release AAB with proper naming
tasks.register("buildReleaseBundle") {
    dependsOn("bundleRelease")
    doLast {
        val versionName = android.defaultConfig.versionName
        val aabDir = layout.buildDirectory.dir("outputs/bundle/release").get().asFile
        val aabFile = aabDir.listFiles()?.find { it.name.endsWith(".aab") }
        if (aabFile != null) {
            val destFile = File(rootProject.rootDir, "SuperChant-v$versionName.aab")
            aabFile.copyTo(destFile, overwrite = true)
            println("Release AAB copied to: ${destFile.absolutePath}")
        }
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.bundles.lifecycle)
    implementation(libs.androidx.activity.compose)

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Kotlin
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    
    // Drag and drop reorderable lists
    implementation("org.burnoutcrew.composereorderable:reorderable:0.9.6")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
