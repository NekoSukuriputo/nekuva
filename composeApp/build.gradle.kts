import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.room)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlinx.serialization)
}

room {
    schemaDirectory("$projectDir/schemas")
}

// Telegram backup bot token (Doki's `tg_backup_bot_token` resValue): a build-time SECRET, never committed.
// Provide it (the Kotatsu backup bot's token, or your own bot's) via local.properties `tg_backup_bot_token=...`,
// `-Dtg_backup_bot_token=...`, or the TG_BACKUP_BOT_TOKEN env var. Empty by default → Telegram backup is off.
val tgBackupBotToken: String = run {
    val props = Properties()
    val f = rootProject.file("local.properties")
    if (f.exists()) FileInputStream(f).use { props.load(it) }
    (System.getProperty("tg_backup_bot_token")
        ?: props.getProperty("tg_backup_bot_token")
        ?: System.getenv("TG_BACKUP_BOT_TOKEN")
        ?: "").trim()
}

// Generate a Kotlin constant holding the token into jvmSharedMain (cross-platform equivalent of Doki's
// Android resValue; keeps the secret out of source control).
val generateTelegramSecrets by tasks.registering {
    val outDir = layout.buildDirectory.dir("generated/telegramSecrets/kotlin")
    val token = tgBackupBotToken
    inputs.property("token", token)
    outputs.dir(outDir)
    doLast {
        val pkgDir = outDir.get().dir("org/nekosukuriputo/nekuva/backups/domain").asFile
        pkgDir.mkdirs()
        val escaped = token.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")
        File(pkgDir, "TelegramSecrets.kt").writeText(
            buildString {
                appendLine("package org.nekosukuriputo.nekuva.backups.domain")
                appendLine()
                appendLine("/** Generated at build time from `tg_backup_bot_token` (local.properties / -D / env). */")
                appendLine("internal object TelegramSecrets {")
                appendLine("    const val BOT_TOKEN: String = \"$escaped\"")
                appendLine("}")
            },
        )
    }
}

// App version — single source, overridable per release from CI (the tag drives these):
//   -PappVersionName=1.0.0-beta  -PappVersionCode=<n>  -PdesktopPackageVersion=1.0.0
// desktopPackageVersion is the INSTALLER version: jpackage/MSI/DMG require MAJOR.MINOR.PATCH with
// MAJOR > 0 (so "-beta" / leading-zero majors are rejected). It's just for upgrade ordering — the
// user-facing version is appVersionName (AppInfo / About / release tag).
val appVersionName: String = (findProperty("appVersionName") as String?)?.takeIf { it.isNotBlank() } ?: "1.0.0-beta"
val appVersionCodeValue: Int = (findProperty("appVersionCode") as String?)?.toIntOrNull() ?: 1
val desktopPackageVersion: String = (findProperty("desktopPackageVersion") as String?)?.takeIf { it.isNotBlank() } ?: "1.0.0"

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    
    sourceSets {
        val jvmSharedMain by creating {
            dependsOn(commonMain.get())
            kotlin.srcDir(generateTelegramSecrets)
            dependencies {
                implementation(libs.ktor.client.okhttp)
                implementation(libs.okhttp.tls)
                implementation(libs.okhttp.dnsoverhttps)
                // Coil network fetcher backed by the app OkHttp client (DoH + CloudFlare cookies + source headers)
                // so protected-source covers/pages/thumbnails load like the parser does.
                implementation(libs.coil.network)
                implementation("com.github.NekoSukuriputo:nekuva-exts:v1.0.6") {
                    exclude(group = "org.json", module = "json")
                }
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
                // org.json for MangaIndex (download index.json). compileOnly: Android bundles it in the
                // platform; Desktop provides it via desktopMain's implementation(libs.json) at runtime.
                compileOnly(libs.json)
            }
        }
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.materialIconsExtended)
                implementation(compose.components.resources)
                implementation(compose.components.uiToolingPreview)
                
                implementation(libs.koin.core)
                implementation(libs.koin.compose)
                implementation(libs.koin.compose.viewmodel)
                
                implementation(libs.ktor.client.core)
                
                implementation(libs.coil.compose)
                implementation(libs.coil.network.ktor)
                
                implementation(libs.androidx.room.runtime)
                implementation(libs.androidx.sqlite.bundled)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.multiplatform.settings)
                implementation(libs.multiplatform.settings.coroutines)
                implementation(libs.navigation.compose)
                implementation(libs.lifecycle.viewmodel.compose)
                implementation(libs.koin.compose.viewmodel)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val androidMain by getting {
            dependsOn(jvmSharedMain)
            dependencies {
                implementation(libs.androidx.activity.compose)
                implementation(libs.koin.android)
                // Background tracker (Doki TrackWorker): periodic new-chapter check + notifications.
                implementation(libs.androidx.work.runtime)
                // Discord Rich Presence (Doki DiscordRpc): gateway WebSocket client — Android-only.
                implementation(libs.kizzyrpc)
                // Splash screen (Doki): backports the Android 12 splash to API 23+.
                implementation(libs.androidx.core.splashscreen)
                // Foldable posture (Doki auto_double_foldable): WindowInfoTracker fold features.
                implementation(libs.androidx.window)
                // AVIF decoder (Doki): the platform ImageDecoder fails on AVIF ("unimplemented"); many sources
                // (e.g. DoujinDesu) serve AVIF pages, so a Coil AVIF decoder is needed or pages render blank.
                implementation(libs.avif.decoder)
            }
        }
        val desktopMain by getting {
            dependsOn(jvmSharedMain)
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.json)
                implementation("org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.7.1")
                // Embedded Chromium (JCEF) for Desktop evaluateJs + in-app browser / CloudFlare.
                implementation(libs.kcef)
                // Conscrypt (Google's BoringSSL-backed JSSE provider). The stock Linux JVM's default
                // TLS stack lacks some ciphers/ALPN that several manga CDNs require, so a source can
                // work on Android (Conscrypt) + Windows JVM yet fail on Linux JVM. Installing Conscrypt
                // as the top security provider (see Main.kt) gives Desktop the same TLS as Android.
                implementation(libs.conscrypt.openjdk.uber)
            }
        }
    }
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspDesktop", libs.androidx.room.compiler)
}

android {
    namespace = "org.nekosukuriputo.nekuva"
    compileSdk = 36

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")
    sourceSets["main"].resources.srcDirs("src/commonMain/resources")

    defaultConfig {
        applicationId = "org.nekosukuriputo.nekuva"
        minSdk = 24
        targetSdk = 36
        versionCode = appVersionCodeValue
        versionName = appVersionName
    }

    // Per-architecture APKs (Doki ships split + universal). Produces armeabi-v7a / arm64-v8a / x86 /
    // x86_64 APKs plus a universal one — matters because some deps (AVIF decoder, bundled SQLite) ship
    // native .so libraries.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    // Declare supported locales so Android 13+ exposes the per-app language picker and
    // the platform resolves to a shipped locale (incl. legacy `in`/`iw`) instead of English.
    androidResources {
        generateLocaleConfig = true
    }

    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localProperties.load(FileInputStream(localPropertiesFile))
    }

    // Release keystore from CI secrets (env) or local.properties; null when neither is set.
    val releaseStoreFile = (System.getenv("NEKUVA_STORE_FILE") ?: localProperties.getProperty("nekuva.storeFile"))
        ?.let { rootProject.file(it) }
        ?.takeIf { it.exists() }

    signingConfigs {
        create("release") {
            if (releaseStoreFile != null) {
                storeFile = releaseStoreFile
                storePassword = System.getenv("NEKUVA_STORE_PASSWORD") ?: localProperties.getProperty("nekuva.storePassword") ?: ""
                keyAlias = System.getenv("NEKUVA_KEY_ALIAS") ?: localProperties.getProperty("nekuva.keyAlias") ?: ""
                keyPassword = System.getenv("NEKUVA_KEY_PASSWORD") ?: localProperties.getProperty("nekuva.keyPassword") ?: ""
            }
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
        }
        getByName("release") {
            // R8: shrink + optimize with the keeps in proguard-rules.pro (Koin / kotlinx.serialization /
            // Room / OkHttp / nekuva-exts parsers / Coil / KizzyRPC / Conscrypt). resources shrunk too.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Use the real release keystore when configured (CI secrets / local.properties); otherwise fall
            // back to the debug key so a beta build is still installable out of the box.
            signingConfig = if (releaseStoreFile != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

compose.desktop {
    application {
        mainClass = "org.nekosukuriputo.nekuva.MainKt"
        // JVM args required by KCEF/JCEF (embedded Chromium) on JDK 17+. The platform-specific
        // --add-opens that don't match the host JVM are harmless (logged as a warning, then ignored).
        jvmArgs += listOf(
            "--add-opens", "java.desktop/sun.awt=ALL-UNNAMED",
            "--add-opens", "java.desktop/sun.lwawt=ALL-UNNAMED",
            "--add-opens", "java.desktop/sun.lwawt.macosx=ALL-UNNAMED",
            "--add-opens", "java.base/java.lang=ALL-UNNAMED",
        )
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb)
            packageName = "Nekuva"
            packageVersion = desktopPackageVersion
            description = "Nekuva — manga reader"
            vendor = "NekoSukuriputo"
            // Per-platform installer icons. Linux uses the PNG; Windows uses an .ico; macOS uses an .icns
            // (built in CI on the macOS runner — only wired when present so non-mac builds don't fail).
            linux {
                iconFile.set(project.file("src/desktopMain/resources/nekuva_icon.png"))
                debMaintainer = "noreply@nekosukuriputo.org"
            }
            windows {
                val ico = project.file("src/desktopMain/resources/nekuva_icon.ico")
                if (ico.exists()) iconFile.set(ico)
                menuGroup = "Nekuva"
                // Stable upgrade UUID so MSI upgrades replace the previous install instead of duplicating.
                upgradeUuid = "7E3B9C2A-1F4D-4C8E-9A6B-2D5E8F0A1B3C"
            }
            macOS {
                bundleID = "org.nekosukuriputo.nekuva"
                val icns = project.file("src/desktopMain/resources/nekuva_icon.icns")
                if (icns.exists()) iconFile.set(icns)
            }
        }
    }
}





