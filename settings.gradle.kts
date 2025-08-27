pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven(
            url = "https://esri.jfrog.io/artifactory/arcgis"
        )
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://www.jitpack.io")
        maven(
            url = "https://esri.jfrog.io/artifactory/arcgis",
        )
    }
}

rootProject.name = "Katchi Abadi"
include(":app")