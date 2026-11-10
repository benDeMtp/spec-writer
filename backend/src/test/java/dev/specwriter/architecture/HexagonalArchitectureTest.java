package dev.specwriter.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Set;

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

    /** La passerelle MCP est un processus à part, sans Quarkus : elle ne parle à l'outil que par HTTP (research.md R1). */
    @ArchTest
    static final ArchRule passerelleMcpAutonome = noClasses().that().resideInAPackage("dev.specwriter.mcp..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "dev.specwriter.domain..", "dev.specwriter.application..", "dev.specwriter.infrastructure..");

    /**
     * Cas d'usage qui écrivent dans une session ou ses versions (research.md R7). {@code ReviseAnswer}
     * est appelé par {@code AnswerQuestion}, qui garde ; {@code ImportJiraIssue} crée toujours une
     * nouvelle session.
     */
    static final Set<String> CAS_D_USAGE_D_ECRITURE = Set.of(
            "dev.specwriter.application.session.AnswerQuestion",
            "dev.specwriter.application.session.ResolveRelatedIssue",
            "dev.specwriter.application.session.ConcludeQuestionnaire",
            "dev.specwriter.application.specification.GenerateSpecification",
            "dev.specwriter.application.specification.ProposeAiEdit",
            "dev.specwriter.application.specification.AcceptAiEdit",
            "dev.specwriter.application.jira.PushToJira",
            "dev.specwriter.application.specification.DeleteVersion",
            "dev.specwriter.application.attachment.ManageAttachments",
            "dev.specwriter.application.conversation.DiscussSpecification",
            "dev.specwriter.application.conversation.ProposeRevision");

    static final String VERROU = "dev.specwriter.domain.session.SpecificationLock";

    /** Une feature implémentée est verrouillée : aucun cas d'usage d'écriture ne contourne le garde (FR-026a). */
    @ArchTest
    static final ArchRule ecrituresGardeesParLeVerrou = classes()
            .that(DescribedPredicate.describe("sont des cas d'usage d'écriture",
                    (JavaClass c) -> CAS_D_USAGE_D_ECRITURE.contains(c.getName())))
            .should(new ArchCondition<JavaClass>("appeler SpecificationLock.requireEditable") {
                @Override
                public void check(JavaClass c, ConditionEvents events) {
                    boolean garde = c.getMethodCallsFromSelf().stream().anyMatch(call ->
                            call.getTargetOwner().getName().equals(VERROU) && "requireEditable".equals(call.getName()));
                    events.add(new SimpleConditionEvent(c, garde,
                            c.getName() + (garde ? " appelle" : " n'appelle pas") + " SpecificationLock.requireEditable"));
                }
            });
}
