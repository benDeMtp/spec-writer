package fr.kawamind.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import fr.kawamind.domain.jira.JiraLabel;
import fr.kawamind.domain.port.JiraLabelRepository;
import fr.kawamind.domain.port.LostJiraLinks;
import fr.kawamind.domain.port.ProjectRepository;
import fr.kawamind.domain.port.TicketLabels;
import fr.kawamind.domain.project.Project;
import fr.kawamind.domain.project.ProjectName;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T004 (024, V36) : label du projet, labels des tickets, lien Jira perdu. */
@QuarkusTest
@org.junit.jupiter.api.parallel.Isolated
class SqliteJiraScopeIT {

    static final Instant T0 = Instant.parse("2026-11-26T09:00:00Z");

    @Inject
    JiraLabelRepository labels;
    @Inject
    TicketLabels tickets;
    @Inject
    LostJiraLinks lost;
    @Inject
    ProjectRepository projects;
    @Inject
    EntityManager em;

    UUID project;

    @BeforeEach
    void projet() {
        project = UUID.randomUUID();
        projects.save(Project.create(project, ProjectName.of("Périmètre " + project.toString().substring(0, 8)), null,
                null, T0));
    }

    @AfterEach
    void nettoyer() {
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("delete from session where project_id = ?1")
                .setParameter(1, project.toString()).executeUpdate());
        projects.delete(project);
    }

    @Test
    void leLabelDuProjetSEnregistreSeRemplaceEtSeRetire() {
        assertThat(labels.find(project)).isEmpty();
        labels.save(project, JiraLabel.of("spec-writer"), T0);
        labels.save(project, JiraLabel.of("sw"), T0.plusSeconds(1));
        assertThat(labels.find(project)).contains(JiraLabel.of("sw"));
        labels.delete(project);
        assertThat(labels.find(project)).isEmpty();
    }

    @Test
    void lesLabelsDesTicketsSeRemplacentSAjoutentEtSuiventLeProjet() {
        tickets.replace(project, "AUR-1", List.of("spec-writer", "backend", "spec-writer"));
        tickets.replace(project, "AUR-2", List.of("backend"));
        assertThat(tickets.has(project, "AUR-1", "spec-writer")).isTrue();
        assertThat(tickets.has(project, "AUR-1", "Spec-Writer")).isFalse();
        tickets.add(project, "AUR-2", "spec-writer");
        tickets.add(project, "AUR-2", "spec-writer");
        assertThat(tickets.has(project, "AUR-2", "spec-writer")).isTrue();
        tickets.replace(project, "AUR-1", List.of());
        assertThat(tickets.has(project, "AUR-1", "spec-writer")).isFalse();
        tickets.replace(project, "AUR-3", List.of("spec-writer"));
        tickets.retain(project, Set.of("AUR-2"));
        assertThat(tickets.has(project, "AUR-3", "spec-writer")).isFalse();
        assertThat(tickets.has(project, "AUR-2", "backend")).isTrue();
        tickets.remove(project, List.of("AUR-2"));
        assertThat(tickets.has(project, "AUR-2", "backend")).isFalse();
    }

    @Test
    void leLienPerduSEcritSeRemplaceEtSEfface() {
        UUID session = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                insert into session (id, project_id, name, need, language, origin, state, created_at, updated_at)
                values (?1, ?2, 'Halo', 'Besoin', 'FR', 'LOCAL', 'QUESTIONING', ?3, ?3)""")
                .setParameter(1, session.toString()).setParameter(2, project.toString()).setParameter(3, T0.toString())
                .executeUpdate());
        assertThat(lost.find(session)).isEmpty();
        lost.record(session, "AUR-12", T0);
        lost.record(session, "AUR-13", T0);
        assertThat(lost.find(session)).contains("AUR-13");
        lost.clear(session);
        assertThat(lost.find(session)).isEmpty();
    }
}
