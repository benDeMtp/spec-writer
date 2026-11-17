package fr.kawamind.infrastructure.llm.ai;

import fr.kawamind.domain.port.TurnTools;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrail;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrailRequest;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrailResult;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Garde-fou d'entrée des outils de l'agent (014, contracts/agent-tools.md) : au-delà de 5 recherches
 * dans le tour, aucune exécution, le modèle doit répondre avec ce qu'il a ; une seconde analyse de
 * périmètre est refusée. Les refus sont des résultats rendus au modèle, jamais des erreurs du tour.
 */
@ApplicationScoped
public class SearchCap implements ToolInputGuardrail {

    static final String STOPPED = "<recherche-arretee>oui</recherche-arretee>";
    static final String PERIMETER_DONE = "<erreur>une seule analyse de périmètre par réponse : réponds avec les "
            + "modules déjà trouvés</erreur>";

    @Override
    public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
        TurnTools tools = AgentTools.current(request.invocationContext() == null ? null
                : request.invocationContext().parameter(AgentTools.TURN)).orElse(null);
        if (tools == null) {
            return ToolInputGuardrailResult.success();
        }
        if (tools.stepsLeft() <= 0) {
            return ToolInputGuardrailResult.failure(STOPPED);
        }
        if ("analyze_perimeter".equals(request.toolName()) && tools.perimeterDone()) {
            return ToolInputGuardrailResult.failure(PERIMETER_DONE);
        }
        return ToolInputGuardrailResult.success();
    }
}
