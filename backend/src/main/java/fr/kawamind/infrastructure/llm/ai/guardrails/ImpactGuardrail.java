package fr.kawamind.infrastructure.llm.ai.guardrails;

import fr.kawamind.infrastructure.llm.ai.LlmOutputs;
import jakarta.enterprise.context.ApplicationScoped;

/** Sortie lue par {@link LlmOutputs#impact(String)}, redemandée si elle n'est pas conforme. */
@ApplicationScoped
public class ImpactGuardrail extends JsonOutputGuardrail {

    public ImpactGuardrail() {
        super(LlmOutputs::impact);
    }
}
