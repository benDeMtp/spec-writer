package dev.specwriter.application.jira;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.application.search.UnifiedSearch;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.project.JiraProjectKey;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.project.ProjectName;
import dev.specwriter.domain.session.Session;
import dev.specwriter.support.JiraWorld;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Projet Jira effectif : la clé du projet, sinon le projet par défaut des réglages (FR-006, T010). */
class JiraAccessTest {

    final JiraWorld w = new JiraWorld().grant(ConsentService.JIRA).configureJira();
    Project facturation;

    @BeforeEach
    void setUp() {
        facturation = Project.create(UUID.randomUUID(), ProjectName.of("Facturation"), null,
                JiraProjectKey.ofNullable("FACT"), Instant.EPOCH);
        w.projects.save(facturation);
    }

    Session inFacturation() {
        Session s = w.generatedSession();
        Session moved = new Session(s.id(), facturation.id(), s.name(), s.need(), s.language(), s.origin(), s.state(),
                s.createdAt(), s.updatedAt());
        w.sessions.save(moved, w.sessions.questionnaire(s.id()).orElseThrow());
        return moved;
    }

    @Test
    void cleDuProjetSinonProjetParDefaut() {
        assertThat(w.jira.effectiveProject(inFacturation())).isEqualTo("FACT");
        assertThat(w.jira.effectiveProject(w.generatedSession())).isEqualTo("PROJ");
        assertThat(w.jira.require(inFacturation()).projectKey()).isEqualTo("FACT");
        assertThat(w.jira.require(inFacturation()).endpoint().serverUrl()).isEqualTo("https://jira.example.com");
    }

    @Test
    void projetCourantPourLaRecherche() {
        assertThat(w.jira.targetForCurrentProject()).hasValueSatisfying(t -> assertThat(t.projectKey()).isEqualTo("PROJ"));
        w.projects.saveCurrentProjectId(facturation.id());
        assertThat(w.jira.targetForCurrentProject()).hasValueSatisfying(t -> assertThat(t.projectKey()).isEqualTo("FACT"));
    }

    @Test
    void apercuEtCreationDeTicketDansLeProjetJiraDuProjet() {
        Session s = inFacturation();
        PreviewJiraTicket preview = new PreviewJiraTicket(w.versions, w.tickets, w.markup, w.tracker, w.jira);
        assertThat(preview.run(s.id(), 1, null).projectKey()).isEqualTo("FACT");

        CreateJiraTicket create = new CreateJiraTicket(w.tracker, w.jira, w.markup, w.versions, w.tickets,
                w.clock);
        assertThat(create.run(s.id(), 1, true, null, Map.of()).issueKey()).startsWith("FACT-");
        assertThat(w.tracker.calls).contains("create FACT 10001");
    }

    @Test
    void rechercheJiraDansLeProjetCourant() {
        w.projects.saveCurrentProjectId(facturation.id());
        UnifiedSearch search = new UnifiedSearch(w.local, w.versions, w.tickets, w.tracker, w.jira, w.projects,
                Duration.ofMillis(300));
        search.run("salle", null);
        assertThat(w.tracker.calls).containsExactly("search FACT salle");
    }

    @Test
    void sansCleNiCleParDefautAucunAppelAJira() {
        // 012 (FR-016) : la clé par défaut est facultative ; un projet sans clé n'a pas de projet Jira
        JiraWorld bare = new JiraWorld().grant(ConsentService.JIRA).configureJiraWithoutDefaultKey();
        Session s = bare.generatedSession();
        assertThat(bare.jira.effectiveProject(s)).isNull();
        PreviewJiraTicket preview = new PreviewJiraTicket(bare.versions, bare.tickets, bare.markup, bare.tracker, bare.jira);
        assertThatThrownBy(() -> preview.run(s.id(), 1, null))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.JIRA_PROJECT_MISSING));
        CreateJiraTicket create = new CreateJiraTicket(bare.tracker, bare.jira, bare.markup, bare.versions, bare.tickets,
                bare.clock);
        assertThatThrownBy(() -> create.run(s.id(), 1, true, null, Map.of()))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.JIRA_PROJECT_MISSING));
        UnifiedSearch search = new UnifiedSearch(bare.local, bare.versions, bare.tickets, bare.tracker, bare.jira,
                bare.projects, Duration.ofMillis(300));
        assertThat(search.run("salle", null).jiraStatus()).isEqualTo(UnifiedSearch.JiraStatus.NOT_CONFIGURED);
        assertThat(bare.tracker.calls).isEmpty();
    }
}
