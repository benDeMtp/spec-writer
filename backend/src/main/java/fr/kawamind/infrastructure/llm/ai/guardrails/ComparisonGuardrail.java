package fr.kawamind.infrastructure.llm.ai.guardrails;

import fr.kawamind.infrastructure.llm.ai.LlmOutputs;
import jakarta.enterprise.context.ApplicationScoped;

/** Sortie lue par {@link LlmOutputs#comparison(String)}, redemandée si elle n'est pas conforme. */
@ApplicationScoped
public class ComparisonGuardrail extends JsonOutputGuardrail {

    public ComparisonGuardrail() {
        super(LlmOutputs::comparison);
    }
}
