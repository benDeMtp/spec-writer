package fr.kawamind.infrastructure.llm.ai.guardrails;

import fr.kawamind.infrastructure.llm.ai.LlmOutputs;
import jakarta.enterprise.context.ApplicationScoped;

/** Sortie lue par {@link LlmOutputs#qualifications(String)}, redemandée si elle n'est pas conforme. */
@ApplicationScoped
public class QualificationGuardrail extends JsonOutputGuardrail {

    public QualificationGuardrail() {
        super(LlmOutputs::qualifications);
    }
}
