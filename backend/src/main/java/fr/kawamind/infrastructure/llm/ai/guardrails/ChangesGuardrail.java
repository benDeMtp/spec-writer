package fr.kawamind.infrastructure.llm.ai.guardrails;

import fr.kawamind.infrastructure.llm.ai.LlmOutputs;
import jakarta.enterprise.context.ApplicationScoped;

/** Sortie lue par {@link LlmOutputs#changes(String)}, redemandée si elle n'est pas conforme. */
@ApplicationScoped
public class ChangesGuardrail extends JsonOutputGuardrail {

    public ChangesGuardrail() {
        super(LlmOutputs::changes);
    }
}
