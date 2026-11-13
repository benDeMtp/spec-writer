package dev.specwriter.domain.sources;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Graphe des dépendances Maven entre les modules recensés (013, US3, data-model.md). */
class ModuleGraphTest {

    static final UUID ROOT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    final Map<ModuleId, ModuleDescriptor> descriptors = new LinkedHashMap<>();

    static ModuleId id(String path) {
        return new ModuleId(ROOT, path);
    }

    ModuleGraphTest pom(String path, String artifact, String parent, Set<String> deps, Set<String> boms,
            List<String> modules) {
        descriptors.put(id(path), new ModuleDescriptor(artifact, "g:" + artifact, parent, deps, boms, modules, 0));
        return this;
    }

    ModuleGraphTest pom(String path, String artifact, String... deps) {
        return pom(path, artifact, null, Set.of(deps), Set.of(), List.of());
    }

    List<ModuleId> chain(String... paths) {
        return java.util.Arrays.stream(paths).map(ModuleGraphTest::id).toList();
    }

    @Test
    void lesDependantsIndirectsAvecLaChaineDepuisLaGraine() {
        pom("a", "a").pom("b", "b", "g:a").pom("c", "c", "g:b").pom("d", "d");
        Map<ModuleId, List<ModuleId>> dependents = ModuleGraph.of(descriptors).dependents(Set.of(id("a")));
        assertThat(dependents).containsOnlyKeys(id("b"), id("c"));
        assertThat(dependents.get(id("c"))).isEqualTo(chain("a", "b", "c"));
    }

    @Test
    void unCycleEstToleree() {
        pom("a", "a", "g:b").pom("b", "b", "g:a");
        assertThat(ModuleGraph.of(descriptors).dependents(Set.of(id("a"))))
                .containsExactly(Map.entry(id("b"), chain("a", "b")));
    }

    @Test
    void uneDependanceHorsDeLArbreNeCreeAucuneArete() {
        pom("a", "a", "io.quarkus:quarkus-rest").pom("b", "b", "g:a");
        assertThat(ModuleGraph.of(descriptors).dependents(Set.of(id("b")))).isEmpty();
    }

    @Test
    void leParentEtLImportDeBomSontDesDependances() {
        pom("super", "super").pom("bom", "bom");
        pom("x", "x", "g:super", Set.of(), Set.of(), List.of());
        pom("y", "y", null, Set.of(), Set.of("g:bom"), List.of());
        ModuleGraph graph = ModuleGraph.of(descriptors);
        assertThat(graph.dependents(Set.of(id("super")))).containsOnlyKeys(id("x"));
        assertThat(graph.dependents(Set.of(id("bom")))).containsOnlyKeys(id("y"));
    }

    @Test
    void deuxGrainesVersUnMemeDependantDonnentLaChaineLaPlusCourte() {
        pom("a", "a").pom("b", "b", "g:a").pom("c", "c", "g:b").pom("z", "z").pom("d", "d", "g:c", "g:z");
        Map<ModuleId, List<ModuleId>> dependents = ModuleGraph.of(descriptors).dependents(Set.of(id("a"), id("z")));
        assertThat(dependents.get(id("d"))).isEqualTo(chain("z", "d"));
        assertThat(dependents.get(id("c"))).isEqualTo(chain("a", "b", "c"));
    }

    @Test
    void uneGraineQuiDependDUneAutreGraineGardeSaChaine() {
        pom("a", "a").pom("b", "b", "g:a").pom("c", "c", "g:b");
        Map<ModuleId, List<ModuleId>> dependents = ModuleGraph.of(descriptors).dependents(Set.of(id("a"), id("c")));
        assertThat(dependents).containsOnlyKeys(id("b"), id("c"));
        assertThat(dependents.get(id("c"))).isEqualTo(chain("a", "b", "c"));
    }

    @Test
    void lAgregateurNommeLePremierModuleTrouveDeSesModules() {
        pom("", "aggregates", null, Set.of(), Set.of(), List.of("libs/a", "./libs/b/", "libs/c"));
        pom("libs/a", "a").pom("libs/b", "b").pom("libs/c", "c");
        pom("libs", "libs-parent", null, Set.of(), Set.of(), List.of("../libs/c"));
        Map<ModuleId, ModuleId> aggregators = ModuleGraph.of(descriptors).aggregators(Set.of(id("libs/c"), id("libs/b")));
        assertThat(aggregators).containsExactly(Map.entry(id(""), id("libs/b")), Map.entry(id("libs"), id("libs/c")));
    }

    @Test
    void desCoordonneesEnDoubleGardentLePremierModuleEnOrdreDeChemin() {
        pom("z/a", "a").pom("b/a", "a").pom("c", "c", "g:a");
        ModuleGraph graph = ModuleGraph.of(descriptors);
        assertThat(graph.duplicates()).containsExactly(id("z/a"));
        assertThat(graph.dependents(Set.of(id("b/a")))).containsOnlyKeys(id("c"));
        assertThat(graph.dependents(Set.of(id("z/a")))).isEmpty();
    }
}
