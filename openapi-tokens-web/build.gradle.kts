plugins {
    `java-library`
}

dependencies {
    api(project(":openapi-tokens-core"))
    // Validation annotations are part of the public DTO/controller signatures.
    api("jakarta.validation:jakarta.validation-api")

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
}
