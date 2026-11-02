package dev.specwriter.domain.specification;

import static org.assertj.core.api.Assertions.assertThat;

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
                .contains("- **Quand** je tape \\`code\\` et une ligne");
        assertThat(MarkdownRenderer.escape(null)).isEmpty();
    }

    @Test
    void sourcesReponseEtHypothese() {
        SpecificationContent c = new SpecificationContent("T", "R", List.of(
                Specs.useCase("UC-01", new SourceRef(SourceRef.Kind.ANSWER, Specs.Q1, "carte bancaire")),
                Specs.useCase("UC-02", new SourceRef(SourceRef.Kind.ASSUMPTION, null, ""))), List.of(), List.of(), List.of());
        String md = MarkdownRenderer.render(c, Language.FR);
        assertThat(md).contains("- **Source** : réponse « carte bancaire »").contains("- **Source** : hypothèse\n");
    }
}
