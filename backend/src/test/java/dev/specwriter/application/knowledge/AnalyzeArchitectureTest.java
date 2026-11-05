package dev.specwriter.application.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.knowledge.ArchDeployment;
import dev.specwriter.domain.knowledge.ArchElement;
import dev.specwriter.domain.knowledge.ArchRelation;
import dev.specwriter.domain.knowledge.ArchScope;
import dev.specwriter.domain.knowledge.ArchitectureModel;
import dev.specwriter.domain.knowledge.ArchitectureModel.DeploymentFact;
import dev.specwriter.domain.knowledge.ArchitectureModel.Exchange;
import dev.specwriter.domain.knowledge.KnowledgeItem;
import dev.specwriter.domain.llm.ImpactResult;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.session.Language;
import dev.specwriter.support.InMemoryKnowledgeIndex;
import dev.specwriter.support.JiraWorld;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T109 : modules impactés, existence des échanges et efforts de déploiement (FR-039 à FR-041, R22). */
class AnalyzeArchitectureTest {

    static final UUID P = Project.DEFAULT_ID;
    static final Instant T = Instant.parse("2026-10-01T08:00:00Z");
    static final String NEED = "Le gestionnaire émet un avoir que la comptabilité et le client reçoivent.";
    static final String AVOIRS = "Facturation/Service Avoirs";
    static final String FACTURES = "Facturation/Service Factures";
    static final String TOPIC = "Bus/Topic Avoirs";
    static final String COMPTA = "Comptabilité";
    static final String EDITIQUE = "Editique";

    final JiraWorld w = new JiraWorld().grant(ConsentService.COPILOT);
    final InMemoryKnowledgeIndex index = new InMemoryKnowledgeIndex();
    AnalyzeArchitecture analyze;

    void element(String name, ArchElement.Type type, ArchScope scope, boolean transport, String description) {
        index.save(KnowledgeItem.archElement(P, new ArchElement(name, type, scope, transport, description, null), T, T),
                null, null);
    }

    @BeforeEach
    void setUp() {
        element("Facturation", ArchElement.Type.SOFTWARE_SYSTEM, ArchScope.PROJECT, false, "Facturation et avoirs");
        element(AVOIRS, ArchElement.Type.CONTAINER, ArchScope.PROJECT, false, "Émet les avoirs");
        element(FACTURES, ArchElement.Type.CONTAINER, ArchScope.PROJECT, false, "Émet les factures et les avoirs");
        element(TOPIC, ArchElement.Type.CONTAINER, ArchScope.COMMON, true, "Publie les avoirs");
        element(COMPTA, ArchElement.Type.SOFTWARE_SYSTEM, ArchScope.COMMON, false, "Reçoit les avoirs");
        element(EDITIQUE, ArchElement.Type.SOFTWARE_SYSTEM, ArchScope.COMMON, false, "Envoie les avoirs au client");
        index.replaceArchitecture(P, new ArchitectureModel(List.of(), List.of(
                new ArchRelation(FACTURES, AVOIRS, "Demande un avoir", null),
                new ArchRelation(AVOIRS, TOPIC, "Publie", null),
                new ArchRelation(TOPIC, COMPTA, "Consomme", null)), List.of(
                new ArchDeployment(FACTURES, "Production", "Azure", "Azure/App Service", 2, false),
                new ArchDeployment(COMPTA, "Production", "ITaaS", "ITaaS/OpenShift", 1, true),
                new ArchDeployment(EDITIQUE, "Production", "ITaaS", "ITaaS/OpenShift", 1, true))));
        analyze = new AnalyzeArchitecture(index, w.llm, w.policy);
    }

    AnalyzeArchitecture.Analysis run() {
        return analyze.run(UUID.randomUUID(), P, Language.FR, NEED, List.of(), NEED, null, null);
    }

    @Test
    void quinzeCandidatsAuPlus() {
        for (int i = 1; i <= 20; i++) {
            element("Autre/Module " + i, ArchElement.Type.CONTAINER, ArchScope.COMMON, false, "Traite les avoirs " + i);
        }
        w.llm.impacts.add(new ImpactResult(List.of(), List.of()));
        run();
        assertThat(w.llm.impactRequests).singleElement().satisfies(r -> {
            assertThat(r.candidates()).hasSize(15);
            assertThat(r.need()).isEqualTo(NEED);
            assertThat(r.candidates()).allSatisfy(c -> assertThat(c.description().length()).isLessThanOrEqualTo(600));
        });
    }

    @Test
    void nomsRamenesAuxCandidatsOuRetires() {
        w.llm.impacts.add(new ImpactResult(List.of("facturation/service avoirs", "Inconnu", COMPTA, COMPTA),
                List.of(new ImpactResult.Exchange("Facturation/Service Avoirs", "comptabilité", "Transmettre l'avoir"),
                        new ImpactResult.Exchange(AVOIRS, "Système fantôme", "Rien"))));
        AnalyzeArchitecture.Analysis a = run();
        assertThat(a.modules()).extracting(m -> m.name() + ":" + m.scope())
                .containsExactly(AVOIRS + ":PROJECT", COMPTA + ":COMMON");
        assertThat(a.exchanges()).extracting(Exchange::source, Exchange::destination)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(AVOIRS, COMPTA));
    }

    @Test
    void existenceDesEchangesCalculeeSurLeModele() {
        w.llm.impacts.add(new ImpactResult(List.of(AVOIRS, FACTURES, COMPTA, EDITIQUE), List.of(
                new ImpactResult.Exchange(FACTURES, AVOIRS, "Demander l'avoir"),
                new ImpactResult.Exchange(AVOIRS, COMPTA, "Transmettre l'avoir"),
                new ImpactResult.Exchange(AVOIRS, EDITIQUE, "Envoyer l'avoir au client"))));
        AnalyzeArchitecture.Analysis a = run();
        assertThat(a.exchanges()).containsExactly(
                Exchange.direct(FACTURES, AVOIRS),
                new Exchange(AVOIRS, COMPTA, Exchange.Kind.INDIRECT, TOPIC),
                new Exchange(AVOIRS, EDITIQUE, Exchange.Kind.MISSING, null));
    }

    @Test
    void constatsDeDeploiementCalculesSurLeModele() {
        w.llm.impacts.add(new ImpactResult(List.of(AVOIRS, FACTURES, COMPTA), List.of(
                new ImpactResult.Exchange(FACTURES, COMPTA, "Transmettre la facture"))));
        AnalyzeArchitecture.Analysis a = run();
        assertThat(a.facts()).containsExactlyInAnyOrder(
                new DeploymentFact(DeploymentFact.Kind.NOT_DEPLOYED, AVOIRS, null),
                new DeploymentFact(DeploymentFact.Kind.MULTIPLE_INSTANCES, FACTURES, null),
                new DeploymentFact(DeploymentFact.Kind.CROSS_INFRASTRUCTURE, FACTURES, COMPTA));
    }

    @Test
    void sansCandidatNiCopilotAucunAppel() {
        AnalyzeArchitecture empty = new AnalyzeArchitecture(new InMemoryKnowledgeIndex(), w.llm, w.policy);
        assertThat(empty.run(UUID.randomUUID(), P, Language.FR, NEED, List.of(), NEED, null, null))
                .isEqualTo(AnalyzeArchitecture.Analysis.EMPTY);
        JiraWorld noCopilot = new JiraWorld();
        AnalyzeArchitecture without = new AnalyzeArchitecture(index, noCopilot.llm, noCopilot.policy);
        assertThat(without.run(UUID.randomUUID(), P, Language.FR, NEED, List.of(), NEED, null, null))
                .isEqualTo(AnalyzeArchitecture.Analysis.EMPTY);
        assertThat(w.llm.calls() + noCopilot.llm.calls()).isZero();
    }
}
