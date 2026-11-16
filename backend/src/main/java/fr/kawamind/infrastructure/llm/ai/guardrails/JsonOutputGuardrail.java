package fr.kawamind.infrastructure.llm.ai.guardrails;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.memory.ChatMemory;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.infrastructure.llm.Operations;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.jboss.logging.Logger;
import tools.jackson.core.JacksonException;

/**
 * Garde-fou de sortie d'une opération structurée (014, research R4) : la réponse est lue et vérifiée
 * par une fonction de {@link fr.kawamind.infrastructure.llm.ai.LlmOutputs}, qui rend l'objet du
 * domaine ; une sortie non conforme est redemandée avec un message neutre, dans la limite de
 * {@code quarkus.langchain4j.guardrails.max-retries}. Le journal ne garde que l'opération, la
 * tentative et la nature de l'erreur.
 */
public abstract class JsonOutputGuardrail implements OutputGuardrail {

    private static final Logger LOG = Logger.getLogger(JsonOutputGuardrail.class);
    /** Reprise demandée au modèle : neutre, sans rien de la réponse fautive. */
    public static final String REPROMPT = "La réponse précédente n'était pas un JSON conforme au schéma demandé.";

    private final Function<String, ?> parse;

    protected JsonOutputGuardrail(Function<String, ?> parse) {
        this.parse = parse;
    }

    @Override
    public OutputGuardrailResult validate(OutputGuardrailRequest request) {
        AiMessage ai = request.responseFromLLM().aiMessage();
        String text = ai == null ? null : ai.text();
        try {
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("réponse vide");
            }
            return successWith(text, parse.apply(text));
        } catch (JacksonException | IllegalArgumentException | DomainException e) {
            List<ChatMessage> messages = Optional.ofNullable(request.requestParams().chatMemory())
                    .map(ChatMemory::messages).orElse(List.of());
            LOG.warnf("LLM %s : sortie invalide à la tentative %d (%s)", operation(messages), attempt(messages),
                    e.getClass().getSimpleName());
            return reprompt("sortie non conforme au schéma demandé", REPROMPT);
        }
    }

    static String operation(List<ChatMessage> messages) {
        return messages.stream().filter(SystemMessage.class::isInstance).map(m -> ((SystemMessage) m).text())
                .findFirst().flatMap(Operations::of).orElse("inconnue");
    }

    /** Chaque reprise ajoute un message utilisateur après le premier. */
    static int attempt(List<ChatMessage> messages) {
        return (int) Math.max(1, messages.stream().filter(UserMessage.class::isInstance).count());
    }
}
