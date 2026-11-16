package fr.kawamind.infrastructure.llm.ai.guardrails;

import fr.kawamind.infrastructure.llm.ai.LlmOutputs;
import jakarta.enterprise.context.ApplicationScoped;

/** Sortie lue par {@link LlmOutputs#summary(String)}, redemandée si elle n'est pas conforme. */
@ApplicationScoped
public class SummaryGuardrail extends JsonOutputGuardrail {

    public SummaryGuardrail() {
        super(LlmOutputs::summary);
    }
}
