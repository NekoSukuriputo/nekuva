pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        maven(url = "https://jitpack.io") {
            content {
                includeGroupByRegex("com\\.github.*")
            }
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
        // KCEF (Desktop embedded Chromium) pulls JOGL (org.jogamp:*) which lives on jogamp's repo.
        maven(url = "https://jogamp.org/deployment/maven")
    }
}

// includeBuild("../nekuva-exts") { // Sesuaikan path relatif ini menuju folder nekuva-exts buat local testing sebelum publish ke JitPack
//     dependencySubstitution {
//         substitute(module("com.github.NekoSukuriputo:nekuva-exts")).using(project(":"))
//     }
// }

include(":composeApp")
rootProject.name = "Nekuva"
