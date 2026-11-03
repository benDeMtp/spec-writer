package dev.specwriter.domain.specification;

import dev.specwriter.domain.scenario.AcceptanceScenario;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Traçabilité (FR-011, FR-013) : chaque use case a une source vérifiable, une source hypothèse est
 * listée dans les hypothèses, chaque scénario référence un use case et chaque use case a un
 * scénario nominal (US2-3). Une source « contexte du projet » cite un extrait du contexte utilisé
 * (FR-010 de 002).
 */
public final class TraceabilityValidator {

    private TraceabilityValidator() {
    }

    public static StructureViolations validate(SpecificationContent content, Set<UUID> answeredQuestions) {
        return validate(content, answeredQuestions, "");
    }

    public static StructureViolations validate(SpecificationContent content, Set<UUID> answeredQuestions,
            String projectContext) {
        String context = normalized(projectContext);
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
                                + " : une source « contexte du projet » doit citer un extrait exact du contexte du projet");
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
        return new StructureViolations(out);
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
