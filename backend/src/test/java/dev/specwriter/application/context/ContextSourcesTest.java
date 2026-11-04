package dev.specwriter.application.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.jira.JiraIssue;
import dev.specwriter.domain.port.DocumentationReader.Page;
import dev.specwriter.domain.port.DocumentationReader.Result;
import dev.specwriter.domain.port.DocumentationReader.Unread;
import dev.specwriter.domain.port.DocumentationReader.UnreadReason;
import dev.specwriter.domain.project.DocSource;
import dev.specwriter.domain.project.JiraProjectKey;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.project.ProjectName;
import dev.specwriter.support.JiraWorld;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Sources de documentation d'un projet et aperçu de ce qui sera envoyé à Copilot (FR-013, FR-013b, T021). */
class ContextSourcesTest {

    static final String SITE = "https://docs.example.com/billing/";
    static final String DOWN = "https://down.example.com/";

    final JiraWorld w = new JiraWorld().grant(ConsentService.JIRA).configureJira();
    Project project;
    ManageDocSources manage;
    PreviewContextSources preview;

    @BeforeEach
    void setUp() {
        project = Project.create(UUID.randomUUID(), ProjectName.of("Facturation"), null, JiraProjectKey.ofNullable("FACT"),
                Instant.EPOCH);
        w.projects.save(project);
        manage = new ManageDocSources(w.projects, w.docSources, UUID::randomUUID);
        preview = new PreviewContextSources(w.projects, w.docSources, w.docs, w.jira, w.tracker);
    }

    @Test
    void ajoutListeEtSuppressionDansLOrdreDAjout() {
        DocSource a = manage.add(project.id(), DocSource.Kind.URL, SITE);
        DocSource b = manage.add(project.id(), DocSource.Kind.URL, DOWN);
        assertThat(manage.list(project.id())).containsExactly(a, b);
        manage.delete(project.id(), a.id());
        assertThat(manage.list(project.id())).containsExactly(b);
        // une source d'un autre projet n'est pas supprimée par ce projet
        assertThatThrownBy(() -> manage.delete(UUID.randomUUID(), b.id())).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.PROJECT_NOT_FOUND));
        Project other = Project.create(UUID.randomUUID(), ProjectName.of("Autre"), null, null, Instant.EPOCH);
        w.projects.save(other);
        assertThatThrownBy(() -> manage.delete(other.id(), b.id())).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void auPlusDixSources() {
        IntStream.range(0, DocSource.MAX_PER_PROJECT).forEach(i -> manage.add(project.id(), DocSource.Kind.URL, SITE + i + "/"));
        assertThatThrownBy(() -> manage.add(project.id(), DocSource.Kind.URL, SITE)).isInstanceOfSatisfying(
                DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void apercuCompteSansLireLesPages() {
        manage.add(project.id(), DocSource.Kind.URL, SITE);
        manage.add(project.id(), DocSource.Kind.URL, DOWN);
        w.docs.sites.put(SITE, new Result(List.of(new Page(SITE + "a.html", "A", "a"), new Page(SITE + "b.html", "B", "b")),
                List.of(new Unread(SITE + "c.html", UnreadReason.HTTP_ERROR))));
        IntStream.range(0, 230).forEach(n -> w.tracker.issues.put("FACT-" + n,
                new JiraIssue("FACT-" + n, "F", "d", Instant.EPOCH.plusSeconds(n))));

        PreviewContextSources.Preview p = preview.run(project.id());
        assertThat(p.pages()).isEqualTo(3);
        assertThat(p.tickets()).isEqualTo(200);
        assertThat(p.unreadable()).containsExactly(new Unread(DOWN, UnreadReason.UNREACHABLE));
        assertThat(w.docs.reads).isEmpty();
    }

    @Test
    void apercuDonneLeMotifDuLecteur() {
        manage.add(project.id(), DocSource.Kind.URL, DOWN);
        w.docs.problems.put(DOWN, UnreadReason.ADOC_SOURCES);
        assertThat(preview.run(project.id()).unreadable()).containsExactly(new Unread(DOWN, UnreadReason.ADOC_SOURCES));
    }

    @Test
    void apercuSansJiraAutorise() {
        JiraWorld bare = new JiraWorld().configureJira();
        bare.projects.save(project);
        PreviewContextSources p = new PreviewContextSources(bare.projects, bare.docSources, bare.docs, bare.jira, bare.tracker);
        assertThat(p.run(project.id()).tickets()).isZero();
        assertThat(bare.tracker.calls).isEmpty();
    }
}
