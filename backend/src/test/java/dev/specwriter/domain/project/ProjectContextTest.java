package dev.specwriter.domain.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Contexte de projet et sources de documentation (data-model.md de 002, FR-008, FR-013, T019). */
class ProjectContextTest {

    static final Instant T0 = Instant.parse("2026-11-03T09:00:00Z");

    @TempDir
    Path site;

    static void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void contexteVideParDefautEtDe20000CaracteresAuPlus() {
        assertThat(ProjectContext.NONE.markdown()).isEmpty();
        assertThat(ProjectContext.NONE.origin()).isEqualTo(ProjectContext.Origin.NONE);
        assertThat(ProjectContext.NONE.isEmpty()).isTrue();
        assertThat(ProjectContext.NONE.hash()).isEmpty();
        assertThat(ProjectContext.NONE.updatedAt()).isNull();
        assertThat(ProjectContext.edited("x".repeat(20_000), T0).markdown()).hasSize(20_000);
        assertInvalid(() -> ProjectContext.edited("x".repeat(20_001), T0));
    }

    @Test
    void hashRecalculeAChaqueEnregistrement() {
        ProjectContext a = ProjectContext.edited("## Acteurs\n- comptable", T0);
        ProjectContext b = ProjectContext.edited("## Acteurs\n- comptable", T0.plusSeconds(60));
        ProjectContext c = ProjectContext.edited("## Acteurs\n- comptable, gestionnaire", T0);
        assertThat(a.hash()).hasSize(64).isEqualTo(b.hash()).isNotEqualTo(c.hash());
        assertThat(a.updatedAt()).isEqualTo(T0);
        // un contexte vidé redevient « aucun contexte »
        assertThat(ProjectContext.edited("   ", T0).isEmpty()).isTrue();
        assertThat(ProjectContext.edited("   ", T0).hash()).isEmpty();
    }

    @Test
    void origineAucunPuisGenerePuisModifie() {
        Project p = Project.create(UUID.randomUUID(), ProjectName.of("Facturation"), null, null, T0);
        assertThat(p.context().origin()).isEqualTo(ProjectContext.Origin.NONE);
        Project generated = p.withContext(ProjectContext.generated("## Vocabulaire\n- avoir", T0));
        assertThat(generated.context().origin()).isEqualTo(ProjectContext.Origin.GENERATED);
        assertThat(generated.updatedAt()).isEqualTo(T0);
        Project edited = generated.withContext(ProjectContext.edited("## Vocabulaire\n- avoir, facture", T0.plusSeconds(1)));
        assertThat(edited.context().origin()).isEqualTo(ProjectContext.Origin.EDITED);
        // modifier le projet ne touche pas à son contexte
        assertThat(edited.update(ProjectName.of("Facturation clients"), null, null, T0.plusSeconds(2)).context())
                .isEqualTo(edited.context());
    }

    @Test
    void sourceUrlAbsolueHttpOuHttpsSansIdentifiants() {
        UUID project = UUID.randomUUID();
        assertThat(DocSource.create(UUID.randomUUID(), project, DocSource.Kind.URL, " https://docs.example.com/billing/ ")
                .location()).isEqualTo("https://docs.example.com/billing/");
        assertThat(DocSource.create(UUID.randomUUID(), project, DocSource.Kind.URL, "http://localhost:8080/").location())
                .isEqualTo("http://localhost:8080/");
        for (String invalid : new String[] {"ftp://docs.example.com/", "docs.example.com/billing", "/billing",
                "https://user:pass@docs.example.com/", "https://user@docs.example.com/", "javascript:alert(1)", "https://",
                "file:///etc/passwd"}) {
            assertInvalid(() -> DocSource.create(UUID.randomUUID(), project, DocSource.Kind.URL, invalid));
        }
        assertInvalid(() -> DocSource.create(UUID.randomUUID(), project, DocSource.Kind.URL,
                "https://docs.example.com/" + "a".repeat(2_000)));
    }

    @Test
    void sourceDossierAbsoluExistant() {
        UUID project = UUID.randomUUID();
        assertThat(DocSource.create(UUID.randomUUID(), project, DocSource.Kind.FOLDER, site.toString()).location())
                .isEqualTo(site.toString());
        assertInvalid(() -> DocSource.create(UUID.randomUUID(), project, DocSource.Kind.FOLDER, "site/relatif"));
        assertInvalid(() -> DocSource.create(UUID.randomUUID(), project, DocSource.Kind.FOLDER,
                site.resolve("absent").toString()));
        assertInvalid(() -> DocSource.create(UUID.randomUUID(), project, DocSource.Kind.FOLDER, "  "));
    }

    @Test
    void dixSourcesAuPlusParProjet() {
        assertThat(DocSource.MAX_PER_PROJECT).isEqualTo(10);
        DocSource.requireRoomFor(9);
        assertInvalid(() -> DocSource.requireRoomFor(10));
    }
}
