package fr.kawamind.infrastructure.llm.ai.guardrails;

import fr.kawamind.infrastructure.llm.ai.LlmOutputs;
import jakarta.enterprise.context.ApplicationScoped;

/** Sortie lue par {@link LlmOutputs#revision(String)}, redemandée si elle n'est pas conforme. */
@ApplicationScoped
public class RevisionGuardrail extends JsonOutputGuardrail {

    public RevisionGuardrail() {
        super(LlmOutputs::revision);
    }
}
