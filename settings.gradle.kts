rootProject.name = "yawn.agent"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

includeBuild("../yawn.db") {
    dependencySubstitution {
        substitute(module("rip.yawn:yawn.db")).using(project(":"))
    }
}
