plugins {
    `java-library`
}

dependencies {
    api(project(":openapi-tokens-autoconfigure"))
    api(project(":openapi-tokens-core"))
    api(project(":openapi-tokens-persistence-jpa"))
    api(project(":openapi-tokens-security"))
    api(project(":openapi-tokens-web"))
}
