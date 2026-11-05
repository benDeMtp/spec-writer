package dev.specwriter.application.specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.application.session.ManageSessions;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.jira.LinkedJiraTicket;
import dev.specwriter.domain.jira.LinkedJiraTicket.SyncState;
import dev.specwriter.domain.llm.DraftSpecification;
import dev.specwriter.domain.llm.GenerationRequest;
import dev.specwriter.domain.question.Questionnaire;
import dev.specwriter.domain.session.Session;
import dev.specwriter.domain.session.SessionState;
import dev.specwriter.domain.specification.SpecificationVersion;
import dev.specwriter.domain.specification.Specs;
import dev.specwriter.domain.specification.VersionOrigin;
import dev.specwriter.support.JiraWorld;
import dev.specwriter.support.StubLlm;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T035 : suppression d'une version locale (FR-014, FR-015, FR-017 à FR-019, research.md R8). */
class DeleteVersionTest {

    final JiraWorld w = new JiraWorld().grant(ConsentService.COPILOT);
    final OperationRegistry operations = new OperationRegistry();
    DeleteVersion delete;
    ManageSessions manage;
    Session session;

    @BeforeEach
    void setUp() {
        delete = new DeleteVersion(w.sessions, w.versions, w.tickets, w.proposals, operations, w.clock);
        manage = new ManageSessions(w.sessions, w.versions, w.projects, w.clock, w.proposals, operations);
        session = w.generatedSession();
        add(2);
        add(3);
    }

    void add(int number) {
        w.versions.save(new SpecificationVersion(w.ids.next(), session.id(), number, Specs.valid(), "fp" + number,
                VersionOrigin.AI_EDIT, "# v" + number, w.clock.now()));
    }

    List<Integer> numbers() {
        return w.versions.list(session.id()).stream().map(SpecificationVersion::number).toList();
    }

    LinkedJiraTicket ticket() {
        return w.tickets.findBySession(session.id()).orElseThrow();
    }

    @Test
    void lesAutresNumerosNeChangentPasEtUnNumeroSupprimeNestPasReattribue() {
        delete.run(session.id(), 2);
        assertThat(numbers()).containsExactly(1, 3);
        delete.run(session.id(), 3);
        assertThat(numbers()).containsExactly(1);
        assertThat(w.versions.nextNumber(session.id())).isEqualTo(4);

        ProposeAiEdit propose = new ProposeAiEdit(w.sessions, w.projects, w.versions, w.llm, w.policy, w.proposals);
        AcceptAiEdit accept = new AcceptAiEdit(w.sessions, w.versions, w.tickets, w.proposals, w.clock, w.ids);
        w.llm.edits.add(JiraWorld.draft("Après suppression"));
        SpecificationVersion next = accept.run(session.id(), propose.run(session.id(), "Précise").id());
        assertThat(next.number()).isEqualTo(4);
        assertThat(numbers()).containsExactly(1, 4);
    }

    @Test
    void leLienJiraSuitLaDerniereVersionRestanteEtLEtatEstRecalcule() {
        w.link(session, "PROJ-3", "d");
        w.tickets.save(ticket().localAhead());

        // les versions plus récentes que celle envoyée disparaissent : le ticket est de nouveau à jour
        delete.run(session.id(), 3);
        delete.run(session.id(), 2);
        assertThat(ticket().linkedVersionNumber()).isEqualTo(1);
        assertThat(ticket().syncState()).isEqualTo(SyncState.IN_SYNC);

        // la version envoyée disparaît : le lien passe à la dernière restante, qui n'est pas dans Jira
        add(4);
        w.tickets.save(ticket().localAhead());
        delete.run(session.id(), 1);
        assertThat(ticket().linkedVersionNumber()).isEqualTo(4);
        assertThat(ticket().syncState()).isEqualTo(SyncState.LOCAL_AHEAD);

        // un conflit en cours le reste
        add(5);
        w.tickets.save(ticket().conflict());
        delete.run(session.id(), 5);
        assertThat(ticket().syncState()).isEqualTo(SyncState.CONFLICT);
        assertThat(w.tracker.writes).isEmpty();
    }

    @Test
    void sansVersionLaSessionRedevientPreteAGenererAvecSonBesoinEtSesReponses() {
        w.link(session, "PROJ-3", "d");
        delete.run(session.id(), 1);
        delete.run(session.id(), 2);
        delete.run(session.id(), 3);
        assertThat(numbers()).isEmpty();
        Session kept = w.sessions.find(session.id()).orElseThrow();
        assertThat(kept.state()).isEqualTo(SessionState.READY_TO_GENERATE);
        assertThat(kept.need()).isEqualTo(session.need());
        assertThat(w.sessions.questionnaire(session.id())).contains(Questionnaire.empty());
        assertThat(ticket().linkedVersionNumber()).isZero();
        assertThat(w.versions.nextNumber(session.id())).isEqualTo(4);
    }

    @Test
    void refuseePendantUneOperationDeLIA() {
        try (OperationRegistry.Operation running = operations.begin(session.id())) {
            assertThatThrownBy(() -> delete.run(session.id(), 2)).isInstanceOfSatisfying(DomainException.class,
                    e -> assertThat(e.code()).isEqualTo(ErrorCode.OPERATION_IN_PROGRESS));
            assertThatThrownBy(() -> manage.delete(session.id())).isInstanceOfSatisfying(DomainException.class,
                    e -> assertThat(e.code()).isEqualTo(ErrorCode.OPERATION_IN_PROGRESS));
        }
        assertThat(numbers()).containsExactly(1, 2, 3);
        delete.run(session.id(), 2);
        assertThat(numbers()).containsExactly(1, 3);
    }

    @Test
    void lesPropositionsEnMemoireSontPurgees() {
        ProposeAiEdit propose = new ProposeAiEdit(w.sessions, w.projects, w.versions, w.llm, w.policy, w.proposals);
        AcceptAiEdit accept = new AcceptAiEdit(w.sessions, w.versions, w.tickets, w.proposals, w.clock, w.ids);
        w.llm.edits.add(JiraWorld.draft("Proposée"));
        ProposalStore.Proposal pending = propose.run(session.id(), "Précise");
        delete.run(session.id(), 2);
        assertThatThrownBy(() -> accept.run(session.id(), pending.id())).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));

        w.llm.edits.add(JiraWorld.draft("Proposée encore"));
        UUID id = propose.run(session.id(), "Précise").id();
        manage.delete(session.id());
        assertThat(w.proposals.take(session.id(), id)).isEmpty();
        assertThat(w.sessions.find(session.id())).isEmpty();
    }

    @Test
    void versionOuSessionInconnue() {
        assertThatThrownBy(() -> delete.run(session.id(), 9)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> delete.run(UUID.randomUUID(), 1)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    /** Les cas d'usage de l'IA ouvrent une opération pendant l'appel, et la ferment ensuite (FR-019). */
    @Test
    void laGenerationEtLaPropositionOuvrentUneOperation() {
        List<Boolean> seen = new ArrayList<>();
        StubLlm watching = new StubLlm() {
            @Override
            public DraftSpecification generateSpecification(GenerationRequest request) {
                seen.add(operations.running(session.id()));
                return super.generateSpecification(request);
            }

            @Override
            public DraftSpecification editSpecification(dev.specwriter.domain.llm.EditRequest request) {
                seen.add(operations.running(session.id()));
                return super.editSpecification(request);
            }
        };
        watching.drafts.add(JiraWorld.draft("Générée"));
        watching.edits.add(JiraWorld.draft("Modifiée"));
        w.sessions.save(session.readyToGenerate(w.clock.now()), Questionnaire.empty());
        new GenerateSpecification(w.sessions, w.versions, watching, w.policy, w.clock, w.ids, "test",
                dev.specwriter.application.knowledge.SessionKnowledge.sheetOnly(w.projects), operations).run(session.id());
        new ProposeAiEdit(w.sessions, w.versions, watching, w.policy, w.proposals,
                dev.specwriter.application.knowledge.SessionKnowledge.sheetOnly(w.projects), operations)
                .run(session.id(), "Précise");
        assertThat(seen).containsExactly(true, true);
        assertThat(operations.running(session.id())).isFalse();
    }
}
