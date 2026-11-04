package dev.specwriter.domain.llm;

import dev.specwriter.domain.project.ContextSheet;
import dev.specwriter.domain.session.Language;
import java.util.List;

/**
 * Entrée de {@code buildProjectContext} (contracts/llm-port.md de 002, research.md R16) : tous les
 * faits extraits, les deux fiches actuelles, que le LLM conserve quand les sources ne les
 * contredisent pas, les réponses déjà données, citables, et les questions passées, à ne pas reposer.
 */
public record BuildContextRequest(Language language, List<SourceFacts.Fact> facts, String currentFunctional,
        String currentTechnical, List<Answer> answers, List<Skipped> skipped, String correction) {

    /** Réponse citable sous {@code ref} ({@code Q-n}). */
    public record Answer(String ref, ContextSheet sheet, String question, String answer) {
    }

    public record Skipped(ContextSheet sheet, String question) {
    }

    public BuildContextRequest {
        facts = List.copyOf(facts);
        currentFunctional = currentFunctional == null ? "" : currentFunctional;
        currentTechnical = currentTechnical == null ? "" : currentTechnical;
        answers = answers == null ? List.of() : List.copyOf(answers);
        skipped = skipped == null ? List.of() : List.copyOf(skipped);
        correction = correction == null ? "" : correction;
    }

    public BuildContextRequest withCorrection(String newCorrection) {
        return new BuildContextRequest(language, facts, currentFunctional, currentTechnical, answers, skipped, newCorrection);
    }
}
