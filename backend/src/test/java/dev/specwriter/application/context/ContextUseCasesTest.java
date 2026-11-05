package dev.specwriter.application.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.project.DocSource;
import dev.specwriter.domain.project.Project;
import dev.specwriter.support.JiraWorld;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Fiche générale et sources de documentation d'un projet (FR-008, FR-013 de 002). */
class ContextUseCasesTest {

    static final UUID P = Project.DEFAULT_ID;
    final JiraWorld w = new JiraWorld();
    final EditContext edit = new EditContext(w.projects, w.clock);
    final ManageDocSources sources = new ManageDocSources(w.projects, w.docSources, w.ids);

    @Test
    void ficheGeneraleEnregistreeEtDatee() {
        Project edited = edit.run(P, "## Acteurs\n- Le gestionnaire.");
        assertThat(edited.context().markdown()).isEqualTo("## Acteurs\n- Le gestionnaire.");
        assertThat(edited.context().updatedAt()).isEqualTo(w.clock.now());
        assertThat(w.projects.find(P).orElseThrow().context().markdown()).isEqualTo("## Acteurs\n- Le gestionnaire.");
        assertThatThrownBy(() -> edit.run(UUID.randomUUID(), "x")).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.PROJECT_NOT_FOUND));
    }

    @Test
    void sourcesAjouteesListeesEtSupprimees() {
        DocSource site = sources.add(P, DocSource.Kind.URL, "https://docs.example.com/facturation/");
        assertThat(sources.list(P)).containsExactly(site);
        sources.delete(P, site.id());
        assertThat(sources.list(P)).isEmpty();

        assertThatThrownBy(() -> sources.delete(P, site.id())).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> sources.list(UUID.randomUUID())).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.PROJECT_NOT_FOUND));
    }

    @Test
    void uneSourceDUnAutreProjetNePeutPasEtreSupprimee() {
        DocSource site = sources.add(P, DocSource.Kind.URL, "https://docs.example.com/facturation/");
        UUID other = UUID.randomUUID();
        w.projects.save(Project.create(other, dev.specwriter.domain.project.ProjectName.of("Autre"), null, null,
                w.clock.now()));
        assertThatThrownBy(() -> sources.delete(other, site.id())).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
        assertThat(sources.list(P)).containsExactly(site);
    }

    @Test
    void dixSourcesAuPlus() {
        for (int i = 0; i < 10; i++) {
            sources.add(P, DocSource.Kind.URL, "https://docs.example.com/site" + i + "/");
        }
        assertThatThrownBy(() -> sources.add(P, DocSource.Kind.URL, "https://docs.example.com/site10/"))
                .isInstanceOf(DomainException.class);
        assertThat(sources.list(P)).hasSize(10);
    }
}
