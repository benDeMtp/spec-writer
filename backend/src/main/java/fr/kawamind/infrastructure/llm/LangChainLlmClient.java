package fr.kawamind.infrastructure.llm;

import fr.kawamind.domain.llm.CompareIssueRequest;
import fr.kawamind.domain.llm.DraftSpecification;
import fr.kawamind.domain.llm.EditRequest;
import fr.kawamind.domain.llm.GenerationRequest;
import fr.kawamind.domain.llm.ImpactRequest;
import fr.kawamind.domain.llm.ImpactResult;
import fr.kawamind.domain.llm.IssueComparison;
import fr.kawamind.domain.llm.MergeRequest;
import fr.kawamind.domain.llm.ProposeQuestionsRequest;
import fr.kawamind.domain.llm.ProposeQuestionsResult;
import fr.kawamind.domain.llm.ReassessRequest;
import fr.kawamind.domain.llm.ReassessResult;
import fr.kawamind.domain.llm.RelatedIssuesJudgement;
import fr.kawamind.domain.llm.RelatedIssuesRequest;
import fr.kawamind.domain.llm.StructureImportRequest;
import fr.kawamind.domain.port.LlmClient;
import fr.kawamind.infrastructure.llm.ai.KnowledgeAi;
import fr.kawamind.infrastructure.llm.ai.QuestionsAi;
import fr.kawamind.infrastructure.llm.ai.SpecificationAi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Port LLM sur les AI Services (contracts/llm-port.md, 014 research R3) : cet adaptateur construit
 * les données balisées et échappées ({@link UserMessages}), appelle l'AI Service de l'opération avec
 * le schéma strict de sa réponse, et convertit les erreurs. La consigne, la reprise d'une sortie non
 * conforme et le journal sont ceux des AI Services et de {@link LlmCallLog}.
 */
@ApplicationScoped
public class LangChainLlmClient implements LlmClient {

    /** Tentatives d'une opération structurée encore hors des AI Services (agent de la 010). */
    static final int MAX_ATTEMPTS = 3;

    private static final Map<String, String> PROMPTS = new ConcurrentHashMap<>();

    private final KnowledgeAi knowledge;
    private final QuestionsAi questions;
    private final SpecificationAi specification;
    /** Délai d'un appel interactif (180 s par défaut). */
    private final Duration timeout;
    /** Délai d'une rédaction de spécification entière, plus longue sur une grosse spécification (600 s par défaut). */
    private final Duration drafting;

    @Inject
    public LangChainLlmClient(KnowledgeAi knowledge, QuestionsAi questions, SpecificationAi specification,
            @ConfigProperty(name = "specwriter.llm.timeout.interactive", defaultValue = "180s") Duration timeout,
            @ConfigProperty(name = "specwriter.llm.timeout.drafting", defaultValue = "600s") Duration drafting) {
        this.knowledge = knowledge;
        this.questions = questions;
        this.specification = specification;
        this.timeout = timeout;
        this.drafting = drafting;
    }

    @Override
    public ProposeQuestionsResult proposeQuestions(ProposeQuestionsRequest request) {
        String user = UserMessages.questions(request.need(), request.language(), request.known(),
                request.remainingThemes(), request.knowledge());
        return AiCalls.call(timeout, () -> questions.propose(user, LlmSchemas.params(LlmSchemas.QUESTIONS)));
    }

    @Override
    public DraftSpecification generateSpecification(GenerationRequest request) {
        String user = UserMessages.generation(request.need(), request.language(), request.answers(),
                request.correction(), request.knowledge());
        return AiCalls.call(drafting, () -> specification.generate(user, LlmSchemas.params(LlmSchemas.SPECIFICATION)));
    }

    @Override
    public ReassessResult reassessAfterChange(ReassessRequest request) {
        String user = UserMessages.reassess(request.need(), request.language(), request.known(),
                request.changedQuestionId(), request.knowledge());
        return AiCalls.call(timeout, () -> questions.reassess(user, LlmSchemas.params(LlmSchemas.REASSESS)));
    }

    @Override
    public DraftSpecification editSpecification(EditRequest request) {
        String user = UserMessages.edit(request.need(), request.language(), request.answers(), request.currentMarkdown(),
                request.instruction(), request.correction(), request.knowledge());
        return AiCalls.call(drafting, () -> specification.edit(user, LlmSchemas.params(LlmSchemas.SPECIFICATION)));
    }

    @Override
    public DraftSpecification mergeConflict(MergeRequest request) {
        String user = UserMessages.merge(request.need(), request.language(), request.answers(), request.localMarkdown(),
                request.jiraMarkdown(), request.correction(), request.knowledge());
        return AiCalls.call(drafting, () -> specification.merge(user, LlmSchemas.params(LlmSchemas.SPECIFICATION)));
    }

    @Override
    public DraftSpecification structureImportedText(StructureImportRequest request) {
        String user = UserMessages.structure(request.text(), request.language(), request.correction(),
                request.knowledge());
        return AiCalls.call(drafting, () -> specification.structure(user, LlmSchemas.params(LlmSchemas.SPECIFICATION)));
    }

    @Override
    public RelatedIssuesJudgement judgeRelatedIssues(RelatedIssuesRequest request) {
        return AiCalls.call(timeout, () -> knowledge.judge(UserMessages.relatedIssues(request),
                LlmSchemas.params(LlmSchemas.RELATED_ISSUES)));
    }

    @Override
    public IssueComparison compareWithIssue(CompareIssueRequest request) {
        return AiCalls.call(timeout, () -> knowledge.compare(UserMessages.compareIssue(request),
                LlmSchemas.params(LlmSchemas.ISSUE_COMPARISON)));
    }

    @Override
    public ImpactResult identifyImpact(ImpactRequest request) {
        return AiCalls.call(timeout, () -> knowledge.impact(UserMessages.impact(request),
                LlmSchemas.params(LlmSchemas.IMPACT)));
    }

    static String prompt(String operation) {
        return PROMPTS.computeIfAbsent(operation, op -> {
            try (InputStream in = LangChainLlmClient.class.getResourceAsStream("/prompts/" + op + ".txt")) {
                if (in == null) {
                    throw new IllegalStateException("Message système absent : " + op);
                }
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
