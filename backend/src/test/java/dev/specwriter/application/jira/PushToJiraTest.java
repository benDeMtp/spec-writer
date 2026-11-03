package dev.specwriter.application.jira;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.application.specification.AcceptAiEdit;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.jira.ConflictDetector;
import dev.specwriter.domain.jira.ConflictResolution;
import dev.specwriter.domain.jira.LinkedJiraTicket;
import dev.specwriter.domain.jira.LinkedJiraTicket.SyncState;
import dev.specwriter.domain.session.Session;
import dev.specwriter.domain.specification.SpecificationVersion;
import dev.specwriter.domain.specification.VersionOrigin;
import dev.specwriter.support.JiraWorld;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T103 : envoi confirmé, détection de conflit et ses trois résolutions (FR-047, FR-048, US6-6, US6-7). */
class PushToJiraTest {

    final JiraWorld w = new JiraWorld().configureJira().grant(ConsentService.JIRA).grant(ConsentService.COPILOT);
    PushToJira push;
    Session session;
    String localMarkdown;

    @BeforeEach
    void setUp() {
        push = new PushToJira(w.tracker, w.jira, w.markup, w.llm, w.policy, w.sessions, w.projects, w.versions,
                w.tickets, w.proposals, w.clock, w.ids);
        session = w.generatedSession();
        w.link(session, "PROJ-5", "wiki:ancienne description");
        localMarkdown = w.versions.find(session.id(), 1).orElseThrow().markdown();
        w.clock.advance(Duration.ofHours(1));
    }

    LinkedJiraTicket ticket() {
        return w.tickets.findBySession(session.id()).orElseThrow();
    }

    @Test
    void sansConfirmationRefus() {
        assertThatThrownBy(() -> push.run(session.id(), 1, false, null)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThat(w.tracker.calls).isEmpty();
    }

    @Test
    void sansConflitJiraRelutPuisEcritEtReferenceMiseAJour() {
        PushToJira.Outcome o = push.run(session.id(), 1, true, null);
        assertThat(w.tracker.calls).containsExactly("get PROJ-5", "put PROJ-5", "get PROJ-5");
        assertThat(w.tracker.issues.get("PROJ-5").description()).isEqualTo("wiki:" + localMarkdown);
        assertThat(o).isInstanceOfSatisfying(PushToJira.Pushed.class, p -> {
            assertThat(p.ticket().syncState()).isEqualTo(SyncState.IN_SYNC);
            assertThat(p.ticket().linkedVersionNumber()).isEqualTo(1);
            assertThat(p.ticket().lastKnownUpdated()).isEqualTo(w.tracker.issues.get("PROJ-5").updated());
            assertThat(p.ticket().lastKnownDescriptionHash()).isEqualTo(ConflictDetector.hash("wiki:" + localMarkdown));
            assertThat(p.ticket().lastPushedAt()).isEqualTo(w.clock.now());
        });
        assertThat(ticket()).isEqualTo(((PushToJira.Pushed) o).ticket());
    }

    @Test
    void ticketModifieDansJiraConflitSansEcriture() {
        w.editInJira("PROJ-5", "wiki:modifiée dans Jira");
        PushToJira.Outcome o = push.run(session.id(), 1, true, null);
        assertThat(o).isInstanceOfSatisfying(PushToJira.Conflict.class, c -> {
            assertThat(c.jiraDescriptionMarkdown()).isEqualTo("modifiée dans Jira");
            assertThat(c.localMarkdown()).isEqualTo(localMarkdown);
            assertThat(c.jiraUpdated()).isEqualTo(JiraWorld.JIRA_T0.plusSeconds(600));
        });
        assertThat(w.tracker.writes).isEmpty();
        assertThat(ticket().syncState()).isEqualTo(SyncState.CONFLICT);
    }

    @Test
    void descriptionModifieeSansChangementDeDateAussiDetectee() {
        w.tracker.issues.put("PROJ-5", new dev.specwriter.domain.jira.JiraIssue("PROJ-5", "s", "wiki:autre",
                JiraWorld.JIRA_T0));
        assertThat(push.run(session.id(), 1, true, null)).isInstanceOf(PushToJira.Conflict.class);
    }

    @Test
    void ecraser() {
        w.editInJira("PROJ-5", "wiki:modifiée dans Jira");
        push.run(session.id(), 1, true, null);
        PushToJira.Outcome o = push.run(session.id(), 1, true, ConflictResolution.OVERWRITE);
        assertThat(o).isInstanceOf(PushToJira.Pushed.class);
        assertThat(w.tracker.writes).containsExactly("PROJ-5");
        assertThat(ticket().syncState()).isEqualTo(SyncState.IN_SYNC);
        // la référence est à jour : un nouvel envoi ne voit plus de conflit
        assertThat(push.run(session.id(), 1, true, null)).isInstanceOf(PushToJira.Pushed.class);
    }

    @Test
    void reprendreLaVersionJiraSansEcriture() {
        w.editInJira("PROJ-5", "wiki:modifiée dans Jira");
        w.llm.structures.add(JiraWorld.draft("Version Jira"));
        PushToJira.Outcome o = push.run(session.id(), 1, true, ConflictResolution.TAKE_JIRA);
        assertThat(o).isInstanceOfSatisfying(PushToJira.TakenFromJira.class, t -> {
            assertThat(t.version().number()).isEqualTo(2);
            assertThat(t.version().origin()).isEqualTo(VersionOrigin.JIRA_IMPORT);
            assertThat(t.ticket().syncState()).isEqualTo(SyncState.IN_SYNC);
            assertThat(t.ticket().linkedVersionNumber()).isEqualTo(2);
            assertThat(t.ticket().lastKnownDescriptionHash()).isEqualTo(ConflictDetector.hash("wiki:modifiée dans Jira"));
        });
        assertThat(w.llm.structureRequests.getFirst().text()).isEqualTo("modifiée dans Jira");
        assertThat(w.tracker.writes).isEmpty();
    }

    @Test
    void fusionnerAvecLIaPropositionAValider() {
        w.editInJira("PROJ-5", "wiki:modifiée dans Jira");
        w.llm.merges.add(JiraWorld.draft("Fusion"));
        PushToJira.Outcome o = push.run(session.id(), 1, true, ConflictResolution.AI_MERGE);
        assertThat(o).isInstanceOf(PushToJira.MergeProposed.class);
        var proposal = ((PushToJira.MergeProposed) o).proposal();
        assertThat(proposal.origin()).isEqualTo(VersionOrigin.CONFLICT_MERGE);
        assertThat(w.llm.mergeRequests.getFirst().localMarkdown()).isEqualTo(localMarkdown);
        assertThat(w.llm.mergeRequests.getFirst().jiraMarkdown()).isEqualTo("modifiée dans Jira");
        assertThat(w.versions.list(session.id())).hasSize(1);
        assertThat(w.tracker.writes).isEmpty();

        SpecificationVersion merged = new AcceptAiEdit(w.sessions, w.versions, w.tickets, w.proposals, w.clock, w.ids)
                .run(session.id(), proposal.id());
        assertThat(merged.origin()).isEqualTo(VersionOrigin.CONFLICT_MERGE);
        assertThat(ticket().syncState()).isEqualTo(SyncState.LOCAL_AHEAD);
        // l'état Jira fusionné est la nouvelle référence : l'envoi de la fusion passe sans conflit
        assertThat(push.run(session.id(), 2, true, null)).isInstanceOf(PushToJira.Pushed.class);
        assertThat(w.tracker.writes).containsExactly("PROJ-5");
    }

    @Test
    void consentementEtElementsAbsents() {
        JiraWorld bare = new JiraWorld().configureJira();
        PushToJira p = new PushToJira(bare.tracker, bare.jira, bare.markup, bare.llm, bare.policy, bare.sessions,
                bare.projects, bare.versions, bare.tickets, bare.proposals, bare.clock, bare.ids);
        assertThatThrownBy(() -> p.run(session.id(), 1, true, null)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.CONSENT_REQUIRED));
        assertThat(bare.tracker.calls).isEmpty();

        assertThatThrownBy(() -> push.run(UUID.randomUUID(), 1, true, null)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> push.run(session.id(), 9, true, null)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
        Session unlinked = w.generatedSession();
        assertThatThrownBy(() -> push.run(unlinked.id(), 1, true, null)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void reprendreOuFusionnerDemandeLeConsentementCopilot() {
        JiraWorld noCopilot = new JiraWorld().configureJira().grant(ConsentService.JIRA);
        PushToJira p = new PushToJira(noCopilot.tracker, noCopilot.jira, noCopilot.markup, noCopilot.llm, noCopilot.policy,
                noCopilot.sessions, noCopilot.projects, noCopilot.versions, noCopilot.tickets, noCopilot.proposals,
                noCopilot.clock, noCopilot.ids);
        Session s = noCopilot.generatedSession();
        noCopilot.link(s, "PROJ-6", "wiki:a");
        noCopilot.editInJira("PROJ-6", "wiki:b");
        for (ConflictResolution r : new ConflictResolution[] {ConflictResolution.TAKE_JIRA, ConflictResolution.AI_MERGE}) {
            assertThatThrownBy(() -> p.run(s.id(), 1, true, r)).isInstanceOfSatisfying(DomainException.class,
                    e -> assertThat(e.code()).isEqualTo(ErrorCode.CONSENT_REQUIRED));
        }
        assertThat(noCopilot.llm.calls()).isZero();
        assertThat(noCopilot.tracker.writes).isEmpty();
    }
}
