plugins {
    java
    id("com.gradleup.shadow") version "8.3.0"
}

val javaVersion = 25
val grpcVersion = "1.75.0"

version = "1.2.0"

repositories {
    mavenCentral()

    maven {
        url = uri("https://jitpack.io")

        credentials {
            username = "jp_1ooht4ug7h5aso9sm2voqhjtjh"
        }
    }
}

dependencies {
    compileOnly("org.jetbrains:annotations:26.0.2")

    implementation("org.flywaydb:flyway-core:12.9.0")
    implementation("org.flywaydb:flyway-mysql:12.9.0")

    implementation("com.github.Life-Steal:LifeProtocol:v1.2.0")

    implementation("com.maxmind.geoip2:geoip2:5.1.0")

    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.3")
    implementation("com.zaxxer:HikariCP:6.3.0")
    implementation("org.jdbi:jdbi3-core:3.45.4")
    implementation("org.slf4j:slf4j-jdk14:2.0.16")

    implementation("io.grpc:grpc-netty-shaded:$grpcVersion")
    implementation("io.grpc:grpc-protobuf:$grpcVersion")
    implementation("io.grpc:grpc-stub:$grpcVersion")

    compileOnly(fileTree("libs") {
        include("*.jar")
    })

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaVersion)
    }

    withSourcesJar()
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    mergeServiceFiles()

    dependencies {
        exclude(dependency("com.hypixel.hytale:Server:.*"))
        exclude(dependency("dev.scaffoldit:.*:.*"))
    }
}

val lifeGroup = "life"

tasks.named("clean") {
    group = lifeGroup
}

tasks.named("devServer") {
    group = lifeGroup
}

tasks.named("shadowJar") {
    group = lifeGroup
    mustRunAfter("clean")
}

tasks.register("cleanShadowJar") {
    group = lifeGroup
    description = "Clean, then build the shadow jar."
    dependsOn("clean", "shadowJar")
}
