package dev.specwriter.application.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.application.knowledge.IndexationProgress.Step;
import dev.specwriter.application.knowledge.SynchronizeKnowledge.Mode;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.jira.IssueTrackerException;
import dev.specwriter.domain.jira.TrackedIssue;
import dev.specwriter.domain.knowledge.AdrStatus;
import dev.specwriter.domain.knowledge.ArchDeployment;
import dev.specwriter.domain.knowledge.ArchElement;
import dev.specwriter.domain.knowledge.ArchRelation;
import dev.specwriter.domain.knowledge.ArchScope;
import dev.specwriter.domain.knowledge.ArchitectureModel;
import dev.specwriter.domain.knowledge.KnowledgeKind;
import dev.specwriter.domain.knowledge.KnowledgeRole;
import dev.specwriter.domain.port.ArchitectureReader;
import dev.specwriter.domain.port.DocumentationReader.Page;
import dev.specwriter.domain.port.DocumentationReader.Result;
import dev.specwriter.domain.port.DocumentationReader.Section;
import dev.specwriter.domain.port.DocumentationReader.Unread;
import dev.specwriter.domain.port.DocumentationReader.UnreadReason;
import dev.specwriter.domain.port.EmbeddingPort;
import dev.specwriter.domain.port.KnowledgeIndex.Key;
import dev.specwriter.domain.project.DocSource;
import dev.specwriter.domain.project.Project;
import dev.specwriter.support.FakeEmbedding;
import dev.specwriter.support.InMemoryKnowledgeIndex;
import dev.specwriter.support.InMemoryKnowledgeSources;
import dev.specwriter.support.JiraWorld;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** T103 : synchronisation de l'index de connaissance (research.md R20). */
class SynchronizeKnowledgeTest {

    static final UUID P = Project.DEFAULT_ID;
    static final String SITE = "https://doc.example.com/facturation/";

    final JiraWorld world = new JiraWorld();
    final InMemoryKnowledgeIndex index = new InMemoryKnowledgeIndex();
    final InMemoryKnowledgeSources sources = new InMemoryKnowledgeSources();
    final FakeEmbedding embedding = new FakeEmbedding();
    final AtomicReference<ArchitectureReader.Result> architecture = new AtomicReference<>();
    final List<String> architectureReads = new ArrayList<>();
    final ArchitectureReader architectureReader = (workspace, ownership) -> {
        architectureReads.add(workspace);
        return architecture.get();
    };
    SynchronizeKnowledge sync;
    Recorder progress;

    @TempDir
    Path tmp;

    /** Suivi qui garde les étapes et les erreurs ; interrompt au besoin. */
    static final class Recorder implements IndexationProgress {
        final List<Step> steps = new ArrayList<>();
        final List<IndexationError> errors = new ArrayList<>();
        volatile boolean cancel;

        @Override
        public void step(Step step, int total) {
            steps.add(step);
        }

        @Override
        public void advance(String current, int done, int total) {
            // affiché seulement
        }

        @Override
        public void error(IndexationError error) {
            errors.add(error);
        }

        @Override
        public boolean cancelled() {
            return cancel;
        }
    }

    static TrackedIssue issue(String key, String summary, String category, Instant updated) {
        return new TrackedIssue(key, summary, "Description de " + summary, "Statut", category, "Story", updated);
    }

    static ArchitectureReader.Result model(String hash) {
        return new ArchitectureReader.Result(new ArchitectureModel(
                List.of(new ArchElement("Facturation", ArchElement.Type.SOFTWARE_SYSTEM, ArchScope.PROJECT, false, null, null),
                        new ArchElement("Facturation/API", ArchElement.Type.CONTAINER, ArchScope.PROJECT, false,
                                "Expose la facturation", "Quarkus")),
                List.of(new ArchRelation("Facturation/API", "Comptabilite", null, null)),
                List.of(new ArchDeployment("Facturation/API", "Production", "Azure", "Azure/AKS", 1, false))),
                List.of("/c4/workspace.dsl"), hash, null);
    }

    @BeforeEach
    void setUp() throws IOException {
        world.grant(ConsentService.JIRA).configureJira();
        world.tracker.tracked.put("PROJ-7", issue("PROJ-7", "Annuler une facture", "done", Instant.parse("2026-09-12T10:00:00Z")));
        world.tracker.tracked.put("PROJ-9", issue("PROJ-9", "Émettre un avoir partiel", "new", Instant.parse("2026-10-28T08:30:00Z")));
        world.docSources.save(DocSource.create(UUID.randomUUID(), P, DocSource.Kind.URL, SITE));
        world.docs.sites.put(SITE, new Result(List.of(
                new Page(SITE + "glossaire.html", "Glossaire", "texte", List.of(
                        new Section(SITE + "glossaire.html#_avoir", 0, "Avoir", "Annule une facture.",
                                KnowledgeKind.DOC_SECTION, null, KnowledgeRole.VOCABULARY))),
                new Page(SITE + "adr-005.html", "ADR-005", "texte", List.of(
                        new Section(SITE + "adr-005.html", 0, "ADR-005", "Asynchrone.", KnowledgeKind.ADR,
                                AdrStatus.Statement.of(AdrStatus.ACCEPTED, null), null)))),
                List.of(new Unread(SITE + "recherche.html", UnreadReason.NOT_ANTORA))));
        Path workspace = Files.writeString(tmp.resolve("workspace.dsl"), "workspace {}");
        sources.save(sources.find(P).withArchitecture(workspace.toString(), "facturation"));
        architecture.set(model("h1"));
        sync = new SynchronizeKnowledge(world.projects, sources, world.docSources, world.docs, architectureReader,
                world.jira, world.tracker, index, embedding, world.clock);
        progress = new Recorder();
    }

    @Test
    void completePuisIncrementale() {
        sync.run(P, Mode.FULL, progress);
        assertThat(progress.steps).containsExactly(Step.MODEL, Step.JIRA, Step.DOCUMENTATION, Step.ARCHITECTURE,
                Step.EMBEDDING);
        assertThat(index.count(P, KnowledgeKind.TICKET)).isEqualTo(2);
        assertThat(index.find(P, new Key(KnowledgeKind.TICKET, "PROJ-7", 0))).get().satisfies(i -> {
            assertThat(i.closed()).isTrue();
            assertThat(i.body()).isEqualTo("Description de Annuler une facture");
        });
        assertThat(index.count(P, KnowledgeKind.DOC_SECTION)).isEqualTo(1);
        assertThat(index.count(P, KnowledgeKind.ADR)).isEqualTo(1);
        assertThat(index.count(P, KnowledgeKind.ARCH_ELEMENT)).isEqualTo(2);
        assertThat(index.architecture(P).relations()).hasSize(1);
        assertThat(index.withoutVector(P, embedding.modelId, 100)).isEmpty();
        assertThat(sources.find(P).jiraMark()).isEqualTo(Instant.parse("2026-10-28T08:30:00Z"));
        assertThat(sources.find(P).architectureHash()).isEqualTo("h1");
        assertThat(sources.find(P).syncedAt()).isEqualTo(world.clock.now());
        assertThat(world.tracker.calls).contains("updatedSince PROJ null 0", "keys PROJ");
        assertThat(progress.errors).isEmpty();

        world.tracker.calls.clear();
        sync.run(P, Mode.INCREMENTAL, new Recorder());
        assertThat(world.tracker.calls).containsExactly("updatedSince PROJ 2026-10-28T08:30:00Z 0");
    }

    @Test
    void seulsLesElementsModifiesSontRevectorises() {
        sync.run(P, Mode.FULL, progress);
        embedding.passages.clear();
        index.writes.clear();
        world.tracker.tracked.put("PROJ-9", new TrackedIssue("PROJ-9", "Émettre un avoir partiel",
                "Plafonné au restant dû.", "En cours", "indeterminate", "Story", Instant.parse("2026-11-01T09:00:00Z")));

        sync.run(P, Mode.INCREMENTAL, new Recorder());
        assertThat(embedding.passages).singleElement().asString().contains("Plafonné au restant dû.");
        assertThat(index.writes).filteredOn(w -> w.startsWith("save ")).containsExactly("save PROJ-9");
        // le modèle d'architecture n'a pas changé : il n'est pas réécrit
        assertThat(architectureReads).hasSize(2);
    }

    @Test
    void unStatutQuiChangeSansLeTexteEstRelu() {
        sync.run(P, Mode.FULL, progress);
        embedding.passages.clear();
        world.tracker.tracked.put("PROJ-9", new TrackedIssue("PROJ-9", "Émettre un avoir partiel",
                "Description de Émettre un avoir partiel", "Terminé", "done", "Story", Instant.parse("2026-11-01T09:00:00Z")));
        sync.run(P, Mode.INCREMENTAL, new Recorder());
        assertThat(index.find(P, new Key(KnowledgeKind.TICKET, "PROJ-9", 0))).get().extracting(i -> i.closed())
                .isEqualTo(true);
        assertThat(embedding.passages).isEmpty();
    }

    @Test
    void synchronisationCompleteRetireLesTicketsSupprimes() {
        sync.run(P, Mode.FULL, progress);
        world.tracker.tracked.remove("PROJ-7");
        sync.run(P, Mode.INCREMENTAL, new Recorder());
        assertThat(index.count(P, KnowledgeKind.TICKET)).isEqualTo(2);
        sync.run(P, Mode.FULL, new Recorder());
        assertThat(index.find(P, new Key(KnowledgeKind.TICKET, "PROJ-7", 0))).isEmpty();
    }

    @Test
    void interruptionGardeCeQuiEstIndexe() {
        world.docs.onPage = () -> progress.cancel = true;
        assertThatThrownBy(() -> sync.run(P, Mode.FULL, progress)).isInstanceOf(IndexationProgress.Cancelled.class);
        assertThat(index.count(P, KnowledgeKind.TICKET)).isEqualTo(2);
        assertThat(index.count(P, KnowledgeKind.ARCH_ELEMENT)).isZero();
    }

    @Test
    void erreursSansContenuEtEtapesSuivantesMenees() {
        world.tracker.failure = IssueTrackerException.Kind.UNREACHABLE;
        world.docs.sites.put(SITE, new Result(List.of(new Page(SITE + "a.html", "A", "t", List.of(
                new Section(SITE + "a.html#_a", 0, "A", "Secret de page", KnowledgeKind.DOC_SECTION, null, null)))),
                List.of(new Unread(SITE + "b.html", UnreadReason.HTTP_ERROR, 500))));
        architecture.set(new ArchitectureReader.Result(ArchitectureModel.EMPTY, List.of(), null,
                new ArchitectureReader.Problem("/c4/workspace.dsl", 4, "Include introuvable")));
        embedding.prepared = EmbeddingPort.State.TERMS_ONLY;

        sync.run(P, Mode.FULL, progress);
        assertThat(progress.errors).extracting(IndexationError::kind).containsExactly(IndexationError.Kind.MODEL,
                IndexationError.Kind.JIRA, IndexationError.Kind.PAGE, IndexationError.Kind.ARCHITECTURE);
        assertThat(progress.errors).filteredOn(e -> e.kind() == IndexationError.Kind.PAGE).singleElement()
                .satisfies(e -> {
                    assertThat(e.location()).isEqualTo(SITE + "b.html");
                    assertThat(e.httpStatus()).isEqualTo(500);
                });
        assertThat(progress.errors).filteredOn(e -> e.kind() == IndexationError.Kind.ARCHITECTURE).singleElement()
                .extracting(IndexationError::location).isEqualTo("/c4/workspace.dsl:4");
        assertThat(progress.errors).allSatisfy(e -> assertThat(String.valueOf(e.message())).doesNotContain("Secret"));
        // la documentation est indexée malgré Jira ; pas d'étape de vecteurs sans modèle
        assertThat(index.count(P, KnowledgeKind.DOC_SECTION)).isEqualTo(1);
        assertThat(progress.steps).doesNotContain(Step.EMBEDDING);
    }

    @Test
    void workspaceEnErreurGardeLeModelePrecedent() {
        sync.run(P, Mode.FULL, progress);
        architecture.set(new ArchitectureReader.Result(ArchitectureModel.EMPTY, List.of(), null,
                new ArchitectureReader.Problem("/c4/workspace.dsl", 12, "Erreur")));
        sync.run(P, Mode.FULL, new Recorder());
        assertThat(index.count(P, KnowledgeKind.ARCH_ELEMENT)).isEqualTo(2);
        assertThat(index.architecture(P).deployments()).hasSize(1);
        assertThat(sources.find(P).architectureHash()).isEqualTo("h1");
    }

    @Test
    void sourceIllisibleNeRetireRien() {
        sync.run(P, Mode.FULL, progress);
        world.docs.sites.remove(SITE);
        Recorder second = new Recorder();
        sync.run(P, Mode.FULL, second);
        assertThat(second.errors).extracting(IndexationError::kind).contains(IndexationError.Kind.SOURCE);
        assertThat(index.count(P, KnowledgeKind.DOC_SECTION)).isEqualTo(1);
        assertThat(index.count(P, KnowledgeKind.ADR)).isEqualTo(1);
    }

    @Test
    void changementDeModeleRevectoriseTout() {
        sync.run(P, Mode.FULL, progress);
        embedding.passages.clear();
        embedding.modelId = "autre@2";
        sync.run(P, Mode.INCREMENTAL, new Recorder());
        // 2 tickets, 1 section, 1 ADR, 2 éléments d'architecture
        assertThat(embedding.passages).hasSize(6);
    }

    @Test
    void sansJiraNiWorkspaceLaDocumentationSuffit() {
        world.settings.deleteJira();
        sources.save(sources.find(P).withArchitecture(null, null));
        sync.run(P, Mode.FULL, progress);
        assertThat(progress.steps).containsExactly(Step.MODEL, Step.DOCUMENTATION, Step.EMBEDDING);
        assertThat(index.count(P, KnowledgeKind.TICKET)).isZero();
    }

    // ---------- Indexations et déclenchements ----------

    /** Exécuteur manuel : les tâches attendent qu'on les lance. */
    static final class ManualExecutor implements java.util.concurrent.Executor {
        final List<Runnable> tasks = new ArrayList<>();

        @Override
        public void execute(Runnable task) {
            tasks.add(task);
        }

        void runAll() {
            List<Runnable> pending = new ArrayList<>(tasks);
            tasks.clear();
            pending.forEach(Runnable::run);
        }
    }

    @Test
    void uneIndexationParProjet() {
        ManualExecutor executor = new ManualExecutor();
        Indexations indexations = new Indexations(sync, world.projects, executor, world.clock);
        Indexations.Snapshot started = indexations.start(P, Mode.FULL);
        assertThat(started.status()).isEqualTo(Indexations.Status.RUNNING);
        assertThatThrownBy(() -> indexations.start(P, Mode.FULL)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code().name()).isEqualTo("OPERATION_IN_PROGRESS"));
        // un déclenchement automatique pendant une indexation est sans effet
        indexations.trigger(P, Mode.INCREMENTAL);
        assertThat(executor.tasks).hasSize(1);

        executor.runAll();
        assertThat(indexations.current(P)).get().satisfies(s -> {
            assertThat(s.status()).isEqualTo(Indexations.Status.SUCCEEDED);
            assertThat(s.mode()).isEqualTo(Mode.FULL);
            assertThat(s.finishedAt()).isEqualTo(world.clock.now());
        });
        indexations.trigger(P, Mode.INCREMENTAL);
        assertThat(executor.tasks).hasSize(1);
        world.clock.advance(Duration.ofMinutes(31));
        executor.runAll();
        assertThat(indexations.current(P)).get().extracting(Indexations.Snapshot::mode).isEqualTo(Mode.INCREMENTAL);
    }

    @Test
    void interruptionImmediate() {
        ManualExecutor executor = new ManualExecutor();
        Indexations indexations = new Indexations(sync, world.projects, executor, world.clock);
        indexations.start(P, Mode.FULL);
        indexations.cancel(P);
        assertThat(indexations.current(P)).get().extracting(Indexations.Snapshot::status)
                .isEqualTo(Indexations.Status.CANCELLED);
        executor.runAll();
        assertThat(index.count(P, KnowledgeKind.TICKET)).isZero();
        // une nouvelle indexation peut partir aussitôt
        assertThat(indexations.start(P, Mode.FULL).status()).isEqualTo(Indexations.Status.RUNNING);
    }

    @Test
    void enregistrerLeWorkspaceLanceUneIndexationComplete() throws IOException {
        ManualExecutor executor = new ManualExecutor();
        Indexations indexations = new Indexations(sync, world.projects, executor, world.clock);
        ManageArchitectureSource manage = new ManageArchitectureSource(world.projects, sources, index, indexations);
        Path other = Files.writeString(tmp.resolve("autre.dsl"), "workspace {}");
        manage.set(P, other.toString(), "facturation");
        assertThat(indexations.current(P)).get().extracting(Indexations.Snapshot::mode).isEqualTo(Mode.FULL);
        assertThat(sources.find(P).workspace()).isEqualTo(other.toString());
    }

    @Test
    void workspaceRefuseAvecUnMotifPrecis() throws IOException {
        ManageArchitectureSource manage = new ManageArchitectureSource(world.projects, sources, index,
                new Indexations(sync, world.projects, new ManualExecutor(), world.clock));
        assertThatThrownBy(() -> manage.set(P, "relatif/workspace.dsl", null)).isInstanceOfSatisfying(
                DomainException.class, e -> assertThat(e.userMessage()).contains("chemin absolu"));
        assertThatThrownBy(() -> manage.set(P, tmp.resolve("absent.dsl").toString(), null)).isInstanceOfSatisfying(
                DomainException.class, e -> assertThat(e.userMessage()).contains("n'existe pas"));
        assertThatThrownBy(() -> manage.set(P, tmp.toString(), null)).isInstanceOfSatisfying(
                DomainException.class, e -> assertThat(e.userMessage()).contains("dossier"));
        assertThatThrownBy(() -> manage.set(P, "/" + "a".repeat(2_000), null)).isInstanceOfSatisfying(
                DomainException.class, e -> assertThat(e.userMessage()).contains("2 000 caractères"));
    }

    @Test
    void retirerLeWorkspaceVideLArchitecture() {
        sync.run(P, Mode.FULL, progress);
        ManageArchitectureSource manage = new ManageArchitectureSource(world.projects, sources, index,
                new Indexations(sync, world.projects, new ManualExecutor(), world.clock));
        manage.clear(P);
        assertThat(index.count(P, KnowledgeKind.ARCH_ELEMENT)).isZero();
        assertThat(index.architecture(P).relations()).isEmpty();
        assertThat(sources.find(P).workspace()).isNull();
    }
}
