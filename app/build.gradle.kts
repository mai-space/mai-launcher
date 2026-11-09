import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Upload-Schlüssel nur lokal bzw. im CI-Runner (key.properties im Wurzelordner,
// nie im Repo). Ohne Datei wird wie bisher mit dem Debug-Schlüssel signiert.
val keyPropertiesFile = rootProject.file("key.properties")
val keyProperties = Properties().apply {
    if (keyPropertiesFile.exists()) keyPropertiesFile.inputStream().use { load(it) }
}

android {
    namespace = "space.mai.mai_launcher"
    compileSdk = 36

    defaultConfig {
        applicationId = "space.mai.mai_launcher"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    // CI auf main: nie versehentlich mit dem Debug-Schlüssel ausliefern.
    if (System.getenv("REQUIRE_RELEASE_SIGNING") == "true" && !keyPropertiesFile.exists()) {
        throw GradleException("REQUIRE_RELEASE_SIGNING is set but key.properties is missing")
    }

    signingConfigs {
        if (keyPropertiesFile.exists()) {
            create("release") {
                storeFile = file(keyProperties.getProperty("storeFile"))
                storePassword = keyProperties.getProperty("storePassword")
                keyAlias = keyProperties.getProperty("keyAlias")
                keyPassword = keyProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Mit Upload-Schlüssel, sonst (Branches, lokal) mit Debug-Schlüssel.
            signingConfig = signingConfigs.getByName(
                if (keyPropertiesFile.exists()) "release" else "debug",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                // Screenshots der Oberfläche (Roborazzi) nur auf Wunsch schreiben:
                // ./gradlew :app:testDebugUnitTest -Pscreenshots
                if (project.hasProperty("screenshots")) {
                    test.systemProperty("roborazzi.test.record", "true")
                }
                // Optionaler Spiegel für Robolectrics Android-Jars (z. B. wenn Maven Central drosselt).
                System.getenv("ROBOLECTRIC_REPO_URL")?.let {
                    test.systemProperty("robolectric.dependency.repo.url", it)
                }
            }
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
}
