package dev.specwriter.domain.llm;

import dev.specwriter.domain.session.Language;
import java.util.List;

/**
 * Entrée de {@code buildProjectContext} : tous les faits extraits et la fiche actuelle, que le LLM
 * conserve quand les sources ne la contredisent pas (contracts/llm-port.md de 002).
 */
public record BuildContextRequest(Language language, List<SourceFacts.Fact> facts, String currentContext, String correction) {

    public BuildContextRequest {
        facts = List.copyOf(facts);
        currentContext = currentContext == null ? "" : currentContext;
        correction = correction == null ? "" : correction;
    }

    public BuildContextRequest withCorrection(String newCorrection) {
        return new BuildContextRequest(language, facts, currentContext, newCorrection);
    }
}
