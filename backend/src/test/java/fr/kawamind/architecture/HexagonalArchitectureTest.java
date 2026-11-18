package fr.kawamind.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.nio.file.Path;
import java.util.Set;

/** Règles de l'architecture hexagonale (plan.md, Structure Decision). */
@AnalyzeClasses(packages = "fr.kawamind", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    @ArchTest
    static final ArchRule domaineSansFramework = noClasses().that().resideInAPackage("fr.kawamind.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "io.quarkus..", "jakarta..", "dev.langchain4j..", "org.hibernate..", "io.vertx..",
                    "com.github.copilot..", "com.fasterxml..", "tools.jackson..",
                    "fr.kawamind.application..", "fr.kawamind.infrastructure..");

    @ArchTest
    static final ArchRule applicationNeDependQueDuDomaine = noClasses().that().resideInAPackage("fr.kawamind.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "io.quarkus..", "dev.langchain4j..", "org.hibernate..", "io.vertx..", "com.github.copilot..",
                    "fr.kawamind.infrastructure..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule pasDHorlogeNiDAleaHorsPlateforme = noClasses()
            .that().resideOutsideOfPackage("fr.kawamind.infrastructure.platform..")
            .and().resideOutsideOfPackage("fr.kawamind.infrastructure.web..")
            .should().callMethod(java.time.Instant.class, "now")
            .orShould().callMethod(java.util.UUID.class, "randomUUID");

    /**
     * 015 (T085) : l'identité d'une connexion d'agent (pid, démarrage du processus) ne se lit que dans
     * la plateforme, et dans la passerelle pour son propre processus.
     */
    @ArchTest
    static final ArchRule processusLusParLaPlateforme = noClasses()
            .that().resideOutsideOfPackage("fr.kawamind.infrastructure.platform..")
            .and().resideOutsideOfPackage("fr.kawamind.mcp..")
            .should().dependOnClassesThat().areAssignableTo(ProcessHandle.class);

    /**
     * 014 (FR-018) : seul l'adaptateur LLM déclare des AI Services et parle au modèle par le Copilot
     * SDK ; l'adaptateur du compte GitHub ({@code infrastructure.github}) n'en lit que l'état de
     * connexion.
     */
    @ArchTest
    static final ArchRule aiServicesDansLAdaptateurLlm = noClasses()
            .that().resideOutsideOfPackage("fr.kawamind.infrastructure.llm..")
            .should().dependOnClassesThat().resideInAnyPackage("dev.langchain4j.service..");

    @ArchTest
    static final ArchRule copilotSdkDansLesAdaptateurs = noClasses()
            .that().resideOutsideOfPackage("fr.kawamind.infrastructure.llm..")
            .and().resideOutsideOfPackage("fr.kawamind.infrastructure.github..")
            .should().dependOnClassesThat().resideInAnyPackage("com.github.copilot..");

    /** 016 (T060) : le client REST Confluence ne vit que dans son adaptateur. */
    @ArchTest
    static final ArchRule clientConfluenceDansSonAdaptateur = noClasses()
            .that().resideOutsideOfPackage("fr.kawamind.infrastructure.confluence..")
            .and().doNotHaveFullyQualifiedName("fr.kawamind.infrastructure.config.UseCaseProducer")
            .should().dependOnClassesThat().haveFullyQualifiedName("fr.kawamind.infrastructure.confluence.ConfluenceApi")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("fr.kawamind.infrastructure.confluence.ConfluenceThrottle");

    /** 016 (T060) : niveaux et places réservées restent du domaine pur. */
    @ArchTest
    static final ArchRule fiabiliteSansInfrastructure = noClasses()
            .that().haveSimpleNameStartingWith("Reliability").or().haveSimpleName("ReservedPlaces")
            .and().resideInAPackage("fr.kawamind.domain..")
            .should().dependOnClassesThat().resideInAnyPackage("fr.kawamind.infrastructure..", "fr.kawamind.application..");

    /** 014 (FR-017) : tout le code livré vit sous {@code fr.kawamind}, plus rien sous l'ancien nom. */
    @ArchTest
    static void toutLeCodeSousKawamind(JavaClasses analyzed) {
        JavaClasses all = new ClassFileImporter().importPath(Path.of("target/classes"));
        assertThat(all).isNotEmpty();
        assertThat(all.stream().map(JavaClass::getPackageName)
                .filter(p -> !p.equals("fr.kawamind") && !p.startsWith("fr.kawamind."))
                // migrations Java de Flyway : paquet imposé par sa convention
                .filter(p -> !p.equals("db.migration")).distinct().toList()).isEmpty();
    }

    /** La passerelle MCP est un processus à part, sans Quarkus : elle ne parle à l'outil que par HTTP (research.md R1). */
    @ArchTest
    static final ArchRule passerelleMcpAutonome = noClasses().that().resideInAPackage("fr.kawamind.mcp..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "fr.kawamind.domain..", "fr.kawamind.application..", "fr.kawamind.infrastructure..");

    /**
     * Cas d'usage qui écrivent dans une session ou ses versions (research.md R7). {@code ReviseAnswer}
     * est appelé par {@code AnswerQuestion}, qui garde ; {@code ImportJiraIssue} crée toujours une
     * nouvelle session.
     */
    static final Set<String> CAS_D_USAGE_D_ECRITURE = Set.of(
            "fr.kawamind.application.session.AnswerQuestion",
            "fr.kawamind.application.session.ResolveRelatedIssue",
            "fr.kawamind.application.session.ConcludeQuestionnaire",
            "fr.kawamind.application.specification.GenerateSpecification",
            "fr.kawamind.application.specification.ProposeAiEdit",
            "fr.kawamind.application.specification.AcceptAiEdit",
            "fr.kawamind.application.jira.PushToJira",
            "fr.kawamind.application.specification.DeleteVersion",
            "fr.kawamind.application.attachment.ManageAttachments",
            "fr.kawamind.application.conversation.DiscussSpecification",
            "fr.kawamind.application.conversation.ProposeRevision");

    static final String VERROU = "fr.kawamind.domain.session.SpecificationLock";

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
