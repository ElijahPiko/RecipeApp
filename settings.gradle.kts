pluginManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // PREFER_SETTINGS (not FAIL_ON_PROJECT_REPOS): machines may inject mirror repositories via a
    // global init script (e.g. $GRADLE_USER_HOME/init.gradle.kts). PREFER_SETTINGS ignores those with a
    // warning instead of failing the build.
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        mavenLocal()
        maven { url = uri("https://mirrors.cloud.tencent.com/maven") }
        google()
        mavenCentral()
    }
}

rootProject.name = "Recipe"
include(":core")
include(":server")
include(":app")
