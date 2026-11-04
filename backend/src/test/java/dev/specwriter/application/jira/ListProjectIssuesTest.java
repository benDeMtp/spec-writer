package dev.specwriter.application.jira;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.jira.IssueTrackerException.Kind;
import dev.specwriter.domain.jira.JiraIssueSummary;
import dev.specwriter.domain.session.Session;
import dev.specwriter.support.JiraWorld;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T045 : tickets du projet Jira par pages de 25, les plus récents d'abord (FR-020 à FR-022 de 002). */
class ListProjectIssuesTest {

    static final Instant T0 = Instant.parse("2026-10-01T00:00:00Z");

    final JiraWorld w = new JiraWorld();
    ListProjectIssues list;

    @BeforeEach
    void setUp() {
        list = new ListProjectIssues(w.tracker, w.jira, w.tickets);
        for (int i = 59; i >= 0; i--) {
            w.tracker.projectResults.add(new JiraIssueSummary("PROJ-" + i, "Ticket " + i, "À faire", "Story",
                    T0.plus(Duration.ofHours(i))));
        }
    }

    @Test
    void pageDe25AvecLeTotalEtLaSessionLiee() {
        w.configureJira().grant(ConsentService.JIRA);
        Session linked = w.generatedSession();
        w.link(linked, "PROJ-33", "d");
        ListProjectIssues.IssuePage page = list.run("PROJ", 25);
        assertThat(page.startAt()).isEqualTo(25);
        assertThat(page.total()).isEqualTo(60);
        assertThat(page.issues()).hasSize(25);
        assertThat(page.issues().getFirst().key()).isEqualTo("PROJ-34");
        assertThat(page.issues()).filteredOn(i -> i.linkedSessionId() != null).singleElement()
                .satisfies(i -> {
                    assertThat(i.key()).isEqualTo("PROJ-33");
                    assertThat(i.linkedSessionId()).isEqualTo(linked.id());
                });
        assertThat(w.tracker.calls).containsExactly("project PROJ 25 25");
    }

    @Test
    void entreesRefusees() {
        w.configureJira().grant(ConsentService.JIRA);
        assertThatThrownBy(() -> list.run("PROJ OR 1=1", 0)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> list.run("PROJ", -1)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThat(w.tracker.calls).isEmpty();
    }

    @Test
    void jiraInjoignableOuNonConsenti() {
        w.configureJira();
        assertThatThrownBy(() -> list.run("PROJ", 0)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.CONSENT_REQUIRED));
        w.grant(ConsentService.JIRA);
        w.tracker.failure = Kind.UNREACHABLE;
        assertThatThrownBy(() -> list.run("PROJ", 0)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.JIRA_UNREACHABLE));
    }
}
