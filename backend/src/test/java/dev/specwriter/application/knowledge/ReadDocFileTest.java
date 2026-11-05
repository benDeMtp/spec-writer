package dev.specwriter.application.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.project.DocSource;
import dev.specwriter.domain.project.Project;
import dev.specwriter.support.FakeDocumentFiles;
import dev.specwriter.support.JiraWorld;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 004, US2 : fichiers d'un dossier de documentation servis par l'outil (research.md R3). */
class ReadDocFileTest {

    static final UUID P = Project.DEFAULT_ID;

    final JiraWorld w = new JiraWorld();
    final FakeDocumentFiles files = new FakeDocumentFiles();
    ReadDocFile read;

    @BeforeEach
    void setUp() {
        w.docSources.save(new DocSource(UUID.randomUUID(), P, DocSource.Kind.FOLDER, "/doc/a", null, null));
        w.docSources.save(new DocSource(UUID.randomUUID(), P, DocSource.Kind.FOLDER, "/doc/b", null, null));
        files.put("/doc/b", "adr/0001-x.html", "<html/>").put("/doc/b", "css/site.css", "body{}")
                .put("/doc/b", "img/a.PNG", "png").put("/doc/b", "js/site.js", "alert(1)")
                .put("/doc/b", "notes.txt", "texte");
        read = new ReadDocFile(w.projects, w.docSources, files);
    }

    @Test
    void fichierDUnDossierDeclareAvecSonType() {
        assertThat(read.run(P, "adr/0001-x.html")).get().satisfies(f -> {
            assertThat(f.mediaType()).isEqualTo("text/html; charset=utf-8");
            assertThat(new String(f.content(), java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("<html/>");
        });
        assertThat(read.run(P, "css/site.css")).get().extracting(ReadDocFile.Served::mediaType)
                .isEqualTo("text/css; charset=utf-8");
        assertThat(read.run(P, "img/a.PNG")).get().extracting(ReadDocFile.Served::mediaType).isEqualTo("image/png");
    }

    @Test
    void extensionRefuseeOuFichierAbsent() {
        assertThat(read.run(P, "js/site.js")).isEmpty();
        assertThat(read.run(P, "notes.txt")).isEmpty();
        assertThat(read.run(P, "adr/absent.html")).isEmpty();
        assertThat(read.run(P, "")).isEmpty();
        assertThat(read.run(P, null)).isEmpty();
    }

    @Test
    void seulesLesSourcesDossierDuProjet() {
        UUID other = UUID.randomUUID();
        w.projects.save(Project.create(other, dev.specwriter.domain.project.ProjectName.of("Autre"), null, null,
                w.clock.now()));
        w.docSources.save(new DocSource(UUID.randomUUID(), other, DocSource.Kind.FOLDER, "/doc/autre", null, null));
        files.put("/doc/autre", "secret.html", "<html/>");
        assertThat(read.run(P, "secret.html")).isEmpty();
        w.docSources.save(DocSource.create(UUID.randomUUID(), P, DocSource.Kind.URL, "https://doc.example.com/"));
        assertThat(read.run(P, "adr/0001-x.html")).isPresent();
        assertThatThrownBy(() -> read.run(UUID.randomUUID(), "adr/0001-x.html"))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.PROJECT_NOT_FOUND));
    }
}
