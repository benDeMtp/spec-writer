package dev.specwriter.domain.llm;

import dev.specwriter.domain.session.Language;
import java.util.List;

/** Besoin, réponses et éléments d'architecture candidats (identifyImpact, contracts/llm-port.md de 002). */
public record ImpactRequest(Language language, String need, List<QuestionAndAnswer> answers, List<Element> candidates) {

    public static final int MAX_CANDIDATES = 15;

    /** Élément candidat, par son nom canonique. */
    public record Element(String name, String type, String description, String technology, String scope) {
    }

    public ImpactRequest {
        answers = answers == null ? List.of() : List.copyOf(answers);
        candidates = List.copyOf(candidates);
    }
}
