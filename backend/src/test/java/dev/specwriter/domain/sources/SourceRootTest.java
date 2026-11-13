package dev.specwriter.domain.sources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.project.ExclusionPatterns;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceRootTest {

    static final UUID PROJECT = UUID.randomUUID();
    static final Instant AT = Instant.parse("2026-11-11T09:00:00Z");

    @TempDir
    Path dir;

    SourceRoot create(Path location) {
        return SourceRoot.create(UUID.randomUUID(), PROJECT, location.toString(), ExclusionPatterns.NONE, AT);
    }

    @Test
    void leLibelleEstLeNomDuDossier() throws Exception {
        Path rv = Files.createDirectory(dir.resolve("rv"));
        SourceRoot root = create(rv);
        assertThat(root.label()).isEqualTo("rv");
        assertThat(root.location()).isEqualTo(rv.toString());
    }

    @Test
    void unCheminRelatifAbsentOuQuiDesigneUnFichierEstRefuseAvecSaRaison() throws Exception {
        Path file = Files.writeString(dir.resolve("f.txt"), "x");
        assertThatThrownBy(() -> SourceRoot.create(UUID.randomUUID(), PROJECT, "relatif/rv", ExclusionPatterns.NONE, AT))
                .isInstanceOf(DomainException.class).satisfies(e -> assertThat(((DomainException) e).userMessage()).contains("absolu"));
        assertThatThrownBy(() -> create(dir.resolve("absent"))).satisfies(e -> assertThat(((DomainException) e).userMessage()).contains("n'existe pas"));
        assertThatThrownBy(() -> create(file)).satisfies(e -> assertThat(((DomainException) e).userMessage()).contains("fichier"));
        assertThatThrownBy(() -> SourceRoot.create(UUID.randomUUID(), PROJECT, " ", ExclusionPatterns.NONE, AT))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void auPlusDixRacinesParProjet() {
        SourceRoot.requireRoomFor(9);
        assertThatThrownBy(() -> SourceRoot.requireRoomFor(10)).isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).userMessage()).contains("10"));
    }

    @Test
    void uneRacineDejaDeclareeOuImbriqueeEstRefusee() throws Exception {
        Path rv = Files.createDirectories(dir.resolve("rv/modules"));
        Path pipeline = Files.createDirectory(dir.resolve("pipeline"));
        SourceRoot parent = create(rv.getParent());
        List<SourceRoot> existing = List.of(parent);
        create(pipeline).requireCompatibleWith(existing);
        assertThatThrownBy(() -> create(rv.getParent()).requireCompatibleWith(existing)).satisfies(e -> assertThat(((DomainException) e).userMessage()).contains("déjà"));
        assertThatThrownBy(() -> create(rv).requireCompatibleWith(existing)).satisfies(e -> assertThat(((DomainException) e).userMessage()).contains("rv"));
        assertThatThrownBy(() -> create(dir).requireCompatibleWith(existing)).satisfies(e -> assertThat(((DomainException) e).userMessage()).contains("rv"));
    }

    @Test
    void lesExclusionsSeRemplacent() throws Exception {
        SourceRoot root = create(Files.createDirectory(dir.resolve("rv")));
        SourceRoot changed = root.withExclusions(ExclusionPatterns.of(List.of("legacy/**")));
        assertThat(changed.exclusions().patterns()).containsExactly("legacy/**");
        assertThat(changed.id()).isEqualTo(root.id());
    }
}
