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
            dependencies {
                implementation(libs.ktor.client.okhttp)
                implementation(libs.okhttp.tls)
                implementation(libs.okhttp.dnsoverhttps)
                implementation("com.github.NekoSukuriputo:nekuva-exts:v1.0.1") {
                    exclude(group = "org.json", module = "json")
                }
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
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
            }
        }
        val desktopMain by getting {
            dependsOn(jvmSharedMain)
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.json)
                implementation("org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.6.1")
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
        versionCode = 1
        versionName = "1.0"
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

    signingConfigs {
        create("release") {
            storeFile = localProperties.getProperty("nekuva.storeFile")?.let { rootProject.file(it) }
            storePassword = localProperties.getProperty("nekuva.storePassword") ?: ""
            keyAlias = localProperties.getProperty("nekuva.keyAlias") ?: ""
            keyPassword = localProperties.getProperty("nekuva.keyPassword") ?: ""
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
        }
        getByName("release") {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
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
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb)
            packageName = "org.nekosukuriputo.nekuva"
            packageVersion = "1.0.0"
        }
    }
}





