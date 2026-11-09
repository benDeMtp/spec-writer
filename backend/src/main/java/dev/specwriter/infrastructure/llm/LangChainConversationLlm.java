package dev.specwriter.infrastructure.llm;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.specwriter.domain.conversation.EstablishedPoint;
import dev.specwriter.domain.conversation.NeedDraft;
import dev.specwriter.domain.conversation.SourceKind;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.ConversationLlm;
import dev.specwriter.domain.question.QuestionTheme;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jboss.logging.Logger;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Agent conversationnel sur Copilot (010, research.md R1, R2) : le plan est un JSON à schéma strict
 * (3 tentatives, comme les autres opérations), la réponse un texte rédigé en flux. Messages système
 * et utilisateur séparés ; le journal ne garde que l'opération, la durée et les tentatives.
 */
public class LangChainConversationLlm implements ConversationLlm {

    private static final Logger LOG = Logger.getLogger(LangChainConversationLlm.class);
    private static final JsonMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    record PlanDto(String action, String query, List<String> kinds) {
    }

    record PointDto(String theme, String question, String answer, String basis, String sourceRef) {
    }

    record DraftDto(String need, List<PointDto> points) {
    }

    private final ChatModel model;
    private final ConversationStream stream;
    private final Duration timeout;

    LangChainConversationLlm(ChatModel model, ConversationStream stream, Duration timeout) {
        this.model = model;
        this.stream = stream;
        this.timeout = timeout;
    }

    @Override
    public Plan plan(PlanRequest request) {
        return call("converse-plan", UserMessages.conversePlan(request), LlmSchemas.CONVERSE_PLAN,
                LangChainConversationLlm::toPlan);
    }

    @Override
    public NeedDraft draftNeed(DraftRequest request) {
        return call("propose-need", UserMessages.needDraft(request), LlmSchemas.NEED_DRAFT,
                LangChainConversationLlm::toDraft);
    }

    private <T> T call(String operation, String user, ResponseFormat format, Function<String, T> parse) {
        String system = LangChainLlmClient.prompt(operation);
        long start = System.nanoTime();
        String feedback = "";
        for (int attempt = 1; attempt <= LangChainLlmClient.MAX_ATTEMPTS; attempt++) {
            ChatRequest chat = ChatRequest.builder()
                    .messages(SystemMessage.from(system), UserMessage.from(user + feedback))
                    .responseFormat(format).build();
            String text = ScopedValue.where(CallTimeout.CURRENT, timeout).call(() -> model.chat(chat).aiMessage().text());
            try {
                T result = parse.apply(text);
                LOG.infof("LLM %s : %d ms, %d tentative(s)", operation, (System.nanoTime() - start) / 1_000_000, attempt);
                return result;
            } catch (JacksonException | IllegalArgumentException e) {
                LOG.warnf("LLM %s : sortie invalide à la tentative %d (%s)", operation, attempt,
                        e.getClass().getSimpleName());
                feedback = "\n<correction>La réponse précédente n'était pas un JSON conforme au schéma demandé."
                        + "</correction>";
            }
        }
        throw new DomainException(ErrorCode.LLM_INVALID_OUTPUT);
    }

    /** Un point mal formé est écarté : l'utilisateur relit de toute façon le brouillon. */
    static NeedDraft toDraft(String json) {
        DraftDto dto = JSON.readValue(json, DraftDto.class);
        if (dto == null || dto.need() == null) {
            throw new IllegalArgumentException("Besoin absent");
        }
        List<EstablishedPoint> points = new ArrayList<>();
        for (PointDto p : dto.points() == null ? List.<PointDto>of() : dto.points()) {
            if (p == null || p.theme() == null || p.basis() == null) {
                continue;
            }
            try {
                points.add(new EstablishedPoint(QuestionTheme.valueOf(p.theme()), p.question(), p.answer(),
                        EstablishedPoint.Basis.valueOf(p.basis()), p.sourceRef()));
            } catch (DomainException | IllegalArgumentException e) {
                LOG.debugf("Point établi écarté (%s)", e.getClass().getSimpleName());
            }
        }
        return new NeedDraft(dto.need(), points.stream().limit(NeedDraft.MAX_POINTS).toList());
    }

    @Override
    public String answer(AnswerRequest request, Consumer<String> deltas, BooleanSupplier cancelled) {
        String system = LangChainLlmClient.prompt("converse-answer");
        long start = System.nanoTime();
        try {
            String text = ScopedValue.where(CallTimeout.CURRENT, timeout)
                    .call(() -> stream.stream(system, UserMessages.converseAnswer(request), deltas, cancelled));
            LOG.infof("LLM converse-answer : %d ms, %d caractères", (System.nanoTime() - start) / 1_000_000, text.length());
            return text;
        } catch (DomainException e) {
            LOG.warnf(e, "LLM converse-answer : échec après %d ms (%s)", (System.nanoTime() - start) / 1_000_000, e.code());
            throw e;
        }
    }

    static Plan toPlan(String json) {
        PlanDto dto = JSON.readValue(json, PlanDto.class);
        if (dto == null || dto.action() == null) {
            throw new IllegalArgumentException("Action absente");
        }
        Action action = Action.valueOf(dto.action());
        if (action == Action.ANSWER) {
            return Plan.answer();
        }
        if (dto.query() == null || dto.query().isBlank()) {
            throw new IllegalArgumentException("Recherche sans mots");
        }
        Set<SourceKind> kinds = EnumSet.noneOf(SourceKind.class);
        if (dto.kinds() != null) {
            for (String k : dto.kinds()) {
                SourceKind kind = SourceKind.valueOf(k);
                if (kind != SourceKind.ATTACHMENT) {
                    kinds.add(kind);
                }
            }
        }
        return new Plan(Action.SEARCH, dto.query().strip(), kinds);
    }
}
