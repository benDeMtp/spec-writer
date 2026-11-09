package dev.specwriter.application.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.consent.ConsentPolicy;
import dev.specwriter.domain.consent.ConsentRecord;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.llm.ProposeQuestionsResult;
import dev.specwriter.domain.llm.ProposedQuestion;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.project.ProjectName;
import dev.specwriter.domain.question.QuestionState;
import dev.specwriter.domain.question.QuestionTheme;
import dev.specwriter.domain.session.SessionState;
import dev.specwriter.support.FixedClock;
import dev.specwriter.application.attachment.AttachmentDrafts;
import dev.specwriter.application.knowledge.SessionKnowledge;
import dev.specwriter.domain.attachment.Attachment;
import dev.specwriter.domain.port.AttachmentReader;
import dev.specwriter.support.InMemoryAttachments;
import dev.specwriter.support.InMemoryConsents;
import dev.specwriter.support.InMemoryProjects;
import dev.specwriter.support.InMemorySessions;
import dev.specwriter.support.SequentialIdGenerator;
import dev.specwriter.support.StubLlm;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** StartSession, AnswerQuestion et ConcludeQuestionnaire (T065). */
class SessionUseCasesTest {

    static final String NEED = "Les employés doivent pouvoir réserver une salle de réunion pour une durée donnée.";

    final InMemorySessions sessions = new InMemorySessions();
    final InMemoryConsents consents = new InMemoryConsents();
    final StubLlm llm = new StubLlm();
    final FixedClock clock = new FixedClock();
    final SequentialIdGenerator ids = new SequentialIdGenerator();
    final ConsentPolicy policy = new ConsentPolicy(consents);
    final InMemoryProjects projects = new InMemoryProjects();

    StartSession start;
    AnswerQuestion answer;
    ConcludeQuestionnaire conclude;

    static ProposeQuestionsResult batch(QuestionTheme... themes) {
        return new ProposeQuestionsResult(java.util.Arrays.stream(themes)
                .map(t -> new ProposedQuestion(t, "Question " + t + " ?", "Pour préciser " + t, List.of("Oui", "Non"), "Oui",
                        List.of()))
                .toList(), List.of(), Set.of(QuestionTheme.ACTORS));
    }

    @BeforeEach
    void setUp() {
        consents.record(new ConsentRecord(ConsentService.COPILOT, true, Instant.EPOCH));
        start = new StartSession(sessions, projects, llm, policy, clock, ids);
        answer = new AnswerQuestion(sessions, new InMemoryProjects(), llm, policy, clock, ids);
        conclude = new ConcludeQuestionnaire(sessions, clock);
    }

    @Test
    void demarrageAvecPremierLotEtThemesCouverts() {
        llm.questions.add(batch(QuestionTheme.GOAL, QuestionTheme.ERRORS));
        SessionSnapshot s = start.run(NEED, null);
        assertThat(s.questionnaire().currentBatch()).hasSize(2);
        assertThat(s.session().state()).isEqualTo(SessionState.QUESTIONING);
        assertThat(sessions.find(s.session().id())).isPresent();
        assertThat(llm.questionRequests.getFirst().known()).isEmpty();
        assertThat(llm.questionRequests.getFirst().remainingThemes()).contains(QuestionTheme.ACTORS);
        assertThat(s.questionnaire().remainingThemes()).doesNotContain(QuestionTheme.ACTORS);
    }

    @Test
    void laSessionEstCreeeDansLeProjetCourant() {
        Project paie = Project.create(UUID.randomUUID(), ProjectName.of("Paie"), null, null, Instant.EPOCH);
        Project achats = Project.create(UUID.randomUUID(), ProjectName.of("Achats"), null, null, Instant.EPOCH);
        projects.save(paie);
        projects.save(achats);
        llm.questions.add(batch(QuestionTheme.GOAL));
        assertThat(start.run(NEED, null).session().projectId()).isEqualTo(Project.DEFAULT_ID);

        projects.saveCurrentProjectId(paie.id());
        llm.questions.add(batch(QuestionTheme.GOAL));
        assertThat(start.run(NEED, null).session().projectId()).isEqualTo(paie.id());

        // projet courant supprimé : le premier projet par nom
        projects.delete(paie.id());
        llm.questions.add(batch(QuestionTheme.GOAL));
        assertThat(start.run(NEED, null).session().projectId()).isEqualTo(achats.id());
    }

    @Test
    void sansConsentementRienNEstEnvoye() {
        consents.record(new ConsentRecord(ConsentService.COPILOT, false, Instant.EPOCH.plusSeconds(1)));
        assertThatThrownBy(() -> start.run(NEED, null)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.CONSENT_REQUIRED));
        assertThat(llm.questionRequests).isEmpty();
        assertThat(sessions.list()).isEmpty();
    }

    @Test
    void besoinInvalideRefuseAvantLeConsentement() {
        consents.record(new ConsentRecord(ConsentService.COPILOT, false, Instant.EPOCH.plusSeconds(1)));
        assertThatThrownBy(() -> start.run("trop court", null)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.NEED_TOO_SHORT));
    }

    @Test
    void llmIndisponibleAuDemarrageAucuneSession() {
        assertThatThrownBy(() -> start.run(NEED, null)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.LLM_UNAVAILABLE));
        assertThat(sessions.list()).isEmpty();
    }

    // 009 : documents joints sur l'écran du besoin, rattachés au démarrage (research.md R3)

    final InMemoryAttachments attachments = new InMemoryAttachments();
    final AttachmentReader reader = (name, content) -> AttachmentReader.Reading.of(null,
            new String(content, java.nio.charset.StandardCharsets.UTF_8));
    final AttachmentDrafts drafts = new AttachmentDrafts(reader, clock, ids);

    StartSession withAttachments() {
        return new StartSession(sessions, projects, llm, policy, clock, ids, null, null,
                SessionKnowledge.sheetOnly(projects), drafts, attachments);
    }

    UUID draft(String name, String text) {
        return drafts.create(name, text.getBytes(java.nio.charset.StandardCharsets.UTF_8)).id();
    }

    @Test
    void documentsJointsRattachesAuDemarrageDansLOrdre() {
        UUID b = draft("B.pdf", "Texte B");
        UUID a = draft("A.docx", "Texte A");
        llm.questions.add(batch(QuestionTheme.GOAL));
        SessionSnapshot s = withAttachments().run(NEED, null, List.of(b, a));

        assertThat(attachments.list(s.session().id())).extracting(Attachment::fileName, Attachment::ordinal)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("B.pdf", 1), org.assertj.core.groups.Tuple.tuple("A.docx", 2));
        assertThat(drafts.find(List.of(a, b))).isEmpty();
        assertThat(sessions.find(s.session().id())).isPresent();
    }

    @Test
    void echecDuDemarrageAvecDocumentsJointsAucuneSessionEtBrouillonsGardes() {
        UUID a = draft("A.pdf", "Texte A");
        assertThatThrownBy(() -> withAttachments().run(NEED, null, List.of(a)))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.LLM_UNAVAILABLE));
        assertThat(sessions.list()).isEmpty();
        assertThat(drafts.find(List.of(a))).hasSize(1);
        int asked = llm.questionRequests.size();
        // un document joint expiré est refusé avant tout envoi
        assertThatThrownBy(() -> withAttachments().run(NEED, null, List.of(UUID.randomUUID())))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThat(llm.questionRequests).hasSize(asked);
    }

    @Test
    void chaqueReponseSauvegardeeEtLotSuivantDemandeQuandLeLotEstFini() {
        llm.questions.add(batch(QuestionTheme.GOAL, QuestionTheme.ERRORS));
        SessionSnapshot s = start.run(NEED, null);
        UUID id = s.session().id();
        List<UUID> q = s.questionnaire().currentBatch().stream().map(x -> x.id()).toList();

        int saves = sessions.saves;
        clock.advance(Duration.ofMinutes(1));
        SessionSnapshot after = answer.run(id, q.get(0), "Éviter les doublons", false);
        assertThat(sessions.saves).isEqualTo(saves + 1);
        assertThat(sessions.find(id).orElseThrow().updatedAt()).isEqualTo(clock.now());
        assertThat(after.questionnaire().currentBatch()).hasSize(1);
        assertThat(llm.questionRequests).hasSize(1);

        llm.questions.add(batch(QuestionTheme.EDGE_CASES));
        SessionSnapshot next = answer.run(id, q.get(1), "", true);
        assertThat(next.questionnaire().question(q.get(1)).orElseThrow().state()).isEqualTo(QuestionState.SKIPPED);
        assertThat(next.questionnaire().currentBatch()).singleElement().satisfies(x -> assertThat(x.batch()).isEqualTo(2));
        assertThat(llm.questionRequests.get(1).known()).hasSize(2)
                .anySatisfy(k -> assertThat(k.assumption()).isTrue());
    }

    @Test
    void llmIndisponibleEnCoursDeRouteLesReponsesSontConservees() {
        llm.questions.add(batch(QuestionTheme.GOAL));
        SessionSnapshot s = start.run(NEED, null);
        UUID id = s.session().id();
        UUID q = s.questionnaire().currentBatch().getFirst().id();
        assertThatThrownBy(() -> answer.run(id, q, "Gagner du temps", false))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.LLM_UNAVAILABLE));
        assertThat(sessions.questionnaire(id).orElseThrow().question(q).orElseThrow().answer().value())
                .isEqualTo("Gagner du temps");

        // relance : renvoyer la réponse redemande le lot suivant
        llm.questions.add(StubLlm.none());
        SessionSnapshot retried = answer.run(id, q, "Gagner du temps", false);
        assertThat(retried.session().state()).isEqualTo(SessionState.READY_TO_GENERATE);
    }

    @Test
    void conclusionAvecMajoriteDHypothesesAvertit() {
        llm.questions.add(batch(QuestionTheme.GOAL, QuestionTheme.ERRORS, QuestionTheme.EDGE_CASES));
        SessionSnapshot s = start.run(NEED, null);
        UUID id = s.session().id();
        answer.run(id, s.questionnaire().currentBatch().getFirst().id(), "Gagner du temps", false);
        SessionSnapshot concluded = conclude.run(id);
        assertThat(concluded.session().state()).isEqualTo(SessionState.READY_TO_GENERATE);
        assertThat(concluded.questionnaire().progress().skipped()).isEqualTo(2);
        assertThat(concluded.warnings()).singleElement().asString().contains("hypothèses");
        assertThat(llm.questionRequests).hasSize(1);
    }

    @Test
    void conclusionSansAvertissementSiPeuDHypotheses() {
        llm.questions.add(batch(QuestionTheme.GOAL));
        SessionSnapshot s = start.run(NEED, null);
        llm.questions.add(StubLlm.none());
        answer.run(s.session().id(), s.questionnaire().currentBatch().getFirst().id(), "Gagner du temps", false);
        assertThat(conclude.run(s.session().id()).warnings()).isEmpty();
        assertThatThrownBy(() -> conclude.run(UUID.randomUUID())).isInstanceOf(DomainException.class);
    }
}
