package dev.specwriter.domain.specification;

import dev.specwriter.domain.knowledge.KnowledgeKind;
import dev.specwriter.domain.knowledge.ProjectKnowledge;
import dev.specwriter.domain.scenario.AcceptanceScenario;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Traçabilité (FR-011, FR-013) : chaque use case a une source vérifiable, une source hypothèse est
 * listée dans les hypothèses, chaque scénario référence un use case et chaque use case a un
 * scénario nominal (US2-3). Une source « contexte du projet » cite un extrait de la fiche générale,
 * une source de la connaissance un élément transmis, et un écart un ADR transmis (FR-010, FR-037 de
 * 002).
 */
public final class TraceabilityValidator {

    private TraceabilityValidator() {
    }

    public static StructureViolations validate(SpecificationContent content, Set<UUID> answeredQuestions) {
        return validate(content, answeredQuestions, ProjectKnowledge.EMPTY);
    }

    /**
     * @param knowledge connaissance transmise : une source PROJECT_CONTEXT cite un extrait exact de
     *     la fiche générale, une source JIRA_ISSUE, DOC_PAGE ou ARCH_ELEMENT un élément retenu
     */
    public static StructureViolations validate(SpecificationContent content, Set<UUID> answeredQuestions,
            ProjectKnowledge knowledge) {
        String context = normalized(knowledge.generalSheet());
        List<String> out = new ArrayList<>();
        if (content.useCases().isEmpty()) {
            out.add("la spécification doit contenir au moins un use case");
        }
        for (UseCase uc : content.useCases()) {
            SourceRef s = uc.source();
            if (s == null) {
                out.add(uc.code() + " : la source est obligatoire");
                continue;
            }
            switch (s.kind()) {
                case NEED_EXCERPT -> {
                    if (s.excerpt() == null || s.excerpt().isBlank()) {
                        out.add(uc.code() + " : l'extrait du besoin est obligatoire");
                    }
                }
                case ANSWER -> {
                    if (s.questionId() == null || !answeredQuestions.contains(s.questionId())) {
                        out.add(uc.code() + " : la source doit citer l'identifiant d'une question répondue");
                    }
                }
                case ASSUMPTION -> {
                    if (!listed(s, content.assumptions())) {
                        out.add(uc.code() + " : une source hypothèse doit figurer dans les hypothèses");
                    }
                }
                case PROJECT_CONTEXT -> {
                    String excerpt = normalized(s.excerpt());
                    if (excerpt.isEmpty() || !context.contains(excerpt)) {
                        out.add(uc.code()
                                + " : une source « contexte du projet » doit citer un extrait exact de la fiche générale");
                    }
                }
                case JIRA_ISSUE, DOC_PAGE, ARCH_ELEMENT -> {
                    if (!citable(knowledge, s.kind()).contains(s.ref())) {
                        out.add(uc.code() + " : une source " + s.kind() + " doit citer la référence d'un élément de"
                                + " <connaissance-projet>, recopiée exactement");
                    }
                }
            }
            boolean nominal = content.scenarios().stream()
                    .anyMatch(sc -> uc.code().equals(sc.useCaseCode()) && sc.kind() == AcceptanceScenario.Kind.NOMINAL);
            if (!nominal) {
                out.add(uc.code() + " : un scénario nominal au moins est attendu");
            }
        }
        Set<String> codes = content.useCases().stream().map(UseCase::code).collect(java.util.stream.Collectors.toSet());
        for (AcceptanceScenario sc : content.scenarios()) {
            if (!codes.contains(sc.useCaseCode())) {
                out.add(sc.code() + " : le scénario doit référencer un use case de la version");
            }
        }
        Set<String> adrs = knowledge.citable(KnowledgeKind.ADR);
        for (SpecificationContent.AdrDeviation d : content.adrDeviations()) {
            if (!adrs.contains(d.adrRef())) {
                out.add("écart à l'ADR « " + d.adrRef() + " » : l'ADR doit figurer dans <connaissance-projet>");
            }
        }
        return new StructureViolations(out);
    }

    private static Set<String> citable(ProjectKnowledge knowledge, SourceRef.Kind kind) {
        Set<String> refs = new java.util.HashSet<>();
        kind.knowledgeKinds().forEach(k -> refs.addAll(knowledge.citable(k)));
        return refs;
    }

    /** Casse, espacements et marques Markdown d'emphase ignorés. */
    private static String normalized(String text) {
        return text == null ? "" : text.replace("*", "").strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static boolean listed(SourceRef source, List<Assumption> assumptions) {
        String excerpt = source.excerpt() == null ? "" : source.excerpt().strip().toLowerCase(Locale.ROOT);
        return assumptions.stream().anyMatch(a -> (source.questionId() != null && source.questionId().equals(a.questionId()))
                || (!excerpt.isEmpty() && a.statement().toLowerCase(Locale.ROOT).contains(excerpt)));
    }
}
