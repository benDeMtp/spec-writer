package fr.kawamind.application.jira;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.kawamind.application.knowledge.IndexationScope;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.jira.IssueSort;
import fr.kawamind.domain.jira.ListedIssue;
import fr.kawamind.domain.port.ListedIssues;
import fr.kawamind.domain.project.Project;
import fr.kawamind.domain.sync.SourceChange;
import fr.kawamind.domain.sync.SourceSyncResult;
import fr.kawamind.support.InMemorySourceSync;
import fr.kawamind.support.JiraWorld;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T010 : vue Jira servie par l'index local, sans appel à Jira (017, US1). */
class ListProjectIssuesTest {

    static final UUID P = Project.DEFAULT_ID;
    static final Instant T = Instant.parse("2026-11-19T10:00:00Z");

    final JiraWorld w = new JiraWorld();
    final InMemorySourceSync syncs = new InMemorySourceSync();
    final List<String> calls = new ArrayList<>();
    final ListedIssues listed = new ListedIssues() {
        @Override
        public List<ListedIssue> page(UUID projectId, IssueSort sort, int offset, int limit) {
            calls.add(sort + " " + offset + " " + limit);
            return List.of(new ListedIssue("PROJ-1", "Avoir", "En cours", "indeterminate", T, ListedIssue.Sync.CONFLICT,
                    UUID.randomUUID()).withSubtasks(List.of(new ListedIssue("PROJ-2", "Remise", "À faire", "new", T,
                            ListedIssue.Sync.UNLINKED, null))));
        }

        @Override
        public int count(UUID projectId) {
            return 120;
        }
    };
    ListProjectIssues list;

    @BeforeEach
    void setUp() {
        list = new ListProjectIssues(w.projects, w.jira, listed, syncs);
    }

    @Test
    void pageTrieeAvecLaDerniereSynchronisationDeJira() {
        w.configureJira();
        syncs.record(P, IndexationScope.JIRA, SourceSyncResult.success(T, new SourceChange(1, 0, 0)));
        ListProjectIssues.IssuePage page = list.run(P, IssueSort.parse("sync", "asc"), 50, 50);
        assertThat(page.jiraProjectKey()).isEqualTo("PROJ");
        assertThat(page.total()).isEqualTo(120);
        assertThat(page.startAt()).isEqualTo(50);
        assertThat(page.issues()).extracting(ListedIssue::key).containsExactly("PROJ-1");
        // 022 : les sous-tâches arrivent avec leur parent
        assertThat(page.issues().getFirst().subtasks()).extracting(ListedIssue::key).containsExactly("PROJ-2");
        assertThat(page.lastSync()).isNotNull();
        assertThat(page.lastSync().at()).isEqualTo(T);
        assertThat(calls).containsExactly("IssueSort[field=SYNC, direction=ASC] 50 50");
        // aucun appel à Jira (SC-002)
        assertThat(w.tracker.calls).isEmpty();
    }

    @Test
    void sansSynchronisationLaDateEstVide() {
        w.configureJira();
        assertThat(list.run(P, IssueSort.DEFAULT, 0, 50).lastSync()).isNull();
    }

    @Test
    void sansProjetJiraLaVueEstRefusee() {
        assertThatThrownBy(() -> list.run(P, IssueSort.DEFAULT, 0, 50)).isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code()).isEqualTo(ErrorCode.JIRA_NOT_CONFIGURED);
    }

    @Test
    void taillesEtPositionsBornees() {
        w.configureJira();
        assertThatThrownBy(() -> list.run(P, IssueSort.DEFAULT, 0, 0)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> list.run(P, IssueSort.DEFAULT, 0, 101)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> list.run(P, IssueSort.DEFAULT, -1, 50)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> list.run(UUID.randomUUID(), IssueSort.DEFAULT, 0, 50))
                .isInstanceOf(DomainException.class).extracting(e -> ((DomainException) e).code())
                .isEqualTo(ErrorCode.PROJECT_NOT_FOUND);
    }

    /** T022 (024, US2) : filtres de la vue, par défaut le label et sans les terminés. */
    @Test
    void lesFiltresSuiventLeLabelDuProjet() {
        w.configureJira();
        List<fr.kawamind.domain.jira.IssueFilters> seen = new ArrayList<>();
        ListedIssues filtered = new ListedIssues() {
            @Override
            public List<ListedIssue> page(UUID projectId, IssueSort sort, int offset, int limit) {
                return List.of();
            }

            @Override
            public int count(UUID projectId) {
                return 0;
            }

            @Override
            public List<ListedIssue> page(UUID projectId, IssueSort sort, fr.kawamind.domain.jira.IssueFilters filters,
                    int offset, int limit) {
                seen.add(filters);
                return List.of();
            }

            @Override
            public int count(UUID projectId, fr.kawamind.domain.jira.IssueFilters filters) {
                return 7;
            }
        };
        ListProjectIssues byLabel = new ListProjectIssues(w.projects, w.jira, filtered, syncs, w.jiraLabels);
        // sans label réglé : le filtre du label est ignoré
        ListProjectIssues.IssuePage none = byLabel.run(P, IssueSort.DEFAULT, true, false, 0, 50);
        assertThat(none.label()).isNull();
        assertThat(none.labelled()).isFalse();
        assertThat(none.done()).isFalse();
        assertThat(seen.getLast()).isEqualTo(new fr.kawamind.domain.jira.IssueFilters(null, false));

        w.jiraLabels.save(P, fr.kawamind.domain.jira.JiraLabel.of("spec-writer"), T);
        ListProjectIssues.IssuePage page = byLabel.run(P, IssueSort.DEFAULT, true, false, 0, 50);
        assertThat(page.label()).isEqualTo("spec-writer");
        assertThat(page.labelled()).isTrue();
        assertThat(page.total()).isEqualTo(7);
        assertThat(seen.getLast()).isEqualTo(new fr.kawamind.domain.jira.IssueFilters("spec-writer", false));
        byLabel.run(P, IssueSort.DEFAULT, false, true, 0, 50);
        assertThat(seen.getLast()).isEqualTo(fr.kawamind.domain.jira.IssueFilters.ALL);
    }
}
