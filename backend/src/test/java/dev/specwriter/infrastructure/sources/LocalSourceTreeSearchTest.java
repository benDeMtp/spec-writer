package dev.specwriter.infrastructure.sources;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.application.sources.AnalyzePerimeter;
import dev.specwriter.domain.port.SourceTree;
import dev.specwriter.domain.port.SourceTree.Limits;
import dev.specwriter.domain.port.SourceTree.ModuleHit;
import dev.specwriter.domain.project.ExclusionPatterns;
import dev.specwriter.domain.sources.ModuleKind;
import dev.specwriter.domain.sources.Occurrence;
import dev.specwriter.domain.sources.PerimeterAnalysis;
import dev.specwriter.domain.sources.PerimeterModule;
import dev.specwriter.domain.sources.PerimeterTerms;
import dev.specwriter.domain.sources.Reason;
import dev.specwriter.domain.sources.SourceRoot;
import dev.specwriter.support.FixedClock;
import dev.specwriter.support.InMemorySourceRoots;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Recherche des termes dans les sources (013, T020). */
class LocalSourceTreeSearchTest {

    @TempDir
    Path dir;

    final LocalSourceTree tree = new LocalSourceTree();

    static SourceRoot root(Path location, String... exclusions) {
        return new SourceRoot(UUID.randomUUID(), UUID.randomUUID(), location.toString(),
                ExclusionPatterns.of(List.of(exclusions)), Instant.EPOCH);
    }

    static void write(Path file, String content) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    SourceTree.Scan search(SourceRoot root, String... terms) {
        return tree.search(List.of(root), PerimeterTerms.of(List.of(terms)), Limits.DEFAULT, () -> false);
    }

    static ModuleHit hit(SourceTree.Scan scan, String name) {
        return scan.hits().stream().filter(h -> h.module().name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void laRechercheEstLitteraleEtInsensibleALaCasse() throws Exception {
        write(dir.resolve("app/pom.xml"), "<project><artifactId>app</artifactId></project>");
        write(dir.resolve("app/A.java"), "class A {\n  ContractStatus s;\n  contractstatus x; CONTRACTSTATUS y;\n}\n");
        write(dir.resolve("app/B.java"), "a.b*c [x]\nabxc\n");
        SourceRoot root = root(dir);
        SourceTree.Scan scan = search(root, "ContractStatus", "b*c [x]", "b.c");
        ModuleHit app = hit(scan, "app");
        // les caractères spéciaux d'un terme ne sont pas des motifs : « b.c » ne trouve pas « bxc »
        assertThat(app.countByTerm()).containsEntry("ContractStatus", 2).containsEntry("b*c [x]", 1)
                .doesNotContainKey("b.c");
        assertThat(app.samples()).extracting(Occurrence::path, Occurrence::line)
                .contains(org.assertj.core.groups.Tuple.tuple("app/A.java", 2), org.assertj.core.groups.Tuple.tuple("app/A.java", 3));
    }

    @Test
    void lesFichiersHorsModuleVontAuModuleDeLaRacine() throws Exception {
        Path rv = dir.resolve("rv");
        write(rv.resolve("README.md"), "ContractStatus\n");
        SourceTree.Scan scan = search(root(rv), "ContractStatus");
        assertThat(scan.hits()).singleElement().satisfies(h -> {
            assertThat(h.module().kind()).isEqualTo(ModuleKind.ROOT);
            assertThat(h.module().name()).isEqualTo("rv");
        });
    }

    @Test
    void binairesEtFichiersTropGrosSontEcartesEtComptes() throws Exception {
        write(dir.resolve("a.txt"), "ContractStatus\n");
        Files.write(dir.resolve("b.bin"), ("\0ContractStatus").getBytes(StandardCharsets.UTF_8));
        write(dir.resolve("gros.txt"), "ContractStatus\n" + "x".repeat(2_000));
        SourceTree.Scan scan = tree.search(List.of(root(dir)), PerimeterTerms.of(List.of("ContractStatus")),
                new Limits(100, Duration.ofMinutes(1), 1_000, 100), () -> false);
        assertThat(scan.hits()).singleElement().satisfies(h -> assertThat(h.total()).isEqualTo(1));
        assertThat(scan.filesSkipped()).isEqualTo(2);
        assertThat(scan.filesScanned()).isEqualTo(1);
        assertThat(scan.skippedExamples()).containsExactly(dir.getFileName() + "/gros.txt");
    }

    @Test
    void unFichierLatin1ResteLisible() throws Exception {
        Files.write(dir.resolve("vieux.txt"), "Statut résilié : ContractStatus\n".getBytes(StandardCharsets.ISO_8859_1));
        assertThat(search(root(dir), "ContractStatus").hits().getFirst().samples().getFirst().excerpt())
                .isEqualTo("Statut résilié : ContractStatus");
    }

    @Test
    void lEchantillonEstBorneEtLeTotalExact() throws Exception {
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 340; i++) {
            many.append("ContractStatus ").append(i).append('\n');
        }
        write(dir.resolve("a/A.java"), many.toString());
        write(dir.resolve("b/B.java"), "ContractStatus\n");
        ModuleHit hit = search(root(dir), "ContractStatus").hits().getFirst();
        assertThat(hit.total()).isEqualTo(341);
        assertThat(hit.samples()).hasSize(20);
        assertThat(hit.samples().getFirst().path()).isEqualTo("a/A.java");
    }

    @Test
    void deuxRecherchesDonnentLeMemeResultat() {
        Path demo = DemoTree.copy(dir);
        SourceRoot root = root(demo);
        SourceTree.Scan first = search(root, "ContractStatus", "CONTRACT_STATUS", "contract.status");
        SourceTree.Scan second = search(root, "ContractStatus", "CONTRACT_STATUS", "contract.status");
        assertThat(second.hits()).isEqualTo(first.hits());
        assertThat(second.modules()).isEqualTo(first.modules());
    }

    @Test
    void lArbreDeDemonstrationDonneLesModulesAttendusSansAucunPiege() {
        Path demo = DemoTree.copy(dir);
        SourceTree.Scan scan = search(root(demo), "ContractStatus", "CONTRACT_STATUS", "contract.status");
        assertThat(scan.hits()).extracting(h -> h.module().name()).containsExactlyInAnyOrder("common-contract",
                "quarkus-contract", "facturation", "annuaire", "ancien", "demo-front", "facturation-chart");
        assertThat(scan.modules()).hasSize(14);
        assertThat(scan.descriptors()).hasSize(12);
        List<String> paths = scan.hits().stream().flatMap(h -> h.samples().stream()).map(Occurrence::path).toList();
        assertThat(paths).noneMatch(p -> p.contains(".env") || p.contains("settings.xml") || p.contains("node_modules")
                || p.contains("target/") || p.contains("generated/") || p.contains("data.bin") || p.contains("lien-externe"));
        assertThat(scan.roots()).singleElement().extracting(PerimeterAnalysis.RootStatus::state)
                .isEqualTo(PerimeterAnalysis.RootState.SCANNED);
        assertThat(scan.incomplete()).isNull();
    }

    @Test
    void surLArbreDeDemonstrationLesDependantsEtLAgregateurSontAjoutes() {
        Path demo = DemoTree.copy(dir);
        SourceRoot root = root(demo);
        InMemorySourceRoots roots = new InMemorySourceRoots();
        roots.save(root);
        PerimeterAnalysis analysis = new AnalyzePerimeter(roots, tree, null, new FixedClock()).analyze(root.projectId(),
                List.of("ContractStatus", "CONTRACT_STATUS", "contract.status"), null, 1, () -> false);
        assertThat(analysis.modules()).extracting(m -> m.module().name()).hasSize(9).contains("relance", "aggregates");
        assertThat(chain(analysis, "relance")).isEqualTo("facturation → relance");
        assertThat(chain(analysis, "facturation")).isEqualTo("quarkus-contract → facturation");
        assertThat(module(analysis, "aggregates").reasons()).singleElement().isInstanceOfSatisfying(Reason.Aggregator.class,
                a -> assertThat(a.of().name()).isEqualTo("common-contract"));
    }

    static PerimeterModule module(PerimeterAnalysis analysis, String name) {
        return analysis.modules().stream().filter(m -> m.module().name().equals(name)).findFirst().orElseThrow();
    }

    static String chain(PerimeterAnalysis analysis, String name) {
        return module(analysis, name).reasons().stream().filter(Reason.Dependency.class::isInstance)
                .map(Reason.Dependency.class::cast).findFirst().orElseThrow().chain().stream()
                .map(r -> r.name()).collect(java.util.stream.Collectors.joining(" → "));
    }

    @Test
    void uneRacineAbsenteEstSignaleeEtLesAutresParcourues() throws Exception {
        Path a = dir.resolve("a");
        write(a.resolve("A.java"), "ContractStatus\n");
        SourceTree.Scan scan = tree.search(List.of(root(dir.resolve("absente")), root(a)),
                PerimeterTerms.of(List.of("ContractStatus")), Limits.DEFAULT, () -> false);
        assertThat(scan.roots()).extracting(PerimeterAnalysis.RootStatus::state)
                .containsExactly(PerimeterAnalysis.RootState.MISSING, PerimeterAnalysis.RootState.SCANNED);
        assertThat(scan.hits()).hasSize(1);
        assertThat(scan.incomplete()).isEqualTo(PerimeterAnalysis.IncompleteReason.ROOT_MISSING);
    }

    @Test
    void lAnnulationArreteLaRecherche() throws Exception {
        write(dir.resolve("A.java"), "ContractStatus\n");
        AtomicBoolean cancelled = new AtomicBoolean(true);
        SourceTree.Scan scan = tree.search(List.of(root(dir)), PerimeterTerms.of(List.of("ContractStatus")),
                Limits.DEFAULT, cancelled::get);
        assertThat(scan.incomplete()).isEqualTo(PerimeterAnalysis.IncompleteReason.CANCELLED);
    }

    @Test
    void laLimiteDeFichiersRendLAnalyseIncomplete() throws Exception {
        for (int i = 0; i < 5; i++) {
            write(dir.resolve("f" + i + ".txt"), "ContractStatus\n");
        }
        SourceTree.Scan scan = tree.search(List.of(root(dir)), PerimeterTerms.of(List.of("ContractStatus")),
                new Limits(3, Duration.ofMinutes(1), 1_000, 100), () -> false);
        assertThat(scan.incomplete()).isEqualTo(PerimeterAnalysis.IncompleteReason.MAX_FILES);
        assertThat(scan.filesScanned()).isEqualTo(3);
    }
}
