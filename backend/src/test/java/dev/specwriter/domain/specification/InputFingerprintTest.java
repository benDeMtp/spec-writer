package dev.specwriter.domain.specification;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.domain.knowledge.ArchScope;
import dev.specwriter.domain.knowledge.KnowledgeKind;
import dev.specwriter.domain.knowledge.KnowledgeSelection;
import dev.specwriter.domain.knowledge.ProjectKnowledge;
import dev.specwriter.domain.question.Question;
import dev.specwriter.domain.session.Language;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class InputFingerprintTest {

    static final String NEED = "Un besoin de test";

    @Test
    void stableEtIndependanteDeLOrdre() {
        List<Question> treated = IdentifierAssignerTest.treated();
        List<Question> reversed = new ArrayList<>(treated).reversed();
        String a = InputFingerprint.of(NEED, treated, "0.1.0", Language.FR);
        assertThat(a).hasSize(64).isEqualTo(InputFingerprint.of(NEED, reversed, "0.1.0", Language.FR));
    }

    @Test
    void changeAvecChaqueEntree() {
        List<Question> treated = IdentifierAssignerTest.treated();
        String base = InputFingerprint.of(NEED, treated, "0.1.0", Language.FR);
        assertThat(InputFingerprint.of(NEED + ".", treated, "0.1.0", Language.FR)).isNotEqualTo(base);
        assertThat(InputFingerprint.of(NEED, treated, "0.2.0", Language.FR)).isNotEqualTo(base);
        assertThat(InputFingerprint.of(NEED, treated, "0.1.0", Language.EN)).isNotEqualTo(base);
        assertThat(InputFingerprint.of(NEED, treated.subList(0, 1), "0.1.0", Language.FR)).isNotEqualTo(base);
    }

    @Test
    void leContexteDuProjetEntreDansLEmpreinte() {
        List<Question> treated = IdentifierAssignerTest.treated();
        String sans = InputFingerprint.of(NEED, treated, "0.1.0", Language.FR);
        // sans contexte, l'empreinte est celle de 001 : les versions existantes restent réutilisables
        assertThat(InputFingerprint.of(NEED, treated, "0.1.0", Language.FR, "")).isEqualTo(sans);
        String avec = InputFingerprint.of(NEED, treated, "0.1.0", Language.FR, "a".repeat(64));
        assertThat(avec).isNotEqualTo(sans)
                .isEqualTo(InputFingerprint.of(NEED, treated, "0.1.0", Language.FR, "a".repeat(64)));
        assertThat(InputFingerprint.of(NEED, treated, "0.1.0", Language.FR, "b".repeat(64))).isNotEqualTo(avec);
    }

    @Test
    void laSelectionDeConnaissanceEntreDansLEmpreinte() {
        List<Question> treated = IdentifierAssignerTest.treated();
        ProjectKnowledge.Entry adr = new ProjectKnowledge.Entry(KnowledgeKind.ADR, null, "adr/adr-005.html", 0, "ADR-005",
                "ACCEPTED", false, "Asynchrone.", "h1");
        ProjectKnowledge k = new ProjectKnowledge("Fiche", Instant.EPOCH, List.of(adr), List.of(), List.of(), List.of());
        String base = InputFingerprint.of(NEED, treated, "0.1.0", Language.FR, k.fingerprint());
        assertThat(ProjectKnowledge.EMPTY.fingerprint()).isEmpty();
        // une autre référence, ou le même élément modifié (contentHash) : une autre empreinte
        ProjectKnowledge otherRef = new ProjectKnowledge("Fiche", Instant.EPOCH, List.of(new ProjectKnowledge.Entry(
                KnowledgeKind.ADR, null, "adr/adr-006.html", 0, "ADR-005", "ACCEPTED", false, "Asynchrone.", "h1")),
                List.of(), List.of(), List.of());
        ProjectKnowledge otherHash = new ProjectKnowledge("Fiche", Instant.EPOCH, List.of(new ProjectKnowledge.Entry(
                KnowledgeKind.ADR, null, "adr/adr-005.html", 0, "ADR-005", "ACCEPTED", false, "Asynchrone.", "h2")),
                List.of(), List.of(), List.of());
        ProjectKnowledge otherSheet = new ProjectKnowledge("Fiche modifiée", Instant.EPOCH, List.of(adr), List.of(),
                List.of(), List.of());
        ProjectKnowledge withModule = k.withArchitecture(List.of(new KnowledgeSelection.ImpactedModule("Comptabilité",
                ArchScope.COMMON)), List.of(), List.of());
        for (ProjectKnowledge other : List.of(otherRef, otherHash, otherSheet, withModule)) {
            assertThat(InputFingerprint.of(NEED, treated, "0.1.0", Language.FR, other.fingerprint())).isNotEqualTo(base);
        }
        // le texte transmis n'y entre pas : seule l'empreinte de l'élément compte
        ProjectKnowledge sameHash = new ProjectKnowledge("Fiche", Instant.EPOCH, List.of(new ProjectKnowledge.Entry(
                KnowledgeKind.ADR, null, "adr/adr-005.html", 0, "ADR-005", "ACCEPTED", false, "Tronqué…", "h1")),
                List.of(), List.of(), List.of());
        assertThat(InputFingerprint.of(NEED, treated, "0.1.0", Language.FR, sameHash.fingerprint())).isEqualTo(base);
    }

    @Test
    void decoupageAmbiguImpossible() {
        assertThat(InputFingerprint.of("ab", List.of(), "c", Language.FR))
                .isNotEqualTo(InputFingerprint.of("a", List.of(), "bc", Language.FR));
    }
}
