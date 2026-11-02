package dev.specwriter.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Règles de l'architecture hexagonale (plan.md, Structure Decision). */
@AnalyzeClasses(packages = "dev.specwriter", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    @ArchTest
    static final ArchRule domaineSansFramework = noClasses().that().resideInAPackage("dev.specwriter.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "io.quarkus..", "jakarta..", "dev.langchain4j..", "org.hibernate..", "io.vertx..",
                    "com.github.copilot..", "com.fasterxml..", "tools.jackson..",
                    "dev.specwriter.application..", "dev.specwriter.infrastructure..");

    @ArchTest
    static final ArchRule applicationNeDependQueDuDomaine = noClasses().that().resideInAPackage("dev.specwriter.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "io.quarkus..", "dev.langchain4j..", "org.hibernate..", "io.vertx..", "com.github.copilot..",
                    "dev.specwriter.infrastructure..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule pasDHorlogeNiDAleaHorsPlateforme = noClasses()
            .that().resideOutsideOfPackage("dev.specwriter.infrastructure.platform..")
            .and().resideOutsideOfPackage("dev.specwriter.infrastructure.web..")
            .should().callMethod(java.time.Instant.class, "now")
            .orShould().callMethod(java.util.UUID.class, "randomUUID");
}
