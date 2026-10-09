plugins {
    `java-library`
}

dependencies {
    // Crypto for TokenHasher — no Spring
    implementation("de.mkammerer:argon2-jvm:2.11")
    implementation("at.favre.lib:bcrypt:0.10.2")
    implementation("com.bucket4j:bucket4j_jdk17-core:8.14.0")
    implementation("org.slf4j:slf4j-api")

    testImplementation("io.cucumber:cucumber-java:7.20.1")
    testImplementation("io.cucumber:cucumber-junit-platform-engine:7.20.1")
    testImplementation("org.junit.platform:junit-platform-suite")
}
