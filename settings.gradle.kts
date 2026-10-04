pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "KvaesitsoBridge"
include(":app")
// Optional emulator-only notification source and standard AppWidgetHost.
if (providers.gradleProperty("deviceFixture").isPresent) {
    include(":device-fixture")
    project(":device-fixture").projectDir = file("tools/device-fixture")
}
