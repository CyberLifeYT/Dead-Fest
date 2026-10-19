import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

/**
 * Release signing.
 *
 * The keystore is deliberately never committed. It is read from `local.properties` (gitignored)
 * when building locally, or from environment variables in CI. If neither is present the build
 * still succeeds and simply produces unsigned output, so a missing keystore can never break
 * someone else's build.
 *
 * The key must stay the key every previously distributed Dead-Fest build was signed with.
 * Android identifies an app by its signing certificate: a different key means the upgrade is
 * refused with INSTALL_FAILED_UPDATE_INCOMPATIBLE, and the only way on is to uninstall — which
 * wipes the saved Firebase session and local preferences. Changing this key is a breaking change
 * for every existing user, not a routine detail.
 */
val signingProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(property: String, environment: String): String? =
    (signingProperties.getProperty(property) ?: System.getenv(environment))?.takeIf { it.isNotBlank() }

/**
 * A value for BuildConfig, read exactly like the signing properties: `local.properties` first (that
 * file is gitignored, so a secret typed there is never committed), then the environment. Null when
 * neither has it — for the game-database key that means "lookup unavailable", which is a supported
 * outcome rather than a build failure.
 */
fun buildConfigValue(property: String, environment: String): String? =
    (signingProperties.getProperty(property) ?: System.getenv(environment))?.takeIf { it.isNotBlank() }

val keystoreFile = signingValue("deadfest.storeFile", "DEADFEST_STORE_FILE")
    ?.let { rootProject.file(it) }
    ?.takeIf { it.exists() }

android {
    namespace = "com.deadfest"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.deadfest"
        minSdk = 24
        targetSdk = 37
        versionCode = 14
        versionName = "2.0.9"

        // Client-side key for the game database (see TheGamesDbClient). It ships inside the APK,
        // so it must be treated as public; see docs/SIGNING.md.
        buildConfigField(
            "String",
            "THEGAMESDB_API_KEY",
            "\"${buildConfigValue("deadfest.theGamesDbKey", "DEADFEST_THEGAMESDB_API_KEY") ?: ""}\""
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreFile != null) {
            create("deadfest") {
                storeFile = keystoreFile
                storePassword = signingValue("deadfest.storePassword", "DEADFEST_STORE_PASSWORD")
                keyAlias = signingValue("deadfest.keyAlias", "DEADFEST_KEY_ALIAS")
                keyPassword = signingValue("deadfest.keyPassword", "DEADFEST_KEY_PASSWORD")
                // v1 keeps unusual OEM installers on old Android versions happy and is what the
                // builds already in the wild carry; v2 is what minSdk 24 requires. Both, deliberately.
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        // The debug build is signed with the SAME key as release, on purpose. It is published to
        // users as "Dead-Fest.apk" alongside the R8 build. If it used the default debug key, a user
        // could not switch between the two downloads without uninstalling first, and the debug
        // download would not install over the app they already have. This is intentional.
        debug {
            if (keystoreFile != null) signingConfig = signingConfigs.getByName("deadfest")
        }
        release {
            // R8 strips unused code (e.g. the thousands of unused material icons) and
            // resources: much smaller APK, faster install and cold start on low-end phones.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystoreFile != null) signingConfig = signingConfigs.getByName("deadfest")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.remote.config)
    implementation(libs.firebase.analytics)
    implementation(libs.play.services.auth)
    implementation(libs.kotlinx.coroutines.play.services)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Coil
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
