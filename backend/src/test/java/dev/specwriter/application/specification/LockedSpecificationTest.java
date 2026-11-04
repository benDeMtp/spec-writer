package dev.specwriter.application.specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.application.jira.PushToJira;
import dev.specwriter.application.session.AnswerQuestion;
import dev.specwriter.application.session.ChangeStatus;
import dev.specwriter.application.session.ConcludeQuestionnaire;
import dev.specwriter.application.session.EvolveSpecification;
import dev.specwriter.application.session.ManageSessions;
import dev.specwriter.application.session.SessionSnapshot;
import dev.specwriter.application.session.StartSession;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.llm.DraftSpecification;
import dev.specwriter.domain.llm.GenerationRequest;
import dev.specwriter.domain.question.Questionnaire;
import dev.specwriter.domain.session.ImplementationStatus;
import dev.specwriter.domain.session.Session;
import dev.specwriter.domain.specification.SpecificationVersion;
import dev.specwriter.support.JiraWorld;
import dev.specwriter.support.StubLlm;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T048 : une spécification implémentée est verrouillée ; l'évolution en repart (FR-026 à FR-026b). */
class LockedSpecificationTest {

    final JiraWorld w = new JiraWorld().configureJira().grant(ConsentService.COPILOT).grant(ConsentService.JIRA);
    final OperationRegistry operations = new OperationRegistry();
    ChangeStatus status;
    Session session;

    @BeforeEach
    void setUp() {
        status = new ChangeStatus(w.sessions, w.clock);
        session = w.generatedSession();
        w.link(session, "PROJ-5", "d");
    }

    void implemented() {
        status.run(session.id(), ImplementationStatus.READY, false);
        status.run(session.id(), ImplementationStatus.IN_PROGRESS, false);
        status.run(session.id(), ImplementationStatus.IMPLEMENTED, false);
    }

    static void locked(ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.SPECIFICATION_LOCKED));
    }

    @Test
    void toutesLesEcrituresSontRefuseesSaufLaSuppressionDeLaSession() {
        ProposeAiEdit propose = new ProposeAiEdit(w.sessions, w.projects, w.versions, w.llm, w.policy, w.proposals);
        AcceptAiEdit accept = new AcceptAiEdit(w.sessions, w.versions, w.tickets, w.proposals, w.clock, w.ids);
        w.llm.edits.add(JiraWorld.draft("Proposée avant le verrou"));
        UUID pending = propose.run(session.id(), "Précise").id();
        implemented();

        UUID id = session.id();
        locked(() -> new AnswerQuestion(w.sessions, w.projects, w.llm, w.policy, w.clock, w.ids).run(id, UUID.randomUUID(), "Oui",
                false));
        locked(() -> new ConcludeQuestionnaire(w.sessions, w.clock).run(id));
        locked(() -> new GenerateSpecification(w.sessions, w.projects, w.versions, w.llm, w.policy, w.clock, w.ids, "t").run(id));
        locked(() -> propose.run(id, "Encore"));
        locked(() -> accept.run(id, pending));
        locked(() -> new PushToJira(w.tracker, w.jira, w.markup, w.llm, w.policy, w.sessions, w.projects, w.versions, w.tickets,
                w.proposals, w.clock, w.ids).run(id, 1, true, null));
        locked(() -> new DeleteVersion(w.sessions, w.versions, w.tickets, w.proposals, operations, w.clock).run(id, 1));
        assertThat(w.versions.list(id)).hasSize(1);
        assertThat(w.tracker.writes).isEmpty();

        new ManageSessions(w.sessions, w.versions, w.projects, w.clock, w.proposals, operations).delete(id);
        assertThat(w.sessions.find(id)).isEmpty();
    }

    @Test
    void uneGenerationEnCoursQuandLaFeatureEstImplementeeNEstPasEnregistree() {
        w.sessions.save(session.readyToGenerate(w.clock.now()), Questionnaire.empty());
        StubLlm slow = new StubLlm() {
            @Override
            public DraftSpecification generateSpecification(GenerationRequest request) {
                implemented();
                return JiraWorld.draft("Générée pendant le passage à implémentée");
            }
        };
        locked(() -> new GenerateSpecification(w.sessions, w.projects, w.versions, slow, w.policy, w.clock, w.ids, "t")
                .run(session.id()));
        assertThat(w.versions.list(session.id())).extracting(SpecificationVersion::number).containsExactly(1);
    }

    @Test
    void uneNouvelleVersionNeChangeJamaisLeStatut() {
        status.run(session.id(), ImplementationStatus.READY, false);
        status.run(session.id(), ImplementationStatus.IN_PROGRESS, false);
        ProposeAiEdit propose = new ProposeAiEdit(w.sessions, w.projects, w.versions, w.llm, w.policy, w.proposals);
        AcceptAiEdit accept = new AcceptAiEdit(w.sessions, w.versions, w.tickets, w.proposals, w.clock, w.ids);
        w.llm.edits.add(JiraWorld.draft("Version 2"));
        accept.run(session.id(), propose.run(session.id(), "Précise").id());
        assertThat(w.sessions.implementation(session.id()).orElseThrow().status()).isEqualTo(ImplementationStatus.IN_PROGRESS);
    }

    @Test
    void evolutionBrouillonDuMemeProjetQuiCiteSonOrigine() {
        implemented();
        w.llm.questions.add(StubLlm.none());
        StartSession start = new StartSession(w.sessions, w.projects, w.llm, w.policy, w.clock, w.ids);
        SessionSnapshot evolved = new EvolveSpecification(w.sessions, w.versions, w.projects, start).run(session.id());
        Session created = evolved.session();
        assertThat(created.id()).isNotEqualTo(session.id());
        assertThat(created.projectId()).isEqualTo(session.projectId());
        assertThat(created.need().text()).isEqualTo(w.versions.find(session.id(), 1).orElseThrow().markdown().strip());
        assertThat(created.name()).startsWith("Évolution de ");
        assertThat(w.sessions.implementation(created.id()).orElseThrow()).satisfies(i -> {
            assertThat(i.status()).isEqualTo(ImplementationStatus.DRAFT);
            assertThat(i.evolvedFrom()).isEqualTo(session.id());
        });
        assertThat(w.sessions.implementation(session.id()).orElseThrow().status()).isEqualTo(ImplementationStatus.IMPLEMENTED);
    }

    @Test
    void retablirDemandeUneConfirmation() {
        implemented();
        assertThatThrownBy(() -> status.run(session.id(), ImplementationStatus.IN_PROGRESS, false))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.UNLOCK_CONFIRMATION_REQUIRED));
        assertThat(status.run(session.id(), ImplementationStatus.IN_PROGRESS, true).implementedAt()).isNull();
    }
}
