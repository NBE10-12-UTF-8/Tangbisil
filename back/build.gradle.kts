plugins {
    java
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    kotlin("plugin.jpa") version "2.3.21"
    kotlin("plugin.lombok") version "2.3.21"
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com"
version = "0.0.1-SNAPSHOT"
description = "NBE10-12-2-UTF-8"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-websocket")

    // H2 Console
    implementation("org.springframework.boot:spring-boot-h2console")

    // OpenAPI / Swagger
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.2")

    // Actuator & Prometheus
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")

    // Database
    runtimeOnly("com.h2database:h2")
    runtimeOnly("com.mysql:mysql-connector-j")

    // Flyway
    implementation("org.springframework.boot:spring-boot-flyway")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")

    // Redis
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // Redisson (분산 락 제어용)
    implementation("org.redisson:redisson:3.43.0")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    // 이 프로젝트는 Jackson 3(tools.jackson.*)를 쓰는데, com.fasterxml.jackson.module:jackson-module-kotlin은
    // Jackson 2 전용이라 공유 ObjectMapper(Ut.json.objectMapper)에 전혀 안 얹힌다 - Kotlin data class를
    // 기본 생성자 없이 JSON에서 역직렬화하면 InvalidDefinitionException이 난다. Jackson 3용 좌표를 써야 한다.
    implementation("tools.jackson.module:jackson-module-kotlin:3.1.4")

    // Dev
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // JJWT
    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")

    // 한국어 형태소 분석 (트렌드 키워드 추출용 명사 필터링)
    implementation("org.apache.lucene:lucene-analysis-nori:9.11.1")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    // @Async 메서드 완료를 폴링으로 기다리는 용도 (TrendAggregationEventHandlerTest)
    testImplementation("org.awaitility:awaitility:4.3.0")

    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
tasks.register<Exec>("installGitHooks") {
    commandLine("git", "config", "core.hooksPath", ".githooks")
    doLast {
        println("✅ Git hooks 설정 완료!")
    }
}

tasks.named("build") {
    dependsOn("installGitHooks")
}
