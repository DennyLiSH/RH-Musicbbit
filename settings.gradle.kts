// Guard: builds must run from the real project path, not through a directory
// symlink (e.g. C:\NC_work -> D:\NC_Work). Mixed entry paths corrupt Kotlin
// incremental caches with cross-root relative paths ("this and base files
// have different roots"). Fails at configuration time, before any task
// execution state is written. No-op on machines where this path is real.
val invokedPath = settingsDir.absolutePath
val realPath = settingsDir.toPath().toRealPath().toString()
if (!invokedPath.equals(realPath, ignoreCase = true)) {
    throw GradleException(
        """
        Build invoked through a linked path - aborting to protect incremental build state.
          invoked: $invokedPath
          real:    $realPath
        Mixing entry paths corrupts Kotlin incremental caches ('this and base files have different roots').
        Re-run the build from the real path above.
        """.trimIndent()
    )
}

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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "音乐兔"
include(":app")
