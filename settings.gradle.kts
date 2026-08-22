pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/akshay-k-a-dev/NewPipeExtractor")
            credentials {
                username = providers.gradleProperty("gpr.user")
                    .orElse(providers.environmentVariable("GPR_USER")).get()
                password = providers.gradleProperty("gpr.token")
                    .orElse(providers.environmentVariable("GPR_KEY")).get()
            }
        }
    }
}

rootProject.name = "AppPlayer"
include(":app")
include(":innertube")
