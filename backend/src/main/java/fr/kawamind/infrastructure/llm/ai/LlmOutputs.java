package fr.kawamind.infrastructure.llm.ai;

import fr.kawamind.domain.conversation.EstablishedPoint;
import fr.kawamind.domain.conversation.NeedDraft;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.llm.Contradiction;
import fr.kawamind.domain.llm.DraftSpecification;
import fr.kawamind.domain.llm.ImpactResult;
import fr.kawamind.domain.llm.IssueComparison;
import fr.kawamind.domain.llm.ProposeQuestionsResult;
import fr.kawamind.domain.llm.ProposedQuestion;
import fr.kawamind.domain.llm.ReassessResult;
import fr.kawamind.domain.llm.RelatedIssuesJudgement;
import fr.kawamind.domain.port.ConversationLlm.ChangeCandidate;
import fr.kawamind.domain.port.ConversationLlm.ChangeOrigin;
import fr.kawamind.domain.port.ConversationLlm.Revision;
import fr.kawamind.domain.port.WikiMarkup;
import fr.kawamind.domain.question.QuestionTheme;
import fr.kawamind.domain.sources.ModuleCorrection;
import fr.kawamind.domain.sources.ModuleQualification;
import fr.kawamind.domain.sources.Qualification;
import fr.kawamind.domain.task.Basis;
import fr.kawamind.domain.task.BasisKind;
import fr.kawamind.domain.task.ProposedTask;
import fr.kawamind.domain.task.ProposedTasks;
import fr.kawamind.infrastructure.jira.JiraFormatConverter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jboss.logging.Logger;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lecture des sorties JSON des opérations (contracts/llm-port.md, 014 research R4) : chaque fonction
 * rend l'objet du domaine, ou lève une erreur si la sortie n'est pas conforme, ce qui fait redemander
 * la réponse par le garde-fou de sortie. Les entrées secondaires mal formées sont écartées.
 */
public final class LlmOutputs {

    private static final Logger LOG = Logger.getLogger(LlmOutputs.class);
    private static final JsonMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    /** Tableaux en wiki markup dans le texte riche de la spécification (FR-012b). */
    private static final WikiMarkup TABLES = new JiraFormatConverter();

    record QuestionsDto(List<QuestionDto> questions, List<Contradiction> contradictions, List<String> coveredThemes) {
    }

    record ReassessDto(List<String> obsoleteQuestionIds, List<QuestionDto> newQuestions) {
    }

    record QuestionDto(String theme, String label, String rationale, List<String> suggestions, String defaultValue,
            List<String> dependsOnQuestionIds) {
    }

    record QualificationDto(String id, String qualification, String justification) {
    }

    record QualificationsDto(List<QualificationDto> modules) {
    }

    record PointDto(String theme, String question, String answer, String basis, String sourceRef) {
    }

    record NeedDto(String need, List<PointDto> points) {
    }

    record SummaryDto(String summary) {
    }

    record RevisionDto(Boolean changed, String reason, DraftSpecification specification,
            List<CorrectionDto> perimeter) {
    }

    record CorrectionDto(String module, String qualification, String justification) {
    }

    record ChangesDto(List<ChangeDto> changes) {
    }

    record ChangeDto(String text, String origin) {
    }

    /** Qualifications de modules rendues par {@code qualify-perimeter} (013). */
    public record Qualifications(List<ModuleQualification> modules) {
    }

    /** Changements relevés par {@code list-changes} (011). */
    public record Changes(List<ChangeCandidate> changes) {
    }

    /** Résumé rendu par {@code compact-memory} (010). */
    public record Summary(String text) {
    }

    private LlmOutputs() {
    }

    public static DraftSpecification draft(String json) {
        return normalized(JSON.readValue(json, DraftSpecification.class));
    }

    /** Tableaux en syntaxe wiki du résumé et des détails convertis en Markdown (FR-012b). */
    public static DraftSpecification normalized(DraftSpecification draft) {
        if (draft == null) {
            throw new IllegalArgumentException("spécification absente");
        }
        return new DraftSpecification(draft.title(), TABLES.wikiTablesToMarkdown(draft.summary()),
                TABLES.wikiTablesToMarkdown(draft.details()), draft.useCases(), draft.scenarios(), draft.assumptions(),
                draft.requirements(), draft.adrDeviations());
    }

    public static RelatedIssuesJudgement related(String json) {
        RelatedIssuesJudgement judgement = JSON.readValue(json, RelatedIssuesJudgement.class);
        if (judgement == null || judgement.related().stream().anyMatch(r -> blank(r.key()))) {
            throw new IllegalArgumentException("ticket proche sans clé");
        }
        return judgement;
    }

    public static IssueComparison comparison(String json) {
        IssueComparison comparison = JSON.readValue(json, IssueComparison.class);
        if (comparison == null || comparison.suggestion() == null) {
            throw new IllegalArgumentException("comparaison sans suggestion");
        }
        return comparison;
    }

    record TaskDto(String title, String outcome, String basisKind, String basisRef, List<Integer> dependsOn) {
    }

    record TasksDto(List<TaskDto> tasks) {
    }

    /**
     * Tâches proposées (015, contracts/llm-propose-tasks.md) : la forme seulement ; les bornes et la
     * base, qui dépendent des données, sont vérifiées par le garde-fou de l'opération.
     */
    public static ProposedTasks tasks(String json) {
        TasksDto dto = JSON.readValue(json, TasksDto.class);
        if (dto == null || dto.tasks() == null) {
            throw new IllegalArgumentException("tâches absentes");
        }
        List<ProposedTask> tasks = new ArrayList<>();
        for (TaskDto t : dto.tasks()) {
            if (t == null || blank(t.title()) || blank(t.outcome()) || blank(t.basisRef())) {
                throw new IllegalArgumentException("tâche incomplète");
            }
            BasisKind kind = BasisKind.valueOf(t.basisKind());
            if (kind == BasisKind.NONE) {
                throw new IllegalArgumentException("base absente");
            }
            List<Integer> deps = t.dependsOn() == null ? List.of() : t.dependsOn();
            if (deps.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("préalable vide");
            }
            tasks.add(new ProposedTask(t.title(), t.outcome(), new Basis(kind, t.basisRef()), deps, false));
        }
        return new ProposedTasks(tasks);
    }

    public static ImpactResult impact(String json) {
        ImpactResult impact = JSON.readValue(json, ImpactResult.class);
        if (impact == null || impact.exchanges().stream().anyMatch(e -> blank(e.from()) || blank(e.to()))) {
            throw new IllegalArgumentException("échange sans émetteur ou sans destinataire");
        }
        return impact;
    }

    public static ProposeQuestionsResult questions(String json) {
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

    public static ReassessResult reassess(String json) {
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

    /** Une entrée mal formée est écartée : le module reste non qualifié, jamais retiré. */
    public static Qualifications qualifications(String json) {
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
        return new Qualifications(List.copyOf(out));
    }

    /** Un point mal formé est écarté : l'utilisateur relit de toute façon le brouillon. */
    public static NeedDraft need(String json) {
        NeedDto dto = JSON.readValue(json, NeedDto.class);
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

    /** Un résumé vide ne remplace pas la mémoire : la sortie est redemandée. */
    public static Summary summary(String json) {
        SummaryDto dto = JSON.readValue(json, SummaryDto.class);
        if (dto == null || dto.summary() == null || dto.summary().isBlank()) {
            throw new IllegalArgumentException("Résumé vide");
        }
        return new Summary(dto.summary().strip());
    }

    /** Un changement sans spécification, ou une absence de changement sans raison, est redemandé. */
    public static Revision revision(String json) {
        RevisionDto dto = JSON.readValue(json, RevisionDto.class);
        if (dto == null || dto.changed() == null) {
            throw new IllegalArgumentException("Proposition sans décision");
        }
        String reason = dto.reason() == null ? "" : dto.reason().strip();
        if (!dto.changed()) {
            if (reason.isEmpty()) {
                throw new IllegalArgumentException("Aucun changement, sans raison");
            }
            return new Revision(false, reason, null, corrections(dto.perimeter()));
        }
        return new Revision(true, reason, normalized(dto.specification()), corrections(dto.perimeter()));
    }

    /** Qualifications établies par la discussion ; les entrées incomplètes ou inconnues sont écartées. */
    static List<ModuleCorrection> corrections(List<CorrectionDto> dtos) {
        List<ModuleCorrection> out = new ArrayList<>();
        for (CorrectionDto c : dtos == null ? List.<CorrectionDto>of() : dtos) {
            if (c == null || c.module() == null || c.module().isBlank() || c.qualification() == null) {
                continue;
            }
            try {
                out.add(new ModuleCorrection(c.module().strip(), Qualification.valueOf(c.qualification()),
                        c.justification() == null ? "" : c.justification().strip()));
            } catch (IllegalArgumentException e) {
                LOG.debugf("Correction de périmètre écartée : qualification %s inconnue", c.qualification());
            }
        }
        return List.copyOf(out);
    }

    /** Changements relevés : vides écartés, doublons retirés, textes et nombre bornés. */
    public static Changes changes(String json) {
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
        return new Changes(List.copyOf(out));
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
