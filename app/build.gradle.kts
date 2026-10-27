import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing is read from keystore.properties (never committed). See README.md.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseKey = keystoreProps.getProperty("storeFile")?.let { rootProject.file(it).exists() } == true

// AdMob IDs. Google's public test IDs are used unless you set your own in gradle.properties
// (admobAppId, admobBannerId, admobRewardedId). Debug builds always use the test IDs.
val testAppId = "ca-app-pub-3940256099942544~3347511713"
val testBannerId = "ca-app-pub-3940256099942544/9214589741"
val testRewardedId = "ca-app-pub-3940256099942544/5224354917"
fun prop(name: String, fallback: String) = providers.gradleProperty(name).orNull?.takeIf { it.isNotBlank() } ?: fallback

android {
    namespace = "com.tinymotors.game"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tinymotors.game"
        minSdk = 24
        targetSdk = 36
        versionCode = 3
        versionName = "1.2.0"
        manifestPlaceholders["admobAppId"] = prop("admobAppId", testAppId)
        buildConfigField("String", "BANNER_AD_UNIT", "\"${prop("admobBannerId", testBannerId)}\"")
        buildConfigField("String", "REWARDED_AD_UNIT", "\"${prop("admobRewardedId", testRewardedId)}\"")
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            manifestPlaceholders["admobAppId"] = testAppId
            buildConfigField("String", "BANNER_AD_UNIT", "\"$testBannerId\"")
            buildConfigField("String", "REWARDED_AD_UNIT", "\"$testRewardedId\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseKey) signingConfig = signingConfigs.getByName("release")
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

    androidResources {
        localeFilters += listOf("en")
    }

    packaging {
        resources.excludes += listOf("/META-INF/{AL2.0,LGPL2.1}", "DebugProbesKt.bin", "/kotlin/**", "META-INF/*.version")
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        // Google Play currently requires target SDK 36; newer platform releases are not required.
        disable += listOf("OldTargetApi", "GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.play.billing.ktx)
    implementation(libs.play.services.ads)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
