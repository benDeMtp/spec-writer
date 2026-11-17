package fr.kawamind.infrastructure.llm.ai;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.function.Function;

/** Outil demandé par le modèle mais inconnu (FR-007) : rien n'est exécuté, une erreur lui est rendue. */
@ApplicationScoped
public class UnknownTool implements Function<ToolExecutionRequest, ToolExecutionResultMessage> {

    static final String MESSAGE = "<erreur>outil inconnu</erreur>";

    @Override
    public ToolExecutionResultMessage apply(ToolExecutionRequest request) {
        return ToolExecutionResultMessage.from(request, MESSAGE);
    }
}
