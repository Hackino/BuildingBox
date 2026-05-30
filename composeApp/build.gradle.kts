import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.crashlytics)
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    jvm("desktop")

    sourceSets {
        val desktopMain by getting

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)

            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.navigation.compose)

            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.coroutines)
        }

        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.splashscreen)
            implementation(libs.androidx.core.ktx)
            implementation(libs.koin.android)

            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.crashlytics)
            implementation(libs.firebase.analytics)

            implementation(libs.gitlive.firebase.auth)
            implementation(libs.gitlive.firebase.database)
            implementation(libs.gitlive.firebase.common)
        }

        iosMain.dependencies {
            implementation(libs.gitlive.firebase.auth)
            implementation(libs.gitlive.firebase.database)
            implementation(libs.gitlive.firebase.common)
        }

        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.core)
            // Provides Dispatchers.Main on the JVM desktop target (backed by the AWT/Swing
            // event dispatch thread). Without it, viewModelScope crashes with
            // "Module with the Main dispatcher is missing".
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            // PDF report export — Graphics2D bridge lets us reuse the Android draw code.
            implementation(libs.pdfbox)
            implementation(libs.pdfbox.graphics2d)
        }
    }
}

// Load signing credentials from keystore.properties (git-ignored, never committed).
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.buildingbox.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")

    defaultConfig {
        applicationId = "com.buildingbox.app"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        create("buildingbox") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("buildingbox")
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.buildingbox.app.resources"
}

compose.desktop {
    application {
        mainClass = "com.buildingbox.app.MainKt"

        // Shrinks the bundled JVM jars (Compose, Ktor, kotlinx, app code) in the
        // *release* package tasks. Keep rules live in compose-desktop.pro.
        // Obfuscation stays OFF for now (lower risk, still shrinks); flip to true
        // only after the runtime smoke test passes. ProGuard breakage is runtime-
        // only, so test sign-in / RTDB reads / report export on a release build.
        buildTypes.release.proguard {
            configurationFiles.from(project.file("compose-desktop.pro"))
            obfuscate.set(false)
            optimize.set(true)
        }

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "BuildingBox"
            packageVersion = "1.0.0"

            // Trim the bundled runtime: only the modules jpackage detects, plus the
            // explicit ones below. java.desktop → AWT (report export opens files);
            // jdk.unsupported → sun.misc.Unsafe used by some coroutine/Skiko paths.
            includeAllModules = false
            modules("java.desktop", "java.management", "jdk.unsupported")

            // Files placed under desktop-resources/common/ are bundled into the app and
            // exposed at runtime via System.getProperty("compose.application.resources.dir").
            // desktop-firebase.properties lives there (written by scripts/restore-secrets.sh
            // and the CI workflow) so the packaged app can read it — see loadConfig().
            appResourcesRootDir.set(project.file("desktop-resources"))

            macOS { iconFile.set(project.file("src/desktopMain/resources/icon.icns")) }
            windows {
                iconFile.set(project.file("src/desktopMain/resources/icon.ico"))
                // Installer UX: offer a Desktop shortcut + Start-menu entry, and let the
                // user pick the install directory.
                shortcut = true
                menu = true
                menuGroup = "BuildingBox"
                dirChooser = true
                // Install per-user (to %LOCALAPPDATA%) so the installer needs NO admin/UAC
                // elevation. A pending UAC prompt the user never sees is the usual cause of
                // an installer that "runs in Task Manager but shows no window". The wizard
                // (incl. dirChooser) still appears; it just no longer requires admin.
                perUserInstall = true
                // Stable upgrade UUID so new installers replace the old version cleanly.
                upgradeUuid = "8B6F9C2A-1D3E-4F5A-9B7C-2E4D6A8F0C13"
            }
            linux { iconFile.set(project.file("src/desktopMain/resources/icon.png")) }
        }
    }
}
