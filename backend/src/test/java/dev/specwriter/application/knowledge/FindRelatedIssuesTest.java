package dev.specwriter.application.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.jira.IssueTrackerException;
import dev.specwriter.domain.jira.TrackedIssue;
import dev.specwriter.domain.knowledge.KnowledgeItem;
import dev.specwriter.domain.knowledge.KnowledgeKind;
import dev.specwriter.domain.llm.RelatedIssuesJudgement;
import dev.specwriter.domain.port.EmbeddingPort;
import dev.specwriter.domain.port.KnowledgeIndex.Key;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.session.Need;
import dev.specwriter.domain.session.Session;
import dev.specwriter.support.FakeEmbedding;
import dev.specwriter.support.InMemoryKnowledgeIndex;
import dev.specwriter.support.InMemoryKnowledgeSources;
import dev.specwriter.support.JiraWorld;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T106 : tickets proches d'un besoin (FR-034, research.md R23). */
class FindRelatedIssuesTest {

    static final UUID P = Project.DEFAULT_ID;
    static final Instant INDEXED = Instant.parse("2026-10-01T08:00:00Z");
    static final String NEED = "Le gestionnaire doit pouvoir annuler une facture émise par un avoir total.";

    final JiraWorld w = new JiraWorld().configureJira().grant(ConsentService.JIRA).grant(ConsentService.COPILOT);
    final InMemoryKnowledgeIndex index = new InMemoryKnowledgeIndex();
    final FakeEmbedding embedding = new FakeEmbedding();
    Indexations indexations;
    FindRelatedIssues find;
    Session session;

    static KnowledgeItem ticket(String key, String title, String category) {
        return KnowledgeItem.ticket(P, key, title, "Description de " + title + " : facture et avoir.", "Indexé", category,
                "Story", INDEXED, INDEXED);
    }

    @BeforeEach
    void setUp() {
        embedding.state = EmbeddingPort.State.READY;
        for (int i = 1; i <= 25; i++) {
            index.save(ticket("PROJ-" + i, "Facture " + i, "new"), null, null);
            w.tracker.tracked.put("PROJ-" + i, new TrackedIssue("PROJ-" + i, "Facture " + i, "", "À faire", "new", "Story",
                    INDEXED));
        }
        index.save(ticket("PROJ-7", "Annuler une facture", "done"), null, null);
        w.tracker.tracked.put("PROJ-7", new TrackedIssue("PROJ-7", "Annuler une facture", "", "Terminé", "done", "Story",
                Instant.parse("2026-11-02T10:00:00Z")));
        w.tracker.tracked.put("PROJ-9", new TrackedIssue("PROJ-9", "Facture 9", "", "En cours", "indeterminate", "Story",
                Instant.parse("2026-11-03T10:00:00Z")));
        SynchronizeKnowledge sync = new SynchronizeKnowledge(w.projects, new InMemoryKnowledgeSources(), w.docSources,
                w.docs, (ws, o) -> null, w.jira, w.tracker, index, embedding, w.clock);
        indexations = new Indexations(sync, w.projects, task -> { }, w.clock, false);
        find = new FindRelatedIssues(index, embedding, w.llm, w.policy, w.jira, w.tracker, indexations);
        session = Session.start(UUID.randomUUID(), P, Need.of(NEED), null, w.clock.now());
    }

    Project project() {
        return w.projects.find(P).orElseThrow();
    }

    @Test
    void vingtCandidatsJugesCinqAuPlusAvecLaRaison() {
        w.llm.judgements.add(new RelatedIssuesJudgement(List.of(
                new RelatedIssuesJudgement.Related("PROJ-7", "Même annulation par un avoir total."),
                new RelatedIssuesJudgement.Related("INCONNU-1", "Hors des candidats"),
                new RelatedIssuesJudgement.Related("PROJ-9", "Concerne les factures émises."))));
        RelatedIssues found = find.find(session, project());

        assertThat(w.llm.relatedRequests).singleElement().satisfies(r -> {
            assertThat(r.candidates()).hasSize(20);
            assertThat(r.need()).isEqualTo(NEED);
            assertThat(r.candidates()).allSatisfy(c -> assertThat(c.excerpt().length()).isLessThanOrEqualTo(600));
        });
        assertThat(found.status()).isEqualTo(RelatedIssues.Status.READY);
        assertThat(found.issues()).extracting(RelatedIssues.Issue::key).containsExactly("PROJ-7", "PROJ-9");
        assertThat(found.issues().getFirst()).satisfies(i -> {
            assertThat(i.reason()).isEqualTo("Même annulation par un avoir total.");
            assertThat(i.closed()).isTrue();
            assertThat(i.statusFresh()).isTrue();
            assertThat(i.status()).isEqualTo("Terminé");
            assertThat(i.updatedAt()).isEqualTo(Instant.parse("2026-11-02T10:00:00Z"));
            assertThat(i.url()).isEqualTo("https://jira.example.com/browse/PROJ-7");
        });
        assertThat(found.awaitingDecision()).isTrue();
    }

    @Test
    void auPlusCinqTicketsRetenus() {
        w.llm.judgements.add(new RelatedIssuesJudgement(java.util.stream.IntStream.rangeClosed(1, 8)
                .mapToObj(i -> new RelatedIssuesJudgement.Related("PROJ-" + i, "Raison " + i)).toList()));
        w.tracker.failure = IssueTrackerException.Kind.UNREACHABLE;
        assertThat(find.find(session, project()).issues()).hasSize(5);
    }

    @Test
    void sansCopilotLesTroisMeilleursSansRaison() {
        JiraWorld noCopilot = new JiraWorld().configureJira().grant(ConsentService.JIRA);
        FindRelatedIssues withoutCopilot = new FindRelatedIssues(index, embedding, noCopilot.llm, noCopilot.policy,
                w.jira, w.tracker, indexations);
        RelatedIssues found = withoutCopilot.find(session, project());
        assertThat(noCopilot.llm.relatedRequests).isEmpty();
        assertThat(found.issues()).hasSize(3).allSatisfy(i -> assertThat(i.reason()).isNull());
    }

    @Test
    void echecDuJugementLesTroisMeilleursSansRaison() {
        // aucune réponse préparée : le faux LLM échoue (LLM_UNAVAILABLE)
        RelatedIssues found = find.find(session, project());
        assertThat(found.issues()).hasSize(3).allSatisfy(i -> assertThat(i.reason()).isNull());
    }

    @Test
    void statutIndexeAvecSaDateSiJiraNeRepondPas() {
        w.llm.judgements.add(new RelatedIssuesJudgement(List.of(new RelatedIssuesJudgement.Related("PROJ-7", "Même"))));
        w.tracker.failure = IssueTrackerException.Kind.UNREACHABLE;
        RelatedIssues.Issue issue = find.find(session, project()).issues().getFirst();
        assertThat(issue.statusFresh()).isFalse();
        assertThat(issue.status()).isEqualTo("Indexé");
        assertThat(issue.updatedAt()).isEqualTo(INDEXED);
        assertThat(issue.closed()).isTrue();
    }

    @Test
    void ticketSupprimeDansJiraRetireDeLIndex() {
        w.tracker.tracked.remove("PROJ-3");
        w.llm.judgements.add(new RelatedIssuesJudgement(List.of(
                new RelatedIssuesJudgement.Related("PROJ-3", "Supprimé depuis"),
                new RelatedIssuesJudgement.Related("PROJ-7", "Même"))));
        RelatedIssues found = find.find(session, project());
        assertThat(found.issues()).extracting(RelatedIssues.Issue::key).containsExactly("PROJ-7");
        assertThat(index.find(P, new Key(KnowledgeKind.TICKET, "PROJ-3", 0))).isEmpty();
    }

    @Test
    void indexPartielSignale() {
        embedding.state = EmbeddingPort.State.TERMS_ONLY;
        w.llm.judgements.add(new RelatedIssuesJudgement(List.of()));
        RelatedIssues found = find.find(session, project());
        assertThat(found.status()).isEqualTo(RelatedIssues.Status.PARTIAL);
        assertThat(found.issues()).isEmpty();
        assertThat(found.awaitingDecision()).isFalse();
    }

    @Test
    void sansTicketIndexeIndisponible() {
        InMemoryKnowledgeIndex empty = new InMemoryKnowledgeIndex();
        FindRelatedIssues none = new FindRelatedIssues(empty, embedding, w.llm, w.policy, w.jira, w.tracker, indexations);
        assertThat(none.find(session, project())).isEqualTo(RelatedIssues.UNAVAILABLE);
        assertThat(w.llm.calls()).isZero();
    }

    @Test
    void demarrerUneSessionAttendLeChoixAvantLesQuestions() {
        RelatedIssuesStore store = new RelatedIssuesStore();
        dev.specwriter.application.session.StartSession start = new dev.specwriter.application.session.StartSession(
                w.sessions, w.projects, w.llm, w.policy, w.clock, w.ids, find, store);
        w.llm.judgements.add(new RelatedIssuesJudgement(List.of(new RelatedIssuesJudgement.Related("PROJ-7", "Même"))));
        var waiting = start.run(NEED, null);
        assertThat(waiting.questionnaire().questions()).isEmpty();
        assertThat(w.llm.questionRequests).isEmpty();
        assertThat(store.find(waiting.session().id())).get().extracting(RelatedIssues::awaitingDecision).isEqualTo(true);

        // aucun ticket proche : les questions sont posées aussitôt
        w.llm.judgements.add(new RelatedIssuesJudgement(List.of()));
        w.llm.questions.add(new dev.specwriter.domain.llm.ProposeQuestionsResult(List.of(
                new dev.specwriter.domain.llm.ProposedQuestion(dev.specwriter.domain.question.QuestionTheme.GOAL,
                        "Quel objectif ?", "Cadrer", List.of(), "", List.of())), List.of(),
                java.util.Set.of(dev.specwriter.domain.question.QuestionTheme.ACTORS)));
        var asked = start.run(NEED, null);
        assertThat(asked.questionnaire().pending()).hasSize(1);
        assertThat(store.find(asked.session().id())).get().extracting(RelatedIssues::awaitingDecision).isEqualTo(false);
    }
}
