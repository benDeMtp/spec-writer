package dev.specwriter.application.specification;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.domain.knowledge.KnowledgeKind;
import dev.specwriter.domain.knowledge.ProjectKnowledge;
import dev.specwriter.domain.specification.SourceRef;
import dev.specwriter.domain.specification.SpecificationContent;
import dev.specwriter.domain.specification.Specs;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.llm.DraftSpecification;
import dev.specwriter.domain.llm.DraftSpecification.DraftScenario;
import dev.specwriter.domain.llm.DraftSpecification.DraftSource;
import dev.specwriter.domain.llm.DraftSpecification.DraftUseCase;
import dev.specwriter.domain.session.Language;
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
            List.of(Specs.useCase("UC-01", null), new dev.specwriter.domain.specification.UseCase("UC-02",
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
}
