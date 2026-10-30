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
import dev.specwriter.domain.port.LlmClient;
import dev.specwriter.domain.question.QuestionTheme;
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

    record QuestionsDto(List<QuestionDto> questions, List<Contradiction> contradictions, List<String> coveredThemes) {
    }

    record QuestionDto(String theme, String label, String rationale, List<String> suggestions, String defaultValue,
            List<String> dependsOnQuestionIds) {
    }

    private final ChatModel model;

    @Inject
    public LangChainLlmClient(ChatModel model) {
        this.model = model;
    }

    @Override
    public ProposeQuestionsResult proposeQuestions(ProposeQuestionsRequest request) {
        String user = UserMessages.questions(request.need(), request.language(), request.known(),
                request.remainingThemes());
        return call("propose-questions", user, LlmSchemas.QUESTIONS, LangChainLlmClient::toQuestions);
    }

    @Override
    public DraftSpecification generateSpecification(GenerationRequest request) {
        String user = UserMessages.generation(request.need(), request.language(), request.answers(),
                request.correction());
        return call("generate-specification", user, LlmSchemas.SPECIFICATION,
                json -> JSON.readValue(json, DraftSpecification.class));
    }

    @Override
    public ReassessResult reassessAfterChange(ReassessRequest request) {
        throw new UnsupportedOperationException("reassessAfterChange arrive avec la user story 3");
    }

    @Override
    public DraftSpecification editSpecification(EditRequest request) {
        throw new UnsupportedOperationException("editSpecification arrive avec la user story 6");
    }

    @Override
    public DraftSpecification mergeConflict(MergeRequest request) {
        throw new UnsupportedOperationException("mergeConflict arrive avec la user story 6");
    }

    @Override
    public DraftSpecification structureImportedText(StructureImportRequest request) {
        throw new UnsupportedOperationException("structureImportedText arrive avec la user story 5");
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
            String text = model.chat(request).aiMessage().text();
            try {
                T result = parse.apply(text);
                LOG.infof("LLM %s : %d ms, %d tentative(s)", operation, (System.nanoTime() - start) / 1_000_000, attempt);
                return result;
            } catch (JacksonException | IllegalArgumentException | NullPointerException | DomainException e) {
                LOG.warnf("LLM %s : sortie invalide à la tentative %d (%s)", operation, attempt, e.getClass().getSimpleName());
                feedback = "\n<correction>La réponse précédente n'était pas un JSON conforme au schéma demandé."
                        + "</correction>";
            }
        }
        throw new DomainException(ErrorCode.LLM_INVALID_OUTPUT);
    }

    private static ProposeQuestionsResult toQuestions(String json) {
        QuestionsDto dto = JSON.readValue(json, QuestionsDto.class);
        List<ProposedQuestion> questions = dto.questions().stream()
                .map(q -> new ProposedQuestion(QuestionTheme.valueOf(q.theme()), q.label(), q.rationale(),
                        q.suggestions() == null ? List.of() : q.suggestions().stream().limit(5).toList(),
                        q.defaultValue(), q.dependsOnQuestionIds() == null ? List.of() : q.dependsOnQuestionIds()))
                .toList();
        if (questions.stream().anyMatch(q -> blank(q.label()) || blank(q.rationale()))) {
            throw new IllegalArgumentException("question sans libellé ou sans justification");
        }
        Set<QuestionTheme> covered = EnumSet.noneOf(QuestionTheme.class);
        if (dto.coveredThemes() != null) {
            dto.coveredThemes().forEach(t -> covered.add(QuestionTheme.valueOf(t)));
        }
        return new ProposeQuestionsResult(questions, dto.contradictions() == null ? List.of() : dto.contradictions(),
                covered);
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
