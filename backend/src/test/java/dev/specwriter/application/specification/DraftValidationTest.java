package dev.specwriter.application.specification;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.domain.knowledge.KnowledgeKind;
import dev.specwriter.domain.knowledge.ProjectKnowledge;
import dev.specwriter.domain.specification.SourceRef;
import dev.specwriter.domain.specification.SpecificationContent;
import dev.specwriter.domain.specification.Specs;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 009 : l'extrait cité d'un document joint ne sort jamais de l'outil (research.md R6). */
class DraftValidationTest {

    static final ProjectKnowledge KNOWLEDGE = new ProjectKnowledge("", null, List.of(
            new ProjectKnowledge.Entry(KnowledgeKind.DOC_SECTION, null, "joint:CR COPIL.pdf", 0, "CR COPIL", null,
                    false, "Le délai de rétractation est de 14 jours.", "h").withDesignated(),
            new ProjectKnowledge.Entry(KnowledgeKind.DOC_SECTION, null, "aurora/faq.html", 0, "FAQ", null, false,
                    "Texte de la FAQ", "h2")),
            List.of(), List.of(), List.of());

    static SpecificationContent citing(String ref, String excerpt) {
        return Specs.with(List.of(Specs.useCase("UC-01", SourceRef.knowledge(SourceRef.Kind.DOC_PAGE, ref, excerpt))),
                List.of(), List.of());
    }

    @Test
    void extraitDUnDocumentJointRetire() {
        SourceRef s = DraftValidation.repaired(citing("joint:cr copil.pdf", "Le délai de rétractation est de 14 jours."),
                KNOWLEDGE).useCases().getFirst().source();
        assertThat(s.ref()).isEqualTo("joint:CR COPIL.pdf");
        assertThat(s.excerpt()).isNullOrEmpty();
        // l'extrait d'une page du projet est gardé
        assertThat(DraftValidation.repaired(citing("aurora/faq.html", "Texte"), KNOWLEDGE).useCases().getFirst()
                .source().excerpt()).isEqualTo("Texte");
    }
}
