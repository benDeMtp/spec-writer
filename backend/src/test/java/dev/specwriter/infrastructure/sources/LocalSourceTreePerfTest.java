package dev.specwriter.infrastructure.sources;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.domain.port.SourceTree;
import dev.specwriter.domain.project.ExclusionPatterns;
import dev.specwriter.domain.sources.PerimeterTerms;
import dev.specwriter.domain.sources.SourceRoot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 013, SC-003 : une analyse sur un arbre de la taille de rv (60 modules, 20 000 fichiers, 5 termes)
 * dure moins de 30 s. Hors de la suite par défaut : {@code ./mvnw -pl backend test -Pperf}.
 */
@Tag("perf")
class LocalSourceTreePerfTest {

    static final int MODULES = 60;
    static final int FILES = 20_000;

    @TempDir
    Path dir;

    @Test
    void soixanteModulesEtVingtMilleFichiersEnMoinsDeTrenteSecondes() throws IOException {
        Path root = dir.resolve("rv");
        int perModule = FILES / MODULES + 1;
        int written = 0;
        for (int m = 0; m < MODULES; m++) {
            Path module = root.resolve("modules/m" + m);
            Files.createDirectories(module.resolve("src/main/java/fr/demo"));
            Files.writeString(module.resolve("pom.xml"), "<project><groupId>fr.demo</groupId><artifactId>m" + m
                    + "</artifactId><dependencies><dependency><groupId>fr.demo</groupId><artifactId>m" + Math.max(0, m - 1)
                    + "</artifactId></dependency></dependencies></project>");
            for (int f = 0; f < perModule && written < FILES; f++, written++) {
                String body = "package fr.demo;\n\nclass C" + f + " {\n" + "    // ligne de remplissage\n".repeat(40)
                        + (f % 97 == 0 ? "    ContractStatus status;\n" : "") + "}\n";
                Files.writeString(module.resolve("src/main/java/fr/demo/C" + f + ".java"), body);
            }
        }
        SourceRoot source = new SourceRoot(UUID.randomUUID(), UUID.randomUUID(), root.toString(), ExclusionPatterns.NONE,
                Instant.EPOCH);

        long start = System.nanoTime();
        SourceTree.Scan scan = new LocalSourceTree().search(List.of(source), PerimeterTerms.of(List.of("ContractStatus",
                "CONTRACT_STATUS", "contract.status", "SuspensionEvent", "billing-topic")), SourceTree.Limits.DEFAULT,
                () -> false);
        Duration took = Duration.ofNanos(System.nanoTime() - start);

        System.out.printf("Périmètre (perf) : %d fichiers, %d module(s) trouvés, %d ms%n", scan.filesScanned(),
                scan.hits().size(), took.toMillis());
        assertThat(scan.incomplete()).isNull();
        assertThat(scan.filesScanned()).isGreaterThanOrEqualTo(FILES);
        assertThat(scan.hits()).hasSize(MODULES);
        assertThat(took).isLessThan(Duration.ofSeconds(30));
    }
}
