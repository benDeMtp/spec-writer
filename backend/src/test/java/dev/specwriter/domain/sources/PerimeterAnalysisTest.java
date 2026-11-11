package dev.specwriter.domain.sources;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.domain.sources.PerimeterAnalysis.Summary;
import dev.specwriter.domain.sources.Reason.Architecture;
import dev.specwriter.domain.sources.Reason.Dependency;
import dev.specwriter.domain.sources.Reason.Term;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PerimeterAnalysisTest {

    static final UUID ROOT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    static Module module(String path) {
        return new Module(ROOT, path, path.substring(path.lastIndexOf('/') + 1), ModuleKind.MAVEN, null);
    }

    static PerimeterModule byTerm(String path, int count) {
        return PerimeterModule.found(module(path), List.of(new Term("ContractStatus", count)), count, List.of());
    }

    static PerimeterModule byDependency(String path, String from) {
        return PerimeterModule.found(module(path),
                List.of(new Dependency(List.of(new ModuleRef(module(from).id(), from), new ModuleRef(module(path).id(), path)))),
                0, List.of());
    }

    static PerimeterModule byArchitecture(String path) {
        return PerimeterModule.found(module(path), List.of(new Architecture("facturation", path, "appelle", "REST")), 0,
                List.of());
    }

    static PerimeterAnalysis analysis(List<PerimeterModule> modules) {
        return PerimeterAnalysis.complete(1, List.of("ContractStatus"), List.of(), List.of(), 100, 0, List.of(), 12, modules,
                List.of());
    }

    @Test
    void unModuleSansOccurrencePartAVerifier() {
        assertThat(byTerm("libs/a", 3).qualification()).isEqualTo(Qualification.UNQUALIFIED);
        assertThat(byDependency("modules/b", "libs/a").qualification()).isEqualTo(Qualification.TO_CHECK);
        assertThat(byArchitecture("modules/c").qualification()).isEqualTo(Qualification.TO_CHECK);
    }

    @Test
    void triParRaisonPuisOccurrencesPuisChemin() {
        PerimeterAnalysis sorted = analysis(List.of(byArchitecture("modules/c"), byDependency("modules/b", "libs/a"),
                byTerm("libs/z", 2), byTerm("libs/a", 5), byTerm("libs/b", 2))).sorted();
        assertThat(sorted.modules()).extracting(m -> m.module().path()).containsExactly("libs/a", "libs/b", "libs/z",
                "modules/b", "modules/c");
    }

    @Test
    void laQualificationNeRetireAucunModuleEtRespecteSesRegles() {
        PerimeterAnalysis analysis = analysis(List.of(byTerm("libs/a", 5), byDependency("modules/b", "libs/a"),
                byTerm("modules/c", 1), byTerm("modules/d", 1)));
        PerimeterAnalysis qualified = analysis.withQualifications(List.of(
                new ModuleQualification(module("libs/a").id().toString(), Qualification.IMPACTED, "Définit l'énumération."),
                new ModuleQualification(module("modules/b").id().toString(), Qualification.IMPACTED, "Dépend de a."),
                new ModuleQualification(module("modules/c").id().toString(), Qualification.NOT_IMPACTED, "x".repeat(400)),
                new ModuleQualification(ROOT + "/inconnu", Qualification.IMPACTED, "Inventé.")));

        assertThat(qualified.modules()).hasSameSizeAs(analysis.modules());
        assertThat(qualified.modules()).extracting(PerimeterModule::qualification).containsExactly(Qualification.IMPACTED,
                Qualification.TO_CHECK, Qualification.NOT_IMPACTED, Qualification.UNQUALIFIED);
        assertThat(qualified.modules().get(2).justification()).hasSize(PerimeterModule.MAX_JUSTIFICATION);
        assertThat(qualified.qualified()).isTrue();
        assertThat(qualified.summary()).isEqualTo(new Summary(1, 1, 1, 1));
    }

    @Test
    void uneReponseVideLaisseTousLesModulesSansQualification() {
        PerimeterAnalysis analysis = analysis(List.of(byTerm("libs/a", 5), byTerm("libs/b", 1)));
        PerimeterAnalysis qualified = analysis.withQualifications(List.of());
        assertThat(qualified.modules()).hasSize(2).allMatch(m -> m.qualification() == Qualification.UNQUALIFIED);
    }

    @Test
    void laTailleDeLaListeEstInvarianteParQualification() {
        List<PerimeterModule> modules = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            modules.add(i % 3 == 0 ? byDependency("m/" + i, "m/0") : byTerm("m/" + i, i));
        }
        PerimeterAnalysis analysis = analysis(modules);
        for (Qualification q : List.of(Qualification.IMPACTED, Qualification.TO_CHECK, Qualification.NOT_IMPACTED)) {
            List<ModuleQualification> answer = modules.stream().filter(m -> m.module().path().hashCode() % 2 == 0)
                    .map(m -> new ModuleQualification(m.module().id().toString(), q, "r")).toList();
            assertThat(analysis.withQualifications(answer).modules()).hasSize(30);
        }
    }

    @Test
    void lesModulesAbsentsDeLAnalysePrecedenteSontNouveaux() {
        PerimeterAnalysis previous = analysis(List.of(byTerm("libs/a", 5)));
        PerimeterAnalysis next = analysis(List.of(byTerm("libs/a", 5), byTerm("modules/batch", 1))).markNew(previous);
        assertThat(next.modules()).extracting(PerimeterModule::isNew).containsExactly(false, true);
        assertThat(analysis(List.of(byTerm("libs/a", 1))).markNew(null).modules().getFirst().isNew()).isFalse();
    }

    @Test
    void sansRacineLAnalyseEstIndisponible() {
        PerimeterAnalysis none = PerimeterAnalysis.noRoots(2, List.of("ContractStatus"));
        assertThat(none.unavailable()).isEqualTo(PerimeterAnalysis.Unavailable.NO_ROOTS);
        assertThat(none.modules()).isEmpty();
        assertThat(none.ordinal()).isEqualTo(2);
    }

    @Test
    void lExtraitEstCentreSurLeTermeEtNettoye() {
        String line = "\t\t" + "a".repeat(300) + " ContractStatus status;\u0007" + "b".repeat(300);
        Occurrence occurrence = Occurrence.of("src/A.java", 12, "contractstatus", line);
        assertThat(occurrence.excerpt()).hasSizeLessThanOrEqualTo(Occurrence.MAX_EXCERPT).contains("ContractStatus status;")
                .doesNotContain("\u0007");
        assertThat(Occurrence.of("A.java", 1, "x", "    int x = 1;").excerpt()).isEqualTo("int x = 1;");
    }
}
