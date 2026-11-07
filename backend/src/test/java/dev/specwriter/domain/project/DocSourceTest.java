package dev.specwriter.domain.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 006 : dossier de documents et ses motifs d'exclusion. */
class DocSourceTest {

    static final UUID P = Project.DEFAULT_ID;

    @TempDir
    Path tmp;

    @Test
    void dossierDeDocumentsValideCommeUnDossier() throws Exception {
        DocSource source = DocSource.create(UUID.randomUUID(), P, DocSource.Kind.DOCUMENTS, tmp.toString(),
                ExclusionPatterns.of(List.of("Archives/**")));
        assertThat(source.kind()).isEqualTo(DocSource.Kind.DOCUMENTS);
        assertThat(source.exclusions().patterns()).containsExactly("Archives/**");

        Path file = Files.writeString(tmp.resolve("a.txt"), "x");
        assertThatThrownBy(() -> DocSource.create(UUID.randomUUID(), P, DocSource.Kind.DOCUMENTS, file.toString(),
                ExclusionPatterns.NONE)).isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.userMessage()).contains("fichier"));
        assertThatThrownBy(() -> DocSource.create(UUID.randomUUID(), P, DocSource.Kind.DOCUMENTS, "relatif",
                ExclusionPatterns.NONE)).isInstanceOf(DomainException.class);
    }

    @Test
    void motifsReservesAuDossierDeDocuments() {
        assertThatThrownBy(() -> DocSource.create(UUID.randomUUID(), P, DocSource.Kind.FOLDER, tmp.toString(),
                ExclusionPatterns.of(List.of("*.pdf")))).isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(e.userMessage()).contains("dossier de documents");
                });
        DocSource antora = DocSource.create(UUID.randomUUID(), P, DocSource.Kind.FOLDER, tmp.toString());
        assertThat(antora.exclusions().isEmpty()).isTrue();
    }

    @Test
    void motifsModifies() {
        DocSource source = DocSource.create(UUID.randomUUID(), P, DocSource.Kind.DOCUMENTS, tmp.toString(),
                ExclusionPatterns.NONE);
        DocSource changed = source.withExclusions(ExclusionPatterns.of(List.of("*.xlsx")));
        assertThat(changed.exclusions().patterns()).containsExactly("*.xlsx");
        assertThat(changed.id()).isEqualTo(source.id());
        DocSource antora = DocSource.create(UUID.randomUUID(), P, DocSource.Kind.FOLDER, tmp.toString());
        assertThatThrownBy(() -> antora.withExclusions(ExclusionPatterns.of(List.of("*.xlsx"))))
                .isInstanceOf(DomainException.class);
    }
}
