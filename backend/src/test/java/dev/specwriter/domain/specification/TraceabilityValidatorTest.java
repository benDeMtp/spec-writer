package dev.specwriter.domain.specification;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.domain.knowledge.ArchScope;
import dev.specwriter.domain.knowledge.KnowledgeKind;
import dev.specwriter.domain.knowledge.KnowledgeRole;
import dev.specwriter.domain.knowledge.KnowledgeSelection;
import dev.specwriter.domain.knowledge.ProjectKnowledge;
import dev.specwriter.domain.scenario.AcceptanceScenario.Kind;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TraceabilityValidatorTest {

    static StructureViolations check(SourceRef source, List<Assumption> assumptions) {
        return TraceabilityValidator.validate(Specs.with(List.of(Specs.useCase("UC-01", source)),
                List.of(Specs.scenario("SC-01-01", "UC-01", Kind.NOMINAL)), assumptions), Set.of(Specs.Q1));
    }

    @Test
    void chaqueUseCaseADesSources() {
        assertThat(TraceabilityValidator.validate(Specs.valid(), Set.of()).isEmpty()).isTrue();
        assertThat(check(null, List.of()).messages()).anyMatch(m -> m.contains("source"));
        assertThat(check(new SourceRef(SourceRef.Kind.NEED_EXCERPT, null, " "), List.of()).messages())
                .anyMatch(m -> m.contains("extrait"));
    }

    @Test
    void sourceReponseCiteUneQuestionRepondue() {
        assertThat(check(new SourceRef(SourceRef.Kind.ANSWER, Specs.Q1, "carte"), List.of()).isEmpty()).isTrue();
        assertThat(check(new SourceRef(SourceRef.Kind.ANSWER, null, "carte"), List.of()).messages())
                .anyMatch(m -> m.contains("question répondue"));
    }

    @Test
    void sourceHypotheseListeeDansLesHypotheses() {
        SourceRef source = new SourceRef(SourceRef.Kind.ASSUMPTION, null, "paiement en 3 fois");
        assertThat(check(source, List.of()).messages()).anyMatch(m -> m.contains("hypothèses"));
        assertThat(check(source, List.of(new Assumption("H-01", "Le paiement en 3 fois est proposé",
                Assumption.Origin.TOOL_INFERENCE, null))).isEmpty()).isTrue();
        assertThat(check(new SourceRef(SourceRef.Kind.ASSUMPTION, Specs.Q1, ""), List.of(new Assumption("H-01", "x",
                Assumption.Origin.SKIPPED_QUESTION, Specs.Q1))).isEmpty()).isTrue();
    }

    @Test
    void sourceContexteDuProjetCiteUnExtraitDuContexte() {
        String context = "## Acteurs\n- Le **gestionnaire de facturation** valide les factures.\n- Le comptable lettre.";
        SourceRef cited = new SourceRef(SourceRef.Kind.PROJECT_CONTEXT, null, "gestionnaire de facturation");
        assertThat(checkWithContext(cited, context).isEmpty()).isTrue();
        // casse et espaces ne comptent pas
        assertThat(checkWithContext(new SourceRef(SourceRef.Kind.PROJECT_CONTEXT, null, "Le  COMPTABLE lettre"), context)
                .isEmpty()).isTrue();
        assertThat(checkWithContext(new SourceRef(SourceRef.Kind.PROJECT_CONTEXT, null, "le directeur signe"), context)
                .messages()).anyMatch(m -> m.contains("fiche générale"));
        assertThat(checkWithContext(new SourceRef(SourceRef.Kind.PROJECT_CONTEXT, null, " "), context).messages())
                .anyMatch(m -> m.contains("contexte du projet"));
        // sans contexte, aucune source ne peut le citer
        assertThat(check(cited, List.of()).messages()).anyMatch(m -> m.contains("fiche générale"));
    }

    static final ProjectKnowledge KNOWLEDGE = new ProjectKnowledge("## Acteurs\n- Le comptable lettre les paiements.",
            Instant.EPOCH, List.of(
                    new ProjectKnowledge.Entry(KnowledgeKind.TICKET, null, "FACT-7", 0, "Annuler une facture", "Terminé",
                            true, "Annuler par un avoir total.", "h1"),
                    new ProjectKnowledge.Entry(KnowledgeKind.ADR, null, "facturation/adr/adr-005.html", 0, "ADR-005",
                            "ACCEPTED", false, "Notification asynchrone.", "h2"),
                    new ProjectKnowledge.Entry(KnowledgeKind.DOC_SECTION, KnowledgeRole.VOCABULARY,
                            "facturation/glossaire.html", 0, "Glossaire", "Avoir.", false, "Avoir.", "h3")),
            List.of(new KnowledgeSelection.ImpactedModule("Facturation/Service Avoirs", ArchScope.PROJECT)), List.of(),
            List.of());

    static StructureViolations checkWithKnowledge(SourceRef source, List<SpecificationContent.AdrDeviation> deviations) {
        SpecificationContent base = Specs.with(List.of(Specs.useCase("UC-01", source)),
                List.of(Specs.scenario("SC-01-01", "UC-01", Kind.NOMINAL)), List.of());
        SpecificationContent content = new SpecificationContent(base.title(), base.summary(), base.details(),
                base.useCases(), base.scenarios(), base.assumptions(), base.requirements(), deviations);
        return TraceabilityValidator.validate(content, Set.of(Specs.Q1), KNOWLEDGE);
    }

    @Test
    void sourceContexteDuProjetCiteLaFicheGenerale() {
        assertThat(checkWithKnowledge(new SourceRef(SourceRef.Kind.PROJECT_CONTEXT, null, "le comptable lettre"), List.of())
                .isEmpty()).isTrue();
        assertThat(checkWithKnowledge(new SourceRef(SourceRef.Kind.PROJECT_CONTEXT, null, "Annuler par un avoir total"),
                List.of()).messages()).anyMatch(m -> m.contains("fiche générale"));
    }

    @Test
    void sourcesDeLaConnaissanceLimiteesALaSelection() {
        assertThat(checkWithKnowledge(SourceRef.knowledge(SourceRef.Kind.JIRA_ISSUE, "FACT-7", ""), List.of()).isEmpty())
                .isTrue();
        assertThat(checkWithKnowledge(SourceRef.knowledge(SourceRef.Kind.DOC_PAGE, "facturation/adr/adr-005.html", ""),
                List.of()).isEmpty()).isTrue();
        assertThat(checkWithKnowledge(SourceRef.knowledge(SourceRef.Kind.DOC_PAGE, "facturation/glossaire.html", ""),
                List.of()).isEmpty()).isTrue();
        assertThat(checkWithKnowledge(SourceRef.knowledge(SourceRef.Kind.ARCH_ELEMENT, "Facturation/Service Avoirs", ""),
                List.of()).isEmpty()).isTrue();
        // hors de la sélection, ou d'une autre sorte
        assertThat(checkWithKnowledge(SourceRef.knowledge(SourceRef.Kind.JIRA_ISSUE, "FACT-99", ""), List.of()).messages())
                .anyMatch(m -> m.contains("JIRA_ISSUE"));
        assertThat(checkWithKnowledge(SourceRef.knowledge(SourceRef.Kind.JIRA_ISSUE, "facturation/glossaire.html", ""),
                List.of()).isEmpty()).isFalse();
        assertThat(checkWithKnowledge(SourceRef.knowledge(SourceRef.Kind.ARCH_ELEMENT, "Comptabilité", ""), List.of())
                .messages()).anyMatch(m -> m.contains("ARCH_ELEMENT"));
        // sans connaissance, aucune source ne peut la citer
        assertThat(check(SourceRef.knowledge(SourceRef.Kind.JIRA_ISSUE, "FACT-7", ""), List.of()).isEmpty()).isFalse();
    }

    @Test
    void ecartsAuxAdrDeLaSelection() {
        SourceRef need = new SourceRef(SourceRef.Kind.NEED_EXCERPT, null, "payer");
        assertThat(checkWithKnowledge(need, List.of(new SpecificationContent.AdrDeviation("facturation/adr/adr-005.html",
                "Notifier en asynchrone", "Appel synchrone"))).isEmpty()).isTrue();
        assertThat(checkWithKnowledge(need, List.of(new SpecificationContent.AdrDeviation("adr-009.html", "x", "y")))
                .messages()).anyMatch(m -> m.contains("adr-009"));
    }

    static StructureViolations checkWithContext(SourceRef source, String context) {
        return TraceabilityValidator.validate(Specs.with(List.of(Specs.useCase("UC-01", source)),
                List.of(Specs.scenario("SC-01-01", "UC-01", Kind.NOMINAL)), List.of()), Set.of(Specs.Q1),
                ProjectKnowledge.sheet(context, Instant.EPOCH));
    }

    @Test
    void scenarioRattacheEtScenarioNominalAttendu() {
        SourceRef need = new SourceRef(SourceRef.Kind.NEED_EXCERPT, null, "payer");
        StructureViolations v = TraceabilityValidator.validate(Specs.with(List.of(Specs.useCase("UC-01", need)),
                List.of(Specs.scenario("SC-01-01", "UC-01", Kind.ERROR), Specs.scenario("SC-02-01", "UC-02", Kind.NOMINAL)),
                List.of()), Set.of());
        assertThat(v.messages()).anyMatch(m -> m.contains("nominal")).anyMatch(m -> m.contains("SC-02-01"));
        assertThat(TraceabilityValidator.validate(Specs.with(List.of(), List.of(), List.of()), Set.of()).messages())
                .anyMatch(m -> m.contains("au moins un use case"));
    }
}
