package dev.specwriter.domain.sources;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.domain.session.Language;
import dev.specwriter.domain.specification.Assumption;
import dev.specwriter.domain.specification.SpecificationContent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Périmètre repris dans la spécification (013, US5, research.md R11). */
class PerimeterSnapshotTest {

    static final UUID ROOT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    static Module module(String path) {
        return new Module(ROOT, path, path.substring(path.lastIndexOf('/') + 1), ModuleKind.MAVEN, null);
    }

    static PerimeterAnalysis analysis() {
        List<Occurrence> samples = List.of(new Occurrence("libs/a/A.java", 1, "ContractStatus", "enum ContractStatus"),
                new Occurrence("libs/a/A.java", 2, "ContractStatus", "x"), new Occurrence("libs/a/B.java", 3, "ContractStatus", "y"),
                new Occurrence("libs/a/C.java", 4, "ContractStatus", "z"));
        PerimeterModule a = PerimeterModule.found(module("libs/a"), List.of(new Reason.Term("ContractStatus", 4)), 4, samples);
        PerimeterModule b = PerimeterModule.found(module("modules/b"), List.of(new Reason.Dependency(List.of(
                ModuleRef.of(module("libs/a")), ModuleRef.of(module("modules/b"))))), 0, List.of());
        PerimeterModule c = PerimeterModule.found(module("modules/c"), List.of(new Reason.Term("ContractStatus", 1)), 1,
                List.of());
        return PerimeterAnalysis.complete(1, List.of("ContractStatus"), List.of(),
                List.of(new PerimeterAnalysis.RootStatus(ROOT, "rv", PerimeterAnalysis.RootState.SCANNED)), 10, 0, List.of(),
                5, List.of(a, b, c), List.of())
                .withQualifications(List.of(new ModuleQualification(ROOT + "/libs/a", Qualification.IMPACTED, "Définit.")))
                .incomplete(PerimeterAnalysis.IncompleteReason.MAX_DURATION);
    }

    static SpecificationContent content(Assumption... assumptions) {
        return new SpecificationContent("Titre", "Résumé", "", List.of(), List.of(), List.of(assumptions), List.of());
    }

    @Test
    void lInstantaneGardeTroisPreuvesSansExtrait() {
        PerimeterSnapshot snapshot = PerimeterSnapshot.of(analysis());
        assertThat(snapshot.incomplete()).isTrue();
        assertThat(snapshot.modules()).extracting(PerimeterSnapshot.Entry::name).containsExactly("a", "b", "c");
        PerimeterSnapshot.Entry a = snapshot.modules().getFirst();
        assertThat(a.rootLabel()).isEqualTo("rv");
        assertThat(a.qualification()).isEqualTo(Qualification.IMPACTED);
        assertThat(a.justification()).isEqualTo("Définit.");
        assertThat(a.occurrences()).hasSize(PerimeterSnapshot.MAX_PROOFS).allMatch(o -> o.excerpt().isEmpty());
    }

    @Test
    void lesModulesAVerifierDeviennentDesHypothesesNumeroteesApresCellesDuLlm() {
        PerimeterSnapshot snapshot = PerimeterSnapshot.of(analysis());
        SpecificationContent content = snapshot.withAssumptions(content(new Assumption("H-01", "Le client est connu.",
                Assumption.Origin.TOOL_INFERENCE, null)), Language.FR);
        assertThat(content.assumptions()).extracting(Assumption::code, Assumption::statement).containsExactly(
                org.assertj.core.groups.Tuple.tuple("H-01", "Le client est connu."),
                org.assertj.core.groups.Tuple.tuple("H-02", "Le module b (modules/b) est à vérifier : dépend de a → b."),
                org.assertj.core.groups.Tuple.tuple("H-03", "Le module c (modules/c) est à vérifier : contient ContractStatus ×1."));
        assertThat(content.assumptions().get(1).origin()).isEqualTo(Assumption.Origin.TOOL_INFERENCE);
        // une révision qui reprend les hypothèses ne les double pas
        assertThat(snapshot.withAssumptions(content, Language.FR)).isSameAs(content);
        assertThat(snapshot.withAssumptions(content(), Language.EN).assumptions().getFirst().statement())
                .isEqualTo("Module b (modules/b) is to be checked: depends on a → b.");
    }
}
