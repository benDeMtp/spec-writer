package dev.specwriter.infrastructure.docs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.port.DocumentationReader.UnreadReason;
import dev.specwriter.support.TestDocuments;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 006, research.md R1 : Word et Excel lus directement, sans DTD ni bombe zip. */
class OoxmlTextTest {

    static final int MAX_CHARS = 200_000;

    @TempDir
    Path tmp;

    @Test
    void wordTitresParagraphesEtTableaux() throws Exception {
        Path file = tmp.resolve("Aurora.docx");
        TestDocuments.docx(file, "Aurora", List.of("H1:Contexte", "Aurora publie l'IHM.", "H2:Exigences",
                "Authentification GAIA.", "TABLE:Composant|Version;nginx|1.27", "Fin."));

        Extracted doc = OoxmlText.docx(file, MAX_CHARS);

        assertThat(doc.title()).isEqualTo("Aurora");
        assertThat(doc.text()).isEqualTo("""
                # Contexte

                Aurora publie l'IHM.

                ## Exigences

                Authentification GAIA.

                | Composant | Version |
                | nginx | 1.27 |

                Fin.""");
    }

    @Test
    void titreDuWordParDefaut() throws Exception {
        Path file = tmp.resolve("Notes de cadrage.docx");
        TestDocuments.zip(file, Map.of("word/document.xml",
                "<w:document xmlns:w=\"w\"><w:body><w:p><w:pPr><w:pStyle w:val=\"Titre1\"/></w:pPr>"
                        + "<w:r><w:t>Cadrage</w:t></w:r></w:p></w:body></w:document>"));
        assertThat(OoxmlText.docx(file, MAX_CHARS).title()).isEqualTo("Cadrage");

        Path bare = tmp.resolve("Sans titre.docx");
        TestDocuments.zip(bare, Map.of("word/document.xml",
                "<w:document xmlns:w=\"w\"><w:body><w:p><w:r><w:t>Texte</w:t></w:r></w:p></w:body></w:document>"));
        assertThat(OoxmlText.docx(bare, MAX_CHARS).title()).isEqualTo("Sans titre");
    }

    @Test
    void excelUneSectionParFeuille() throws Exception {
        Path file = tmp.resolve("Suivi.xlsx");
        Map<String, List<List<String>>> sheets = new LinkedHashMap<>();
        sheets.put("Jalons", List.of(List.of("Jalon", "Date"), List.of("Recette", "2026-12-01")));
        sheets.put("Risques", List.of(List.of("Risque", "Niveau"), List.of("Quota", "moyen")));
        TestDocuments.xlsx(file, sheets);

        Extracted doc = OoxmlText.xlsx(file, MAX_CHARS);

        assertThat(doc.title()).isEqualTo("Suivi");
        assertThat(doc.text()).isEqualTo("""
                ## Jalons

                | Jalon | Date |
                | Recette | 2026-12-01 |

                ## Risques

                | Risque | Niveau |
                | Quota | moyen |""");
    }

    @Test
    void classeurTropGrand() throws Exception {
        Path file = tmp.resolve("Gros.xlsx");
        List<List<String>> rows = new ArrayList<>();
        for (int i = 0; i < 25_001; i++) {
            rows.add(List.of("a" + i, "b"));
        }
        TestDocuments.xlsx(file, Map.of("Données", rows));
        failsWith(() -> OoxmlText.xlsx(file, 10_000_000), UnreadReason.TOO_LARGE);
    }

    @Test
    void entitesExternesJamaisResolues() throws Exception {
        Path secret = Files.writeString(tmp.resolve("secret.txt"), "MOT-DE-PASSE");
        Path file = tmp.resolve("xxe.docx");
        TestDocuments.zip(file, Map.of("word/document.xml", "<?xml version=\"1.0\"?>"
                + "<!DOCTYPE d [<!ENTITY xxe SYSTEM \"" + secret.toUri() + "\">]>"
                + "<w:document xmlns:w=\"w\"><w:body><w:p><w:r><w:t>&xxe;</w:t></w:r></w:p></w:body></w:document>"));
        try {
            assertThat(OoxmlText.docx(file, MAX_CHARS).text()).doesNotContain("MOT-DE-PASSE");
        } catch (ExtractionException e) {
            assertThat(e.reason()).isEqualTo(UnreadReason.UNREADABLE);
        }
    }

    @Test
    void bombeZipArreteeALaLimite() throws Exception {
        Path file = tmp.resolve("bombe.docx");
        TestDocuments.zipBomb(file, 60);
        failsWith(() -> OoxmlText.docx(file, MAX_CHARS), UnreadReason.TOO_LARGE);
    }

    @Test
    void documentProtegeIllisibleOuTropLong() throws Exception {
        Path protectedFile = tmp.resolve("protege.docx");
        TestDocuments.protectedOffice(protectedFile);
        failsWith(() -> OoxmlText.docx(protectedFile, MAX_CHARS), UnreadReason.PROTECTED);

        Path garbage = Files.writeString(tmp.resolve("casse.docx"), "pas un zip");
        failsWith(() -> OoxmlText.docx(garbage, MAX_CHARS), UnreadReason.UNREADABLE);

        Path empty = tmp.resolve("vide.docx");
        TestDocuments.zip(empty, Map.of("autre.xml", "<a/>"));
        failsWith(() -> OoxmlText.docx(empty, MAX_CHARS), UnreadReason.UNREADABLE);

        Path longDoc = tmp.resolve("long.docx");
        TestDocuments.docx(longDoc, "Long", List.of("x".repeat(500)));
        failsWith(() -> OoxmlText.docx(longDoc, 100), UnreadReason.TOO_LARGE);
    }

    interface Extraction {
        Extracted run() throws Exception;
    }

    static void failsWith(Extraction extraction, UnreadReason reason) {
        assertThatThrownBy(extraction::run).isInstanceOfSatisfying(ExtractionException.class,
                e -> assertThat(e.reason()).isEqualTo(reason));
    }
}
