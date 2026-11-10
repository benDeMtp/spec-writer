package dev.specwriter.infrastructure.llm;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.llm.Contradiction;
import dev.specwriter.domain.llm.DraftSpecification;
import dev.specwriter.domain.llm.EditRequest;
import dev.specwriter.domain.llm.GenerationRequest;
import dev.specwriter.domain.llm.MergeRequest;
import dev.specwriter.domain.llm.ProposeQuestionsRequest;
import dev.specwriter.domain.llm.ProposeQuestionsResult;
import dev.specwriter.domain.llm.ProposedQuestion;
import dev.specwriter.domain.llm.ReassessRequest;
import dev.specwriter.domain.llm.ReassessResult;
import dev.specwriter.domain.llm.StructureImportRequest;
import dev.specwriter.domain.llm.CompareIssueRequest;
import dev.specwriter.domain.llm.ImpactRequest;
import dev.specwriter.domain.llm.ImpactResult;
import dev.specwriter.domain.llm.IssueComparison;
import dev.specwriter.domain.llm.RelatedIssuesJudgement;
import dev.specwriter.domain.llm.RelatedIssuesRequest;
import dev.specwriter.domain.port.LlmClient;
import dev.specwriter.domain.port.WikiMarkup;
import dev.specwriter.domain.question.QuestionTheme;
import dev.specwriter.infrastructure.jira.JiraFormatConverter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.time.Duration;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Implémentation du port LLM au-dessus d'un {@link ChatModel} LangChain4j (contracts/llm-port.md).
 * Instructions en message système uniquement, données en message utilisateur entre balises,
 * réponse JSON validée ; une sortie illisible est redemandée 2 fois au maximum. Seuls l'opération,
 * la durée et le nombre de tentatives sont journalisés (FR-025).
 */
@ApplicationScoped
public class LangChainLlmClient implements LlmClient {

    private static final Logger LOG = Logger.getLogger(LangChainLlmClient.class);
    static final int MAX_ATTEMPTS = 3;

    private static final JsonMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    private static final Map<String, String> PROMPTS = new ConcurrentHashMap<>();
    /** Tableaux en wiki markup dans le texte riche de la spécification (FR-012b). */
    private static final WikiMarkup TABLES = new JiraFormatConverter();

    record QuestionsDto(List<QuestionDto> questions, List<Contradiction> contradictions, List<String> coveredThemes) {
    }

    record ReassessDto(List<String> obsoleteQuestionIds, List<QuestionDto> newQuestions) {
    }

    record QuestionDto(String theme, String label, String rationale, List<String> suggestions, String defaultValue,
            List<String> dependsOnQuestionIds) {
    }

    private final ChatModel model;
    /** Délai d'un appel : toutes les opérations sont interactives (180 s par défaut). */
    private final Duration timeout;

    public LangChainLlmClient(ChatModel model) {
        this(model, CallTimeout.DEFAULT);
    }

    @Inject
    public LangChainLlmClient(ChatModel model,
            @ConfigProperty(name = "specwriter.llm.timeout.interactive", defaultValue = "180s") Duration timeout) {
        this.model = model;
        this.timeout = timeout;
    }

    @Override
    public ProposeQuestionsResult proposeQuestions(ProposeQuestionsRequest request) {
        String user = UserMessages.questions(request.need(), request.language(), request.known(),
                request.remainingThemes(), request.knowledge());
        return call("propose-questions", user, LlmSchemas.QUESTIONS, LangChainLlmClient::toQuestions);
    }

    @Override
    public DraftSpecification generateSpecification(GenerationRequest request) {
        String user = UserMessages.generation(request.need(), request.language(), request.answers(),
                request.correction(), request.knowledge());
        return call("generate-specification", user, LlmSchemas.SPECIFICATION, LangChainLlmClient::toDraft);
    }

    @Override
    public ReassessResult reassessAfterChange(ReassessRequest request) {
        String user = UserMessages.reassess(request.need(), request.language(), request.known(),
                request.changedQuestionId(), request.knowledge());
        return call("reassess-after-change", user, LlmSchemas.REASSESS, LangChainLlmClient::toReassess);
    }

    @Override
    public DraftSpecification editSpecification(EditRequest request) {
        String user = UserMessages.edit(request.need(), request.language(), request.answers(), request.currentMarkdown(),
                request.instruction(), request.correction(), request.knowledge());
        return call("edit-specification", user, LlmSchemas.SPECIFICATION, LangChainLlmClient::toDraft);
    }

    @Override
    public DraftSpecification mergeConflict(MergeRequest request) {
        String user = UserMessages.merge(request.need(), request.language(), request.answers(), request.localMarkdown(),
                request.jiraMarkdown(), request.correction(), request.knowledge());
        return call("merge-conflict", user, LlmSchemas.SPECIFICATION, LangChainLlmClient::toDraft);
    }

    @Override
    public DraftSpecification structureImportedText(StructureImportRequest request) {
        String user = UserMessages.structure(request.text(), request.language(), request.correction(),
                request.knowledge());
        return call("structure-imported-text", user, LlmSchemas.SPECIFICATION, LangChainLlmClient::toDraft);
    }

    @Override
    public RelatedIssuesJudgement judgeRelatedIssues(RelatedIssuesRequest request) {
        return call("judge-related-issues", UserMessages.relatedIssues(request), LlmSchemas.RELATED_ISSUES,
                LangChainLlmClient::toRelated);
    }

    @Override
    public IssueComparison compareWithIssue(CompareIssueRequest request) {
        return call("compare-with-issue", UserMessages.compareIssue(request), LlmSchemas.ISSUE_COMPARISON,
                LangChainLlmClient::toComparison);
    }

    @Override
    public ImpactResult identifyImpact(ImpactRequest request) {
        return call("identify-impact", UserMessages.impact(request), LlmSchemas.IMPACT, LangChainLlmClient::toImpact);
    }

    private <T> T call(String operation, String user, ResponseFormat format, Function<String, T> parse) {
        String system = prompt(operation);
        long start = System.nanoTime();
        String feedback = "";
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            ChatRequest request = ChatRequest.builder()
                    .messages(SystemMessage.from(system), UserMessage.from(user + feedback))
                    .responseFormat(format)
                    .build();
            String text;
            try {
                text = ScopedValue.where(CallTimeout.CURRENT, timeout)
                        .call(() -> model.chat(request).aiMessage().text());
            } catch (DomainException e) {
                if (Thread.currentThread().isInterrupted()) {
                    LOG.infof("LLM %s : appel interrompu à la tentative %d", operation, attempt);
                    throw e;
                }
                // ni le besoin ni la réponse : l'opération, la tentative, la durée et la cause sans contenu
                LOG.warnf(e, "LLM %s : échec à la tentative %d après %d ms (%s)", operation, attempt,
                        (System.nanoTime() - start) / 1_000_000, e.code());
                throw e;
            }
            try {
                T result = parse.apply(text);
                LOG.infof("LLM %s : %d ms, %d tentative(s)", operation, (System.nanoTime() - start) / 1_000_000, attempt);
                return result;
            } catch (JacksonException | IllegalArgumentException | DomainException e) {
                LOG.warnf("LLM %s : sortie invalide à la tentative %d (%s)", operation, attempt, e.getClass().getSimpleName());
                feedback = "\n<correction>La réponse précédente n'était pas un JSON conforme au schéma demandé."
                        + "</correction>";
            }
        }
        throw new DomainException(ErrorCode.LLM_INVALID_OUTPUT);
    }

    private static DraftSpecification toDraft(String json) {
        return normalized(JSON.readValue(json, DraftSpecification.class));
    }

    /** Tableaux en syntaxe wiki du résumé et des détails convertis en Markdown (FR-012b). */
    static DraftSpecification normalized(DraftSpecification draft) {
        if (draft == null) {
            throw new IllegalArgumentException("spécification absente");
        }
        return new DraftSpecification(draft.title(), TABLES.wikiTablesToMarkdown(draft.summary()),
                TABLES.wikiTablesToMarkdown(draft.details()), draft.useCases(), draft.scenarios(), draft.assumptions(),
                draft.requirements(), draft.adrDeviations());
    }

    private static RelatedIssuesJudgement toRelated(String json) {
        RelatedIssuesJudgement judgement = JSON.readValue(json, RelatedIssuesJudgement.class);
        if (judgement == null || judgement.related().stream().anyMatch(r -> blank(r.key()))) {
            throw new IllegalArgumentException("ticket proche sans clé");
        }
        return judgement;
    }

    private static IssueComparison toComparison(String json) {
        IssueComparison comparison = JSON.readValue(json, IssueComparison.class);
        if (comparison == null || comparison.suggestion() == null) {
            throw new IllegalArgumentException("comparaison sans suggestion");
        }
        return comparison;
    }

    private static ImpactResult toImpact(String json) {
        ImpactResult impact = JSON.readValue(json, ImpactResult.class);
        if (impact == null || impact.exchanges().stream().anyMatch(e -> blank(e.from()) || blank(e.to()))) {
            throw new IllegalArgumentException("échange sans émetteur ou sans destinataire");
        }
        return impact;
    }

    private static ProposeQuestionsResult toQuestions(String json) {
        QuestionsDto dto = JSON.readValue(json, QuestionsDto.class);
        if (dto == null || dto.questions() == null) {
            throw new IllegalArgumentException("liste de questions absente");
        }
        List<ProposedQuestion> questions = proposed(dto.questions());
        Set<QuestionTheme> covered = EnumSet.noneOf(QuestionTheme.class);
        if (dto.coveredThemes() != null) {
            dto.coveredThemes().forEach(t -> covered.add(theme(t)));
        }
        return new ProposeQuestionsResult(questions, dto.contradictions() == null ? List.of() : dto.contradictions(),
                covered);
    }

    private static ReassessResult toReassess(String json) {
        ReassessDto dto = JSON.readValue(json, ReassessDto.class);
        if (dto == null || dto.obsoleteQuestionIds() == null || dto.newQuestions() == null) {
            throw new IllegalArgumentException("réévaluation incomplète");
        }
        return new ReassessResult(dto.obsoleteQuestionIds(), proposed(dto.newQuestions()));
    }

    private static List<ProposedQuestion> proposed(List<QuestionDto> dtos) {
        List<ProposedQuestion> questions = dtos.stream()
                .map(q -> new ProposedQuestion(theme(q.theme()), q.label(), q.rationale(),
                        q.suggestions() == null ? List.of() : q.suggestions().stream().limit(5).toList(),
                        q.defaultValue(), q.dependsOnQuestionIds() == null ? List.of() : q.dependsOnQuestionIds()))
                .toList();
        if (questions.stream().anyMatch(q -> blank(q.label()) || blank(q.rationale()))) {
            throw new IllegalArgumentException("question sans libellé ou sans justification");
        }
        return questions;
    }

    /** Thème inconnu ou absent : sortie non conforme, redemandée. */
    private static QuestionTheme theme(String value) {
        if (value == null) {
            throw new IllegalArgumentException("thème absent");
        }
        return QuestionTheme.valueOf(value);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
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
