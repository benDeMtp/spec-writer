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
    void origineAucunPuisGenerePuisModifieFicheParFiche() {
        Project p = Project.create(UUID.randomUUID(), ProjectName.of("Facturation"), null, null, T0);
        assertThat(p.context(ContextSheet.FUNCTIONAL).origin()).isEqualTo(ProjectContext.Origin.NONE);
        assertThat(p.contexts().isEmpty()).isTrue();
        Project generated = p.withContext(ContextSheet.FUNCTIONAL, ProjectContext.generated("## Vocabulaire\n- avoir", T0));
        assertThat(generated.context(ContextSheet.FUNCTIONAL).origin()).isEqualTo(ProjectContext.Origin.GENERATED);
        assertThat(generated.context(ContextSheet.TECHNICAL)).isEqualTo(ProjectContext.NONE);
        assertThat(generated.updatedAt()).isEqualTo(T0);
        Project edited = generated.withContext(ContextSheet.TECHNICAL,
                ProjectContext.edited("## Architecture\n- Quarkus", T0.plusSeconds(1)));
        assertThat(edited.context(ContextSheet.TECHNICAL).origin()).isEqualTo(ProjectContext.Origin.EDITED);
        assertThat(edited.context(ContextSheet.FUNCTIONAL)).isEqualTo(generated.context(ContextSheet.FUNCTIONAL));
        // modifier le projet ne touche pas à ses fiches
        assertThat(edited.update(ProjectName.of("Facturation clients"), null, null, T0.plusSeconds(2)).contexts())
                .isEqualTo(edited.contexts());
    }

    @Test
    void hashCombineEtDateLaPlusRecenteDesDeuxFiches() {
        ProjectContext f = ProjectContext.edited("## Acteurs\n- comptable", T0);
        ProjectContext t = ProjectContext.edited("## Architecture\n- Quarkus", T0.plusSeconds(60));
        assertThat(ContextSheets.NONE.combinedHash()).isEmpty();
        assertThat(ContextSheets.NONE.updatedAt()).isNull();
        ContextSheets both = new ContextSheets(f, t);
        assertThat(both.combinedHash()).hasSize(64).isNotEqualTo(f.hash()).isNotEqualTo(t.hash());
        assertThat(both.updatedAt()).isEqualTo(T0.plusSeconds(60));
        // une fiche qui change change le hash combiné ; la même fiche dans l'autre case aussi
        assertThat(new ContextSheets(f, ProjectContext.NONE).combinedHash()).isNotEqualTo(both.combinedHash())
                .isNotEqualTo(new ContextSheets(ProjectContext.NONE, f).combinedHash());
        assertThat(new ContextSheets(ProjectContext.NONE, t).updatedAt()).isEqualTo(T0.plusSeconds(60));
    }

    @Test
    void questionNumeroteeRepondueOuPassee() {
        UUID project = UUID.randomUUID();
        ContextQuestion answered = ContextQuestion.recorded(UUID.randomUUID(), project, 3, ContextSheet.TECHNICAL,
                "Quelle base de données ?", "Les sources se contredisent", "  PostgreSQL 16 ", false, T0, T0.plusSeconds(5));
        assertThat(answered.ref()).isEqualTo("Q-3");
        assertThat(answered.answer()).isEqualTo("PostgreSQL 16");
        assertThat(answered.state()).isEqualTo(ContextQuestion.State.ANSWERED);
        ContextQuestion skipped = answered.reanswered("ignorée", true, T0.plusSeconds(9));
        assertThat(skipped.state()).isEqualTo(ContextQuestion.State.SKIPPED);
        assertThat(skipped.answer()).isEmpty();
        assertThat(skipped.answeredAt()).isEqualTo(T0.plusSeconds(9));
        assertThat(skipped.askedAt()).isEqualTo(T0);
        assertInvalid(() -> answered.reanswered("  ", false, T0));
        assertInvalid(() -> answered.reanswered("x".repeat(2_001), false, T0));
        // un libellé trop long est tronqué, pas refusé : il vient de l'IA
        assertThat(ContextQuestion.recorded(UUID.randomUUID(), project, 1, ContextSheet.FUNCTIONAL, "q".repeat(600), null,
                null, true, T0, T0).label()).hasSize(ContextQuestion.MAX_LABEL);
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
