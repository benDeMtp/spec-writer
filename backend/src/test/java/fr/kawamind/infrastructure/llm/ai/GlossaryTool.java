package fr.kawamind.infrastructure.llm.ai;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import fr.kawamind.domain.port.TurnTools;
import fr.kawamind.infrastructure.llm.UserMessages;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrails;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;

/** Outil fictif (014, US3) : passe par les outils du tour, comme les outils de l'agent. */
@ApplicationScoped
public class GlossaryTool {

    @Tool(name = "lookup_glossary", value = "Définition d'un terme du projet, en sources numérotées.")
    @ToolInputGuardrails(SearchCap.class)
    public String lookup(@P("Le terme à définir.") String term, InvocationParameters parameters) {
        TurnTools turn = AgentTools.turn(parameters);
        return UserMessages.found(turn.search(term, Set.of()), turn.stepsLeft());
    }
}
