rootProject.name = "LifeHub"

plugins {
    // See documentation on https://scaffoldit.dev
    id("dev.scaffoldit") version "0.2.+"
}

hytale {
    usePatchline("release")
    useVersion("latest")

    repositories {
        maven("https://jitpack.io")
    }

    // Dependencies are declared in build.gradle.kts.
    dependencies {
    }

    manifest {
        Group = "Saltt"
        Name = "LifeHub"
        Main = "dev.saltt.hub.Main"
    }
}
