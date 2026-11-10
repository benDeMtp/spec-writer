package dev.specwriter.application.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.application.knowledge.IndexationProgress.Step;
import dev.specwriter.application.knowledge.SynchronizeKnowledge.Mode;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.jira.IssueTrackerException;
import dev.specwriter.domain.jira.TicketLink;
import dev.specwriter.domain.jira.TrackedIssue;
import dev.specwriter.domain.knowledge.AdrStatus;
import dev.specwriter.domain.knowledge.ArchDeployment;
import dev.specwriter.domain.knowledge.ArchElement;
import dev.specwriter.domain.knowledge.ArchRelation;
import dev.specwriter.domain.knowledge.ArchScope;
import dev.specwriter.domain.knowledge.ArchitectureModel;
import dev.specwriter.domain.knowledge.KnowledgeItem;
import dev.specwriter.domain.knowledge.KnowledgeKind;
import dev.specwriter.domain.knowledge.KnowledgeRole;
import dev.specwriter.domain.port.ArchitectureReader;
import dev.specwriter.domain.port.DocumentationReader.Page;
import dev.specwriter.domain.port.DocumentationReader.Result;
import dev.specwriter.domain.port.DocumentationReader.Section;
import dev.specwriter.domain.port.DocumentationReader.Unread;
import dev.specwriter.domain.port.DocumentationReader.UnreadReason;
import dev.specwriter.domain.port.DocumentFolder;
import dev.specwriter.domain.port.EmbeddingPort;
import dev.specwriter.domain.port.KnowledgeIndex.Key;
import dev.specwriter.domain.project.DocSource;
import dev.specwriter.domain.project.ExclusionPatterns;
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
    final AtomicReference<RuntimeException> architectureFailure = new AtomicReference<>();
    final ArchitectureReader architectureReader = (workspace, ownership) -> {
        architectureReads.add(workspace);
        if (architectureFailure.get() != null) {
            throw architectureFailure.get();
        }
        return architecture.get();
    };
    SynchronizeKnowledge sync;
    Recorder progress;

    @TempDir
    Path tmp;

    /** Suivi qui garde les étapes et les erreurs ; interrompt au besoin. */
    static class Recorder implements IndexationProgress {
        final List<Step> steps = new ArrayList<>();
        final List<IndexationError> errors = new ArrayList<>();
        TicketSummary tickets;
        volatile boolean cancel;

        @Override
        public void tickets(TicketSummary summary) {
            tickets = summary;
        }

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
        sync = new SynchronizeKnowledge(world.projects, sources, world.docSources, world.docs, world.folder, world.fileStates, architectureReader,
                world.jira, world.tracker, world.ticketLinks, index, embedding, world.clock);
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

    /**
     * Un workspace déclaré, changé ou retiré pendant la synchronisation n'est pas écrasé par la source
     * lue au départ (vu en E2E, T117) ; l'empreinte lue ne s'applique qu'au workspace lu.
     */
    @Test
    void workspaceDeclarePendantLaSynchronisationGarde() {
        sources.save(sources.find(P).withArchitecture(null, null));
        Recorder declaring = new Recorder() {
            @Override
            public void step(Step step, int total) {
                super.step(step, total);
                if (step == Step.DOCUMENTATION) {
                    sources.save(sources.find(P).withArchitecture("/c4/nouveau.dsl", "facturation"));
                }
            }
        };
        sync.run(P, Mode.FULL, declaring);
        assertThat(sources.find(P)).satisfies(s -> {
            assertThat(s.workspace()).isEqualTo("/c4/nouveau.dsl");
            assertThat(s.architectureHash()).isNull();
            assertThat(s.syncedAt()).isEqualTo(world.clock.now());
        });

        Recorder changing = new Recorder() {
            @Override
            public void step(Step step, int total) {
                super.step(step, total);
                if (step == Step.EMBEDDING) {
                    sources.save(sources.find(P).withArchitecture("/c4/autre.dsl", null));
                }
            }
        };
        sync.run(P, Mode.FULL, changing);
        assertThat(sources.find(P).workspace()).isEqualTo("/c4/autre.dsl");
        assertThat(sources.find(P).architectureHash()).isNull();

        sync.run(P, Mode.FULL, new Recorder());
        assertThat(sources.find(P).architectureHash()).isEqualTo("h1");
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

    /** Un !script ignoré est un avertissement : le modèle est lu, l'index n'est pas partiel. */
    @Test
    void scriptIgnoreEnAvertissement() {
        ArchitectureReader.Result read = model("h1");
        architecture.set(new ArchitectureReader.Result(read.model(), read.files(), read.hash(), null,
                List.of(new ArchitectureReader.Problem("/c4/workspace.dsl", 829, "!script ignoré"))));
        sync.run(P, Mode.FULL, progress);
        assertThat(progress.errors).singleElement().satisfies(e -> {
            assertThat(e.kind()).isEqualTo(IndexationError.Kind.WARNING);
            assertThat(e.warning()).isTrue();
            assertThat(e.location()).isEqualTo("/c4/workspace.dsl:829");
            assertThat(e.reason()).isEqualTo("SCRIPT_IGNORED");
        });
        assertThat(index.architecture(P).elements()).isNotEmpty();
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

    /** 003, FR-009 : l'état de l'indexation garde le bilan de l'étape Jira, absent sans étape Jira. */
    @Test
    void bilanDesTicketsDansLEtatDeLIndexation() {
        ManualExecutor executor = new ManualExecutor();
        Indexations indexations = new Indexations(sync, world.projects, executor, world.clock);
        indexations.start(P, Mode.FULL);
        executor.runAll();
        assertThat(indexations.current(P)).get().extracting(Indexations.Snapshot::tickets)
                .isEqualTo(new TicketSummary(2, 0, 0));

        world.clock.advance(Duration.ofMinutes(31));
        world.consents.record(new dev.specwriter.domain.consent.ConsentRecord(ConsentService.JIRA, false, Instant.EPOCH));
        indexations.start(P, Mode.INCREMENTAL);
        executor.runAll();
        assertThat(indexations.current(P)).get().extracting(Indexations.Snapshot::tickets).isNull();
    }

    /**
     * Une source enregistrée pendant une indexation (déclenchement complet) est lue juste après, sans
     * nouvelle action ; l'ouverture d'un besoin (incrémentale) n'ajoute rien ; une interruption annule
     * la suite (vu en E2E, T117 : le workspace déclaré après la documentation n'était pas indexé).
     */
    @Test
    void sourceEnregistreePendantUneIndexationLueEnSuite() {
        ManualExecutor executor = new ManualExecutor();
        Indexations indexations = new Indexations(sync, world.projects, executor, world.clock);
        indexations.trigger(P, Mode.INCREMENTAL);
        indexations.trigger(P, Mode.INCREMENTAL);
        indexations.trigger(P, Mode.FULL);
        assertThat(executor.tasks).hasSize(1);

        executor.runAll();
        assertThat(executor.tasks).hasSize(1);
        assertThat(indexations.current(P)).get().satisfies(s -> {
            assertThat(s.status()).isEqualTo(Indexations.Status.RUNNING);
            assertThat(s.mode()).isEqualTo(Mode.FULL);
        });
        executor.runAll();
        assertThat(executor.tasks).isEmpty();
        assertThat(indexations.current(P)).get().extracting(Indexations.Snapshot::status)
                .isEqualTo(Indexations.Status.SUCCEEDED);

        indexations.trigger(P, Mode.INCREMENTAL);
        indexations.trigger(P, Mode.FULL);
        indexations.cancel(P);
        executor.runAll();
        assertThat(executor.tasks).isEmpty();
        assertThat(indexations.current(P)).get().extracting(Indexations.Snapshot::status)
                .isEqualTo(Indexations.Status.CANCELLED);
    }

    /** T123 : liens réécrits à chaque relecture, même sans changement du texte, et retirés avec le ticket. */
    @Test
    void liensDesTicketsReecritsEtRetiresAvecLeTicket() {
        world.tracker.tracked.put("PROJ-7", new TrackedIssue("PROJ-7", "Annuler une facture",
                "Description de Annuler une facture", "Statut", "done", "Story", Instant.parse("2026-09-12T10:00:00Z"),
                List.of(TicketLink.subtask("PROJ-8"))));
        sync.run(P, Mode.FULL, progress);
        assertThat(world.ticketLinks.of(P, "PROJ-7")).containsExactly(TicketLink.subtask("PROJ-8"));

        // même texte, nouveau lien : le lien est réécrit sans que le ticket change dans l'index
        String hash = index.hashes(P, KnowledgeKind.TICKET).get(new Key(KnowledgeKind.TICKET, "PROJ-7", 0));
        TicketLink blocks = TicketLink.link("Blocks", TicketLink.Direction.OUTWARD, "blocks", "PROJ-9");
        world.tracker.tracked.put("PROJ-7", new TrackedIssue("PROJ-7", "Annuler une facture",
                "Description de Annuler une facture", "Statut", "done", "Story", Instant.parse("2026-11-05T10:00:00Z"),
                List.of(TicketLink.subtask("PROJ-8"), blocks)));
        sync.run(P, Mode.INCREMENTAL, new Recorder());
        assertThat(world.ticketLinks.of(P, "PROJ-7")).containsExactly(TicketLink.subtask("PROJ-8"), blocks);
        assertThat(index.hashes(P, KnowledgeKind.TICKET).get(new Key(KnowledgeKind.TICKET, "PROJ-7", 0))).isEqualTo(hash);

        // ticket supprimé de Jira : ses liens partent avec lui à la synchronisation complète
        world.tracker.tracked.remove("PROJ-7");
        sync.run(P, Mode.FULL, new Recorder());
        assertThat(world.ticketLinks.of(P, "PROJ-7")).isEmpty();
    }

    // ---------- 005, US1 : une étape en échec n'arrête pas les autres ----------

    static void unexpected(List<IndexationError> errors, IndexationError.Kind kind, String location) {
        assertThat(errors).filteredOn(e -> "UNEXPECTED".equals(e.reason())).singleElement().satisfies(e -> {
            assertThat(e.kind()).isEqualTo(kind);
            assertThat(e.location()).isEqualTo(location);
            assertThat(e.message()).startsWith("Erreur inattendue (");
        });
    }

    /** Erreur inattendue à Jira : documentation et architecture indexées, repère Jira inchangé. */
    @Test
    void jiraEnErreurInattendueNArretePasLesAutresEtapes() {
        sync.run(P, Mode.FULL, progress);
        Instant mark = sources.find(P).jiraMark();
        index.retain(P, KnowledgeKind.DOC_SECTION, java.util.Set.of());
        index.retain(P, KnowledgeKind.ARCH_ELEMENT, java.util.Set.of());
        world.tracker.unexpected = () -> new IllegalStateException("panne");
        world.clock.advance(Duration.ofHours(1));
        Recorder second = new Recorder();

        sync.run(P, Mode.FULL, second);

        unexpected(second.errors, IndexationError.Kind.JIRA, "PROJ");
        assertThat(second.steps).contains(Step.DOCUMENTATION, Step.ARCHITECTURE);
        assertThat(index.count(P, KnowledgeKind.DOC_SECTION)).isEqualTo(1);
        assertThat(index.count(P, KnowledgeKind.ARCH_ELEMENT)).isEqualTo(2);
        assertThat(index.count(P, KnowledgeKind.TICKET)).isEqualTo(2);
        assertThat(sources.find(P).jiraMark()).isEqualTo(mark);
    }

    /** Bibliothèque manquante (essai de la 003, {@code NoSuchMethodError}) : même traitement. */
    @Test
    void bibliothequeManquanteAJiraNArretePasLesAutresEtapes() {
        world.tracker.unexpected = () -> new NoSuchMethodError("searchLenient");

        sync.run(P, Mode.FULL, progress);

        unexpected(progress.errors, IndexationError.Kind.JIRA, "PROJ");
        assertThat(index.count(P, KnowledgeKind.ADR)).isEqualTo(1);
        assertThat(index.count(P, KnowledgeKind.ARCH_ELEMENT)).isEqualTo(2);
        assertThat(sources.find(P).jiraMark()).isNull();
    }

    /** Une source de documentation en erreur : l'autre est lue, rien n'est retiré. */
    @Test
    void sourceDeDocumentationEnErreurNArretePasLesAutres() {
        String other = "https://doc.example.com/clients/";
        world.docSources.save(DocSource.create(UUID.randomUUID(), P, DocSource.Kind.URL, other));
        world.docs.sites.put(other, new Result(List.of(new Page(other + "client.html", "Client", "texte", List.of(
                new Section(other + "client.html", 0, "Client", "Un client.", KnowledgeKind.DOC_SECTION, null, null)))),
                List.of()));
        sync.run(P, Mode.FULL, progress);
        assertThat(index.count(P, KnowledgeKind.DOC_SECTION)).isEqualTo(2);
        world.docs.sites.put(other, new Result(List.of(new Page(other + "client.html", "Client", "texte", List.of(
                new Section(other + "client.html", 0, "Client", "Un client, modifié.", KnowledgeKind.DOC_SECTION,
                        null, null)))), List.of()));
        world.docs.broken.put(SITE, new IllegalStateException("panne"));
        Recorder second = new Recorder();

        sync.run(P, Mode.FULL, second);

        unexpected(second.errors, IndexationError.Kind.SOURCE, SITE);
        assertThat(index.count(P, KnowledgeKind.DOC_SECTION)).isEqualTo(2);
        assertThat(index.count(P, KnowledgeKind.ADR)).isEqualTo(1);
        assertThat(index.all(P, KnowledgeKind.DOC_SECTION)).extracting(i -> i.body()).contains("Un client, modifié.");
        assertThat(second.steps).contains(Step.ARCHITECTURE);
    }

    /** Erreur inattendue à l'architecture : Jira et documentation indexés, empreinte inchangée. */
    @Test
    void architectureEnErreurInattendueGardeSonEmpreinte() {
        sync.run(P, Mode.FULL, progress);
        architectureFailure.set(new IllegalArgumentException("panne"));
        world.tracker.tracked.put("PROJ-10", issue("PROJ-10", "Relancer un client", "new",
                Instant.parse("2026-11-01T08:00:00Z")));
        Recorder second = new Recorder();

        sync.run(P, Mode.FULL, second);

        unexpected(second.errors, IndexationError.Kind.ARCHITECTURE, sources.find(P).workspace());
        assertThat(index.count(P, KnowledgeKind.TICKET)).isEqualTo(3);
        assertThat(sources.find(P).architectureHash()).isEqualTo("h1");
        assertThat(index.count(P, KnowledgeKind.ARCH_ELEMENT)).isEqualTo(2);
    }

    /** Erreur inattendue au calcul des vecteurs : signalée, les sources restent indexées. */
    @Test
    void vecteursEnErreurInattendueSignales() {
        embedding.passagesFailure = new IllegalStateException("panne");

        sync.run(P, Mode.FULL, progress);

        unexpected(progress.errors, IndexationError.Kind.MODEL, null);
        assertThat(index.count(P, KnowledgeKind.TICKET)).isEqualTo(2);
        assertThat(sources.find(P).syncedAt()).isNotNull();
    }

    /** Une annulation demandée pendant une étape n'est pas une erreur d'étape : elle remonte. */
    @Test
    void annulationPendantUneEtapeRemonte() {
        world.tracker.unexpected = () -> {
            progress.cancel = true;
            return new IllegalStateException("appel coupé");
        };

        assertThatThrownBy(() -> sync.run(P, Mode.FULL, progress)).isInstanceOf(RuntimeException.class);
        assertThat(progress.errors).noneMatch(e -> "UNEXPECTED".equals(e.reason()));
        assertThat(progress.steps).doesNotContain(Step.DOCUMENTATION);
    }

    /** L'indexation se termine « avec des erreurs », pas en échec (FR-002). */
    @Test
    void etapeEnErreurTermineAvecDesErreurs() {
        ManualExecutor executor = new ManualExecutor();
        Indexations indexations = new Indexations(sync, world.projects, executor, world.clock);
        world.tracker.unexpected = () -> new IllegalStateException("panne");
        indexations.start(P, Mode.FULL);
        executor.runAll();
        assertThat(indexations.current(P)).get().satisfies(s -> {
            assertThat(s.status()).isEqualTo(Indexations.Status.SUCCEEDED);
            unexpected(s.errors(), IndexationError.Kind.JIRA, "PROJ");
        });
    }

    // ---------- 005, US2 : réindexer seulement les sources choisies ----------

    String second() {
        String other = "https://doc.example.com/clients/";
        world.docSources.save(DocSource.create(UUID.randomUUID(), P, DocSource.Kind.URL, other));
        world.docs.sites.put(other, new Result(List.of(new Page(other + "client.html", "Client", "texte", List.of(
                new Section(other + "client.html", 0, "Client", "Un client.", KnowledgeKind.DOC_SECTION, null, null)))),
                List.of()));
        return other;
    }

    DocSource declared(String location) {
        return world.docSources.list(P).stream().filter(d -> d.location().equals(location)).findFirst().orElseThrow();
    }

    /** Une source de documentation choisie : seule elle est lue ; ni Jira ni l'architecture. */
    @Test
    void seuleLaSourceChoisieEstLue() {
        String other = second();
        sync.run(P, Mode.FULL, progress);
        Instant synced = sources.find(P).syncedAt();
        world.docs.reads.clear();
        world.tracker.calls.clear();
        architectureReads.clear();
        world.clock.advance(Duration.ofHours(1));
        Recorder chosen = new Recorder();

        sync.run(P, Mode.FULL, IndexationScope.of(List.of(declared(other).id().toString()), world.docSources.list(P)),
                chosen);

        assertThat(world.docs.reads).containsExactly(other);
        assertThat(world.tracker.calls).isEmpty();
        assertThat(architectureReads).isEmpty();
        assertThat(chosen.steps).doesNotContain(Step.JIRA, Step.ARCHITECTURE);
        assertThat(chosen.tickets).isNull();
        assertThat(sources.find(P).syncedAt()).isEqualTo(synced);
    }

    /** Page retirée de la source choisie : ses éléments partent, ceux des autres sources restent. */
    @Test
    void sourceChoisieRetireSesPagesDisparues() {
        String other = second();
        sync.run(P, Mode.FULL, progress);
        world.docs.sites.put(SITE, new Result(List.of(new Page(SITE + "adr-005.html", "ADR-005", "texte", List.of(
                new Section(SITE + "adr-005.html", 0, "ADR-005", "Asynchrone.", KnowledgeKind.ADR,
                        AdrStatus.Statement.of(AdrStatus.ACCEPTED, null), null)))), List.of()));

        sync.run(P, Mode.FULL, IndexationScope.of(List.of(declared(SITE).id().toString()), world.docSources.list(P)),
                new Recorder());

        assertThat(index.all(P, KnowledgeKind.DOC_SECTION)).extracting(KnowledgeItem::ref)
                .containsExactly(other + "client.html");
        assertThat(index.count(P, KnowledgeKind.ADR)).isEqualTo(1);
    }

    /**
     * ADR renommé dans la documentation (même numéro, nouvelle adresse) : la source lue en entier
     * remplace ses éléments, même si une autre source est en panne ; plus de doublon.
     */
    @Test
    void adrRenommeRemplaceLAncienMemeSiUneAutreSourceEchoue() {
        String other = second();
        sync.run(P, Mode.FULL, progress);
        world.docs.sites.put(SITE, new Result(List.of(new Page(SITE + "adr-005-avoirs.html", "ADR-005", "texte",
                List.of(new Section(SITE + "adr-005-avoirs.html", 0, "ADR-005 Avoirs asynchrones", "Asynchrone.",
                        KnowledgeKind.ADR, AdrStatus.Statement.of(AdrStatus.ACCEPTED, null), null)))), List.of()));
        world.docs.broken.put(other, new IllegalStateException("panne"));

        sync.run(P, Mode.FULL, new Recorder());

        assertThat(index.all(P, KnowledgeKind.ADR)).extracting(KnowledgeItem::ref)
                .containsExactly(SITE + "adr-005-avoirs.html");
        // la source en panne garde son indexation précédente
        assertThat(index.all(P, KnowledgeKind.DOC_SECTION)).extracting(KnowledgeItem::ref)
                .containsExactly(other + "client.html");
    }

    /** Architecture choisie : réanalysée même si son empreinte n'a pas changé. */
    @Test
    void architectureChoisieReanalysee() {
        sync.run(P, Mode.FULL, progress);
        Instant synced = sources.find(P).syncedAt();
        index.retain(P, KnowledgeKind.ARCH_ELEMENT, java.util.Set.of());
        world.docs.reads.clear();
        world.clock.advance(Duration.ofHours(1));

        sync.run(P, Mode.FULL, IndexationScope.of(List.of(IndexationScope.ARCHITECTURE), world.docSources.list(P)),
                new Recorder());

        assertThat(index.count(P, KnowledgeKind.ARCH_ELEMENT)).isEqualTo(2);
        assertThat(architectureReads).hasSize(2);
        assertThat(world.docs.reads).isEmpty();
        assertThat(sources.find(P).architectureHash()).isEqualTo("h1");
        assertThat(sources.find(P).syncedAt()).isEqualTo(synced);
    }

    /** Jira choisi : relu selon les règles de la 003, repère avancé, date de synchronisation inchangée. */
    @Test
    void jiraChoisiAvanceSonRepere() {
        sync.run(P, Mode.FULL, progress);
        Instant synced = sources.find(P).syncedAt();
        world.tracker.tracked.put("PROJ-10", issue("PROJ-10", "Relancer un client", "new",
                Instant.parse("2026-11-01T08:00:00Z")));
        world.docs.reads.clear();
        architectureReads.clear();
        world.clock.advance(Duration.ofHours(1));
        Recorder chosen = new Recorder();

        sync.run(P, Mode.FULL, IndexationScope.of(List.of(IndexationScope.JIRA), world.docSources.list(P)), chosen);

        assertThat(index.count(P, KnowledgeKind.TICKET)).isEqualTo(3);
        assertThat(chosen.tickets.keptClosed()).isEqualTo(1);
        assertThat(sources.find(P).jiraMark()).isEqualTo(Instant.parse("2026-11-01T08:00:00Z"));
        assertThat(sources.find(P).syncedAt()).isEqualTo(synced);
        assertThat(world.docs.reads).isEmpty();
        assertThat(architectureReads).isEmpty();
    }

    /** Sources proposées au choix : seules celles qui seraient lues (research.md R5). */
    @Test
    void sourcesReindexables() {
        assertThat(sync.sources(P)).extracting(IndexableSource::id, IndexableSource::kind, IndexableSource::label)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(IndexationScope.JIRA, IndexableSource.Kind.JIRA, "Jira PROJ"),
                        org.assertj.core.groups.Tuple.tuple(declared(SITE).id().toString(),
                                IndexableSource.Kind.DOCUMENTATION, SITE),
                        org.assertj.core.groups.Tuple.tuple(IndexationScope.ARCHITECTURE,
                                IndexableSource.Kind.ARCHITECTURE, sources.find(P).workspace()));

        world.consents.record(new dev.specwriter.domain.consent.ConsentRecord(ConsentService.JIRA, false, Instant.EPOCH));
        sources.save(sources.find(P).withArchitecture(null, null));
        assertThat(sync.sources(P)).extracting(IndexableSource::kind).containsExactly(IndexableSource.Kind.DOCUMENTATION);
    }

    /** Lancement d'une réindexation de sources choisies : validée, gardée dans l'état de l'indexation. */
    @Test
    void indexationDeSourcesChoisies() {
        ManualExecutor executor = new ManualExecutor();
        Indexations indexations = new Indexations(sync, world.projects, executor, world.clock);
        for (List<String> refused : List.of(List.<String>of(), List.of("WIKI"))) {
            assertThatThrownBy(() -> indexations.start(P, Mode.FULL, refused)).isInstanceOfSatisfying(
                    DomainException.class, e -> assertThat(e.code().name()).isEqualTo("VALIDATION_FAILED"));
        }
        assertThatThrownBy(() -> indexations.start(P, Mode.INCREMENTAL, List.of(IndexationScope.JIRA)))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code().name()).isEqualTo("VALIDATION_FAILED"));
        assertThat(executor.tasks).isEmpty();

        Indexations.Snapshot started = indexations.start(P, Mode.FULL, List.of(IndexationScope.ARCHITECTURE));
        assertThat(started.sources()).containsExactly(IndexationScope.ARCHITECTURE);
        executor.runAll();
        assertThat(architectureReads).hasSize(1);
        assertThat(world.docs.reads).isEmpty();
        assertThat(indexations.current(P)).get().satisfies(s -> {
            assertThat(s.status()).isEqualTo(Indexations.Status.SUCCEEDED);
            assertThat(s.sources()).containsExactly(IndexationScope.ARCHITECTURE);
        });
        world.clock.advance(Duration.ofMinutes(31));
        assertThat(indexations.start(P, Mode.FULL).sources()).isNull();
    }

    // ---------- 006, US1 : dossier de documents ----------

    static final String DOCS = "/Users/moi/Library/CloudStorage/OneDrive-LaPoste/Aurora";
    static final Instant M1 = Instant.parse("2026-11-01T10:00:00Z");

    DocSource documents(String... exclusions) {
        DocSource source = new DocSource(UUID.randomUUID(), P, DocSource.Kind.DOCUMENTS, DOCS, null, null,
                ExclusionPatterns.of(List.of(exclusions)));
        world.docSources.save(source);
        return source;
    }

    static DocumentFolder.Reading docPage(DocSource source, String path, String title, String... bodies) {
        String ref = DocumentFolder.ref(source, path);
        List<Section> sections = new ArrayList<>();
        for (int i = 0; i < bodies.length; i++) {
            sections.add(new Section(ref + "#" + i, 0, title, bodies[i], KnowledgeKind.DOC_SECTION, null, null));
        }
        return DocumentFolder.Reading.of(new Page(ref, title, String.join("\n\n", bodies), sections));
    }

    List<String> documentRefs() {
        List<String> refs = new ArrayList<>();
        index.all(P, KnowledgeKind.DOC_SECTION).forEach(i -> refs.add(i.ref()));
        index.all(P, KnowledgeKind.ADR).forEach(i -> refs.add(i.ref()));
        return refs.stream().filter(r -> r.startsWith("docs:")).sorted().toList();
    }

    /** Documents indexés avec leur source dans la référence ; les sources Antora gardent leurs éléments. */
    @Test
    void dossierDeDocumentsIndexe() {
        DocSource docs = documents();
        world.folder.put(DOCS, "Specs/Aurora.docx", 1200, M1,
                docPage(docs, "Specs/Aurora.docx", "Aurora", "Contexte GAIA.", "Exigences nginx."));
        world.folder.put(DOCS, "Suivi.xlsx", 800, M1, docPage(docs, "Suivi.xlsx", "Suivi", "| Recette | 2026-12-01 |"));
        world.folder.put(DOCS, "Decisions/0017-ihm-nginx.docx", 500, M1, DocumentFolder.Reading.of(new Page(
                DocumentFolder.ref(docs, "Decisions/0017-ihm-nginx.docx"), "17. IHM nginx", "Statut : accepté", List.of(
                        new Section(DocumentFolder.ref(docs, "Decisions/0017-ihm-nginx.docx"), 0, "17. IHM nginx",
                                "Statut : accepté. nginx.", KnowledgeKind.ADR,
                                AdrStatus.Statement.of(AdrStatus.ACCEPTED, null), null)))));

        sync.run(P, Mode.FULL, progress);

        String prefix = DocumentFolder.prefix(docs);
        assertThat(documentRefs()).containsExactly(prefix + "Decisions/0017-ihm-nginx.docx",
                prefix + "Specs/Aurora.docx#0", prefix + "Specs/Aurora.docx#1", prefix + "Suivi.xlsx#0");
        assertThat(index.all(P, KnowledgeKind.ADR)).filteredOn(i -> i.ref().startsWith(prefix)).singleElement()
                .satisfies(i -> assertThat(i.status()).isEqualTo("ACCEPTED"));
        // la source Antora déclarée dans setUp garde ses éléments
        assertThat(index.count(P, KnowledgeKind.DOC_SECTION)).isEqualTo(4);
        assertThat(world.fileStates.of(docs.id())).containsOnlyKeys("Decisions/0017-ihm-nginx.docx",
                "Specs/Aurora.docx", "Suivi.xlsx");
        assertThat(progress.errors).isEmpty();
    }

    /** Un document inchangé n'est pas relu ; modifié, il l'est ; supprimé, il est retiré. */
    @Test
    void seulsLesDocumentsChangesSontRelus() {
        DocSource docs = documents();
        world.folder.put(DOCS, "Specs/Aurora.docx", 1200, M1, docPage(docs, "Specs/Aurora.docx", "Aurora", "v1"));
        world.folder.put(DOCS, "Suivi.xlsx", 800, M1, docPage(docs, "Suivi.xlsx", "Suivi", "v1"));
        sync.run(P, Mode.FULL, progress);
        world.folder.reads.clear();

        sync.run(P, Mode.INCREMENTAL, new Recorder());
        assertThat(world.folder.reads).isEmpty();

        world.folder.put(DOCS, "Suivi.xlsx", 900, M1.plusSeconds(60), docPage(docs, "Suivi.xlsx", "Suivi", "v2"));
        world.folder.remove(DOCS, "Specs/Aurora.docx");
        sync.run(P, Mode.INCREMENTAL, new Recorder());

        assertThat(world.folder.reads).containsExactly("Suivi.xlsx");
        assertThat(documentRefs()).containsExactly(DocumentFolder.prefix(docs) + "Suivi.xlsx#0");
        assertThat(index.all(P, KnowledgeKind.DOC_SECTION)).filteredOn(i -> i.ref().startsWith("docs:"))
                .extracting(i -> i.body()).containsExactly("v2");

        world.folder.reads.clear();
        sync.run(P, Mode.FULL, new Recorder());
        assertThat(world.folder.reads).containsExactly("Suivi.xlsx");
    }

    /** Formats ignorés comptés ; document illisible signalé, son indexation précédente gardée. */
    @Test
    void ignoresEtEchecsSignales() {
        DocSource docs = documents();
        world.folder.put(DOCS, "Exploitation.pdf", 300, M1, docPage(docs, "Exploitation.pdf", "Exploitation", "Prometheus."));
        world.folder.ignored.put(DOCS, List.of("vieux.doc", "plan.pptx"));
        sync.run(P, Mode.FULL, progress);
        assertThat(progress.errors).singleElement().satisfies(e -> {
            assertThat(e.kind()).isEqualTo(IndexationError.Kind.WARNING);
            assertThat(e.reason()).isEqualTo("IGNORED_FORMAT");
            assertThat(e.location()).isEqualTo(DOCS);
            assertThat(e.message()).contains("2");
        });

        world.folder.put(DOCS, "Exploitation.pdf", 400, M1.plusSeconds(60),
                DocumentFolder.Reading.failed(UnreadReason.UNAVAILABLE));
        Recorder second = new Recorder();
        sync.run(P, Mode.FULL, second);

        assertThat(second.errors).extracting(IndexationError::kind, IndexationError::location, IndexationError::reason)
                .contains(org.assertj.core.groups.Tuple.tuple(IndexationError.Kind.PAGE, "Exploitation.pdf", "UNAVAILABLE"));
        assertThat(documentRefs()).containsExactly(DocumentFolder.prefix(docs) + "Exploitation.pdf#0");
        // le fichier en échec sera relu à la prochaine synchronisation
        assertThat(world.fileStates.of(docs.id())).doesNotContainKey("Exploitation.pdf");
    }

    /** Dossier absent : signalé, rien n'est retiré. */
    @Test
    void dossierAbsentNeRetireRien() {
        DocSource docs = documents();
        world.folder.put(DOCS, "a.docx", 1, M1, docPage(docs, "a.docx", "A", "a"));
        sync.run(P, Mode.FULL, progress);
        world.folder.folders.remove(DOCS);
        Recorder second = new Recorder();

        sync.run(P, Mode.FULL, second);

        assertThat(second.errors).extracting(IndexationError::kind, IndexationError::reason)
                .contains(org.assertj.core.groups.Tuple.tuple(IndexationError.Kind.SOURCE, "FOLDER_NOT_FOUND"));
        assertThat(documentRefs()).hasSize(1);
    }

    /** Réindexation choisie (005) d'un dossier de documents : seul lui est lu, ses disparus retirés. */
    @Test
    void reindexationChoisieDuDossier() {
        DocSource docs = documents();
        world.folder.put(DOCS, "a.docx", 1, M1, docPage(docs, "a.docx", "A", "a"));
        world.folder.put(DOCS, "b.docx", 1, M1, docPage(docs, "b.docx", "B", "b"));
        sync.run(P, Mode.FULL, progress);
        world.docs.reads.clear();
        world.folder.remove(DOCS, "b.docx");

        sync.run(P, Mode.FULL, IndexationScope.of(List.of(docs.id().toString()), world.docSources.list(P)), new Recorder());

        assertThat(world.docs.reads).isEmpty();
        assertThat(documentRefs()).containsExactly(DocumentFolder.prefix(docs) + "a.docx#0");
    }

    /** 006, US2 : un document nouvellement exclu est retiré ; tout exclure n'est pas une erreur. */
    @Test
    void documentsExclusRetires() {
        DocSource docs = documents();
        world.folder.put(DOCS, "Specs/a.docx", 1, M1, docPage(docs, "Specs/a.docx", "A", "a"));
        world.folder.put(DOCS, "Archives/b.pdf", 1, M1, docPage(docs, "Archives/b.pdf", "B", "b"));
        sync.run(P, Mode.FULL, progress);
        assertThat(documentRefs()).hasSize(2);

        world.docSources.save(docs.withExclusions(ExclusionPatterns.of(List.of("Archives/**"))));
        sync.run(P, Mode.INCREMENTAL, new Recorder());
        assertThat(documentRefs()).containsExactly(DocumentFolder.prefix(docs) + "Specs/a.docx#0");

        world.docSources.save(docs.withExclusions(ExclusionPatterns.of(List.of("**"))));
        Recorder all = new Recorder();
        sync.run(P, Mode.FULL, all);
        assertThat(documentRefs()).isEmpty();
        assertThat(all.errors).isEmpty();
    }

    /** Erreur inattendue d'un dossier de documents (005) : les autres sources continuent. */
    @Test
    void erreurInattendueDuDossier() {
        documents();
        world.folder.broken.put(DOCS, new IllegalStateException("panne"));

        sync.run(P, Mode.FULL, progress);

        unexpected(progress.errors, IndexationError.Kind.SOURCE, DOCS);
        assertThat(index.count(P, KnowledgeKind.DOC_SECTION)).isEqualTo(1);
        assertThat(index.count(P, KnowledgeKind.ARCH_ELEMENT)).isEqualTo(2);
    }

    /** Une bibliothèque absente du jar est signalée à son étape, les autres continuent (T117, puis 005). */
    @Test
    void bibliothequeAbsenteSignaleeASonEtape() {
        ManualExecutor executor = new ManualExecutor();
        ArchitectureReader missingLibrary = (workspace, ownership) -> {
            throw new NoClassDefFoundError("org/slf4j/LoggerFactory");
        };
        Indexations indexations = new Indexations(new SynchronizeKnowledge(world.projects, sources, world.docSources,
                world.docs, world.folder, world.fileStates, missingLibrary, world.jira, world.tracker, world.ticketLinks, index, embedding, world.clock), world.projects,
                executor, world.clock);
        indexations.start(P, Mode.FULL);
        executor.runAll();
        assertThat(indexations.current(P)).get().satisfies(s -> {
            assertThat(s.status()).isEqualTo(Indexations.Status.SUCCEEDED);
            assertThat(s.errors()).extracting(IndexationError::kind, IndexationError::reason)
                    .contains(org.assertj.core.groups.Tuple.tuple(IndexationError.Kind.ARCHITECTURE, "UNEXPECTED"));
        });
        assertThat(index.count(P, KnowledgeKind.TICKET)).isEqualTo(2);
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

    // ---------- 003 : tickets fermés gardés sans relecture ----------

    static final Instant OLD = Instant.parse("2026-09-01T10:00:00Z");
    static final Instant MARK = Instant.parse("2026-10-28T08:30:00Z");

    /** 8 tickets fermés anciens (PROJ-1 à PROJ-8), 2 ouverts (PROJ-9, PROJ-10) ; repère = MARK. */
    void tenTicketsIndexed() {
        world.tracker.tracked.clear();
        for (int i = 1; i <= 8; i++) {
            world.tracker.tracked.put("PROJ-" + i, issue("PROJ-" + i, "Fermé " + i, "done", OLD));
        }
        world.tracker.tracked.put("PROJ-9", issue("PROJ-9", "Ouvert 9", "new", MARK));
        world.tracker.tracked.put("PROJ-10", issue("PROJ-10", "Ouvert 10", "indeterminate", OLD));
        sync.run(P, Mode.FULL, new Recorder());
        assertThat(sources.find(P).jiraMark()).isEqualTo(MARK);
        world.tracker.calls.clear();
        world.tracker.read.clear();
        index.writes.clear();
    }

    /** US1-1 : seuls les tickets ouverts sont relus ; les fermés restent tels quels, vecteurs compris. */
    @Test
    void completeAvecRepereNeRelitQueLesOuverts() {
        tenTicketsIndexed();
        float[] vector = index.items.get(P).get(new Key(KnowledgeKind.TICKET, "PROJ-3", 0)).vector();

        sync.run(P, Mode.FULL, progress);

        assertThat(world.tracker.read).containsExactlyInAnyOrder("PROJ-9", "PROJ-10");
        assertThat(world.tracker.calls).contains("openOrUpdatedSince PROJ " + MARK + " 0", "keys PROJ")
                .noneMatch(c -> c.startsWith("updatedSince") || c.startsWith("byKeys"));
        assertThat(index.writes).filteredOn(w -> w.startsWith("save PROJ-")).containsExactlyInAnyOrder("save PROJ-9",
                "save PROJ-10");
        assertThat(index.find(P, new Key(KnowledgeKind.TICKET, "PROJ-3", 0))).get().satisfies(i -> {
            assertThat(i.closed()).isTrue();
            assertThat(i.body()).isEqualTo("Description de Fermé 3");
        });
        assertThat(index.items.get(P).get(new Key(KnowledgeKind.TICKET, "PROJ-3", 0)).vector()).isSameAs(vector);
        assertThat(progress.tickets).isEqualTo(new TicketSummary(2, 8, 0));
    }

    /** US1-2 : un ticket fermé jamais indexé est lu une fois par clé, puis plus relu. */
    @Test
    void fermeAbsentDeLIndexLuUneFoisParCle() {
        tenTicketsIndexed();
        world.tracker.tracked.put("PROJ-11", issue("PROJ-11", "Fermé avant la synchronisation", "done", OLD));

        sync.run(P, Mode.FULL, progress);
        assertThat(world.tracker.calls).contains("byKeys PROJ 1");
        assertThat(index.find(P, new Key(KnowledgeKind.TICKET, "PROJ-11", 0))).get()
                .satisfies(i -> assertThat(i.closed()).isTrue());
        assertThat(progress.tickets).isEqualTo(new TicketSummary(3, 8, 0));

        world.tracker.read.clear();
        Recorder again = new Recorder();
        sync.run(P, Mode.FULL, again);
        assertThat(world.tracker.read).containsExactlyInAnyOrder("PROJ-9", "PROJ-10");
        assertThat(again.tickets).isEqualTo(new TicketSummary(2, 9, 0));
    }

    /** Cas limite : indexé ouvert, fermé dans Jira sans date plus récente : lu par clé, l'index se corrige. */
    @Test
    void indexeOuvertNonRamenePuisLuParCle() {
        tenTicketsIndexed();
        world.tracker.tracked.put("PROJ-10", issue("PROJ-10", "Ouvert 10", "done", OLD));

        sync.run(P, Mode.FULL, progress);
        assertThat(world.tracker.calls).contains("byKeys PROJ 1");
        assertThat(index.find(P, new Key(KnowledgeKind.TICKET, "PROJ-10", 0))).get()
                .satisfies(i -> assertThat(i.closed()).isTrue());
    }

    /** US1-5 : un ticket fermé supprimé ou déplacé est retiré, avec ses liens ; compté dans le bilan. */
    @Test
    void fermeSupprimeRetireEtCompte() {
        tenTicketsIndexed();
        world.tracker.tracked.remove("PROJ-4");
        sync.run(P, Mode.FULL, progress);
        assertThat(index.find(P, new Key(KnowledgeKind.TICKET, "PROJ-4", 0))).isEmpty();
        assertThat(progress.tickets).isEqualTo(new TicketSummary(2, 7, 1));
    }

    /** FR-008 : sans repère, tout est relu (relecture unique), puis la règle reprend. */
    @Test
    void sansRepereToutEstRelu() {
        tenTicketsIndexed();
        sources.save(sources.find(P).synced(null, world.clock.now()));

        sync.run(P, Mode.FULL, progress);
        assertThat(world.tracker.calls).contains("updatedSince PROJ null 0")
                .noneMatch(c -> c.startsWith("openOrUpdatedSince"));
        assertThat(world.tracker.read).hasSize(10);
        assertThat(progress.tickets).isEqualTo(new TicketSummary(10, 0, 0));
    }

    /** Jira injoignable à l'étape des clés : rien n'est retiré, le repère suit la requête. */
    @Test
    void erreurALEtapeDesClesNeRetireRien() {
        tenTicketsIndexed();
        world.tracker.tracked.remove("PROJ-4");
        world.tracker.tracked.put("PROJ-9", issue("PROJ-9", "Ouvert 9", "new", Instant.parse("2026-11-02T10:00:00Z")));
        world.tracker.failingCall = "keys";

        sync.run(P, Mode.FULL, progress);
        assertThat(index.find(P, new Key(KnowledgeKind.TICKET, "PROJ-4", 0))).isPresent();
        assertThat(sources.find(P).jiraMark()).isEqualTo(Instant.parse("2026-11-02T10:00:00Z"));
        assertThat(progress.errors).extracting(IndexationError::kind).contains(IndexationError.Kind.JIRA);
        assertThat(progress.tickets).isEqualTo(new TicketSummary(2, 0, 0));
    }

    /** Lecture par clé en lots de 50 au plus. */
    @Test
    void lectureParCleParLotsDeCinquante() {
        tenTicketsIndexed();
        for (int i = 100; i < 220; i++) {
            world.tracker.tracked.put("PROJ-" + i, issue("PROJ-" + i, "Fermé " + i, "done", OLD));
        }
        sync.run(P, Mode.FULL, progress);
        assertThat(world.tracker.calls).filteredOn(c -> c.startsWith("byKeys"))
                .containsExactly("byKeys PROJ 50", "byKeys PROJ 50", "byKeys PROJ 20");
        assertThat(index.count(P, KnowledgeKind.TICKET)).isEqualTo(130);
        assertThat(progress.tickets).isEqualTo(new TicketSummary(122, 8, 0));
    }

    /** En incrémental, la liste des clés n'est pas lue : aucun fermé n'est compté comme gardé. */
    @Test
    void bilanIncremental() {
        tenTicketsIndexed();
        sync.run(P, Mode.INCREMENTAL, progress);
        assertThat(progress.tickets).isEqualTo(new TicketSummary(1, 0, 0));
    }

    /** US1-3 (FR-003, SC-003) : un ticket fermé rouvert est relu par l'incrémentale et redevient ouvert. */
    @Test
    void fermeRouvertReluEtOuvert() {
        tenTicketsIndexed();
        world.tracker.tracked.put("PROJ-2", issue("PROJ-2", "Fermé 2", "indeterminate",
                Instant.parse("2026-11-02T10:00:00Z")));
        sync.run(P, Mode.INCREMENTAL, progress);
        assertThat(world.tracker.read).contains("PROJ-2");
        assertThat(index.find(P, new Key(KnowledgeKind.TICKET, "PROJ-2", 0))).get()
                .satisfies(i -> assertThat(i.closed()).isFalse());
    }

    /** Cas limite : un ticket fermé corrigé sans réouverture est relu une fois par l'incrémentale. */
    @Test
    void fermeCorrigeSansReouvertureRelu() {
        tenTicketsIndexed();
        world.tracker.tracked.put("PROJ-2", new TrackedIssue("PROJ-2", "Fermé 2", "Texte corrigé.", "Terminé", "done",
                "Story", Instant.parse("2026-11-02T10:00:00Z")));
        sync.run(P, Mode.INCREMENTAL, progress);
        assertThat(index.find(P, new Key(KnowledgeKind.TICKET, "PROJ-2", 0))).get().satisfies(i -> {
            assertThat(i.closed()).isTrue();
            assertThat(i.body()).isEqualTo("Texte corrigé.");
        });
    }

    /**
     * SC-001 : 100 tickets dont 80 fermés indexés ; l'indexation complète suivante en lit au plus 20
     * et son étape Jira est au moins deux fois plus courte (coût simulé par ticket lu).
     */
    @Test
    void indexationCompleteAuMoinsDeuxFoisPlusRapide() {
        world.tracker.tracked.clear();
        for (int i = 1; i <= 100; i++) {
            world.tracker.tracked.put("PROJ-" + i, issue("PROJ-" + i, "Ticket " + i, i <= 80 ? "done" : "new",
                    i <= 80 ? OLD : MARK));
        }
        world.tracker.readDelayMillis = 3;
        long first = jiraStepMillis();
        assertThat(world.tracker.read).hasSize(100);

        world.tracker.read.clear();
        long second = jiraStepMillis();
        assertThat(world.tracker.read).hasSize(20);
        assertThat(progress.tickets).isEqualTo(new TicketSummary(20, 80, 0));
        assertThat(second * 2).isLessThanOrEqualTo(first);
    }

    private long jiraStepMillis() {
        progress = new Recorder();
        long start = System.nanoTime();
        sync.run(P, Mode.FULL, progress);
        return (System.nanoTime() - start) / 1_000_000;
    }

    /** US3-1 (FR-007) : un an plus tard, sans modification dans Jira, aucun fermé n'est relu ni retiré. */
    @Test
    void unAnPlusTardAucunFermeReluNiRetire() {
        tenTicketsIndexed();
        Instant indexedAt = index.find(P, new Key(KnowledgeKind.TICKET, "PROJ-3", 0)).orElseThrow().indexedAt();
        world.clock.advance(Duration.ofDays(365));

        sync.run(P, Mode.INCREMENTAL, new Recorder());
        sync.run(P, Mode.FULL, progress);
        assertThat(world.tracker.read).doesNotContain("PROJ-1", "PROJ-2", "PROJ-3", "PROJ-4", "PROJ-5", "PROJ-6",
                "PROJ-7", "PROJ-8");
        assertThat(index.closedTickets(P)).hasSize(8);
        assertThat(index.find(P, new Key(KnowledgeKind.TICKET, "PROJ-3", 0))).get()
                .extracting(i -> i.indexedAt()).isEqualTo(indexedAt);
    }

    /** US3-2 (FR-005) : un autre modèle revectorise les fermés depuis le texte indexé, sans les relire. */
    @Test
    void changementDeModeleRevectoriseLesFermesSansJira() {
        tenTicketsIndexed();
        embedding.passages.clear();
        embedding.modelId = "autre@2";

        sync.run(P, Mode.FULL, progress);
        assertThat(world.tracker.read).containsExactlyInAnyOrder("PROJ-9", "PROJ-10");
        assertThat(embedding.passages).anyMatch(t -> t.startsWith("Fermé 3\nDescription de Fermé 3"));
        assertThat(index.withoutVector(P, "autre@2", 100)).isEmpty();
        assertThat(index.items.get(P).get(new Key(KnowledgeKind.TICKET, "PROJ-3", 0)).model()).isEqualTo("autre@2");
    }
}
