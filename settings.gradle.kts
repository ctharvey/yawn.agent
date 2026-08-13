rootProject.name = "yawn.agent"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

val yawnDbPath = providers.gradleProperty("yawnDbPath").getOrElse("../yawn.db")

includeBuild(yawnDbPath) {
    dependencySubstitution {
        substitute(module("rip.yawn:yawn.db")).using(project(":"))
    }
}
