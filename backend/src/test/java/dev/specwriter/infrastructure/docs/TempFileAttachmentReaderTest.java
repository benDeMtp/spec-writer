package dev.specwriter.infrastructure.docs;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.domain.port.AttachmentReader.Reading;
import dev.specwriter.domain.port.DocumentFolder.Limits;
import dev.specwriter.domain.port.DocumentationReader.UnreadReason;
import dev.specwriter.support.TestDocuments;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 009 : un document joint est lu par les lecteurs de la 006 sans laisser de fichier (research.md R2). */
class TempFileAttachmentReaderTest {

    @TempDir
    Path sources;
    @TempDir
    Path tmp;

    TempFileAttachmentReader reader() {
        return new TempFileAttachmentReader(tmp.resolve("tmp"), Limits.DEFAULT);
    }

    byte[] bytes(Path file) throws Exception {
        return Files.readAllBytes(file);
    }

    void noTemporaryFileLeft() throws Exception {
        Path dir = tmp.resolve("tmp");
        if (Files.exists(dir)) {
            try (Stream<Path> files = Files.list(dir)) {
                assertThat(files).isEmpty();
            }
        }
    }

    @Test
    void quatreFormatsLus() throws Exception {
        Path docx = sources.resolve("CR.docx");
        TestDocuments.docx(docx, "Compte rendu", List.of("H1:Décisions", "Le délai de rétractation est de 14 jours."));
        Reading word = reader().read("CR.docx", bytes(docx));
        assertThat(word.ok()).isTrue();
        assertThat(word.title()).isEqualTo("Compte rendu");
        assertThat(word.text()).contains("14 jours");

        Path xlsx = sources.resolve("Suivi.xlsx");
        TestDocuments.xlsx(xlsx, Map.of("Jalons", List.of(List.of("Jalon", "Date"), List.of("Recette", "2026-12-01"))));
        assertThat(reader().read("Suivi.xlsx", bytes(xlsx)).text()).contains("Recette");

        Path pdf = sources.resolve("Note.pdf");
        TestDocuments.pdf(pdf, "Note", List.of("Plafond de 300 euros par avoir."));
        assertThat(reader().read("Note.pdf", bytes(pdf)).text()).contains("300 euros");

        byte[] html = "<html><head><title>Charte</title></head><body><h1>Charte</h1><p>Les avoirs sont signés.</p></body></html>"
                .getBytes(StandardCharsets.UTF_8);
        assertThat(reader().read("charte.htm", html).text()).contains("Les avoirs sont signés.");
        noTemporaryFileLeft();
    }

    @Test
    void refusAvecLaRaisonDeLa006() throws Exception {
        Path encrypted = sources.resolve("Secret.pdf");
        TestDocuments.encryptedPdf(encrypted);
        assertThat(reader().read("Secret.pdf", bytes(encrypted)).failure()).isEqualTo(UnreadReason.PROTECTED);

        Path scanned = sources.resolve("Scan.pdf");
        TestDocuments.scannedPdf(scanned);
        assertThat(reader().read("Scan.pdf", bytes(scanned)).failure()).isEqualTo(UnreadReason.NO_TEXT);

        Path office = sources.resolve("Protege.docx");
        TestDocuments.protectedOffice(office);
        assertThat(reader().read("Protege.docx", bytes(office)).failure()).isEqualTo(UnreadReason.PROTECTED);

        Path bomb = sources.resolve("Bombe.docx");
        TestDocuments.zipBomb(bomb, 60);
        assertThat(reader().read("Bombe.docx", bytes(bomb)).failure()).isEqualTo(UnreadReason.TOO_LARGE);

        assertThat(reader().read("ancien.doc", new byte[] {1, 2}).failure()).isEqualTo(UnreadReason.UNREADABLE);
        assertThat(new TempFileAttachmentReader(tmp.resolve("tmp"), new Limits(1, Limits.DEFAULT.maxDuration(), 10,
                1000, Limits.DEFAULT.fileTimeout())).read("gros.pdf", new byte[11]).failure()).isEqualTo(UnreadReason.TOO_LARGE);
        assertThat(reader().read("Casse.pdf", "pas un pdf".getBytes(StandardCharsets.UTF_8)).ok()).isFalse();
        noTemporaryFileLeft();
    }
}
