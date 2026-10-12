plugins {
    `java-library`
}

dependencies {
    api(project(":openapi-tokens-core"))
    // OpenApiTokensPersistenceJpaConfig exposes Spring Data annotations, so consumers need them too.
    api("org.springframework.boot:spring-boot-starter-data-jpa")

    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
}

tasks.withType<Test>().configureEach {
    environment("TESTCONTAINERS_RYUK_DISABLED", "true")
}
