package ru.openapi.tokens.autoconfigure;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture rules checked where both the REST DTOs and the JPA entities are on the classpath.
 *
 * <p>{@code allowEmptyShould} is intentionally not used: if the packages disappear the rules must
 * fail instead of silently passing.</p>
 */
@AnalyzeClasses(
        packages = "ru.openapi.tokens",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class ArchitectureTest {

    @ArchTest
    static final ArchRule dtoShouldNotDependOnEntity =
            noClasses().that().resideInAPackage("..dto..")
                    .should().dependOnClassesThat().areAnnotatedWith(Entity.class);

    @ArchTest
    static final ArchRule entityShouldNotDependOnDto =
            noClasses().that().areAnnotatedWith(Entity.class)
                    .should().dependOnClassesThat().resideInAPackage("..dto..");

    @ArchTest
    static final ArchRule coreShouldNotDependOnSpring =
            noClasses().that().resideInAPackage("ru.openapi.tokens.token..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..",
                            "jakarta.persistence..",
                            "jakarta.servlet.."
                    );

    @ArchTest
    static final ArchRule persistenceInternalsShouldNotLeak =
            noClasses().that().resideOutsideOfPackage("ru.openapi.tokens.persistence..")
                    .should().dependOnClassesThat().resideInAPackage("ru.openapi.tokens.persistence.internal..");
}
