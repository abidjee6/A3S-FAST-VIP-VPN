
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(
        RepositoriesMode.FAIL_ON_PROJECT_REPOS
    )

    repositories {
        google()
        mavenCentral()

        // Existing project compatibility
        maven {
            url = uri("https://jitpack.io")
        }
    }
}

rootProject.name = "A3S-FAST-VIP-VPN"

include(":app")
