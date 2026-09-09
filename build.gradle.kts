plugins {
    `maven-publish`
    id("com.gradleup.shadow") version "8.3.0"
}

group = "dev.saltt"
version = "1.2.0"

val grpcVersion = "1.74.0"
val protobufVersion = "4.31.1"

// Must match the grpc and protobuf versions LifeProtocol was generated against.
val protocolVersion = "v1.2.0"

repositories {
    mavenLocal()
    maven("https://jitpack.io")
    mavenCentral()
}

dependencies {
    implementation("com.github.Life-Steal:LifeProtocol:$protocolVersion")
    implementation("com.google.protobuf:protobuf-java:$protobufVersion")
    implementation("io.grpc:grpc-netty-shaded:$grpcVersion")
    implementation("io.grpc:grpc-stub:$grpcVersion")
    implementation("io.grpc:grpc-protobuf:$grpcVersion")

    implementation("org.jdbi:jdbi3-core:3.45.4")
    implementation("com.zaxxer:HikariCP:6.3.0")
    implementation("org.flywaydb:flyway-core:11.1.0")
    implementation("org.flywaydb:flyway-mysql:11.1.0")
    runtimeOnly("org.mariadb.jdbc:mariadb-java-client:3.5.3")

    implementation("com.maxmind.geoip2:geoip2:5.0.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("org.slf4j:slf4j-api:2.0.16")

    compileOnly("com.google.code.findbugs:jsr305:3.0.2")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
    withSourcesJar()
}

tasks.shadowJar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    mergeServiceFiles()

    dependencies {
        exclude(dependency("com.hypixel.hytale:Server:.*"))
        exclude(dependency("dev.scaffoldit:.*:.*"))
    }
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
