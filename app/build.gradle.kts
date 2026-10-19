import java.time.LocalDate
import java.time.ZoneId
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing keys live in the untracked local.properties.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

// Versioning follows ChatGPT's date-based scheme: versionName MAJOR.YYYY.DDD (DDD = day of year, IST)
// and versionCode YYDDDNN (e.g. 2.2026.292 / 2629200). NN (0-99, `-PbuildNumber=N`) allows several uploads
// on one day; `-PreleaseDate=YYYY-MM-DD` pins the date. Codes only ever go up.
abstract class TodayIst : ValueSource<String, ValueSourceParameters.None> {
    override fun obtain(): String = LocalDate.now(ZoneId.of("Asia/Kolkata")).toString()
}
val appMajorVersion = 2
val releaseDate = LocalDate.parse(
    (findProperty("releaseDate") as String?) ?: providers.of(TodayIst::class) {}.get()
)
val buildNumber = (findProperty("buildNumber") as String?)?.toInt() ?: 0
require(buildNumber in 0..99) { "buildNumber must be 0-99" }
val appVersionName = "%d.%d.%03d".format(appMajorVersion, releaseDate.year, releaseDate.dayOfYear)
val appVersionCode = (releaseDate.year % 100) * 100_000 + releaseDate.dayOfYear * 100 + buildNumber

android {
    namespace = "com.superchant"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.superchant"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        localProps.getProperty("RELEASE_STORE_FILE")?.let { storePath ->
            create("release") {
                storeFile = file(storePath)
                storePassword = localProps.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = localProps.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Without release keys, fall back to debug signing so local release builds still install.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // "SuperChant Dev" installs next to the Play Store app, so testing never touches real practice data.
        debug {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// buildReleaseApk / buildReleaseBundle: build, then copy to the repo root as SuperChant-v{version}.{apk,aab}.
mapOf("Apk" to "apk", "Bundle" to "aab").forEach { (kind, ext) ->
    val outDir = layout.buildDirectory.dir("outputs/${if (ext == "apk") "apk" else "bundle"}/release")
    val dest = rootProject.file("SuperChant-v${android.defaultConfig.versionName}.$ext")
    tasks.register("buildRelease$kind") {
        dependsOn(if (ext == "apk") "assembleRelease" else "bundleRelease")
        doLast {
            outDir.get().file("app-release.$ext").asFile.copyTo(dest, overwrite = true)
            println("Copied to $dest")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.reorderable)

    testImplementation(libs.junit)
}
