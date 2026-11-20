package fr.kawamind.infrastructure.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import fr.kawamind.domain.jira.IssueSort;
import fr.kawamind.domain.jira.LinkedJiraTicket;
import fr.kawamind.domain.jira.ListedIssue;
import fr.kawamind.domain.knowledge.KnowledgeItem;
import fr.kawamind.domain.port.KnowledgeIndex;
import fr.kawamind.domain.port.LinkedTicketRepository;
import fr.kawamind.domain.port.ListedIssues;
import fr.kawamind.domain.port.ProjectRepository;
import fr.kawamind.domain.port.SessionRepository;
import fr.kawamind.domain.project.Project;
import fr.kawamind.domain.project.ProjectName;
import fr.kawamind.domain.question.Questionnaire;
import fr.kawamind.domain.session.Need;
import fr.kawamind.domain.session.Session;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T011 : tri et pagination de la vue Jira par SQLite, 2 000 tickets (017, SC-001). */
@QuarkusTest
@org.junit.jupiter.api.parallel.Isolated
class SqliteListedIssuesIT {

    static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    static final int TOTAL = 2_000;
    static final String[] CATEGORIES = { "new", "indeterminate", "done", null };

    @Inject
    ListedIssues listed;
    @Inject
    KnowledgeIndex index;
    @Inject
    ProjectRepository projects;
    @Inject
    SessionRepository sessions;
    @Inject
    LinkedTicketRepository tickets;

    UUID project;
    UUID other;
    final List<UUID> created = new ArrayList<>();

    @BeforeEach
    void tickets() {
        project = UUID.randomUUID();
        other = UUID.randomUUID();
        projects.save(Project.create(project, ProjectName.of("Vue Jira " + project.toString().substring(0, 8)), null, null, T0));
        projects.save(Project.create(other, ProjectName.of("Autre " + other.toString().substring(0, 8)), null, null, T0));
        QuarkusTransaction.requiringNew().run(() -> {
            for (int i = 0; i < TOTAL; i++) {
                String category = CATEGORIES[i % CATEGORIES.length];
                index.save(KnowledgeItem.ticket(project, "VJ" + project.toString().substring(0, 4).toUpperCase().replaceAll("[^A-Z]", "X")
                        + "-" + i, "Ticket " + i, "", category == null ? "Inconnu" : "Statut " + category, category,
                        "Story", T0.plus(Duration.ofMinutes(i)), T0), null, null);
            }
            index.save(KnowledgeItem.ticket(other, "AUTRE-1", "Autre", "", "À faire", "new", "Story",
                    T0.plus(Duration.ofDays(400)), T0), null, null);
        });
        link(key(10), LinkedJiraTicket.SyncState.CONFLICT);
        link(key(20), LinkedJiraTicket.SyncState.LOCAL_AHEAD);
        link(key(30), LinkedJiraTicket.SyncState.IN_SYNC);
    }

    String key(int i) {
        return "VJ" + project.toString().substring(0, 4).toUpperCase().replaceAll("[^A-Z]", "X") + "-" + i;
    }

    void link(String key, LinkedJiraTicket.SyncState state) {
        UUID id = UUID.randomUUID();
        created.add(id);
        sessions.save(Session.start(id, project, Need.of("Les employés doivent pouvoir réserver une salle de réunion vue jira."),
                null, T0), Questionnaire.empty());
        tickets.save(new LinkedJiraTicket(id, key, "https://jira.example.com/browse/" + key, "Story", 1, T0, "h", T0, null, state));
    }

    @AfterEach
    void nettoyer() {
        created.forEach(sessions::delete);
        projects.delete(project);
        projects.delete(other);
    }

    @Test
    void lesTroisTrisSurToutLaListeEnMoinsDUneSeconde() {
        assertThat(listed.count(project)).isEqualTo(TOTAL);
        for (IssueSort.Field field : IssueSort.Field.values()) {
            for (IssueSort.Direction direction : IssueSort.Direction.values()) {
                long start = System.nanoTime();
                List<ListedIssue> page = listed.page(project, new IssueSort(field, direction), 1_950, 50);
                long ms = (System.nanoTime() - start) / 1_000_000;
                assertThat(page).hasSize(50);
                assertThat(ms).as("%s %s en %d ms", field, direction, ms).isLessThan(1_000);
            }
        }
    }

    @Test
    void parDateDeModification() {
        assertThat(listed.page(project, IssueSort.DEFAULT, 0, 2)).extracting(ListedIssue::key)
                .containsExactly(key(1999), key(1998));
        assertThat(listed.page(project, new IssueSort(IssueSort.Field.UPDATED, IssueSort.Direction.ASC), 0, 1))
                .extracting(ListedIssue::key).containsExactly(key(0));
    }

    @Test
    void parStatutCategoriePuisNom() {
        List<ListedIssue> asc = listed.page(project, new IssueSort(IssueSort.Field.STATUS, IssueSort.Direction.ASC), 0, TOTAL);
        assertThat(asc.getFirst().statusCategory()).isEqualTo("new");
        assertThat(asc.get(500).statusCategory()).isEqualTo("indeterminate");
        assertThat(asc.get(1000).statusCategory()).isEqualTo("done");
        assertThat(asc.getLast().statusCategory()).isNull();
        assertThat(listed.page(project, new IssueSort(IssueSort.Field.STATUS, IssueSort.Direction.DESC), 0, 1).getFirst()
                .statusCategory()).isNull();
    }

    @Test
    void parEtatDeSynchronisation() {
        List<ListedIssue> asc = listed.page(project, new IssueSort(IssueSort.Field.SYNC, IssueSort.Direction.ASC), 0, 4);
        assertThat(asc).extracting(ListedIssue::key, ListedIssue::sync).containsExactly(
                org.assertj.core.groups.Tuple.tuple(key(10), ListedIssue.Sync.CONFLICT),
                org.assertj.core.groups.Tuple.tuple(key(20), ListedIssue.Sync.LOCAL_AHEAD),
                org.assertj.core.groups.Tuple.tuple(key(30), ListedIssue.Sync.IN_SYNC),
                org.assertj.core.groups.Tuple.tuple(key(1999), ListedIssue.Sync.UNLINKED));
        assertThat(asc.getFirst().sessionId()).isEqualTo(created.getFirst());
        assertThat(asc.getLast().sessionId()).isNull();
        assertThat(listed.page(project, new IssueSort(IssueSort.Field.SYNC, IssueSort.Direction.DESC), TOTAL - 1, 1))
                .extracting(ListedIssue::sync).containsExactly(ListedIssue.Sync.CONFLICT);
    }
}
