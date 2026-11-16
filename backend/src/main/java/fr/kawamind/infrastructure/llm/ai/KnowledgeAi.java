package fr.kawamind.infrastructure.llm.ai;

import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import fr.kawamind.domain.llm.ImpactResult;
import fr.kawamind.domain.llm.IssueComparison;
import fr.kawamind.domain.llm.RelatedIssuesJudgement;
import fr.kawamind.infrastructure.llm.ai.guardrails.ComparisonGuardrail;
import fr.kawamind.infrastructure.llm.ai.guardrails.ImpactGuardrail;
import fr.kawamind.infrastructure.llm.ai.guardrails.RelatedGuardrail;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Opérations de la connaissance du projet (002, 014 research R3) : tickets proches, comparaison avec
 * un ticket, modules impactés. Consigne en système, données déjà balisées et échappées par
 * {@code UserMessages} ; {@code format} porte le schéma strict de la réponse.
 */
@RegisterAiService(chatLanguageModelSupplier = AppModels.Chat.class,
        chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
@ApplicationScoped
public interface KnowledgeAi {

    @SystemMessage(fromResource = "prompts/judge-related-issues.txt")
    @UserMessage("{data}")
    @OutputGuardrails(RelatedGuardrail.class)
    RelatedIssuesJudgement judge(String data, ChatRequestParameters format);

    @SystemMessage(fromResource = "prompts/compare-with-issue.txt")
    @UserMessage("{data}")
    @OutputGuardrails(ComparisonGuardrail.class)
    IssueComparison compare(String data, ChatRequestParameters format);

    @SystemMessage(fromResource = "prompts/identify-impact.txt")
    @UserMessage("{data}")
    @OutputGuardrails(ImpactGuardrail.class)
    ImpactResult impact(String data, ChatRequestParameters format);
}
