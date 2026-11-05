package dev.specwriter.domain.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Fiche générale et sources de documentation (data-model.md de 002, FR-008, FR-013, T019, T113). */
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
    void uneSeuleFicheGeneraleModifieeALaMain() {
        Project p = Project.create(UUID.randomUUID(), ProjectName.of("Facturation"), null, null, T0);
        assertThat(p.context()).isEqualTo(ProjectContext.NONE);
        Project edited = p.withContext(ProjectContext.edited("## Vocabulaire\n- avoir", T0.plusSeconds(1)));
        assertThat(edited.context().markdown()).isEqualTo("## Vocabulaire\n- avoir");
        assertThat(edited.updatedAt()).isEqualTo(T0.plusSeconds(1));
        // modifier le projet ne touche pas à sa fiche
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
        assertInvalid(() -> DocSource.create(UUID.randomUUID(), project, DocSource.Kind.FOLDER, "  "));
    }

    /** Le motif du refus correspond au dossier : relatif, absent, fichier ou illisible. */
    @Test
    void sourceDossierRefuseeAvecSonMotif() throws IOException {
        UUID project = UUID.randomUUID();
        assertFolderRefused("site/relatif", "Le dossier doit être un chemin absolu (par exemple /Users/moi/site).");
        assertFolderRefused(site.resolve("absent").toString(), "Ce dossier n'existe pas : " + site.resolve("absent"));
        Path file = Files.createTempFile("page", ".html");
        try {
            assertFolderRefused(file.toString(), "Ce chemin désigne un fichier, pas un dossier : " + file);
        } finally {
            Files.delete(file);
        }
        Path locked = Files.createTempDirectory("verrouille");
        try {
            if (locked.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                Files.setPosixFilePermissions(locked, java.util.Set.of());
                if (!Files.isReadable(locked)) {
                    assertFolderRefused(locked.toString(), "Droits insuffisants pour lire ce dossier : " + locked);
                }
            }
        } finally {
            Files.setPosixFilePermissions(locked, java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
            Files.delete(locked);
        }
        assertThat(DocSource.create(UUID.randomUUID(), project, DocSource.Kind.FOLDER, site.toString())).isNotNull();
    }

    static void assertFolderRefused(String location, String message) {
        assertThatThrownBy(() -> DocSource.create(UUID.randomUUID(), UUID.randomUUID(), DocSource.Kind.FOLDER, location))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(e.userMessage()).isEqualTo(message);
                });
    }

    @Test
    void dixSourcesAuPlusParProjet() {
        assertThat(DocSource.MAX_PER_PROJECT).isEqualTo(10);
        DocSource.requireRoomFor(9);
        assertInvalid(() -> DocSource.requireRoomFor(10));
    }
}
