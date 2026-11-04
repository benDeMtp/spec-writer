package dev.specwriter.application.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.application.session.ChangeStatus;
import dev.specwriter.domain.agent.AgentAccess;
import dev.specwriter.domain.agent.AgentOperation;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.knowledge.AdrStatus;
import dev.specwriter.domain.knowledge.ArchElement;
import dev.specwriter.domain.knowledge.ArchRelation;
import dev.specwriter.domain.knowledge.ArchScope;
import dev.specwriter.domain.knowledge.ArchitectureModel;
import dev.specwriter.domain.knowledge.KnowledgeItem;
import dev.specwriter.domain.knowledge.KnowledgeKind;
import dev.specwriter.domain.knowledge.KnowledgeSelection;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.project.ProjectContext;
import dev.specwriter.domain.session.ImplementationStatus;
import dev.specwriter.domain.session.Session;
import dev.specwriter.domain.specification.SpecificationVersion;
import dev.specwriter.domain.specification.Specs;
import dev.specwriter.domain.specification.VersionOrigin;
import dev.specwriter.support.InMemoryAgentAccess;
import dev.specwriter.support.InMemoryKnowledgeIndex;
import dev.specwriter.support.JiraWorld;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T050 (cas d'usage) : features à implémenter, contenu et connaissance, transitions d'un agent, journal. */
class AgentFeaturesTest {

    static final String ADR = "http://docs/facturation/adr/adr-005.html";
    static final Instant T = Instant.parse("2026-11-04T08:00:00Z");

    final JiraWorld w = new JiraWorld();
    final InMemoryAgentAccess access = new InMemoryAgentAccess();
    final InMemoryKnowledgeIndex index = new InMemoryKnowledgeIndex();
    final AgentJournal journal = new AgentJournal(access, w.clock, w.ids);
    final ChangeStatus user = new ChangeStatus(w.sessions, w.clock);
    ListFeatures list;
    GetFeature get;
    AgentChangeStatus status;
    Session ready;
    Session draft;

    @BeforeEach
    void setUp() {
        list = new ListFeatures(w.projects, w.sessions, w.versions, journal);
        get = new GetFeature(w.sessions, w.versions, w.tickets, new FeatureKnowledge(w.projects, index), journal);
        status = new AgentChangeStatus(w.sessions, w.clock, journal);
        ready = w.generatedSession();
        user.run(ready.id(), ImplementationStatus.READY, false);
        draft = w.generatedSession();
        w.link(ready, "PROJ-9", "d");
    }

    @Test
    void seulesLesFeaturesPretesOuEnCoursAvecUneVersion() {
        Session readyWithoutVersion = w.generatedSession();
        w.versions.delete(readyWithoutVersion.id(), 1);
        user.run(readyWithoutVersion.id(), ImplementationStatus.READY, false);
        List<ListFeatures.Feature> features = list.run("Claude Code", Project.DEFAULT_ID);
        assertThat(features).extracting(ListFeatures.Feature::id).containsExactly(ready.id());
        assertThat(features.getFirst().latestVersion()).isEqualTo(1);
        assertThat(access.entries).extracting(AgentAccess::operation).containsExactly(AgentOperation.LIST_FEATURES);
        assertThat(access.entries.getFirst().agentName()).isEqualTo("Claude Code");
    }

    @Test
    void contenuIdentiqueALExportAvecLaConnaissanceEnregistree() {
        w.projects.save(w.projects.find(Project.DEFAULT_ID).orElseThrow()
                .withContext(ProjectContext.edited("Fiche générale : les avoirs passent par Kafka.", T)));
        index.save(KnowledgeItem.adr(Project.DEFAULT_ID, ADR, 0, "ADR-005 Avoirs asynchrones", "Décision : Kafka. " + "x".repeat(800),
                AdrStatus.Statement.of(AdrStatus.ACCEPTED, null), T, T), null, null);
        // comme l'index réel, les éléments sont des entrées de l'index et les relations le modèle
        index.save(KnowledgeItem.archElement(Project.DEFAULT_ID,
                new ArchElement("Avoirs", ArchElement.Type.CONTAINER, ArchScope.PROJECT, false, "Avoirs", "Java"), T, T),
                null, null);
        index.replaceArchitecture(Project.DEFAULT_ID, new ArchitectureModel(List.of(),
                List.of(new ArchRelation("Avoirs", "Comptabilité", "publie les avoirs", "Kafka")), List.of()));
        KnowledgeSelection selection = new KnowledgeSelection("h",
                List.of(new KnowledgeSelection.Selected(KnowledgeKind.ADR, ADR, 0, "c")),
                List.of(new KnowledgeSelection.ImpactedModule("Avoirs", ArchScope.PROJECT)),
                List.of(new ArchitectureModel.Exchange("Avoirs", "Référentiel", ArchitectureModel.Exchange.Kind.MISSING, null)),
                List.of());
        SpecificationVersion v2 = new SpecificationVersion(w.ids.next(), ready.id(), 2, Specs.valid(), "fp2",
                VersionOrigin.AI_EDIT, "# Version deux\n", T, null, null, selection);
        w.versions.save(v2);

        GetFeature.FeatureContent content = get.run("Claude Code", ready.id());
        assertThat(content.version()).isEqualTo(2);
        assertThat(content.markdown()).isEqualTo(v2.markdown());
        assertThat(content.jiraIssueKey()).isEqualTo("PROJ-9");
        assertThat(content.knowledge().generalContext()).contains("Kafka");
        assertThat(content.knowledge().adrs()).singleElement().satisfies(a -> {
            assertThat(a.ref()).isEqualTo(ADR);
            assertThat(a.url()).isEqualTo(ADR);
            assertThat(a.status()).isEqualTo("ACCEPTED");
            assertThat(a.excerpt()).hasSize(600);
        });
        assertThat(content.knowledge().modules()).singleElement().satisfies(m -> {
            assertThat(m.type()).isEqualTo("CONTAINER");
            assertThat(m.scope()).isEqualTo(ArchScope.PROJECT);
            assertThat(m.relations()).extracting(FeatureKnowledge.Relation::to).containsExactly("Comptabilité", "Référentiel");
            assertThat(m.relations().get(1).description()).isEqualTo("flux manquant, à créer");
        });
        assertThat(access.entries).singleElement().satisfies(e -> {
            assertThat(e.operation()).isEqualTo(AgentOperation.GET_FEATURE);
            assertThat(e.sessionId()).isEqualTo(ready.id());
        });
    }

    @Test
    void transitionsDeLAgentConsigneesEtFeatureImplementeeIntrouvable() {
        assertThat(status.run("Cursor", ready.id(), ImplementationStatus.IN_PROGRESS).status()).isEqualTo(ImplementationStatus.IN_PROGRESS);
        assertThatThrownBy(() -> status.run("Cursor", ready.id(), ImplementationStatus.READY)).isInstanceOfSatisfying(
                DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.TRANSITION_NOT_ALLOWED));
        status.run("Cursor", ready.id(), ImplementationStatus.IMPLEMENTED);
        assertThatThrownBy(() -> status.run("Cursor", ready.id(), ImplementationStatus.IN_PROGRESS)).isInstanceOfSatisfying(
                DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.TRANSITION_NOT_ALLOWED));
        assertThatThrownBy(() -> status.run("Cursor", draft.id(), ImplementationStatus.IN_PROGRESS)).isInstanceOfSatisfying(
                DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.TRANSITION_NOT_ALLOWED));
        assertThatThrownBy(() -> get.run("Cursor", ready.id())).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
        assertThat(access.entries).extracting(AgentAccess::oldStatus, AgentAccess::newStatus).containsExactly(
                org.assertj.core.groups.Tuple.tuple(ImplementationStatus.READY, ImplementationStatus.IN_PROGRESS),
                org.assertj.core.groups.Tuple.tuple(ImplementationStatus.IN_PROGRESS, ImplementationStatus.IMPLEMENTED));
        // l'utilisateur peut rétablir le statut précédent
        assertThat(user.run(ready.id(), ImplementationStatus.IN_PROGRESS, true).status()).isEqualTo(ImplementationStatus.IN_PROGRESS);
    }

    @Test
    void nomAnnonceNettoyeEtTronque() {
        assertThat(AgentAccess.agentName(null)).isEqualTo(AgentAccess.UNKNOWN);
        assertThat(AgentAccess.agentName("  ")).isEqualTo(AgentAccess.UNKNOWN);
        assertThat(AgentAccess.agentName("a\nb\u0007c")).isEqualTo("a b c");
        assertThat(AgentAccess.agentName("x".repeat(200))).hasSize(80);
    }

    @Test
    void activationRegenerationEtPurgeAuDemarrage() {
        ManageAgentAccess manage = new ManageAgentAccess(access, access, w.clock);
        assertThatThrownBy(manage::regenerate).isInstanceOf(DomainException.class);
        manage.setEnabled(true);
        manage.regenerate();
        manage.setEnabled(false);
        assertThat(access.endpointCalls).containsExactly("publish", "regenerate", "withdraw");

        access.record(new AgentAccess(w.ids.next(), w.clock.now().minus(java.time.Duration.ofDays(91)), "vieux",
                AgentOperation.LIST_PROJECTS, null, null, null, null));
        access.record(new AgentAccess(w.ids.next(), w.clock.now().minus(java.time.Duration.ofDays(89)), "récent",
                AgentOperation.LIST_PROJECTS, null, null, null, null));
        manage.startup();
        assertThat(access.entries).extracting(AgentAccess::agentName).containsExactly("récent");
        assertThat(access.endpointCalls).hasSize(3);
        access.enabled = true;
        manage.startup();
        assertThat(access.endpointCalls).last().isEqualTo("publish");
        assertThat(manage.log(0).total()).isEqualTo(1);
    }
}
