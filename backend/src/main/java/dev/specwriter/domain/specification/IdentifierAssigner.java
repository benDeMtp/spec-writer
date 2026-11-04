package dev.specwriter.domain.specification;

import dev.specwriter.domain.llm.DraftSpecification;
import dev.specwriter.domain.llm.DraftSpecification.DraftAssumption;
import dev.specwriter.domain.llm.DraftSpecification.DraftRequirement;
import dev.specwriter.domain.llm.DraftSpecification.DraftScenario;
import dev.specwriter.domain.llm.DraftSpecification.DraftUseCase;
import dev.specwriter.domain.question.Question;
import dev.specwriter.domain.scenario.AcceptanceScenario;
import dev.specwriter.domain.session.Language;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Transforme la sortie du LLM en contenu identifié. Les codes {@code UC-nn}, {@code SC-nn-nn},
 * {@code H-nn}, {@code EF-nn} et {@code ET-nn} sont attribués ici, dans l'ordre, quelle que soit la
 * sortie du LLM (research.md R12, FR-014). Chaque question passée devient une hypothèse (FR-006).
 */
public final class IdentifierAssigner {

    public record Assigned(SpecificationContent content, StructureViolations violations) {
    }

    private IdentifierAssigner() {
    }

    /**
     * @param treated questions répondues ou passées, dans l'ordre du questionnaire ; leurs identifiants
     *     sont les seuls que le LLM peut citer.
     */
    public static Assigned assign(DraftSpecification draft, List<Question> treated, Language language) {
        List<String> violations = new ArrayList<>();
        Map<String, UUID> known = new HashMap<>();
        treated.forEach(q -> known.put(q.id().toString(), q.id()));

        List<UseCase> useCases = new ArrayList<>();
        for (int i = 0; i < draft.useCases().size(); i++) {
            DraftUseCase d = draft.useCases().get(i);
            String code = "UC-%02d".formatted(i + 1);
            if (blank(d.title()) || blank(d.actor()) || blank(d.goal())) {
                violations.add(code + " : titre, acteur et objectif sont obligatoires");
            }
            List<String> flow = clean(d.nominalFlow());
            if (flow.isEmpty()) {
                violations.add(code + " : le déroulement nominal doit avoir au moins une étape");
            }
            useCases.add(new UseCase(code, strip(d.title()), strip(d.actor()), strip(d.goal()), clean(d.preconditions()),
                    flow, clean(d.alternatives()), source(d, code, known, violations)));
        }

        List<AcceptanceScenario> scenarios = new ArrayList<>();
        Map<Integer, Integer> perUseCase = new HashMap<>();
        for (int i = 0; i < draft.scenarios().size(); i++) {
            DraftScenario d = draft.scenarios().get(i);
            int uc = d.useCaseIndex() + 1;
            int n = perUseCase.merge(uc, 1, Integer::sum);
            String code = "SC-%02d-%02d".formatted(Math.max(uc, 0), n);
            AcceptanceScenario.Kind kind = parse(AcceptanceScenario.Kind.class, d.kind()).orElseGet(() -> {
                violations.add(code + " : type de scénario inconnu (NOMINAL, ERROR ou EDGE)");
                return AcceptanceScenario.Kind.NOMINAL;
            });
            scenarios.add(new AcceptanceScenario(code, "UC-%02d".formatted(Math.max(uc, 0)), strip(d.title()), kind,
                    strip(d.asA()), strips(d.given()), strips(d.when()), strips(d.then()), d.assumption()));
        }

        List<Assumption> assumptions = new ArrayList<>();
        Set<UUID> covered = new HashSet<>();
        for (DraftAssumption d : draft.assumptions()) {
            UUID questionId = known.get(strip(d.questionId()));
            boolean skipped = questionId != null && "SKIPPED_QUESTION".equals(strip(d.origin()));
            if (skipped) {
                covered.add(questionId);
            }
            if (!blank(d.statement())) {
                assumptions.add(new Assumption("H-%02d".formatted(assumptions.size() + 1), strip(d.statement()),
                        skipped ? Assumption.Origin.SKIPPED_QUESTION : Assumption.Origin.TOOL_INFERENCE,
                        skipped ? questionId : null));
            }
        }
        for (Question q : treated) {
            if (q.answer() != null && q.answer().assumption() && !covered.contains(q.id())) {
                assumptions.add(new Assumption("H-%02d".formatted(assumptions.size() + 1),
                        skippedStatement(q, language), Assumption.Origin.SKIPPED_QUESTION, q.id()));
            }
        }

        List<Requirement> requirements = new ArrayList<>();
        int functional = 0;
        int technical = 0;
        for (DraftRequirement d : draft.requirements()) {
            Optional<Requirement.Category> category = parse(Requirement.Category.class, d.category());
            if (category.isEmpty() || blank(d.statement())) {
                violations.add("exigence : catégorie FUNCTIONAL ou TECHNICAL et énoncé obligatoires");
                continue;
            }
            String code = category.get() == Requirement.Category.FUNCTIONAL
                    ? "EF-%02d".formatted(++functional)
                    : "ET-%02d".formatted(++technical);
            requirements.add(new Requirement(code, category.get(), strip(d.statement())));
        }

        if (blank(draft.title()) || blank(draft.summary())) {
            violations.add("le titre et le résumé sont obligatoires");
        }
        String summary = strip(draft.summary());
        String details = strip(draft.details());
        if (summary.length() > SpecificationContent.MAX_SUMMARY) {
            violations.add("le résumé dépasse 4 000 caractères");
        }
        if (details.length() > SpecificationContent.MAX_DETAILS) {
            violations.add("les détails dépassent 20 000 caractères");
        }
        String title = strip(draft.title());
        if (title.length() > SpecificationContent.MAX_TITLE) {
            title = title.substring(0, SpecificationContent.MAX_TITLE);
        }
        List<SpecificationContent.AdrDeviation> deviations = draft.adrDeviations().stream()
                .filter(d -> d != null && !blank(d.adrRef()) && !blank(d.deviation()))
                .map(d -> new SpecificationContent.AdrDeviation(strip(d.adrRef()), strip(d.decision()),
                        strip(d.deviation())))
                .toList();
        SpecificationContent content = new SpecificationContent(title, summary, details, useCases, scenarios,
                assumptions, requirements, deviations);
        return new Assigned(content, new StructureViolations(violations));
    }

    private static SourceRef source(DraftUseCase d, String code, Map<String, UUID> known, List<String> violations) {
        if (d.source() == null) {
            violations.add(code + " : la source est obligatoire");
            return null;
        }
        Optional<SourceRef.Kind> kind = parse(SourceRef.Kind.class, d.source().kind());
        if (kind.isEmpty()) {
            violations.add(code + " : type de source inconnu (NEED_EXCERPT, ANSWER, ASSUMPTION, PROJECT_CONTEXT, JIRA_ISSUE,"
                    + " DOC_PAGE ou ARCH_ELEMENT)");
            return null;
        }
        if (!kind.get().knowledgeKinds().isEmpty()) {
            if (blank(d.source().ref())) {
                violations.add(code + " : une source " + kind.get() + " doit citer la référence de l'élément");
                return null;
            }
            return SourceRef.knowledge(kind.get(), strip(d.source().ref()), strip(d.source().excerpt()));
        }
        return new SourceRef(kind.get(), known.get(strip(d.source().questionId())), strip(d.source().excerpt()));
    }

    private static String skippedStatement(Question q, Language language) {
        return language == Language.EN
                ? "“" + q.label() + "”: " + q.answer().value() + " (default value, question skipped)"
                : "« " + q.label() + " » : " + q.answer().value() + " (valeur par défaut, question passée)";
    }

    private static <E extends Enum<E>> Optional<E> parse(Class<E> type, String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String strip(String value) {
        return value == null ? "" : value.strip();
    }

    /** Listes facultatives : les entrées vides sont retirées. */
    private static List<String> clean(List<String> values) {
        return values.stream().filter(v -> !blank(v)).map(String::strip).toList();
    }

    /** Listes contrôlées par la structure : les entrées vides sont gardées pour être signalées. */
    private static List<String> strips(List<String> values) {
        return values.stream().map(IdentifierAssigner::strip).toList();
    }
}
