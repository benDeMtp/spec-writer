package fr.kawamind.application.specification;

import static org.assertj.core.api.Assertions.assertThat;

import fr.kawamind.domain.knowledge.KnowledgeKind;
import fr.kawamind.domain.knowledge.ProjectKnowledge;
import fr.kawamind.domain.specification.SourceRef;
import fr.kawamind.domain.specification.SpecificationContent;
import fr.kawamind.domain.specification.Specs;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.llm.DraftSpecification;
import fr.kawamind.domain.llm.DraftSpecification.DraftScenario;
import fr.kawamind.domain.llm.DraftSpecification.DraftSource;
import fr.kawamind.domain.llm.DraftSpecification.DraftUseCase;
import fr.kawamind.domain.session.Language;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
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

    static final String REF = "discussion:00000000-0000-0000-0000-000000000001#3";
    static final DraftValidation.Discussion DISCUSSION = new DraftValidation.Discussion(
            Map.of(REF, "Discussion du 11/11/2026, message 3"),
            List.of(Specs.useCase("UC-01", null), new fr.kawamind.domain.specification.UseCase("UC-02",
                    "Annuler la commande", "client", "Annuler", List.of(), List.of("Il annule"), List.of(), null)));

    static DraftUseCase useCase(String title, DraftSource source) {
        return new DraftUseCase(title, "client connecté", "Régler son panier", List.of(), List.of("Il paie"), List.of(),
                source);
    }

    static DraftSpecification draft(DraftUseCase... useCases) {
        List<DraftScenario> scenarios = new ArrayList<>();
        for (int i = 0; i < useCases.length; i++) {
            scenarios.add(new DraftScenario(i, "Nominal", "NOMINAL", "client", List.of(), List.of("il agit"),
                    List.of("c'est fait"), false));
        }
        return new DraftSpecification("Paiement", "Résumé", List.of(useCases), scenarios, List.of(), List.of());
    }

    @Test
    void unMessageDeLaDiscussionEstUneSourceCitableEtTitree() {
        SpecificationContent content = DraftValidation.produce(c -> draft(
                useCase("Payer la commande", new DraftSource("NEED_EXCERPT", "", "payer")),
                useCase("Annuler la commande", new DraftSource("DISCUSSION", "", "", REF))),
                List.of(), Language.FR, ErrorCode.LLM_INVALID_OUTPUT, ProjectKnowledge.EMPTY, DISCUSSION);

        SourceRef source = content.useCases().get(1).source();
        assertThat(source.kind()).isEqualTo(SourceRef.Kind.DISCUSSION);
        assertThat(source.ref()).isEqualTo(REF);
        assertThat(source.excerpt()).isEqualTo("Discussion du 11/11/2026, message 3");
    }

    @Test
    void unMessageInconnuEstRedemandePuisRefuse() {
        List<String> corrections = new ArrayList<>();
        Assertions.assertThatThrownBy(() -> DraftValidation.produce(c -> {
            corrections.add(c);
            return draft(useCase("Payer la commande", new DraftSource("DISCUSSION", "", "", "discussion:x#9")),
                    useCase("Annuler la commande", new DraftSource("NEED_EXCERPT", "", "annuler")));
        }, List.of(), Language.FR, ErrorCode.LLM_INVALID_OUTPUT, ProjectKnowledge.EMPTY, DISCUSSION))
                .isInstanceOf(DomainException.class);
        assertThat(corrections.get(1)).contains("UC-01").contains("<discussion>");
        // hors discussion, la sorte DISCUSSION n'est pas citable
        Assertions.assertThatThrownBy(() -> DraftValidation.produce(c -> draft(
                useCase("Payer", new DraftSource("DISCUSSION", "", "", REF))), List.of(), Language.FR,
                ErrorCode.LLM_INVALID_OUTPUT)).isInstanceOf(DomainException.class);
    }

    @Test
    void unUseCaseDisparuEstRedemandeSansBloquerLaProposition() {
        List<String> corrections = new ArrayList<>();
        SpecificationContent content = DraftValidation.produce(c -> {
            corrections.add(c);
            return draft(useCase("Payer la commande", new DraftSource("NEED_EXCERPT", "", "payer")));
        }, List.of(), Language.FR, ErrorCode.LLM_INVALID_OUTPUT, ProjectKnowledge.EMPTY, DISCUSSION);

        assertThat(corrections).hasSize(DraftValidation.MAX_ATTEMPTS);
        assertThat(corrections.get(1)).contains("UC-02 « Annuler la commande » a disparu");
        // le retrait peut être ce que la discussion a établi : la dernière tentative est gardée
        assertThat(content.useCases()).hasSize(1);
    }

    @Test
    void lesUseCasesGardesNeDemandentAucuneCorrection() {
        List<String> corrections = new ArrayList<>();
        DraftValidation.produce(c -> {
            corrections.add(c);
            return draft(useCase("payer la commande ", new DraftSource("NEED_EXCERPT", "", "payer")),
                    useCase("Annuler la commande", new DraftSource("NEED_EXCERPT", "", "annuler")),
                    useCase("Rembourser", new DraftSource("DISCUSSION", "", "", REF)));
        }, List.of(), Language.FR, ErrorCode.LLM_INVALID_OUTPUT, ProjectKnowledge.EMPTY, DISCUSSION);

        assertThat(corrections).containsExactly((String) null);
    }

    /** T041 (016) : un écart entre sources n'est gardé que si ses deux références sont citables. */
    @Test
    void ecartEntreSourcesCitableCompleteDeSaSourceEtDeSaDate() {
        java.time.Instant sept = java.time.Instant.parse("2026-09-12T08:00:00Z");
        ProjectKnowledge knowledge = new ProjectKnowledge("", null, List.of(
                new ProjectKnowledge.Entry(KnowledgeKind.DOC_SECTION, null, "docs:x/Demande.docx", 0, "Demande", null,
                        false, "Plafond : 500 euros.", "h").withProvenance(new fr.kawamind.domain.knowledge.Provenance(
                                fr.kawamind.domain.knowledge.SourceOrigin.DOCUMENTS, sept,
                                fr.kawamind.domain.knowledge.Reliability.REFERENCE)),
                new ProjectKnowledge.Entry(KnowledgeKind.DOC_SECTION, null,
                        "https://wiki/pages/viewpage.action?pageId=103", 0, "Notes de frais", null, false,
                        "Plafond : 1 000 euros.", "h2").withProvenance(new fr.kawamind.domain.knowledge.Provenance(
                                fr.kawamind.domain.knowledge.SourceOrigin.CONFLUENCE, null, null))),
                List.of(), List.of(), List.of());
        java.util.function.Function<String, fr.kawamind.domain.specification.SourceDeviation> deviation = other ->
                new fr.kawamind.domain.specification.SourceDeviation(
                        new fr.kawamind.domain.specification.SourceDeviation.Cited("DOCS:x/demande.docx", null, null,
                                null),
                        new fr.kawamind.domain.specification.SourceDeviation.Cited(other, null, null, null),
                        "500 contre 1 000 euros.");
        SpecificationContent content = Specs.with(List.of(), List.of(), List.of()).withSourceDeviations(List.of(
                deviation.apply("https://wiki/pages/viewpage.action?pageId=103"),
                deviation.apply("https://wiki/pages/viewpage.action?pageId=999"),
                deviation.apply("docs:x/Demande.docx")));

        List<fr.kawamind.domain.specification.SourceDeviation> kept = DraftValidation.repaired(content, knowledge)
                .sourceDeviations();

        assertThat(kept).singleElement().satisfies(d -> {
            assertThat(d.reference().ref()).isEqualTo("docs:x/Demande.docx");
            assertThat(d.reference().origin()).isEqualTo(fr.kawamind.domain.knowledge.SourceOrigin.DOCUMENTS);
            assertThat(d.reference().updatedAt()).isEqualTo(sept);
            assertThat(d.interpretation().title()).isEqualTo("Notes de frais");
            assertThat(d.interpretation().origin()).isEqualTo(fr.kawamind.domain.knowledge.SourceOrigin.CONFLUENCE);
        });
    }
}
