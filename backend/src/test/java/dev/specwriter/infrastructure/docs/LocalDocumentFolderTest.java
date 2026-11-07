package dev.specwriter.infrastructure.docs;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.domain.knowledge.AdrStatus;
import dev.specwriter.domain.knowledge.KnowledgeKind;
import dev.specwriter.domain.port.DocumentFolder;
import dev.specwriter.domain.port.DocumentFolder.FileEntry;
import dev.specwriter.domain.port.DocumentationReader.Section;
import dev.specwriter.domain.port.DocumentationReader.UnreadReason;
import dev.specwriter.domain.project.DocSource;
import dev.specwriter.domain.project.ExclusionPatterns;
import dev.specwriter.domain.project.Project;
import dev.specwriter.support.TestDocuments;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 006, US1 et US2 : parcours et lecture d'un dossier de documents. */
class LocalDocumentFolderTest {

    @TempDir
    Path tmp;

    Path root;
    final LocalDocumentFolder folder = new LocalDocumentFolder(DocumentFolder.Limits.DEFAULT);

    @BeforeEach
    void setUp() throws IOException {
        root = TestDocuments.writeAll(tmp.resolve("Aurora"));
    }

    DocSource source(String... exclusions) {
        return new DocSource(UUID.randomUUID(), Project.DEFAULT_ID, DocSource.Kind.DOCUMENTS, root.toString(), null, null,
                ExclusionPatterns.of(List.of(exclusions)));
    }

    @Test
    void parcoursSansOuvrirDeFichier() {
        DocumentFolder.Scan scan = folder.scan(source());

        assertThat(scan.retained()).extracting(FileEntry::path).containsExactly("Accueil.html", "Archives/2023.pdf",
                "Decisions/0017-ihm-nginx.docx", "Exploitation.pdf", "Specs/Aurora.docx", "Suivi.xlsx", "protege.docx",
                "scan.pdf");
        assertThat(scan.ignored()).containsExactly("vieux.doc");
        assertThat(scan.excluded()).isEmpty();
        assertThat(scan.limitReached()).isFalse();
        assertThat(scan.problem()).isNull();
        assertThat(scan.retained()).allSatisfy(f -> {
            assertThat(f.size()).isPositive();
            assertThat(f.modifiedAt()).isNotNull();
        });
    }

    @Test
    void motifsDExclusion() {
        DocumentFolder.Scan scan = folder.scan(source("Archives/**", "*.xlsx"));
        assertThat(scan.excluded()).containsExactly("Archives/2023.pdf", "Suivi.xlsx");
        assertThat(scan.retained()).extracting(FileEntry::path).doesNotContain("Archives/2023.pdf", "Suivi.xlsx");
    }

    @Test
    void liensSymboliquesEtElementsCachesIgnores() throws IOException {
        Path outside = Files.createDirectories(tmp.resolve("ailleurs"));
        TestDocuments.pdf(outside.resolve("secret.pdf"), "Secret", List.of("Secret."));
        Files.createSymbolicLink(root.resolve("lien"), outside);
        Files.createSymbolicLink(root.resolve("lien.pdf"), outside.resolve("secret.pdf"));
        TestDocuments.pdf(root.resolve(".cache/cache.pdf"), "Cache", List.of("Cache."));

        DocumentFolder.Scan scan = folder.scan(source());

        assertThat(scan.retained()).extracting(FileEntry::path).noneMatch(p -> p.contains("lien") || p.contains("cache"));
        assertThat(folder.read(source(), "lien.pdf").failure()).isEqualTo(UnreadReason.UNREADABLE);
        assertThat(folder.read(source(), "../ailleurs/secret.pdf").failure()).isEqualTo(UnreadReason.UNREADABLE);
    }

    @Test
    void dossierIllisible() throws IOException {
        DocSource missing = new DocSource(UUID.randomUUID(), Project.DEFAULT_ID, DocSource.Kind.DOCUMENTS,
                tmp.resolve("absent").toString(), null, null);
        assertThat(folder.scan(missing).problem()).isEqualTo(UnreadReason.FOLDER_NOT_FOUND);
        Path file = Files.writeString(tmp.resolve("fichier.txt"), "x");
        DocSource notFolder = new DocSource(UUID.randomUUID(), Project.DEFAULT_ID, DocSource.Kind.DOCUMENTS,
                file.toString(), null, null);
        assertThat(folder.scan(notFolder).problem()).isEqualTo(UnreadReason.NOT_A_FOLDER);
    }

    @Test
    void limiteDeDocuments() {
        LocalDocumentFolder small = new LocalDocumentFolder(new DocumentFolder.Limits(3, Duration.ofMinutes(1),
                20L * 1024 * 1024, 200_000, Duration.ofSeconds(60)));
        DocumentFolder.Scan scan = small.scan(source());
        assertThat(scan.retained()).hasSize(3);
        assertThat(scan.limitReached()).isTrue();
    }

    @Test
    void documentWordEnSections() {
        DocSource source = source();
        DocumentFolder.Reading reading = folder.read(source, "Specs/Aurora.docx");

        assertThat(reading.failure()).isNull();
        String ref = "docs:" + source.id() + "/Specs/Aurora.docx";
        assertThat(reading.page().ref()).isEqualTo(ref);
        assertThat(reading.page().title()).isEqualTo("Aurora");
        assertThat(reading.page().sections()).extracting(Section::ref, Section::title).containsExactly(
                org.assertj.core.groups.Tuple.tuple(ref + "#0", "Aurora — Contexte"),
                org.assertj.core.groups.Tuple.tuple(ref + "#1", "Aurora — Exigences"),
                org.assertj.core.groups.Tuple.tuple(ref + "#2", "Aurora — Exploitation"));
        assertThat(reading.page().sections().get(1).body()).contains("GAIA").contains("| nginx | 1.27 |");
        assertThat(reading.page().sections()).allMatch(s -> s.kind() == KnowledgeKind.DOC_SECTION);
    }

    @Test
    void adrEnWordGardeEntier() {
        DocSource source = source();
        DocumentFolder.Reading reading = folder.read(source, "Decisions/0017-ihm-nginx.docx");

        assertThat(reading.page().sections()).singleElement().satisfies(s -> {
            assertThat(s.kind()).isEqualTo(KnowledgeKind.ADR);
            assertThat(s.ref()).isEqualTo("docs:" + source.id() + "/Decisions/0017-ihm-nginx.docx");
            assertThat(s.adr().status()).isEqualTo(AdrStatus.ACCEPTED);
            assertThat(s.body()).contains("nginx avec OIDC").contains("Une image nginx");
        });
    }

    @Test
    void autresFormats() {
        DocSource source = source();
        assertThat(folder.read(source, "Suivi.xlsx").page().sections()).extracting(Section::title)
                .containsExactly("Suivi — Jalons", "Suivi — Risques");
        assertThat(folder.read(source, "Exploitation.pdf").page().text()).contains("Prometheus");
        assertThat(folder.read(source, "Accueil.html").page().text()).contains("Bienvenue").doesNotContain("alert");
    }

    @Test
    void echecsParFichier() throws IOException {
        DocSource source = source();
        assertThat(folder.read(source, "protege.docx").failure()).isEqualTo(UnreadReason.PROTECTED);
        assertThat(folder.read(source, "scan.pdf").failure()).isEqualTo(UnreadReason.NO_TEXT);
        assertThat(folder.read(source, "disparu.docx").failure()).isEqualTo(UnreadReason.UNAVAILABLE);
        assertThat(folder.read(source, "vieux.doc").failure()).isEqualTo(UnreadReason.UNREADABLE);

        LocalDocumentFolder strict = new LocalDocumentFolder(new DocumentFolder.Limits(10, Duration.ofMinutes(1),
                100, 200_000, Duration.ofSeconds(60)));
        assertThat(strict.read(source, "Exploitation.pdf").failure()).isEqualTo(UnreadReason.TOO_LARGE);

        LocalDocumentFolder hurried = new LocalDocumentFolder(new DocumentFolder.Limits(10, Duration.ofMinutes(1),
                20L * 1024 * 1024, 200_000, Duration.ofNanos(1)));
        assertThat(hurried.read(source, "Exploitation.pdf").failure()).isEqualTo(UnreadReason.UNAVAILABLE);
    }

    @Test
    void aucunFichierEcrit() throws IOException {
        long before = count(tmp);
        DocSource source = source();
        folder.scan(source).retained().forEach(f -> folder.read(source, f.path()));
        assertThat(count(tmp)).isEqualTo(before);
    }

    static long count(Path dir) throws IOException {
        try (Stream<Path> files = Files.walk(dir)) {
            return files.count();
        }
    }
}
