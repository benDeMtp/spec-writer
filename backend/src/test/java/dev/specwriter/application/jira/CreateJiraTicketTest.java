package dev.specwriter.application.jira;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.jira.ConflictDetector;
import dev.specwriter.domain.jira.IssueTrackerException;
import dev.specwriter.domain.jira.IssueType;
import dev.specwriter.domain.jira.LinkedJiraTicket;
import dev.specwriter.domain.jira.RequiredField;
import dev.specwriter.domain.jira.RequiredField.Kind;
import dev.specwriter.domain.session.Session;
import dev.specwriter.support.JiraWorld;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T126 : aperçu et création du ticket unique (FR-036 à FR-038). */
class CreateJiraTicketTest {

    final JiraWorld w = new JiraWorld().configureJira().grant(ConsentService.JIRA);
    PreviewJiraTicket preview;
    CreateJiraTicket create;
    Session session;
    String markdown;

    @BeforeEach
    void setUp() {
        preview = new PreviewJiraTicket(w.versions, w.tickets, w.markup, w.tracker, w.jira);
        create = new CreateJiraTicket(w.tracker, w.jira, w.markup, w.versions, w.tickets, w.clock);
        session = w.generatedSession();
        markdown = w.versions.find(session.id(), 1).orElseThrow().markdown();
        w.tracker.required.put("10001", List.of(
                new RequiredField("summary", "Résumé", Kind.TEXT, List.of()),
                new RequiredField("reporter", "Rapporteur", Kind.UNSUPPORTED, List.of()),
                new RequiredField("customfield_1", "Équipe", Kind.OPTION,
                        List.of(new RequiredField.Option("1", "Alpha"), new RequiredField.Option("2", "Beta")))));
    }

    @Test
    void apercuStoryParDefautTitreDescriptionEtChampsAFournir() {
        PreviewJiraTicket.Preview p = preview.run(session.id(), 1, null);
        assertThat(p.mode()).isEqualTo(PreviewJiraTicket.Mode.CREATE);
        assertThat(p.projectKey()).isEqualTo("PROJ");
        assertThat(p.issueTypes()).extracting(IssueType::name).containsExactly("Bug", "Story");
        assertThat(p.defaultIssueTypeId()).isEqualTo("10001");
        assertThat(p.issueTypeId()).isEqualTo("10001");
        assertThat(p.summary()).isEqualTo(w.versions.find(session.id(), 1).orElseThrow().content().title());
        assertThat(p.descriptionWiki()).isEqualTo("wiki:" + markdown);
        assertThat(p.requiredFields()).extracting(RequiredField::id).containsExactly("customfield_1");
        assertThat(w.tracker.writes).isEmpty();
    }

    @Test
    void sansStoryLePremierTypeEtTypeChoisi() {
        w.tracker.types.removeIf(t -> t.name().equals("Story"));
        assertThat(preview.run(session.id(), 1, null).defaultIssueTypeId()).isEqualTo("10002");
        w.tracker.types.add(new IssueType("10001", "Story"));
        PreviewJiraTicket.Preview bug = preview.run(session.id(), 1, "10002");
        assertThat(bug.issueTypeId()).isEqualTo("10002");
        assertThat(bug.requiredFields()).isEmpty();
        assertThatThrownBy(() -> preview.run(session.id(), 1, "999")).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void creationConfirmeeLieeEtEnregistree() {
        LinkedJiraTicket t = create.run(session.id(), 1, true, "10001", Map.of("customfield_1", "2"));
        assertThat(t.issueKey()).isEqualTo("PROJ-101");
        assertThat(t.issueUrl()).isEqualTo("https://jira.example.com/browse/PROJ-101");
        assertThat(t.issueType()).isEqualTo("Story");
        assertThat(t.linkedVersionNumber()).isEqualTo(1);
        assertThat(t.syncState()).isEqualTo(LinkedJiraTicket.SyncState.IN_SYNC);
        assertThat(t.lastPushedAt()).isEqualTo(w.clock.now());
        assertThat(t.lastKnownDescriptionHash()).isEqualTo(ConflictDetector.hash("wiki:" + markdown));
        assertThat(w.tickets.findBySession(session.id())).contains(t);
        assertThat(w.tracker.created.getFirst()).containsEntry("summary", w.versions.find(session.id(), 1).orElseThrow()
                .content().title()).containsEntry("description", "wiki:" + markdown)
                .containsEntry("customfield_1", Map.of("id", "2"));
    }

    @Test
    void sansConfirmationRienNestCree() {
        assertThatThrownBy(() -> create.run(session.id(), 1, false, "10001", Map.of("customfield_1", "2")))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThat(w.tracker.calls).isEmpty();
    }

    @Test
    void champObligatoireManquantOuInvalide() {
        assertThatThrownBy(() -> create.run(session.id(), 1, true, "10001", Map.of()))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(e.userMessage()).contains("Équipe");
                });
        assertThatThrownBy(() -> create.run(session.id(), 1, true, "10001", Map.of("customfield_1", "9")))
                .isInstanceOf(DomainException.class);
        assertThat(w.tracker.writes).isEmpty();
    }

    @Test
    void sessionDejaLiee() {
        w.link(session, "PROJ-7", "wiki:d");
        assertThatThrownBy(() -> create.run(session.id(), 1, true, "10001", Map.of("customfield_1", "1")))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.ALREADY_LINKED));
        assertThat(w.tracker.writes).isEmpty();
        assertThat(preview.run(session.id(), 1, null).mode()).isEqualTo(PreviewJiraTicket.Mode.UPDATE);
    }

    @Test
    void refusDeJiraAvecSaCauseEtSpecificationIntacte() {
        w.tracker.createFailure = new IssueTrackerException(IssueTrackerException.Kind.REJECTED, "refus", null,
                List.of("components : Composant inconnu"));
        assertThatThrownBy(() -> create.run(session.id(), 1, true, "10001", Map.of("customfield_1", "1")))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.JIRA_REJECTED);
                    assertThat(e.userMessage()).contains("Composant inconnu");
                });
        assertThat(w.tickets.all()).isEmpty();
        assertThat(w.versions.list(session.id())).hasSize(1);
    }

    @Test
    void sansConsentementRienNestAppele() {
        JiraWorld bare = new JiraWorld().configureJira();
        Session s = bare.generatedSession();
        CreateJiraTicket c = new CreateJiraTicket(bare.tracker, bare.jira, bare.markup, bare.versions,
                bare.tickets, bare.clock);
        PreviewJiraTicket p = new PreviewJiraTicket(bare.versions, bare.tickets, bare.markup, bare.tracker, bare.jira);
        assertThatThrownBy(() -> c.run(s.id(), 1, true, "10001", Map.of())).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.CONSENT_REQUIRED));
        assertThatThrownBy(() -> p.run(s.id(), 1, null)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.CONSENT_REQUIRED));
        assertThat(bare.tracker.calls).isEmpty();
    }

    @Test
    void sessionOuVersionAbsente() {
        assertThatThrownBy(() -> create.run(UUID.randomUUID(), 1, true, "10001", Map.of())).isInstanceOfSatisfying(
                DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> create.run(session.id(), 4, true, "10001", Map.of())).isInstanceOfSatisfying(
                DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    }
}
