package fr.kawamind.application.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.kawamind.application.session.ChangeStatus;
import fr.kawamind.domain.agent.AgentAccess;
import fr.kawamind.domain.agent.AgentOperation;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.knowledge.AdrStatus;
import fr.kawamind.domain.knowledge.ArchElement;
import fr.kawamind.domain.knowledge.ArchRelation;
import fr.kawamind.domain.knowledge.ArchScope;
import fr.kawamind.domain.knowledge.ArchitectureModel;
import fr.kawamind.domain.knowledge.KnowledgeItem;
import fr.kawamind.domain.knowledge.KnowledgeKind;
import fr.kawamind.domain.knowledge.KnowledgeSelection;
import fr.kawamind.domain.project.Project;
import fr.kawamind.domain.project.ProjectContext;
import fr.kawamind.domain.session.ImplementationStatus;
import fr.kawamind.domain.session.Session;
import fr.kawamind.domain.specification.SpecificationVersion;
import fr.kawamind.domain.specification.Specs;
import fr.kawamind.domain.specification.VersionOrigin;
import fr.kawamind.support.InMemoryAgentAccess;
import fr.kawamind.support.InMemoryKnowledgeIndex;
import fr.kawamind.support.JiraWorld;
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

    /** 022 (US2, research R5) : type et parent du ticket lié à la feature, lus dans l'index. */
    @Test
    void leTicketDeLaFeatureDitSonTypeEtSonParent() {
        fr.kawamind.support.InMemoryTicketLinks links = new fr.kawamind.support.InMemoryTicketLinks();
        GetFeature indexed = new GetFeature(w.sessions, w.versions, w.tickets, new FeatureKnowledge(w.projects, index),
                journal, null, index, links);
        // hors de l'index : la clé seule
        GetFeature.JiraTicket unknown = indexed.run("Claude Code", ready.id()).jiraTicket();
        assertThat(unknown).isEqualTo(new GetFeature.JiraTicket("PROJ-9", null, null));

        index.save(KnowledgeItem.ticket(ready.projectId(), "PROJ-9", "Accès Halo", "", "À faire", "new",
                "Sous-Tâche (AGILE)", T, T), null, null);
        links.replace(ready.projectId(), "PROJ-9", List.of(fr.kawamind.domain.jira.TicketLink.parent("PROJ-1"),
                fr.kawamind.domain.jira.TicketLink.link("Lier", fr.kawamind.domain.jira.TicketLink.Direction.OUTWARD,
                        "lie", "PROJ-4")));
        GetFeature.FeatureContent content = indexed.run("Claude Code", ready.id());
        assertThat(content.jiraTicket()).isEqualTo(new GetFeature.JiraTicket("PROJ-9", "Sous-Tâche (AGILE)", "PROJ-1"));
        assertThat(content.jiraIssueKey()).isEqualTo("PROJ-9");

        // une story : pas de parent
        links.replace(ready.projectId(), "PROJ-9", List.of());
        assertThat(indexed.run("Claude Code", ready.id()).jiraTicket().parentKey()).isNull();

        // sans ticket lié : pas de ticket
        Session alone = w.generatedSession();
        user.run(alone.id(), ImplementationStatus.READY, false);
        assertThat(indexed.run("Claude Code", alone.id()).jiraTicket()).isNull();
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

    @Test
    void uneFeatureSansTachesImplementeeFaitAvancerSaStory() {
        // 023 (US4, FR-016) : sans tâches, la feature déclarée implémentée passe sa story au statut d'arrivée
        fr.kawamind.support.TaskWorld tw = new fr.kawamind.support.TaskWorld();
        AgentJournal j = new AgentJournal(access, tw.clock, tw.ids);
        AgentChangeStatus follow = new AgentChangeStatus(tw.sessions, tw.clock, j, tw.tasks,
                new fr.kawamind.application.task.StoryFollow(tw.desk, new fr.kawamind.application.task.TaskJira(tw.tracker)));
        Session s = tw.linkedSession();
        new ChangeStatus(tw.sessions, tw.clock).run(s.id(), ImplementationStatus.READY, false);
        tw.tracker.tracked.put("FACT-120", new fr.kawamind.domain.jira.TrackedIssue("FACT-120", "Story", "", "En Cours",
                "indeterminate", "Story", T, List.of()));
        tw.tracker.workflows.put("FACT-120", List.of(
                new fr.kawamind.domain.jira.Transition("31", "Réaliser", "indeterminate", "Réalisée"),
                new fr.kawamind.domain.jira.Transition("61", "Fermer", "done", "Fermée")));

        // sans réglage : la story n'est pas touchée
        assertThat(follow.run("Claude Code", s.id(), ImplementationStatus.IN_PROGRESS).story()).isNull();
        AgentChangeStatus.Changed skipped = follow.run("Claude Code", s.id(), ImplementationStatus.IMPLEMENTED);
        assertThat(skipped.story().status()).isEqualTo(fr.kawamind.application.task.StoryMove.Outcome.SKIPPED);
        assertThat(tw.tracker.calls).noneMatch(c -> c.startsWith("transition FACT-120"));

        // avec réglage : la story passe à « Réalisée »
        Session other = tw.linkedSession();
        new ChangeStatus(tw.sessions, tw.clock).run(other.id(), ImplementationStatus.READY, false);
        tw.workflows.save(other.projectId(),
                fr.kawamind.domain.task.TaskWorkflow.of("Réalisée", List.of("En cours de test")), tw.clock.now());
        follow.run("Claude Code", other.id(), ImplementationStatus.IN_PROGRESS);
        AgentChangeStatus.Changed moved = follow.run("Claude Code", other.id(), ImplementationStatus.IMPLEMENTED);
        assertThat(moved.story()).isEqualTo(new fr.kawamind.application.task.StoryMove("FACT-120",
                fr.kawamind.application.task.StoryMove.Outcome.MOVED, "Réalisée", null));
        assertThat(tw.tracker.tracked.get("FACT-120").status()).isEqualTo("Réalisée");
    }
}
