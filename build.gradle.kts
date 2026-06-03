plugins {
    id("org.springframework.boot") version "3.4.3"
    id("io.spring.dependency-management") version "1.1.4"
    java
}

group = "rip.yawn"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

dependencies {
    // Web + JPA (read-only)
    implementation("org.springframework.boot:spring-boot-starter-web:3.4.3")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa:3.4.3")
    implementation("org.springframework.boot:spring-boot-starter-validation:3.4.3")
    implementation("org.springframework.boot:spring-boot-starter-actuator:3.4.3")

    // PostgreSQL driver
    runtimeOnly("org.postgresql:postgresql:42.7.3")

    // Caching (card resolution results are nearly static)
    implementation("org.springframework.boot:spring-boot-starter-cache:3.4.3")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")

    // Dotenv
    implementation("io.github.cdimascio:dotenv-java:3.2.0")

    // Dev tools
    developmentOnly("org.springframework.boot:spring-boot-devtools:3.4.3")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test:3.4.3")
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.21.3"))
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

springBoot {
    buildInfo()
}
