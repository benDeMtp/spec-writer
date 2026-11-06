package dev.specwriter.infrastructure.docs;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.domain.port.DocumentationReader.UnreadReason;
import dev.specwriter.support.TestDocuments;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 006, research.md R1 : texte des PDF par page. */
class PdfTextTest {

    @TempDir
    Path tmp;

    @Test
    void texteParPage() throws Exception {
        Path file = tmp.resolve("Exploitation.pdf");
        TestDocuments.pdf(file, "Exploitation", List.of("Supervision par Prometheus.", "Sauvegarde quotidienne."));

        Extracted doc = PdfText.read(file, 200_000);

        assertThat(doc.title()).isEqualTo("Exploitation");
        assertThat(doc.text()).isEqualTo("""
                ## Page 1

                Supervision par Prometheus.

                ## Page 2

                Sauvegarde quotidienne.""");
    }

    @Test
    void titreParDefautEtPagesVidesIgnorees() throws Exception {
        Path file = tmp.resolve("Plan de reprise.pdf");
        TestDocuments.pdf(file, "", List.of("Reprise en 4 heures.", " "));
        Extracted doc = PdfText.read(file, 200_000);
        assertThat(doc.title()).isEqualTo("Plan de reprise");
        assertThat(doc.text()).isEqualTo("## Page 1\n\nReprise en 4 heures.");
    }

    @Test
    void echecsMotives() throws Exception {
        Path scan = tmp.resolve("scan.pdf");
        TestDocuments.scannedPdf(scan);
        OoxmlTextTest.failsWith(() -> PdfText.read(scan, 200_000), UnreadReason.NO_TEXT);

        Path encrypted = tmp.resolve("chiffre.pdf");
        TestDocuments.encryptedPdf(encrypted);
        OoxmlTextTest.failsWith(() -> PdfText.read(encrypted, 200_000), UnreadReason.PROTECTED);

        Path broken = Files.writeString(tmp.resolve("casse.pdf"), "%PDF-1.7 rien");
        OoxmlTextTest.failsWith(() -> PdfText.read(broken, 200_000), UnreadReason.UNREADABLE);

        Path many = tmp.resolve("long.pdf");
        TestDocuments.pdf(many, "Long", Collections.nCopies(PdfText.MAX_PAGES + 1, "p"));
        OoxmlTextTest.failsWith(() -> PdfText.read(many, 200_000), UnreadReason.TOO_LARGE);

        Path text = tmp.resolve("texte.pdf");
        TestDocuments.pdf(text, "Texte", List.of("x".repeat(80)));
        OoxmlTextTest.failsWith(() -> PdfText.read(text, 50), UnreadReason.TOO_LARGE);
    }
}
