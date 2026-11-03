package dev.specwriter.domain.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Projet (data-model.md de 002, FR-001, FR-002, FR-006). */
class ProjectTest {

    static final Instant T0 = Instant.parse("2026-11-03T09:00:00Z");

    static Project facturation() {
        return Project.create(UUID.randomUUID(), ProjectName.of("Facturation"), null, JiraProjectKey.ofNullable("FACT"), T0);
    }

    @Test
    void nomDe1A80CaracteresSansEspaceEnDebutNiEnFin() {
        assertThat(ProjectName.of("  Facturation  ").value()).isEqualTo("Facturation");
        assertThat(ProjectName.of("x".repeat(80)).value()).hasSize(80);
        assertThatThrownBy(() -> ProjectName.of("x".repeat(81))).isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThatThrownBy(() -> ProjectName.of("   ")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> ProjectName.of(null)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new ProjectName(" Facturation")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new ProjectName("Facturation ")).isInstanceOf(DomainException.class);
    }

    @Test
    void unicitéDuNomSansTenirCompteDeLaCasse() {
        assertThat(ProjectName.of("Facturation").nameKey()).isEqualTo(ProjectName.of("FACTURATION").nameKey())
                .isEqualTo("facturation");
        assertThat(ProjectName.of("Équipe Paie").nameKey()).isEqualTo(ProjectName.of("équipe paie").nameKey());
        assertThat(ProjectName.of("Paie").nameKey()).isNotEqualTo(ProjectName.of("Paies").nameKey());
        assertThat(facturation().nameKey()).isEqualTo("facturation");
    }

    @Test
    void descriptionFacultativeDe1000CaracteresAuPlus() {
        UUID id = UUID.randomUUID();
        ProjectName name = ProjectName.of("Paie");
        assertThat(Project.create(id, name, null, null, T0).description()).isNull();
        assertThat(Project.create(id, name, "   ", null, T0).description()).isNull();
        assertThat(Project.create(id, name, " Bulletins de paie ", null, T0).description()).isEqualTo("Bulletins de paie");
        assertThat(Project.create(id, name, "d".repeat(1000), null, T0).description()).hasSize(1000);
        assertThatThrownBy(() -> Project.create(id, name, "d".repeat(1001), null, T0))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void cléJiraFacultativeAuFormatJira() {
        assertThat(JiraProjectKey.ofNullable(null)).isNull();
        assertThat(JiraProjectKey.ofNullable("  ")).isNull();
        assertThat(JiraProjectKey.ofNullable(" FACT ").value()).isEqualTo("FACT");
        assertThat(JiraProjectKey.ofNullable("AB").value()).isEqualTo("AB");
        assertThat(JiraProjectKey.ofNullable("A2_B3C4D5E").value()).hasSize(10);
        for (String invalid : new String[] {"A", "fact", "1FACT", "FA-CT", "ABCDEFGHIJK", "FACT OR 1=1"}) {
            assertThatThrownBy(() -> JiraProjectKey.ofNullable(invalid)).as(invalid).isInstanceOf(DomainException.class)
                    .extracting(e -> ((DomainException) e).code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        }
    }

    @Test
    void projetJiraEffectifCléDuProjetSinonDéfautDesRéglages() {
        assertThat(facturation().effectiveJiraProject("PROJ")).contains("FACT");
        Project sansClé = Project.create(UUID.randomUUID(), ProjectName.of("Paie"), null, null, T0);
        assertThat(sansClé.effectiveJiraProject("PROJ")).contains("PROJ");
        assertThat(sansClé.effectiveJiraProject(null)).isEmpty();
        assertThat(facturation().effectiveJiraProject(null)).contains("FACT");
    }

    @Test
    void modificationConserveLIdentitéEtLaDateDeCréation() {
        Project p = facturation();
        Instant later = T0.plusSeconds(60);
        Project changed = p.update(ProjectName.of("Facturation clients"), "Factures et avoirs", null, later);
        assertThat(changed.id()).isEqualTo(p.id());
        assertThat(changed.createdAt()).isEqualTo(T0);
        assertThat(changed.updatedAt()).isEqualTo(later);
        assertThat(changed.name().value()).isEqualTo("Facturation clients");
        assertThat(changed.description()).isEqualTo("Factures et avoirs");
        assertThat(changed.jiraProjectKey()).isNull();
        assertThat(changed.effectiveJiraProject("PROJ")).contains("PROJ");
    }

    @Test
    void projetParDéfaut() {
        assertThat(Project.DEFAULT_NAME).isEqualTo("Projet par défaut");
        assertThat(ProjectName.of(Project.DEFAULT_NAME).nameKey()).isEqualTo("projet par défaut");
    }
}
