package dev.specwriter.domain.specification;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.domain.knowledge.ArchScope;
import dev.specwriter.domain.knowledge.ArchitectureModel.DeploymentFact;
import dev.specwriter.domain.knowledge.ArchitectureModel.Exchange;
import dev.specwriter.domain.knowledge.KnowledgeSelection;
import dev.specwriter.domain.scenario.AcceptanceScenario;
import dev.specwriter.domain.scenario.AcceptanceScenario.Kind;
import dev.specwriter.domain.session.Language;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarkdownRendererTest {

    static final SourceRef NEED = new SourceRef(SourceRef.Kind.NEED_EXCERPT, null, "payer en ligne");

    static SpecificationContent spec(AcceptanceScenario scenario, List<Assumption> assumptions,
            List<Requirement> requirements) {
        return new SpecificationContent("Paiement", "Le client paie.", List.of(Specs.useCase("UC-01", NEED)),
                List.of(scenario), assumptions, requirements);
    }

    static AcceptanceScenario refused(boolean assumption) {
        return new AcceptanceScenario("SC-01-02", "UC-01", "Paiement refusé", Kind.ERROR, "client connecté",
                List.of("un panier de 3 articles"), List.of("je valide le paiement", "ma banque refuse la transaction"),
                List.of("un message m'indique que le paiement a échoué", "mon panier est conservé"), assumption);
    }

    @Test
    void exempleDuContratRenduALIdentique() {
        String md = MarkdownRenderer.render(spec(refused(false), List.of(), List.of()), Language.FR);
        assertThat(md).contains("""
                #### SC-01-02 : Paiement refusé
                - **En tant que** client connecté
                - **Étant donné** un panier de 3 articles
                - **Quand** je valide le paiement
                - **Et** ma banque refuse la transaction
                - **Alors** un message m'indique que le paiement a échoué
                - **Et** mon panier est conservé
                """);
    }

    @Test
    void sectionsEtMentionHypothese() {
        String md = MarkdownRenderer.render(spec(refused(true),
                List.of(new Assumption("H-01", "Paiement en euros", Assumption.Origin.TOOL_INFERENCE, null)),
                List.of(new Requirement("EF-01", Requirement.Category.FUNCTIONAL, "Envoyer un reçu"),
                        new Requirement("ET-01", Requirement.Category.TECHNICAL, "TLS 1.3"))), Language.FR);
        assertThat(md).startsWith("# Paiement\n\n## Résumé\n\nLe client paie.\n")
                .contains("## Use cases\n\n### UC-01 : Payer la commande\n")
                .contains("- **Acteur** : client connecté\n")
                .contains("- **Déroulement nominal** :\n  1. Le client valide le panier\n  2. Il paie\n")
                .contains("- **Préconditions** :\n  - Panier non vide\n")
                .contains("- **Source** : extrait du besoin « payer en ligne »")
                .contains("## Scénarios d'acceptation\n\n### UC-01 : Payer la commande\n\n#### SC-01-02 : Paiement refusé *(Hypothèse)*\n")
                .contains("## Hypothèses\n\n- **H-01** : Paiement en euros\n")
                .contains("## Exigences fonctionnelles\n\n- **EF-01** : Envoyer un reçu\n")
                .contains("## Exigences techniques\n\n- **ET-01** : TLS 1.3\n")
                .endsWith("TLS 1.3\n");
        assertThat(md.indexOf("## Résumé")).isLessThan(md.indexOf("## Use cases"));
        assertThat(md.indexOf("## Use cases")).isLessThan(md.indexOf("## Scénarios"));
        assertThat(md.indexOf("## Hypothèses")).isLessThan(md.indexOf("## Exigences fonctionnelles"));
    }

    @Test
    void motsClesEnAnglais() {
        String md = MarkdownRenderer.render(spec(refused(true), List.of(), List.of()), Language.EN);
        assertThat(md).contains("- **As a** client connecté\n- **Given** un panier")
                .contains("- **When** je valide").contains("- **And** ma banque").contains("- **Then** un message")
                .contains("*(Assumption)*").contains("## Summary").contains("## Acceptance scenarios")
                .contains("## Assumptions\n\nNone.").contains("## Functional requirements")
                .contains("### UC-01: Payer la commande").contains("#### SC-01-02: Paiement refusé")
                .contains("- **Actor**: client connecté");
    }

    @Test
    void texteSaisiEchappe() {
        AcceptanceScenario s = new AcceptanceScenario("SC-01-01", "UC-01", "# titre | *gras* {x} [lien](y)", Kind.NOMINAL,
                "client", List.of(), List.of("je tape `code`\net une ligne"), List.of("ok"), false);
        String md = MarkdownRenderer.render(spec(s, List.of(), List.of()), Language.FR);
        assertThat(md).contains("#### SC-01-01 : \\# titre \\| \\*gras\\* \\{x\\} \\[lien\\](y)")
                .contains("- **Quand** je tape \\`code\\`\\\n  et une ligne\n");
        assertThat(MarkdownRenderer.escape(null)).isEmpty();
    }

    @Test
    void retoursALaLigneConservesDansLesChampsSimples() {
        AcceptanceScenario s = new AcceptanceScenario("SC-01-01", "UC-01", "Titre\nsur deux lignes", Kind.NOMINAL,
                "client", List.of(), List.of("je saisis\n1. un\n- deux"), List.of("ok"), false);
        SpecificationContent c = new SpecificationContent("Paiement", "R", "", List.of(Specs.useCase("UC-01", NEED)),
                List.of(s), List.of(), List.of(new Requirement("EF-01", Requirement.Category.FUNCTIONAL, "ligne 1\r\nligne 2")));
        String md = MarkdownRenderer.render(c, Language.FR);

        // un titre reste sur une ligne ; ailleurs, saut de ligne forcé, sans créer de liste
        assertThat(md).contains("#### SC-01-01 : Titre sur deux lignes\n")
                .contains("- **Quand** je saisis\\\n  1\\. un\\\n  \\- deux\n")
                .contains("- **EF-01** : ligne 1\\\n  ligne 2\n");
    }

    @Test
    void resumeEtDetailsEnTexteRiche() {
        String table = "| Moyen | Frais |\n| --- | --- |\n| Carte | 0 € |";
        SpecificationContent c = new SpecificationContent("Paiement", "Le client **paie**.\n\n" + table,
                "# Règles\n\nDétail *important*.\n\n## Annexe\n\n### Sous-partie", List.of(Specs.useCase("UC-01", NEED)),
                List.of(refused(false)), List.of(), List.of());
        String md = MarkdownRenderer.render(c, Language.FR);

        assertThat(md).contains("## Résumé\n\nLe client **paie**.\n\n" + table + "\n\n## Détails\n\n")
                .contains("#### Règles\n\nDétail *important*.\n\n#### Annexe\n\n### Sous-partie\n\n## Use cases");
        assertThat(MarkdownRenderer.render(c, Language.EN)).contains("## Details\n\n");
    }

    @Test
    void sansDetailsPasDeSection() {
        String md = MarkdownRenderer.render(spec(refused(false), List.of(), List.of()), Language.FR);

        assertThat(md).doesNotContain("Détails").contains("## Résumé\n\nLe client paie.\n\n## Use cases");
    }

    @Test
    void unSouligneDeTitreNeCreePasDeTitre() {
        SpecificationContent c = new SpecificationContent("T", "Texte\n===\nSuite\n---", "",
                List.of(Specs.useCase("UC-01", NEED)), List.of(refused(false)), List.of(), List.of());

        assertThat(MarkdownRenderer.render(c, Language.FR)).contains("Texte\n\\===\nSuite\n\n---\n");
    }

    @Test
    void sourcesReponseEtHypothese() {
        SpecificationContent c = new SpecificationContent("T", "R", List.of(
                Specs.useCase("UC-01", new SourceRef(SourceRef.Kind.ANSWER, Specs.Q1, "carte bancaire")),
                Specs.useCase("UC-02", new SourceRef(SourceRef.Kind.ASSUMPTION, null, ""))), List.of(), List.of(), List.of());
        String md = MarkdownRenderer.render(c, Language.FR);
        assertThat(md).contains("- **Source** : réponse « carte bancaire »").contains("- **Source** : hypothèse\n");
    }

    @Test
    void sourcesDeLaConnaissance() {
        SpecificationContent c = new SpecificationContent("T", "R", List.of(
                Specs.useCase("UC-01", SourceRef.knowledge(SourceRef.Kind.JIRA_ISSUE, "FACT-7", "")),
                Specs.useCase("UC-02", SourceRef.knowledge(SourceRef.Kind.DOC_PAGE, "facturation/adr/adr-005.html", "")),
                Specs.useCase("UC-03", SourceRef.knowledge(SourceRef.Kind.ARCH_ELEMENT, "Facturation/Service Avoirs", ""))),
                List.of(), List.of(), List.of());
        assertThat(MarkdownRenderer.render(c, Language.FR)).contains("- **Source** : ticket FACT-7\n")
                .contains("- **Source** : page facturation/adr/adr-005.html\n")
                .contains("- **Source** : élément d'architecture Facturation/Service Avoirs\n");
    }

    static final KnowledgeSelection FACTS = new KnowledgeSelection("h", List.of(),
            List.of(new KnowledgeSelection.ImpactedModule("Facturation/Service Avoirs", ArchScope.PROJECT),
                    new KnowledgeSelection.ImpactedModule("Comptabilité", ArchScope.COMMON)),
            List.of(new Exchange("Facturation/Service Avoirs", "Comptabilité", Exchange.Kind.INDIRECT, "Bus/Topic Avoirs"),
                    new Exchange("Facturation/Service Avoirs", "Editique", Exchange.Kind.MISSING, null)),
            List.of(new DeploymentFact(DeploymentFact.Kind.NOT_DEPLOYED, "Facturation/Service Avoirs", null),
                    new DeploymentFact(DeploymentFact.Kind.CROSS_INFRASTRUCTURE, "Facturation/Service Avoirs", "Comptabilité"),
                    new DeploymentFact(DeploymentFact.Kind.MULTIPLE_INSTANCES, "Comptabilité", null),
                    new DeploymentFact(DeploymentFact.Kind.SHARED_INFRASTRUCTURE, "Facturation/Service Avoirs", null)));

    @Test
    void rubriquesArchitectureEcartsEtEfforts() {
        SpecificationContent base = spec(refused(false), List.of(), List.of());
        SpecificationContent c = new SpecificationContent(base.title(), base.summary(), base.details(), base.useCases(),
                base.scenarios(), base.assumptions(), base.requirements(), List.of(new SpecificationContent.AdrDeviation(
                        "facturation/adr/adr-005.html", "Notification asynchrone", "Appel synchrone demandé")));
        String md = MarkdownRenderer.render(c, Language.FR, FACTS);
        assertThat(md).contains("""
                ## Architecture

                ### Modules impactés

                - Facturation/Service Avoirs *(propre au projet)*
                - Comptabilité *(commun)*

                ### Flux

                - Facturation/Service Avoirs → Comptabilité : flux existant, via Bus/Topic Avoirs
                - Facturation/Service Avoirs → Editique : **flux manquant, à créer**

                ## Écarts aux ADR

                - **facturation/adr/adr-005.html**
                  - Décision : Notification asynchrone
                  - Écart : Appel synchrone demandé

                ## Efforts supplémentaires (déploiement)

                - Facturation/Service Avoirs : aucune instance en production, à déployer
                - Facturation/Service Avoirs → Comptabilité : traverse des infrastructures (ouverture réseau ou exposition à prévoir)
                - Comptabilité : plusieurs instances en production
                - Facturation/Service Avoirs : dépend d'une infrastructure partagée
                """);
        assertThat(MarkdownRenderer.render(c, Language.EN, FACTS)).contains("## Additional effort (deployment)")
                .contains("*(project-specific)*").contains("## ADR deviations");
    }

    @Test
    void rubriquesCalculeesJamaisTireesDuContenu() {
        // les rubriques calculées viennent des faits ; sans faits, ni « Architecture » ni « Efforts »
        SpecificationContent c = spec(refused(false), List.of(), List.of());
        String md = MarkdownRenderer.render(c, Language.FR, KnowledgeSelection.EMPTY);
        assertThat(md).isEqualTo(MarkdownRenderer.render(c, Language.FR)).doesNotContain("## Architecture")
                .doesNotContain("## Efforts supplémentaires").doesNotContain("## Écarts aux ADR");
    }
}
