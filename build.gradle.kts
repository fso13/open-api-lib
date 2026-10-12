plugins {
    java
    jacoco
    id("io.spring.dependency-management") version "1.1.7" apply false
}

allprojects {
    group = providers.gradleProperty("group").getOrElse("ru.openapi")
    version = providers.gradleProperty("version").getOrElse("0.1.0-SNAPSHOT")

    repositories {
        mavenCentral()
    }
}

/**
 * Modules that are published as artifacts. Sample applications only live in the repository.
 */
val libraryModules = setOf(
    "openapi-tokens-core",
    "openapi-tokens-persistence-jpa",
    "openapi-tokens-security",
    "openapi-tokens-web",
    "openapi-tokens-autoconfigure",
    "openapi-tokens-spring-boot-starter",
)

/**
 * The starter contains only a package descriptor, so it has nothing to document: javadoc would fail
 * with "No public or protected classes found to document".
 */
val javadocModules = libraryModules - "openapi-tokens-spring-boot-starter"

/**
 * Line-coverage gates, set slightly below the measured baseline so that regressions fail the build.
 */
val coverageGatedModules = mapOf(
    "openapi-tokens-core" to "0.78",
    "openapi-tokens-security" to "0.72",
    "openapi-tokens-persistence-jpa" to "0.85",
    "openapi-tokens-autoconfigure" to "0.62",
    "openapi-tokens-web" to "0.45",
)

subprojects {
    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "jacoco")

    val isLibrary = name in libraryModules

    the<JavaPluginExtension>().apply {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
        if (name in javadocModules) {
            withJavadocJar()
        }
        if (isLibrary) {
            withSourcesJar()
        }
    }

    if (isLibrary && name !in javadocModules) {
        tasks.named<Javadoc>("javadoc") {
            enabled = false
        }
    }

    the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().apply {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:3.4.1")
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-parameters")
        options.compilerArgs.add("-Xlint:all,-serial,-processing")
        options.compilerArgs.add("-Werror")
    }

    tasks.withType<Javadoc>().configureEach {
        options.encoding = "UTF-8"
        (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:none", true)
        // Broken javadoc references must fail the build instead of silently producing a jar.
        isFailOnError = true
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        finalizedBy(tasks.named("jacocoTestReport"))
    }

    tasks.named<JacocoReport>("jacocoTestReport") {
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }

    coverageGatedModules[name]?.let { minimumCoverage ->
        tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
            dependsOn(tasks.named("test"))
            violationRules {
                rule {
                    limit {
                        counter = "LINE"
                        minimum = minimumCoverage.toBigDecimal()
                    }
                }
            }
        }
        tasks.named("check") {
            dependsOn(tasks.named("jacocoTestCoverageVerification"))
        }
    }

    if (isLibrary) {
        apply(plugin = "maven-publish")
        configure<PublishingExtension> {
            publications {
                create<MavenPublication>("maven") {
                    from(components["java"])
                    pom {
                        name.set(project.name)
                        description.set("Reusable Spring Boot starter for API tokens")
                    }
                }
            }
        }
    }

    dependencies {
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testImplementation"("org.assertj:assertj-core")
        "testImplementation"("org.mockito:mockito-core")
        "testImplementation"("org.mockito:mockito-junit-jupiter")
        // Required by Gradle 9: the launcher is no longer added automatically.
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }
}
