package fr.kawamind.infrastructure.llm.ai.guardrails;

import fr.kawamind.infrastructure.llm.ai.LlmOutputs;
import jakarta.enterprise.context.ApplicationScoped;

/** Sortie lue par {@link LlmOutputs#reassess(String)}, redemandée si elle n'est pas conforme. */
@ApplicationScoped
public class ReassessGuardrail extends JsonOutputGuardrail {

    public ReassessGuardrail() {
        super(LlmOutputs::reassess);
    }
}
