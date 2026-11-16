package fr.kawamind.infrastructure.llm.ai.guardrails;

import fr.kawamind.infrastructure.llm.ai.LlmOutputs;
import jakarta.enterprise.context.ApplicationScoped;

/** Sortie lue par {@link LlmOutputs#related(String)}, redemandée si elle n'est pas conforme. */
@ApplicationScoped
public class RelatedGuardrail extends JsonOutputGuardrail {

    public RelatedGuardrail() {
        super(LlmOutputs::related);
    }
}
