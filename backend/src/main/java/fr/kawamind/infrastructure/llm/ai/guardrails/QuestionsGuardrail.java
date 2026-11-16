package fr.kawamind.infrastructure.llm.ai.guardrails;

import fr.kawamind.infrastructure.llm.ai.LlmOutputs;
import jakarta.enterprise.context.ApplicationScoped;

/** Sortie lue par {@link LlmOutputs#questions(String)}, redemandée si elle n'est pas conforme. */
@ApplicationScoped
public class QuestionsGuardrail extends JsonOutputGuardrail {

    public QuestionsGuardrail() {
        super(LlmOutputs::questions);
    }
}
