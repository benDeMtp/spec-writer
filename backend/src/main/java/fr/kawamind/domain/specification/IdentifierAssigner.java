package fr.kawamind.domain.specification;

import fr.kawamind.domain.llm.DraftSpecification;
import fr.kawamind.domain.llm.DraftSpecification.DraftAssumption;
import fr.kawamind.domain.llm.DraftSpecification.DraftRequirement;
import fr.kawamind.domain.llm.DraftSpecification.DraftScenario;
import fr.kawamind.domain.llm.DraftSpecification.DraftUseCase;
import fr.kawamind.domain.question.Question;
import fr.kawamind.domain.scenario.AcceptanceScenario;
import fr.kawamind.domain.session.Language;
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
        List<String> draftAnchors = new ArrayList<>();
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
                        skipped ? questionId : null, null, d.fromCode()));
                draftAnchors.add(d.anchor());
            }
        }
        int modelAssumptions = assumptions.size();
        for (Question q : treated) {
            if (q.answer() != null && q.answer().assumption() && !covered.contains(q.id())) {
                assumptions.add(new Assumption("H-%02d".formatted(assumptions.size() + 1),
                        skippedStatement(q, language), Assumption.Origin.SKIPPED_QUESTION, q.id()));
            }
        }

        List<Requirement> requirements = new ArrayList<>();
        List<String> requirementCodes = new ArrayList<>();
        int functional = 0;
        int technical = 0;
        for (DraftRequirement d : draft.requirements()) {
            Optional<Requirement.Category> category = parse(Requirement.Category.class, d.category());
            if (category.isEmpty() || blank(d.statement())) {
                violations.add("exigence : catégorie FUNCTIONAL ou TECHNICAL et énoncé obligatoires");
                requirementCodes.add(null);
                continue;
            }
            String code = category.get() == Requirement.Category.FUNCTIONAL
                    ? "EF-%02d".formatted(++functional)
                    : "ET-%02d".formatted(++technical);
            requirements.add(new Requirement(code, category.get(), strip(d.statement())));
            requirementCodes.add(code);
        }
        Anchors anchors = new Anchors(useCases, scenarios, requirementCodes);

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
        // écarts entre sources (016) : références telles que citées, complétées et filtrées à la validation
        List<DraftSpecification.DraftSourceDeviation> keptDeviations = draft.sourceDeviations().stream()
                .filter(d -> d != null && !blank(d.referenceRef()) && !blank(d.interpretationRef())
                        && !blank(d.difference()) && strip(d.difference()).length() <= SourceDeviation.MAX_DIFFERENCE)
                .limit(SourceDeviation.MAX_PER_VERSION)
                .toList();
        List<String> deviationAnchors = keptDeviations.stream().map(DraftSpecification.DraftSourceDeviation::anchor)
                .toList();
        List<SourceDeviation> sourceDeviations = keptDeviations.stream()
                .map(d -> new SourceDeviation(new SourceDeviation.Cited(strip(d.referenceRef()), null, null, null),
                        new SourceDeviation.Cited(strip(d.interpretationRef()), null, null, null), d.difference()))
                .toList();
        // ancres (021) : traduites en codes une fois tous les éléments codés ; hors limites, nulles
        List<Assumption> anchored = new ArrayList<>();
        for (int i = 0; i < assumptions.size(); i++) {
            Assumption a = assumptions.get(i);
            anchored.add(i < modelAssumptions ? a.withAnchor(anchors.of(draftAnchors.get(i))) : a);
        }
        List<SourceDeviation> anchoredDeviations = new ArrayList<>();
        for (int i = 0; i < sourceDeviations.size(); i++) {
            anchoredDeviations.add(sourceDeviations.get(i).withAnchor(anchors.of(deviationAnchors.get(i))));
        }
        SpecificationContent content = new SpecificationContent(title, summary, details, useCases, scenarios,
                anchored, requirements, deviations, draft.story(), draft.splitProposal(), anchoredDeviations,
                openQuestions(draft.openQuestions(), anchors));
        return new Assigned(content, new StructureViolations(violations));
    }

    /**
     * Points ouverts d'une révision (021), codés {@code Q-nn} dans l'ordre : une question vide ou trop
     * longue est écartée, un responsable trop long oublié ; 50 au plus.
     */
    private static List<OpenQuestion> openQuestions(List<DraftSpecification.DraftOpenQuestion> drafts,
            Anchors anchors) {
        List<OpenQuestion> out = new ArrayList<>();
        for (DraftSpecification.DraftOpenQuestion d : drafts) {
            if (d == null || blank(d.question()) || strip(d.question()).length() > OpenQuestion.MAX_QUESTION
                    || out.size() == SpecificationContent.MAX_OPEN_QUESTIONS) {
                continue;
            }
            String owner = strip(d.owner()).length() > OpenQuestion.MAX_OWNER ? null : d.owner();
            out.add(new OpenQuestion("Q-%02d".formatted(out.size() + 1), d.question(), owner, null,
                    anchors.of(d.anchor()), d.fromCode()));
        }
        return out;
    }

    /**
     * Traduction d'une ancre de la sortie ({@code USE_CASE:1:4}, {@code SCENARIO:0}…) en ancre codée
     * (021, R1) ; nulle si elle est vide, mal formée ou hors limites : le point reste, non rattaché.
     */
    record Anchors(List<UseCase> useCases, List<AcceptanceScenario> scenarios, List<String> requirements) {

        Anchor of(String raw) {
            if (blank(raw)) {
                return null;
            }
            String[] parts = raw.strip().toUpperCase(Locale.ROOT).split(":");
            try {
                Anchor anchor = switch (parts[0]) {
                    case "SUMMARY" -> parts.length == 1 ? new Anchor(Anchor.SUMMARY, null, false) : null;
                    case "USE_CASE" -> useCase(parts);
                    case "SCENARIO" -> parts.length == 2 ? new Anchor(scenarios.get(index(parts[1])).code(), null, false)
                            : null;
                    case "REQUIREMENT" -> parts.length == 2 && requirements.get(index(parts[1])) != null
                            ? new Anchor(requirements.get(index(parts[1])), null, false) : null;
                    default -> null;
                };
                return anchor == null || !valid(anchor) ? null : anchor;
            } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
                return null;
            }
        }

        private Anchor useCase(String[] parts) {
            if (parts.length < 2 || parts.length > 3) {
                return null;
            }
            String code = useCases.get(index(parts[1])).code();
            if (parts.length == 2) {
                return new Anchor(code, null, false);
            }
            boolean alternative = parts[2].startsWith("A");
            return new Anchor(code, Integer.parseInt(alternative ? parts[2].substring(1) : parts[2]), alternative);
        }

        /** L'étape existe dans le use case codé. */
        private boolean valid(Anchor a) {
            if (a.step() == null) {
                return true;
            }
            return useCases.stream().filter(uc -> uc.code().equals(a.element())).findFirst()
                    .map(uc -> a.step() >= 1 && a.step() <= (a.alternative() ? uc.alternatives() : uc.nominalFlow()).size())
                    .orElse(false);
        }

        private static int index(String raw) {
            int i = Integer.parseInt(raw);
            if (i < 0) {
                throw new IndexOutOfBoundsException(i);
            }
            return i;
        }
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
        if (kind.get().byRef()) {
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
