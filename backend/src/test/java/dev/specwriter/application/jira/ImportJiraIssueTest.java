package dev.specwriter.application.jira;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.jira.ConflictDetector;
import dev.specwriter.domain.jira.IssueTrackerException.Kind;
import dev.specwriter.domain.jira.JiraIssue;
import dev.specwriter.domain.jira.LinkedJiraTicket.SyncState;
import dev.specwriter.domain.session.SessionOrigin;
import dev.specwriter.domain.session.SessionState;
import dev.specwriter.domain.specification.VersionOrigin;
import dev.specwriter.support.JiraWorld;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T101 : import d'un ticket en session liée, sans aucune écriture dans Jira (FR-046). */
class ImportJiraIssueTest {

    static final String DESCRIPTION = "wiki:Les clients doivent pouvoir payer leur panier en ligne par carte bancaire.";

    final JiraWorld w = new JiraWorld().configureJira().grant(ConsentService.JIRA).grant(ConsentService.COPILOT);
    ImportJiraIssue importer;

    @BeforeEach
    void setUp() {
        importer = new ImportJiraIssue(w.tracker, w.jira, w.markup, w.llm, w.policy, w.sessions, w.projects, w.versions,
                w.tickets, w.clock, w.ids);
        w.tracker.issues.put("PAY-4", new JiraIssue("PAY-4", "Paiement en ligne", DESCRIPTION, JiraWorld.JIRA_T0));
    }

    @Test
    void sessionLieeEtPremiereVersionStructuree() {
        w.llm.structures.add(JiraWorld.draft("Paiement"));
        ImportJiraIssue.Imported r = importer.run("PAY-4");
        assertThat(r.session().origin()).isEqualTo(SessionOrigin.JIRA_IMPORT);
        assertThat(r.session().state()).isEqualTo(SessionState.GENERATED);
        assertThat(r.session().name()).isEqualTo("Paiement en ligne");
        assertThat(r.session().need().text()).isEqualTo(DESCRIPTION.substring(5));
        assertThat(r.truncated()).isFalse();
        assertThat(r.version().origin()).isEqualTo(VersionOrigin.JIRA_IMPORT);
        assertThat(r.version().number()).isEqualTo(1);
        assertThat(r.version().content().useCases().getFirst().code()).isEqualTo("UC-01");
        assertThat(w.llm.structureRequests.getFirst().text()).isEqualTo(DESCRIPTION.substring(5));
        assertThat(r.ticket().issueKey()).isEqualTo("PAY-4");
        assertThat(r.ticket().issueUrl()).isEqualTo("https://jira.example.com/browse/PAY-4");
        assertThat(r.ticket().lastKnownUpdated()).isEqualTo(JiraWorld.JIRA_T0);
        assertThat(r.ticket().lastKnownDescriptionHash()).isEqualTo(ConflictDetector.hash(DESCRIPTION));
        assertThat(r.ticket().syncState()).isEqualTo(SyncState.IN_SYNC);
        assertThat(w.sessions.find(r.session().id())).isPresent();
        assertThat(w.versions.list(r.session().id())).hasSize(1);
        assertThat(w.tickets.findByIssueKey("PAY-4")).isPresent();
        assertThat(w.tracker.writes).isEmpty();
        assertThat(w.tracker.calls).containsExactly("get PAY-4");
    }

    @Test
    void descriptionTronqueeA10000CaracteresAvecAvertissement() {
        String longText = "mot ".repeat(3000);
        w.tracker.issues.put("PAY-5", new JiraIssue("PAY-5", "Long", "wiki:" + longText, JiraWorld.JIRA_T0));
        w.llm.structures.add(JiraWorld.draft("Long"));
        ImportJiraIssue.Imported r = importer.run("PAY-5");
        assertThat(r.truncated()).isTrue();
        assertThat(r.session().need().text()).hasSizeLessThanOrEqualTo(10_000);
    }

    @Test
    void descriptionCourteCompleteeParLeTitre() {
        w.tracker.issues.put("PAY-6", new JiraIssue("PAY-6", "Les clients paient leur panier en ligne sur le site", "wiki:Par carte.",
                JiraWorld.JIRA_T0));
        w.llm.structures.add(JiraWorld.draft("Court"));
        assertThat(importer.run("PAY-6").session().need().text())
                .isEqualTo("Les clients paient leur panier en ligne sur le site\n\nPar carte.");
        w.tracker.issues.put("PAY-7", new JiraIssue("PAY-7", "Payer", "wiki:Vite.", JiraWorld.JIRA_T0));
        assertThatThrownBy(() -> importer.run("PAY-7")).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.IMPORT_UNSTRUCTURABLE));
    }

    @Test
    void structurationImpossibleRienNestCree() {
        w.llm.structures.add(JiraWorld.invalidDraft());
        w.llm.structures.add(JiraWorld.invalidDraft());
        w.llm.structures.add(JiraWorld.invalidDraft());
        assertThatThrownBy(() -> importer.run("PAY-4")).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.IMPORT_UNSTRUCTURABLE));
        assertThat(w.llm.structureRequests).hasSize(3);
        assertThat(w.llm.structureRequests.get(2).correction()).isNotBlank();
        assertThat(w.sessions.list()).isEmpty();
        assertThat(w.tickets.all()).isEmpty();
        assertThat(w.tracker.writes).isEmpty();
    }

    @Test
    void sortieIllisibleDuLlmDevientImportUnstructurable() {
        w.llm.structureFailure = ErrorCode.LLM_INVALID_OUTPUT;
        assertThatThrownBy(() -> importer.run("PAY-4")).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.IMPORT_UNSTRUCTURABLE));
    }

    @Test
    void consentementsConfigurationEtTicket() {
        JiraWorld noJira = new JiraWorld().configureJira().grant(ConsentService.COPILOT);
        ImportJiraIssue i = new ImportJiraIssue(noJira.tracker, noJira.jira, noJira.markup, noJira.llm, noJira.policy,
                noJira.sessions, noJira.projects, noJira.versions, noJira.tickets, noJira.clock, noJira.ids);
        assertThatThrownBy(() -> i.run("PAY-4")).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.CONSENT_REQUIRED));
        assertThat(noJira.tracker.calls).isEmpty();

        JiraWorld unconfigured = new JiraWorld().grant(ConsentService.JIRA);
        ImportJiraIssue u = new ImportJiraIssue(unconfigured.tracker, unconfigured.jira, unconfigured.markup,
                unconfigured.llm, unconfigured.policy, unconfigured.sessions, unconfigured.projects,
                unconfigured.versions, unconfigured.tickets, unconfigured.clock, unconfigured.ids);
        assertThatThrownBy(() -> u.run("PAY-4")).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.JIRA_NOT_CONFIGURED));

        assertThatThrownBy(() -> importer.run("pas-une-clé")).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> importer.run("PAY-99")).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
        w.tracker.failure = Kind.UNAUTHORIZED;
        assertThatThrownBy(() -> importer.run("PAY-4")).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.JIRA_TOKEN_REJECTED));
    }

    /** 011, US4 : un ticket déjà lié renvoie sa spécification, sans nouvel import. */
    @Test
    void ticketDejaLieRenvoieSaSpecification() {
        w.llm.structures.add(JiraWorld.draft("Paiement"));
        ImportJiraIssue.Imported first = importer.run("PAY-4");

        ImportJiraIssue.Imported again = importer.run("PAY-4");

        assertThat(again.existing()).isTrue();
        assertThat(first.existing()).isFalse();
        assertThat(again.session().id()).isEqualTo(first.session().id());
        assertThat(again.version().number()).isEqualTo(1);
        assertThat(w.sessions.list()).hasSize(1);
    }
}
