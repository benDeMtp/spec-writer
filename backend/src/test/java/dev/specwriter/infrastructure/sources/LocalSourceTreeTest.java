package dev.specwriter.infrastructure.sources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import dev.specwriter.domain.port.SourceTree;
import dev.specwriter.domain.port.SourceTree.Limits;
import dev.specwriter.domain.port.SourceTree.Preview;
import dev.specwriter.domain.project.ExclusionPatterns;
import dev.specwriter.domain.sources.Module;
import dev.specwriter.domain.sources.ModuleKind;
import dev.specwriter.domain.sources.SourceRoot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalSourceTreeTest {

    @TempDir
    Path dir;

    final LocalSourceTree tree = new LocalSourceTree();

    static SourceRoot root(Path location, String... exclusions) {
        return new SourceRoot(UUID.randomUUID(), UUID.randomUUID(), location.toString(),
                ExclusionPatterns.of(List.of(exclusions)), Instant.EPOCH);
    }

    static Path write(Path file, String content) throws Exception {
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content);
    }

    @Test
    void uneRacineAbsenteOuQuiDesigneUnFichierEstSignalee() throws Exception {
        assertThat(tree.preview(root(dir.resolve("absent")), Limits.DEFAULT).problem())
                .isEqualTo(SourceTree.RootProblem.NOT_FOUND);
        Path file = write(dir.resolve("f.txt"), "x");
        assertThat(tree.preview(root(file), Limits.DEFAULT).problem()).isEqualTo(SourceTree.RootProblem.NOT_A_FOLDER);
    }

    @Test
    void lesModulesSontDetectesParLeurDescripteurEtTriesParChemin() throws Exception {
        write(dir.resolve("pom.xml"), "<project><groupId>g</groupId><artifactId>agregat</artifactId></project>");
        write(dir.resolve("libs/a/pom.xml"), "<project><groupId>g</groupId><artifactId>lib-a</artifactId></project>");
        write(dir.resolve("libs/a/sous/pom.xml"), "<project><artifactId>imbrique</artifactId></project>");
        write(dir.resolve("web/package.json"), "{\"name\": \"@demo/web\"}");
        write(dir.resolve("web/node_modules/dep/package.json"), "{\"name\": \"dep\"}");
        write(dir.resolve("charts/app/Chart.yaml"), "apiVersion: v2\n# name: faux\nname: \"app-chart\"\nversion: 1\n");
        write(dir.resolve("charts/sans-nom/Chart.yaml"), "apiVersion: v2\n");
        write(dir.resolve("illisible/pom.xml"), "<project>");

        Preview preview = tree.preview(root(dir), Limits.DEFAULT);

        assertThat(preview.problem()).isNull();
        assertThat(preview.modules()).extracting(Module::path, Module::name, Module::kind).containsExactly(
                org.assertj.core.groups.Tuple.tuple("", "agregat", ModuleKind.MAVEN),
                org.assertj.core.groups.Tuple.tuple("charts/app", "app-chart", ModuleKind.HELM),
                org.assertj.core.groups.Tuple.tuple("charts/sans-nom", "sans-nom", ModuleKind.HELM),
                org.assertj.core.groups.Tuple.tuple("illisible", "illisible", ModuleKind.MAVEN),
                org.assertj.core.groups.Tuple.tuple("libs/a", "lib-a", ModuleKind.MAVEN),
                org.assertj.core.groups.Tuple.tuple("libs/a/sous", "imbrique", ModuleKind.MAVEN),
                org.assertj.core.groups.Tuple.tuple("web", "@demo/web", ModuleKind.NPM));
        assertThat(preview.modules().getFirst().coordinates()).isEqualTo("g:agregat");
    }

    @Test
    void fichiersSensiblesGeneresIgnoresParGitEtExclusLesSontDecomptes() throws Exception {
        write(dir.resolve(".env"), "x");
        write(dir.resolve("app/settings.xml"), "x");
        write(dir.resolve("app/src/A.java"), "x");
        write(dir.resolve("app/target/B.java"), "x");
        write(dir.resolve(".gitignore"), "*.log\ngen/\n");
        write(dir.resolve("app/trace.log"), "x");
        write(dir.resolve("app/gen/C.java"), "x");
        write(dir.resolve("legacy/D.java"), "x");

        Preview preview = tree.preview(root(dir, "legacy/**"), Limits.DEFAULT);

        assertThat(preview.retainedFiles()).isEqualTo(1);
        assertThat(preview.sensitiveFiles()).isEqualTo(2);
        assertThat(preview.ignoredByGit()).isEqualTo(2);
        assertThat(preview.excludedFiles()).isEqualTo(1);
    }

    @Test
    void unMotifDeDossierRetireSesModules() throws Exception {
        write(dir.resolve("legacy/ancien/pom.xml"), "<project><artifactId>ancien</artifactId></project>");
        write(dir.resolve("app/pom.xml"), "<project><artifactId>app</artifactId></project>");
        assertThat(tree.preview(root(dir, "legacy/**"), Limits.DEFAULT).modules()).extracting(Module::name)
                .containsExactly("app");
    }

    @Test
    void unLienSymboliqueNEstJamaisSuivi() throws Exception {
        Path outside = write(dir.resolve("dehors/pom.xml"), "<project><artifactId>dehors</artifactId></project>").getParent();
        Path rootDir = Files.createDirectories(dir.resolve("arbre"));
        write(rootDir.resolve("A.java"), "x");
        try {
            Files.createSymbolicLink(rootDir.resolve("lien"), outside);
        } catch (UnsupportedOperationException e) {
            assumeThat(false).as("liens symboliques non pris en charge").isTrue();
        }
        Preview preview = tree.preview(root(rootDir), Limits.DEFAULT);
        assertThat(preview.modules()).isEmpty();
        assertThat(preview.retainedFiles()).isEqualTo(1);
    }

    @Test
    void laLimiteDeFichiersArreteLeParcours() throws Exception {
        for (int i = 0; i < 5; i++) {
            write(dir.resolve("f" + i + ".txt"), "x");
        }
        Preview preview = tree.preview(root(dir), new Limits(3, Duration.ofMinutes(1), 1_000, 100));
        assertThat(preview.limitReached()).isTrue();
        assertThat(preview.retainedFiles()).isEqualTo(3);
    }

    @Test
    void lArbreDeDemonstrationCompteQuatorzeModules() {
        Path demo = DemoTree.copy(dir);
        Preview preview = tree.preview(root(demo), Limits.DEFAULT);
        assertThat(preview.modules()).extracting(Module::name).containsExactlyInAnyOrder("aggregates", "super", "bom",
                "common-contract", "quarkus-contract", "spring-utils", "facturation", "relance", "annuaire",
                "batch-suspension", "notification", "ancien", "demo-front", "facturation-chart");
        assertThat(preview.sensitiveFiles()).isEqualTo(2);
        assertThat(tree.preview(root(demo, "legacy/**"), Limits.DEFAULT).modules()).extracting(Module::name)
                .doesNotContain("ancien").hasSize(13);
    }
}
