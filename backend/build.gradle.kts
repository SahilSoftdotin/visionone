plugins {
    java
    id("org.springframework.boot") version "3.5.5"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.visiondigitallab"
version = "0.1.0-SNAPSHOT"
description = "VisionOne API - Practice Growth Operating System"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

extra["testcontainersVersion"] = "1.21.3"
extra["resilience4jVersion"] = "2.3.0"
extra["archunitVersion"] = "1.3.0"
extra["springdocVersion"] = "2.8.6"

dependencies {
    // Web + validation
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Persistence
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // Security - OAuth2 resource server (Keycloak via standards, no vendor SDK)
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-security")

    // Caching - Caffeine only in Phase 1
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("com.github.ben-manes.caffeine:caffeine")

    // Resilience
    implementation("io.github.resilience4j:resilience4j-spring-boot3:${property("resilience4jVersion")}")
    implementation("org.springframework.boot:spring-boot-starter-aop")

    // Observability
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")

    // API docs -> the frontend's TypeScript types are generated from this spec
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${property("springdocVersion")}")

    developmentOnly("org.springframework.boot:spring-boot-devtools")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.wiremock:wiremock-standalone:3.10.0")
    testImplementation("com.tngtech.archunit:archunit-junit5:${property("archunitVersion")}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:${property("testcontainersVersion")}")
    }
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all,-serial,-processing"))
}

tasks.withType<Test> {
    useJUnitPlatform()

    // Docker Engine 29 refuses API versions below 1.40, but docker-java still negotiates 1.32 by
    // default. Testcontainers' probe gets a 400, discards every strategy, and reports the
    // misleading "Could not find a valid Docker environment". Pin a version both old and new
    // engines accept - 1.41 has been valid since Docker 20.10. Override with -Dapi.version=... .
    systemProperty("api.version", System.getProperty("api.version") ?: "1.41")

    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}
