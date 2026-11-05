package dev.specwriter.application.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.application.knowledge.SubtaskReport.Closeness;
import dev.specwriter.application.knowledge.SubtaskReport.Item;
import dev.specwriter.application.knowledge.SubtaskReport.State;
import dev.specwriter.application.knowledge.SubtaskReport.UseCaseRef;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.jira.IssueTrackerException;
import dev.specwriter.domain.jira.TicketLink;
import dev.specwriter.domain.jira.TrackedIssue;
import dev.specwriter.domain.knowledge.KnowledgeItem;
import dev.specwriter.domain.port.EmbeddingPort;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.session.Session;
import dev.specwriter.domain.specification.Specs;
import dev.specwriter.domain.specification.SpecificationVersion;
import dev.specwriter.domain.specification.UseCase;
import dev.specwriter.domain.specification.UseCaseChanges;
import dev.specwriter.domain.specification.VersionOrigin;
import dev.specwriter.support.InMemoryKnowledgeIndex;
import dev.specwriter.support.JiraWorld;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T125 : rattachement des sous-tâches aux cas d'usage et leur état (FR-046, FR-047, research.md R29). */
class SubtaskReportTest {

    static final UUID P = Project.DEFAULT_ID;
    static final Instant T = Instant.parse("2026-10-01T10:00:00Z");

    /** Vecteur lu dans le texte : la dernière marque {@code [x,y,z]}, pour choisir les proximités. */
    static final class MarkedEmbedding implements EmbeddingPort {
        static final Pattern MARK = Pattern.compile("\\[(-?[\\d.]+),(-?[\\d.]+),(-?[\\d.]+)]");
        State state = State.READY;
        final List<String> texts = new ArrayList<>();

        @Override
        public State state() {
            return state;
        }

        @Override
        public String problem() {
            return state == State.READY ? null : "Modèle absent : téléchargement impossible.";
        }

        @Override
        public String modelId() {
            return "marked@1";
        }

        @Override
        public State prepare(Progress progress) {
            return state;
        }

        @Override
        public float[] query(String text) {
            return vector(text);
        }

        @Override
        public List<float[]> passages(List<String> list) {
            texts.addAll(list);
            return list.stream().map(MarkedEmbedding::vector).toList();
        }

        static float[] vector(String text) {
            Matcher m = MARK.matcher(text);
            float[] v = {0, 0, 0};
            while (m.find()) {
                v = new float[] {Float.parseFloat(m.group(1)), Float.parseFloat(m.group(2)), Float.parseFloat(m.group(3))};
            }
            return v;
        }
    }

    final JiraWorld w = new JiraWorld().configureJira().grant(ConsentService.JIRA);
    final InMemoryKnowledgeIndex index = new InMemoryKnowledgeIndex();
    final MarkedEmbedding embedding = new MarkedEmbedding();
    ReportSubtasks report;
    Session session;

    static UseCase uc(String code, String title, String goal) {
        return new UseCase(code, title, "client", goal, List.of(), List.of("Étape"), List.of(), null);
    }

    void version(int number, UseCase... useCases) {
        w.versions.save(new SpecificationVersion(UUID.randomUUID(), session.id(), number,
                Specs.with(List.of(useCases), List.of(), List.of()), "fp" + number, VersionOrigin.AI_EDIT, "# v" + number,
                T));
    }

    void subtask(String key, String title, String category) {
        index.save(KnowledgeItem.ticket(P, key, title, "", category.equals("done") ? "Terminé" : "À faire", category,
                "Sous-tâche", T, T), null, null);
        w.tracker.tracked.put(key, new TrackedIssue(key, title, "", category.equals("done") ? "Terminé" : "En cours",
                category, "Sous-tâche", T));
    }

    static final UseCase PAYER = uc("UC-01", "Payer [1,0,0]", "Régler son panier");
    static final UseCase REMBOURSER = uc("UC-02", "Rembourser [0,1,0]", "Récupérer son argent");
    static final UseCase SUIVRE = uc("UC-03", "Suivre [0.9,0,0.436]", "Suivre sa commande");

    @BeforeEach
    void setUp() {
        session = w.generatedSession();
        w.link(session, "FACT-20", "d");
        w.ticketLinks.replace(P, "FACT-20", List.of(TicketLink.subtask("FACT-21"), TicketLink.subtask("FACT-22"),
                TicketLink.subtask("FACT-23"), TicketLink.subtask("FACT-24")));
        subtask("FACT-21", "API de paiement [1,0,0]", "indeterminate");          // UC-01, très proche
        subtask("FACT-22", "Écran du remboursement [0.527,0.85,0]", "done");     // UC-02, proche
        subtask("FACT-23", "Mise à jour des dépendances [0.5,0.5,0.707]", "new"); // aucun cas d'usage
        subtask("FACT-24", "Historique [0.97,0,0.24]", "new");                   // UC-03, UC-01 tout près
        // bruit de fond : six autres tickets du projet, hors famille
        double[] ys = {0.6, 0.8, 1, 1.2, 1.5, 2};
        for (int i = 0; i < ys.length; i++) {
            index.save(KnowledgeItem.ticket(P, "FOND-" + i, "Autre sujet [0," + ys[i] + ",1]", "", "À faire", "new", "Story",
                    T, T), null, null);
        }
        report = new ReportSubtasks(w.sessions, w.versions, new StoryFamily(w.tickets, w.ticketLinks), index, embedding,
                w.jira, w.tracker, 1.5);
    }

    static Item item(SubtaskReport r, String key) {
        return r.subtasks().stream().filter(i -> i.key().equals(key)).findFirst().orElseThrow();
    }

    @Test
    void rattachementEtEtatsFaceAuxChangements() {
        version(2, PAYER, REMBOURSER, SUIVRE);
        UseCase payerAutrement = uc("UC-01", "Payer [1,0,0]", "Régler son panier en trois fois");
        version(3, payerAutrement, SUIVRE);

        SubtaskReport r = report.run(session.id(), 3).orElseThrow();
        assertThat(r.storyKey()).isEqualTo("FACT-20");
        assertThat(r.matching()).isEqualTo(SubtaskReport.Matching.SEMANTIC);

        Item api = item(r, "FACT-21");
        assertThat(api.useCase()).isEqualTo(new UseCaseRef("UC-01", "Payer [1,0,0]"));
        assertThat(api.closeness()).isEqualTo(Closeness.VERY_CLOSE);
        assertThat(api.change()).isEqualTo(UseCaseChanges.Change.CHANGED);
        assertThat(api.state()).isEqualTo(State.TO_REVIEW);
        assertThat(api.statusFresh()).isTrue();
        assertThat(api.status()).isEqualTo("En cours");
        assertThat(api.url()).isEqualTo("https://jira.example.com/browse/FACT-21");

        Item ecran = item(r, "FACT-22");
        assertThat(ecran.useCase().code()).isEqualTo("UC-02");
        assertThat(ecran.closeness()).isEqualTo(Closeness.CLOSE);
        assertThat(ecran.change()).isEqualTo(UseCaseChanges.Change.REMOVED);
        assertThat(ecran.state()).isEqualTo(State.DELIVERED_TO_REWORK);

        assertThat(item(r, "FACT-23").state()).isEqualTo(State.UNMATCHED);
        assertThat(item(r, "FACT-23").useCase()).isNull();

        Item historique = item(r, "FACT-24");
        assertThat(historique.useCase().code()).isEqualTo("UC-03");
        assertThat(historique.alternative()).isEqualTo(new UseCaseRef("UC-01", "Payer [1,0,0]"));
        assertThat(historique.state()).isEqualTo(State.CONSISTENT);

        // à revoir, livrées à reprendre, puis les autres
        assertThat(r.subtasks()).extracting(Item::key).containsExactly("FACT-21", "FACT-22", "FACT-24", "FACT-23");
        assertThat(w.tracker.writes).isEmpty();
    }

    @Test
    void premiereVersionSansChangement() {
        version(2, PAYER, REMBOURSER);
        w.versions.delete(session.id(), 1);
        SubtaskReport r = report.run(session.id(), 2).orElseThrow();
        assertThat(r.subtasks()).extracting(Item::state).doesNotContain(State.TO_REVIEW, State.DELIVERED_TO_REWORK);
        assertThat(item(r, "FACT-21").state()).isEqualTo(State.CONSISTENT);
    }

    @Test
    void laVersionPrecedenteEstLaPlusRecenteDesVersionsRestantes() {
        version(2, PAYER, REMBOURSER, SUIVRE);
        version(3, PAYER, SUIVRE);
        version(4, PAYER, SUIVRE);
        w.versions.delete(session.id(), 3);
        // la 4 se compare à la 2 : UC-02 a disparu
        assertThat(item(report.run(session.id(), 4).orElseThrow(), "FACT-22").state()).isEqualTo(State.DELIVERED_TO_REWORK);
    }

    @Test
    void tropPeuDeTicketsPourMesurerLeFond() {
        version(2, PAYER);
        for (int i = 0; i < 3; i++) {
            index.delete(P, new dev.specwriter.domain.port.KnowledgeIndex.Key(
                    dev.specwriter.domain.knowledge.KnowledgeKind.TICKET, "FOND-" + i, 0));
        }
        SubtaskReport r = report.run(session.id(), 2).orElseThrow();
        assertThat(r.matching()).isEqualTo(SubtaskReport.Matching.UNAVAILABLE);
        assertThat(r.matchingProblem()).contains("Trop peu de tickets indexés (3)");
    }

    @Test
    void sansModeleAucunRattachementEtLaRaison() {
        version(2, PAYER);
        embedding.state = EmbeddingPort.State.MISSING;
        SubtaskReport r = report.run(session.id(), 2).orElseThrow();
        assertThat(r.matching()).isEqualTo(SubtaskReport.Matching.UNAVAILABLE);
        assertThat(r.matchingProblem()).contains("Modèle absent");
        assertThat(r.subtasks()).allSatisfy(i -> assertThat(i.state()).isEqualTo(State.UNMATCHED));
    }

    @Test
    void statutIndexeSiJiraInjoignableEtSousTacheSupprimeeRetiree() {
        version(2, PAYER);
        w.tracker.failure = IssueTrackerException.Kind.UNREACHABLE;
        Item api = item(report.run(session.id(), 2).orElseThrow(), "FACT-21");
        assertThat(api.statusFresh()).isFalse();
        assertThat(api.status()).isEqualTo("À faire");
        assertThat(api.statusDate()).isEqualTo(T);

        w.tracker.failure = null;
        w.tracker.tracked.remove("FACT-23");
        assertThat(report.run(session.id(), 2).orElseThrow().subtasks()).extracting(Item::key).doesNotContain("FACT-23");
    }

    /** 003, US2-3 : une sous-tâche fermée dans l'index n'est pas relue dans Jira et reste à jour. */
    @Test
    void sousTacheFermeeIndexeeNonRelue() {
        version(2, PAYER);
        w.tracker.calls.clear();
        Item ecran = item(report.run(session.id(), 2).orElseThrow(), "FACT-22");
        assertThat(ecran.statusFresh()).isTrue();
        assertThat(ecran.statusCategory()).isEqualTo("done");
        assertThat(w.tracker.calls).contains("status FACT-21", "status FACT-23", "status FACT-24")
                .doesNotContain("status FACT-22");

        w.tracker.failure = IssueTrackerException.Kind.UNREACHABLE;
        assertThat(item(report.run(session.id(), 2).orElseThrow(), "FACT-22").statusFresh()).isTrue();
    }

    @Test
    void sousTacheHorsIndexNonRattachee() {
        version(2, PAYER);
        w.ticketLinks.replace(P, "FACT-20", List.of(TicketLink.subtask("AUTRE-1")));
        w.tracker.tracked.put("AUTRE-1", new TrackedIssue("AUTRE-1", "Ailleurs", "", "À faire", "new", "Sous-tâche", T));
        w.tracker.calls.clear();
        Item ailleurs = report.run(session.id(), 2).orElseThrow().subtasks().getFirst();
        // hors de l'index : toujours relue
        assertThat(w.tracker.calls).contains("status AUTRE-1");
        assertThat(ailleurs.key()).isEqualTo("AUTRE-1");
        assertThat(ailleurs.title()).isEqualTo("AUTRE-1");
        assertThat(ailleurs.state()).isEqualTo(State.UNMATCHED);
    }

    @Test
    void sansSousTacheNiParentAucuneRubrique() {
        w.ticketLinks.replace(P, "FACT-20", List.of());
        assertThat(report.run(session.id(), 1)).isEmpty();
        Session unlinked = w.generatedSession();
        assertThat(report.run(unlinked.id(), 1)).isEmpty();
    }

    @Test
    void storySousTacheMontreSonParent() {
        w.ticketLinks.replace(P, "FACT-20", List.of(TicketLink.parent("FACT-10")));
        SubtaskReport r = report.run(session.id(), 1).orElseThrow();
        assertThat(r.parentKey()).isEqualTo("FACT-10");
        assertThat(r.subtasks()).isEmpty();
    }

    @Test
    void versionOuSessionInconnue() {
        assertThatThrownBy(() -> report.run(session.id(), 9)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> report.run(UUID.randomUUID(), 1)).isInstanceOf(DomainException.class);
    }

    @Test
    void auPlusQuinzeSousTaches() {
        version(2, PAYER);
        List<TicketLink> many = new ArrayList<>();
        for (int i = 100; i < 120; i++) {
            subtask("FACT-" + i, "Tâche " + i + " [0,0,1]", "new");
            many.add(TicketLink.subtask("FACT-" + i));
        }
        w.ticketLinks.replace(P, "FACT-20", many);
        assertThat(report.run(session.id(), 2).orElseThrow().subtasks()).hasSize(15);
    }
}
