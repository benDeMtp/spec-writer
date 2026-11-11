package dev.specwriter.infrastructure.llm;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.specwriter.domain.sources.ModuleQualification;
import dev.specwriter.domain.sources.Qualification;
import dev.specwriter.domain.conversation.EstablishedPoint;
import dev.specwriter.domain.conversation.NeedDraft;
import dev.specwriter.domain.conversation.SourceKind;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.llm.DraftSpecification;
import dev.specwriter.domain.port.ConversationLlm;
import dev.specwriter.domain.question.QuestionTheme;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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

    record PlanDto(String action, String query, List<String> kinds, List<String> terms) {
    }

    record QualificationDto(String id, String qualification, String justification) {
    }

    record QualificationsDto(List<QualificationDto> modules) {
    }

    record PointDto(String theme, String question, String answer, String basis, String sourceRef) {
    }

    record DraftDto(String need, List<PointDto> points) {
    }

    record SummaryDto(String summary) {
    }

    record RevisionDto(Boolean changed, String reason, DraftSpecification specification) {
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
    public void thinking(Consumer<String> reasoning, Runnable work) {
        ScopedValue.where(ReasoningSink.CURRENT, reasoning).run(work);
    }

    @Override
    public Plan plan(PlanRequest request) {
        return call("converse-plan", UserMessages.conversePlan(request), LlmSchemas.CONVERSE_PLAN,
                LangChainConversationLlm::toPlan);
    }

    @Override
    public List<ModuleQualification> qualify(QualifyRequest request) {
        return call("qualify-perimeter", UserMessages.qualifyPerimeter(request), LlmSchemas.PERIMETER_QUALIFICATION,
                LangChainConversationLlm::toQualifications);
    }

    /** Une entrée mal formée est écartée : le module reste non qualifié, jamais retiré. */
    static List<ModuleQualification> toQualifications(String json) {
        QualificationsDto dto = JSON.readValue(json, QualificationsDto.class);
        if (dto == null || dto.modules() == null) {
            throw new IllegalArgumentException("Qualification absente");
        }
        List<ModuleQualification> out = new ArrayList<>();
        for (QualificationDto q : dto.modules()) {
            if (q == null || q.id() == null || q.qualification() == null) {
                continue;
            }
            try {
                Qualification value = Qualification.valueOf(q.qualification());
                if (value != Qualification.UNQUALIFIED) {
                    out.add(new ModuleQualification(q.id(), value, q.justification() == null ? "" : q.justification().strip()));
                }
            } catch (IllegalArgumentException e) {
                LOG.debugf("Qualification écartée (%s)", e.getClass().getSimpleName());
            }
        }
        return out;
    }

    @Override
    public NeedDraft draftNeed(DraftRequest request) {
        return call("propose-need", UserMessages.needDraft(request), LlmSchemas.NEED_DRAFT,
                LangChainConversationLlm::toDraft);
    }

    @Override
    public String compact(CompactRequest request) {
        return call("compact-memory", UserMessages.compact(request), LlmSchemas.COMPACT_MEMORY,
                LangChainConversationLlm::toSummary);
    }

    @Override
    public Revision revise(ReviseRequest request) {
        return call("revise-specification", UserMessages.revise(request), LlmSchemas.REVISION,
                LangChainConversationLlm::toRevision);
    }

    @Override
    public List<ChangeCandidate> changes(ChangesRequest request) {
        return call("list-changes", UserMessages.changes(request), LlmSchemas.CHANGE_CANDIDATES,
                LangChainConversationLlm::toChanges);
    }

    /** Changements relevés : vides écartés, doublons retirés, textes et nombre bornés. */
    static List<ChangeCandidate> toChanges(String json) {
        ChangesDto dto = JSON.readValue(json, ChangesDto.class);
        if (dto == null || dto.changes() == null) {
            throw new IllegalArgumentException("Relevé sans liste de changements");
        }
        Set<String> seen = new HashSet<>();
        List<ChangeCandidate> out = new ArrayList<>();
        for (ChangeDto c : dto.changes()) {
            String text = c == null || c.text() == null ? "" : c.text().strip();
            if (text.isEmpty() || !seen.add(text.toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (text.length() > ChangeCandidate.MAX_TEXT) {
                text = text.substring(0, ChangeCandidate.MAX_TEXT - 1).strip() + "…";
            }
            out.add(new ChangeCandidate(text, "USER".equals(c.origin()) ? ChangeOrigin.USER : ChangeOrigin.AGENT));
            if (out.size() == ChangeCandidate.MAX_CHANGES) {
                break;
            }
        }
        return List.copyOf(out);
    }

    record ChangesDto(List<ChangeDto> changes) {
    }

    record ChangeDto(String text, String origin) {
    }

    /** Un changement sans spécification, ou une absence de changement sans raison, est redemandé. */
    static Revision toRevision(String json) {
        RevisionDto dto = JSON.readValue(json, RevisionDto.class);
        if (dto == null || dto.changed() == null) {
            throw new IllegalArgumentException("Proposition sans décision");
        }
        String reason = dto.reason() == null ? "" : dto.reason().strip();
        if (!dto.changed()) {
            if (reason.isEmpty()) {
                throw new IllegalArgumentException("Aucun changement, sans raison");
            }
            return new Revision(false, reason, null);
        }
        return new Revision(true, reason, LangChainLlmClient.normalized(dto.specification()));
    }

    /** Un résumé vide ne remplace pas la mémoire : la sortie est redemandée. */
    static String toSummary(String json) {
        SummaryDto dto = JSON.readValue(json, SummaryDto.class);
        if (dto == null || dto.summary() == null || dto.summary().isBlank()) {
            throw new IllegalArgumentException("Résumé vide");
        }
        return dto.summary().strip();
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
        if (action == Action.PERIMETER) {
            List<String> terms = dto.terms() == null ? List.of() : dto.terms().stream()
                    .filter(t -> t != null && !t.isBlank()).map(String::strip).toList();
            if (terms.isEmpty()) {
                throw new IllegalArgumentException("Analyse de périmètre sans terme");
            }
            return Plan.perimeter(terms);
        }
        if (dto.query() == null || dto.query().isBlank()) {
            throw new IllegalArgumentException("Recherche sans mots");
        }
        Set<SourceKind> kinds = EnumSet.noneOf(SourceKind.class);
        if (dto.kinds() != null) {
            for (String k : dto.kinds()) {
                SourceKind kind = SourceKind.valueOf(k);
                if (kind != SourceKind.ATTACHMENT && kind != SourceKind.DISCUSSION) {
                    kinds.add(kind);
                }
            }
        }
        return new Plan(Action.SEARCH, dto.query().strip(), kinds);
    }
}
