plugins {
    `maven-publish`
}

group = "dev.saltt"
version = "1.0.0"
version = "1.0.1"

repositories {
    maven("https://jitpack.io")
    mavenCentral()
}

dependencies {
    implementation("com.github.saltAgain:LifeCommon:v1.1.51")
    implementation("com.github.Life-Steal:LifeProtocol:v1.1.0")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    withSourcesJar()   // this is what makes source usable in consumers' IDEs
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

tasks.test {
    useJUnitPlatform()
}