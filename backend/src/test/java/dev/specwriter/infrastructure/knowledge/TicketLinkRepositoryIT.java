package dev.specwriter.infrastructure.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.domain.jira.TicketLink;
import dev.specwriter.domain.jira.TicketLink.Direction;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.port.TicketLinkRepository;
import dev.specwriter.domain.port.TicketLinkRepository.Inbound;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.project.ProjectName;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T122 : liens entre tickets indexés (data-model TicketLink, migration V14, research.md R29). */
@QuarkusTest
class TicketLinkRepositoryIT {

    static final Instant T0 = Instant.parse("2026-11-04T09:00:00Z");

    @Inject
    TicketLinkRepository links;
    @Inject
    ProjectRepository projects;

    UUID project;

    @BeforeEach
    void projet() {
        project = UUID.randomUUID();
        projects.save(Project.create(project, ProjectName.of("Liens " + project.toString().substring(0, 8)), null, null,
                T0));
    }

    /** Un projet laissé derrière passerait avant le projet par défaut (premier par nom) pour les autres tests. */
    @AfterEach
    void supprimer() {
        if (projects.find(project).isPresent()) {
            projects.delete(project);
        }
    }

    @Test
    void remplacementEnBlocEtLiensEntrants() {
        TicketLink blocks = TicketLink.link("Blocks", Direction.OUTWARD, "blocks", "FACT-30");
        links.replace(project, "FACT-20", List.of(TicketLink.subtask("FACT-21"), TicketLink.subtask("FACT-22"), blocks,
                TicketLink.link("Relates", Direction.INWARD, "relates to", "COMPTA-4")));
        links.replace(project, "FACT-21", List.of(TicketLink.parent("FACT-20")));
        assertThat(links.of(project, "FACT-20")).hasSize(4).contains(blocks);
        assertThat(links.pointingTo(project, "FACT-20")).containsExactly(new Inbound("FACT-21", TicketLink.parent("FACT-20")));
        assertThat(links.pointingTo(project, "FACT-30")).containsExactly(new Inbound("FACT-20", blocks));

        links.replace(project, "FACT-20", List.of(TicketLink.subtask("FACT-21")));
        assertThat(links.of(project, "FACT-20")).containsExactly(TicketLink.subtask("FACT-21"));
        assertThat(links.pointingTo(project, "FACT-30")).isEmpty();
    }

    @Test
    void libelleBorneA100Caracteres() {
        links.replace(project, "FACT-1", List.of(TicketLink.link("Long", Direction.OUTWARD, "x".repeat(150), "FACT-2")));
        assertThat(links.of(project, "FACT-1").get(0).label()).hasSize(100);
    }

    @Test
    void ticketsRetiresEtProjetSupprime() {
        links.replace(project, "FACT-20", List.of(TicketLink.subtask("FACT-21")));
        links.replace(project, "FACT-21", List.of(TicketLink.parent("FACT-20")));
        assertThat(links.retain(project, Set.of("FACT-20"))).isEqualTo(1);
        assertThat(links.of(project, "FACT-21")).isEmpty();
        assertThat(links.of(project, "FACT-20")).hasSize(1);

        projects.delete(project);
        assertThat(links.of(project, "FACT-20")).isEmpty();
    }
}
