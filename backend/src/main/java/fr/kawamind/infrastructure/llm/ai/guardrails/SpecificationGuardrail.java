package fr.kawamind.infrastructure.llm.ai.guardrails;

import fr.kawamind.infrastructure.llm.ai.LlmOutputs;
import jakarta.enterprise.context.ApplicationScoped;

/** Sortie lue par {@link LlmOutputs#draft(String)}, redemandée si elle n'est pas conforme. */
@ApplicationScoped
public class SpecificationGuardrail extends JsonOutputGuardrail {

    public SpecificationGuardrail() {
        super(LlmOutputs::draft);
    }
}
