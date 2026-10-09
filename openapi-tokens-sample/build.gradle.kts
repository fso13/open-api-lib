plugins {
    java
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":openapi-tokens-spring-boot-starter"))
    implementation(project(":openapi-tokens-sample-ui"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation(project(":openapi-tokens-sample-ui"))
    testImplementation(testFixtures(project(":openapi-tokens-sample-ui")))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    environment("TESTCONTAINERS_RYUK_DISABLED", "true")
}
