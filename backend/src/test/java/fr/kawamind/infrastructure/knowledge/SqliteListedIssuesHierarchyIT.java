package fr.kawamind.infrastructure.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import fr.kawamind.domain.jira.IssueSort;
import fr.kawamind.domain.jira.ListedIssue;
import fr.kawamind.domain.jira.TicketLink;
import fr.kawamind.domain.knowledge.KnowledgeItem;
import fr.kawamind.domain.port.KnowledgeIndex;
import fr.kawamind.domain.port.ListedIssues;
import fr.kawamind.domain.port.ProjectRepository;
import fr.kawamind.domain.port.TicketLinkRepository;
import fr.kawamind.domain.project.Project;
import fr.kawamind.domain.project.ProjectName;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * T003, T004 (022, US1) : la vue Jira ne liste au premier niveau que les tickets sans parent indexé ;
 * chacun porte ses sous-tâches, classées par clé ; tri, pagination et total portent sur le premier
 * niveau ; les liens qui ne sont pas de la hiérarchie n'y changent rien.
 */
@QuarkusTest
@org.junit.jupiter.api.parallel.Isolated
class SqliteListedIssuesHierarchyIT {

    static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    @Inject
    ListedIssues listed;
    @Inject
    KnowledgeIndex index;
    @Inject
    TicketLinkRepository links;
    @Inject
    ProjectRepository projects;

    UUID project;

    @BeforeEach
    void tickets() {
        project = UUID.randomUUID();
        projects.save(Project.create(project, ProjectName.of("Hiérarchie " + project.toString().substring(0, 8)), null,
                null, T0));
        QuarkusTransaction.requiringNew().run(() -> {
            ticket("S-1", "Story", "indeterminate", 10);
            ticket("S-2", "Sous-tâche", "new", 100);
            ticket("S-3", "Sous-tâche", "done", 1);
            ticket("S-10", "Sous-tâche", "new", 2);
            ticket("O-5", "Sous-tâche", "new", 5);
            ticket("A-1", "Story", "new", 20);
            ticket("A-2", "Story", "done", 30);
            for (String child : List.of("S-2", "S-3", "S-10")) {
                links.replace(project, child, List.of(TicketLink.parent("S-1")));
            }
            links.replace(project, "S-1", List.of(TicketLink.subtask("S-2")));
            links.replace(project, "O-5", List.of(TicketLink.parent("P-9")));
            links.replace(project, "A-1", List.of(TicketLink.link("Lier", TicketLink.Direction.OUTWARD, "lie", "A-2")));
        });
    }

    void ticket(String key, String type, String category, int minutes) {
        index.save(KnowledgeItem.ticket(project, key, "Ticket " + key, "", "Statut " + category, category, type,
                T0.plus(Duration.ofMinutes(minutes)), T0), null, null);
    }

    @AfterEach
    void nettoyer() {
        projects.delete(project);
    }

    @Test
    void lePremierNiveauPorteSesSousTachesClasseesParCle() {
        List<ListedIssue> page = listed.page(project, IssueSort.DEFAULT, 0, 50);
        assertThat(page).extracting(ListedIssue::key).containsExactly("A-2", "A-1", "S-1", "O-5");
        assertThat(listed.count(project)).isEqualTo(4);

        ListedIssue story = page.get(2);
        assertThat(story.parentKey()).isNull();
        assertThat(story.subtasks()).extracting(ListedIssue::key).containsExactly("S-2", "S-3", "S-10");
        assertThat(story.subtasks()).allSatisfy(s -> {
            assertThat(s.subtasks()).isEmpty();
            assertThat(s.parentKey()).isNull();
        });
        assertThat(story.subtasks().getFirst().statusCategory()).isEqualTo("new");

        ListedIssue orphan = page.get(3);
        assertThat(orphan.parentKey()).isEqualTo("P-9");
        assertThat(orphan.subtasks()).isEmpty();

        assertThat(page.get(0).subtasks()).isEmpty();
        assertThat(page.get(1).subtasks()).as("un lien « Lier » n'est pas de la hiérarchie").isEmpty();
    }

    @Test
    void uneStoryEtSesSousTachesRestentSurLaMemePageQuelQueSoitLeTri() {
        for (IssueSort.Field field : IssueSort.Field.values()) {
            for (IssueSort.Direction direction : IssueSort.Direction.values()) {
                IssueSort sort = new IssueSort(field, direction);
                List<String> keys = new java.util.ArrayList<>();
                for (int offset = 0; offset < listed.count(project); offset++) {
                    List<ListedIssue> one = listed.page(project, sort, offset, 1);
                    assertThat(one).hasSize(1);
                    keys.add(one.getFirst().key());
                    if (one.getFirst().key().equals("S-1")) {
                        assertThat(one.getFirst().subtasks()).as("%s %s", field, direction).hasSize(3);
                    }
                }
                assertThat(keys).as("%s %s", field, direction).containsExactlyInAnyOrder("A-1", "A-2", "S-1", "O-5");
            }
        }
    }

    @Test
    void uneSousTachePlusRecenteNeRemontePasSaStory() {
        assertThat(listed.page(project, IssueSort.DEFAULT, 0, 1)).extracting(ListedIssue::key).containsExactly("A-2");
    }

    @Test
    void unePageVideNeLitAucuneSousTache() {
        assertThat(listed.page(project, IssueSort.DEFAULT, 10, 50)).isEmpty();
    }
}
