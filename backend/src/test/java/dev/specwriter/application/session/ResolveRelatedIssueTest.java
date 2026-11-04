package dev.specwriter.application.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.application.jira.ImportJiraIssue;
import dev.specwriter.application.knowledge.CompareWithIssue;
import dev.specwriter.application.knowledge.RelatedIssues;
import dev.specwriter.application.knowledge.RelatedIssues.Decision;
import dev.specwriter.application.knowledge.RelatedIssuesStore;
import dev.specwriter.application.specification.ProposeAiEdit;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.jira.JiraIssue;
import dev.specwriter.domain.llm.IssueComparison;
import dev.specwriter.domain.llm.ProposeQuestionsResult;
import dev.specwriter.domain.llm.ProposedQuestion;
import dev.specwriter.domain.port.SessionRelationRepository;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.question.QuestionTheme;
import dev.specwriter.domain.question.Questionnaire;
import dev.specwriter.domain.session.Need;
import dev.specwriter.domain.session.Session;
import dev.specwriter.domain.session.SessionRelation;
import dev.specwriter.support.JiraWorld;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T106 : choix devant les tickets proches et comparaison (FR-035, research.md R23). */
class ResolveRelatedIssueTest {

    static final String NEED = "Le gestionnaire doit pouvoir annuler une facture émise par un avoir total.";

    final JiraWorld w = new JiraWorld().configureJira().grant(ConsentService.JIRA).grant(ConsentService.COPILOT);
    final RelatedIssuesStore store = new RelatedIssuesStore();
    final Map<UUID, SessionRelation> saved = new HashMap<>();
    final SessionRelationRepository relations = new SessionRelationRepository() {
        @Override
        public Optional<SessionRelation> find(UUID sessionId) {
            return Optional.ofNullable(saved.get(sessionId));
        }

        @Override
        public void save(SessionRelation relation) {
            saved.put(relation.sessionId(), relation);
        }
    };
    ResolveRelatedIssue resolve;
    CompareWithIssue compare;
    Session session;

    static RelatedIssues.Issue issue(String key, boolean closed) {
        return new RelatedIssues.Issue(key, "Titre " + key, closed ? "Terminé" : "À faire", closed ? "done" : "new", closed,
                true, Instant.parse("2026-11-01T10:00:00Z"), "Même problème", "https://jira.example.com/browse/" + key);
    }

    static ProposeQuestionsResult batch() {
        return new ProposeQuestionsResult(List.of(new ProposedQuestion(QuestionTheme.GOAL, "Quel objectif ?", "Cadrer",
                List.of(), "", List.of())), List.of(), Set.of(QuestionTheme.ACTORS));
    }

    @BeforeEach
    void setUp() {
        ImportJiraIssue importer = new ImportJiraIssue(w.tracker, w.jira, w.markup, w.llm, w.policy, w.sessions,
                w.projects, w.versions, w.tickets, w.clock, w.ids);
        ProposeAiEdit propose = new ProposeAiEdit(w.sessions, w.projects, w.versions, w.llm, w.policy, w.proposals);
        ManageSessions manage = new ManageSessions(w.sessions, w.versions, w.projects, w.clock);
        resolve = new ResolveRelatedIssue(w.sessions, w.projects, store, relations, importer, propose, manage, w.llm,
                w.clock, w.ids);
        compare = new CompareWithIssue(w.sessions, store, w.jira, w.tracker, w.markup, w.llm, w.policy);
        session = Session.start(w.ids.next(), Project.DEFAULT_ID, Need.of(NEED), null, w.clock.now());
        w.sessions.save(session, Questionnaire.empty());
        store.save(session.id(), new RelatedIssues(RelatedIssues.Status.READY,
                List.of(issue("PROJ-7", true), issue("PROJ-9", false)), null));
    }

    @Test
    void lierUneNouvelleStoryAuTicketFerme() {
        w.llm.questions.add(batch());
        ResolveRelatedIssue.Outcome outcome = resolve.run(session.id(), Decision.LINK_NEW, "PROJ-7");
        assertThat(outcome.sessionId()).isEqualTo(session.id());
        assertThat(outcome.relatesTo()).isEqualTo("PROJ-7");
        assertThat(saved.get(session.id())).isEqualTo(new SessionRelation(session.id(), "PROJ-7",
                SessionRelation.Link.PENDING));
        // les questions viennent après le choix
        assertThat(w.sessions.questionnaire(session.id()).orElseThrow().pending()).hasSize(1);
        assertThat(store.find(session.id()).orElseThrow().decision()).isEqualTo(Decision.LINK_NEW);
    }

    @Test
    void unTicketNonFermeNeSeLiePas() {
        assertThatThrownBy(() -> resolve.run(session.id(), Decision.LINK_NEW, "PROJ-9"))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> resolve.run(session.id(), Decision.SAME, "PROJ-7")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> resolve.run(session.id(), Decision.DIFFERENT, "AUTRE-1"))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> resolve.run(session.id(), null, null)).isInstanceOf(DomainException.class);
        assertThat(w.llm.calls()).isZero();
    }

    @Test
    void laMemeStoryRemplaceLaSessionParLImportPuisProposeLeBesoin() {
        w.tracker.issues.put("PROJ-9", new JiraIssue("PROJ-9", "Annuler une facture",
                "Il faut réserver une salle pour annuler une facture.", JiraWorld.JIRA_T0));
        w.llm.structures.add(JiraWorld.draft("Annuler une facture"));
        w.llm.edits.add(JiraWorld.draft("Annuler une facture par un avoir"));

        ResolveRelatedIssue.Outcome outcome = resolve.run(session.id(), Decision.SAME, "PROJ-9");
        assertThat(outcome.sessionId()).isNotEqualTo(session.id());
        assertThat(w.tickets.findByIssueKey("PROJ-9")).get().extracting(t -> t.sessionId()).isEqualTo(outcome.sessionId());
        assertThat(outcome.proposal()).isNotNull();
        assertThat(w.llm.editRequests).singleElement().extracting(r -> r.instruction()).isEqualTo(NEED);
        assertThat(w.sessions.find(session.id())).isEmpty();
        assertThat(store.find(session.id())).isEmpty();
    }

    @Test
    void echecDeLaPropositionGardeLaSessionDuBesoin() {
        w.tracker.issues.put("PROJ-9", new JiraIssue("PROJ-9", "Annuler", "Il faut réserver une salle.",
                JiraWorld.JIRA_T0));
        w.llm.structures.add(JiraWorld.draft("Annuler"));
        assertThatThrownBy(() -> resolve.run(session.id(), Decision.SAME, "PROJ-9")).isInstanceOf(DomainException.class);
        assertThat(w.sessions.find(session.id())).isPresent();
    }

    @Test
    void differentOuAucunLaRedactionContinue() {
        w.llm.questions.add(batch());
        ResolveRelatedIssue.Outcome outcome = resolve.run(session.id(), Decision.DIFFERENT, "PROJ-9");
        assertThat(outcome.relatesTo()).isNull();
        assertThat(w.sessions.questionnaire(session.id()).orElseThrow().pending()).hasSize(1);
        assertThat(saved).isEmpty();

        // un second choix ne redemande pas de questions
        resolve.run(session.id(), Decision.NONE, null);
        assertThat(w.llm.questionRequests).hasSize(1);
    }

    @Test
    void comparaisonDuBesoinEtDuTicket() {
        w.tracker.issues.put("PROJ-9", new JiraIssue("PROJ-9", "Facture 9", "wiki:Annuler *partiellement*.",
                JiraWorld.JIRA_T0));
        w.llm.comparisons.add(new IssueComparison(List.of("Annuler une facture"),
                java.util.stream.IntStream.range(0, 12).mapToObj(i -> "Écart " + i).toList(),
                IssueComparison.Suggestion.DIFFERENT));
        IssueComparison comparison = compare.run(session.id(), "PROJ-9");
        assertThat(comparison.common()).containsExactly("Annuler une facture");
        assertThat(comparison.different()).hasSize(8);
        assertThat(comparison.suggestion()).isEqualTo(IssueComparison.Suggestion.DIFFERENT);
        assertThat(w.llm.compareRequests).singleElement().satisfies(r -> {
            assertThat(r.need()).isEqualTo(NEED);
            assertThat(r.description()).isEqualTo("Annuler *partiellement*.");
            assertThat(r.status()).isEqualTo("À faire");
        });
        assertThatThrownBy(() -> compare.run(session.id(), "AUTRE-1")).isInstanceOf(DomainException.class);
    }
}
