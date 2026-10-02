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
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 腾讯导航SDK Maven仓库（已注释，不需要腾讯/Google/MapLibre SDK）
        // maven { url = uri("https://oss.sonatype.org/content/groups/public/") }
        // maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots/") }
        // maven { url = uri("https://oss.sonatype.org/content/repositories/staging/") }
    }
}

rootProject.name = "Navipilot"
include(":app")
 
