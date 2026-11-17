package fr.kawamind.infrastructure.llm.ai.guardrails;

import fr.kawamind.infrastructure.llm.ai.LlmOutputs;
import jakarta.enterprise.context.ApplicationScoped;

/** Sortie lue par {@link LlmOutputs#need(String)}, redemandée si elle n'est pas conforme. */
@ApplicationScoped
public class NeedGuardrail extends JsonOutputGuardrail {

    public NeedGuardrail() {
        super(LlmOutputs::need);
    }
}
