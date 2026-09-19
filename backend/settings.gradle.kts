plugins {
    // Lets Gradle download a matching JDK when the machine has none, so a fresh
    // clone builds without anyone installing the right Java first. Also what makes
    // the build reproducible in CI.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "visionone-api"
