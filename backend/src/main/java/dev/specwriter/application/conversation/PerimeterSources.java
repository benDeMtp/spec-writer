package dev.specwriter.application.conversation;

import dev.specwriter.domain.conversation.SourceKind;
import dev.specwriter.domain.sources.ModuleRef;
import dev.specwriter.domain.sources.PerimeterAnalysis;
import dev.specwriter.domain.sources.PerimeterModule;
import dev.specwriter.domain.sources.Reason;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Modules d'une analyse de périmètre transmis à la réponse comme sources citables (013, research.md
 * R7) : nom, chemin, qualification, justification et raisons, sans aucun extrait de code.
 */
final class PerimeterSources {

    static final String PREFIX = "module:";

    private PerimeterSources() {
    }

    static List<ConversationSearch.Found> of(PerimeterAnalysis analysis) {
        String state = analysis.state() == PerimeterAnalysis.State.COMPLETE ? "complète"
                : "incomplète (" + analysis.incompleteReason() + ")";
        return analysis.modules().stream().map(m -> new ConversationSearch.Found(SourceKind.MODULE,
                PREFIX + m.module().id(), title(m), text(m, state))).toList();
    }

    static String title(PerimeterModule m) {
        return m.module().path().isEmpty() ? m.module().name() : m.module().name() + " (" + m.module().path() + ")";
    }

    private static String text(PerimeterModule m, String state) {
        return "Module : " + m.module().name() + " (" + m.module().kind() + ")\n"
                + "Qualification : " + label(m) + "\n"
                + (m.justification() == null ? "" : "Justification : " + m.justification() + "\n")
                + "Raisons : " + m.reasons().stream().map(PerimeterSources::reason).collect(Collectors.joining(" ; "))
                + "\n" + "Analyse : " + state;
    }

    private static String label(PerimeterModule m) {
        return switch (m.qualification()) {
            case IMPACTED -> "impacté";
            case TO_CHECK -> "à vérifier";
            case NOT_IMPACTED -> "non impacté";
            case UNQUALIFIED -> "non qualifié";
        };
    }

    private static String reason(Reason r) {
        return switch (r) {
            case Reason.Term t -> t.term() + " ×" + t.count();
            case Reason.Dependency d -> "dépend de " + d.chain().stream().map(ModuleRef::name)
                    .collect(Collectors.joining(" → "));
            case Reason.Aggregator g -> "agrège " + g.of().name();
            case Reason.Architecture x -> "relié à " + x.related() + (x.technology() == null ? "" : " (" + x.technology() + ")");
        };
    }
}
