package dev.specwriter.application.specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.jira.LinkedJiraTicket.SyncState;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.session.Session;
import dev.specwriter.domain.specification.SpecificationVersion;
import dev.specwriter.domain.specification.VersionOrigin;
import dev.specwriter.support.JiraWorld;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T102 : proposition et diff sans nouvelle version, puis acceptation (FR-044, US6-4, US6-5). */
class AiEditTest {

    final JiraWorld w = new JiraWorld().grant(ConsentService.COPILOT);
    ProposeAiEdit propose;
    AcceptAiEdit accept;
    Session session;

    @BeforeEach
    void setUp() {
        propose = new ProposeAiEdit(w.sessions, w.projects, w.versions, w.llm, w.policy, w.proposals);
        accept = new AcceptAiEdit(w.sessions, w.versions, w.tickets, w.proposals, w.clock, w.ids);
        session = w.generatedSession();
    }

    @Test
    void propositionAvecDiffSansNouvelleVersion() {
        w.llm.edits.add(JiraWorld.draft("Réservation modifiée"));
        ProposalStore.Proposal p = propose.run(session.id(), "Ajoute l'annulation");
        assertThat(p.baseVersion()).isEqualTo(1);
        assertThat(p.origin()).isEqualTo(VersionOrigin.AI_EDIT);
        assertThat(p.unifiedDiff()).startsWith("--- v1\n+++ proposition");
        assertThat(p.markdown()).contains("Réservation modifiée");
        assertThat(p.content().useCases().getFirst().code()).isEqualTo("UC-01");
        assertThat(w.versions.list(session.id())).hasSize(1);
        assertThat(w.llm.editRequests.getFirst().instruction()).isEqualTo("Ajoute l'annulation");
        assertThat(w.llm.editRequests.getFirst().currentMarkdown())
                .isEqualTo(w.versions.find(session.id(), 1).orElseThrow().markdown());
        assertThat(w.llm.editRequests.getFirst().need()).isEqualTo(JiraWorld.NEED);
    }

    @Test
    void sortieInvalideRedemandeeAvecSesEcarts() {
        w.llm.edits.add(JiraWorld.invalidDraft());
        w.llm.edits.add(JiraWorld.draft("Corrigée"));
        propose.run(session.id(), "Précise");
        assertThat(w.llm.editRequests).hasSize(2);
        assertThat(w.llm.editRequests.get(0).correction()).isNull();
        assertThat(w.llm.editRequests.get(1).correction()).isNotBlank();
    }

    @Test
    void acceptationCreeUneVersionAiEditEtTicketLieEnAvance() {
        w.link(session, "PROJ-3", "d");
        w.llm.edits.add(JiraWorld.draft("Réservation modifiée"));
        ProposalStore.Proposal p = propose.run(session.id(), "Ajoute l'annulation");
        SpecificationVersion v = accept.run(session.id(), p.id());
        assertThat(v.number()).isEqualTo(2);
        assertThat(v.origin()).isEqualTo(VersionOrigin.AI_EDIT);
        assertThat(v.markdown()).isEqualTo(p.markdown());
        assertThat(w.versions.list(session.id())).hasSize(2);
        assertThat(w.tickets.findBySession(session.id()).orElseThrow().syncState()).isEqualTo(SyncState.LOCAL_AHEAD);
        assertThat(w.tracker.writes).isEmpty();
        // une proposition ne s'applique qu'une fois
        assertThatThrownBy(() -> accept.run(session.id(), p.id())).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void propositionInconnueExpireeOuDUneAutreSession() {
        assertThatThrownBy(() -> accept.run(session.id(), UUID.randomUUID())).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));

        w.llm.edits.add(JiraWorld.draft("Autre"));
        ProposalStore.Proposal p = propose.run(session.id(), "Modifie");
        Session other = w.generatedSession();
        assertThatThrownBy(() -> accept.run(other.id(), p.id())).isInstanceOf(DomainException.class);

        w.clock.advance(Duration.ofMinutes(31));
        assertThatThrownBy(() -> accept.run(session.id(), p.id())).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
        assertThat(w.versions.list(session.id())).hasSize(1);
    }

    @Test
    void instructionSessionEtConsentement() {
        assertThatThrownBy(() -> propose.run(session.id(), " ")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> propose.run(session.id(), "x".repeat(2001))).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> propose.run(UUID.randomUUID(), "Modifie")).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
        JiraWorld bare = new JiraWorld();
        Session s = bare.generatedSession();
        ProposeAiEdit noConsent = new ProposeAiEdit(bare.sessions, bare.projects, bare.versions, bare.llm, bare.policy, bare.proposals);
        assertThatThrownBy(() -> noConsent.run(s.id(), "Modifie")).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.CONSENT_REQUIRED));
        assertThat(bare.llm.calls()).isZero();
    }

    @Test
    void sansVersionRien() {
        Session empty = Session.start(w.ids.next(), Project.DEFAULT_ID, dev.specwriter.domain.session.Need.of(JiraWorld.NEED),
                null, w.clock.now());
        w.sessions.save(empty, dev.specwriter.domain.question.Questionnaire.empty());
        assertThatThrownBy(() -> propose.run(empty.id(), "Modifie")).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }
}
