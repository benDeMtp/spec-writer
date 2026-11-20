package fr.kawamind.domain.specification;

import fr.kawamind.domain.scenario.AcceptanceScenario;
import fr.kawamind.domain.session.Language;

/**
 * Rattachement d'une hypothèse, d'un écart entre sources ou d'un point ouvert à un élément de la
 * même version (021, research.md R1) : un use case (et l'une de ses étapes ou alternatives, numérotées
 * à partir de 1), un scénario, une exigence ou le résumé.
 */
public record Anchor(String element, Integer step, boolean alternative) {

    public static final String SUMMARY = "SUMMARY";

    public Anchor {
        element = element == null ? null : element.strip();
    }

    /** Vrai si l'ancre désigne un élément de cette version. */
    public boolean resolves(SpecificationContent content) {
        if (element == null || element.isEmpty()) {
            return false;
        }
        if (SUMMARY.equals(element)) {
            return step == null && !alternative;
        }
        for (UseCase uc : content.useCases()) {
            if (uc.code().equals(element)) {
                if (step == null) {
                    return !alternative;
                }
                int size = alternative ? uc.alternatives().size() : uc.nominalFlow().size();
                return step >= 1 && step <= size;
            }
        }
        if (step != null || alternative) {
            return false;
        }
        return content.scenarios().stream().map(AcceptanceScenario::code).anyMatch(element::equals)
                || content.requirements().stream().map(Requirement::code).anyMatch(element::equals);
    }

    /** L'ancre si elle désigne un élément de cette version, sinon nulle : le point reste, non rattaché. */
    public static Anchor orNull(Anchor anchor, SpecificationContent content) {
        return anchor != null && anchor.resolves(content) ? anchor : null;
    }

    /** « UC-02, étape 4 », « UC-02, alternative 1 », « Résumé », « EF-03 ». */
    public String label(Language language) {
        boolean en = language == Language.EN;
        if (SUMMARY.equals(element)) {
            return en ? "Summary" : "Résumé";
        }
        if (step == null) {
            return element;
        }
        String kind = alternative ? (en ? "alternative" : "alternative") : (en ? "step" : "étape");
        return element + ", " + kind + " " + step;
    }
}
