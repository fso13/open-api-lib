package ru.openapi.tokens.persistence;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "ru.openapi.tokens",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class ArchitectureTest {

    @ArchTest
    static final ArchRule dtoShouldNotDependOnEntity =
            noClasses().that().resideInAPackage("..dto..")
                    .should().dependOnClassesThat().areAnnotatedWith(Entity.class)
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule entityShouldNotDependOnDto =
            noClasses().that().areAnnotatedWith(Entity.class)
                    .should().dependOnClassesThat().resideInAPackage("..dto..")
                    .allowEmptyShould(true);
}
