package dev.specwriter.domain.specification;

import dev.specwriter.domain.scenario.AcceptanceScenario;
import dev.specwriter.domain.session.Language;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Rendu Markdown déterministe d'une version (export FR-019, indexation, Jira). Le texte vient du
 * besoin, des réponses ou du LLM : il est échappé (edge case « caractères spéciaux »).
 */
public final class MarkdownRenderer {

    private static final Pattern SPECIAL = Pattern.compile("([\\\\`*_{}\\[\\]<>#|!~])");

    private record Labels(String summary, String useCases, String scenarios, String assumptions, String functional,
            String technical, String actor, String goal, String preconditions, String flow, String alternatives,
            String source, String needExcerpt, String answer, String assumption, String asA, String given,
            String when, String then, String and, String none) {
    }

    private static final Labels FR = new Labels("Résumé", "Use cases", "Scénarios d'acceptation", "Hypothèses",
            "Exigences fonctionnelles", "Exigences techniques", "Acteur", "Objectif", "Préconditions",
            "Déroulement nominal", "Alternatives", "Source", "extrait du besoin", "réponse", "Hypothèse",
            "En tant que", "Étant donné", "Quand", "Alors", "Et", "Aucune.");
    private static final Labels EN = new Labels("Summary", "Use cases", "Acceptance scenarios", "Assumptions",
            "Functional requirements", "Technical requirements", "Actor", "Goal", "Preconditions", "Main flow",
            "Alternatives", "Source", "need excerpt", "answer", "Assumption", "As a", "Given", "When", "Then", "And",
            "None.");

    private MarkdownRenderer() {
    }

    public static String render(SpecificationContent spec, Language language) {
        Labels l = language == Language.EN ? EN : FR;
        StringBuilder md = new StringBuilder();
        md.append("# ").append(escape(spec.title())).append("\n\n");
        md.append("## ").append(l.summary()).append("\n\n").append(escape(spec.summary())).append("\n\n");

        md.append("## ").append(l.useCases()).append("\n\n");
        for (UseCase uc : spec.useCases()) {
            md.append("### ").append(uc.code()).append(" : ").append(escape(uc.title())).append("\n\n");
            md.append("- **").append(l.actor()).append("** : ").append(escape(uc.actor())).append('\n');
            md.append("- **").append(l.goal()).append("** : ").append(escape(uc.goal())).append('\n');
            list(md, l.preconditions(), uc.preconditions(), false);
            list(md, l.flow(), uc.nominalFlow(), true);
            list(md, l.alternatives(), uc.alternatives(), false);
            if (uc.source() != null) {
                md.append("- **").append(l.source()).append("** : ").append(sourceLabel(uc.source(), l));
                if (uc.source().excerpt() != null && !uc.source().excerpt().isBlank()) {
                    md.append(" « ").append(escape(uc.source().excerpt())).append(" »");
                }
                md.append('\n');
            }
            md.append('\n');
        }

        md.append("## ").append(l.scenarios()).append("\n\n");
        for (UseCase uc : spec.useCases()) {
            List<AcceptanceScenario> scenarios = spec.scenarios().stream()
                    .filter(s -> uc.code().equals(s.useCaseCode())).toList();
            if (scenarios.isEmpty()) {
                continue;
            }
            md.append("### ").append(uc.code()).append(" : ").append(escape(uc.title())).append("\n\n");
            for (AcceptanceScenario s : scenarios) {
                scenario(md, s, l);
            }
        }

        md.append("## ").append(l.assumptions()).append("\n\n");
        if (spec.assumptions().isEmpty()) {
            md.append(l.none()).append("\n\n");
        } else {
            spec.assumptions().forEach(a -> md.append("- **").append(a.code()).append("** : ")
                    .append(escape(a.statement())).append('\n'));
            md.append('\n');
        }
        requirements(md, l.functional(), spec.requirements(), Requirement.Category.FUNCTIONAL, l);
        requirements(md, l.technical(), spec.requirements(), Requirement.Category.TECHNICAL, l);
        return md.toString().stripTrailing() + "\n";
    }

    /** Rendu d'un scénario, conforme à l'exemple de contracts/scenario-format.md. */
    static void scenario(StringBuilder md, AcceptanceScenario s, Labels l) {
        md.append("#### ").append(s.code()).append(" : ").append(escape(s.title()));
        if (s.assumption()) {
            md.append(" *(").append(l.assumption()).append(")*");
        }
        md.append('\n');
        md.append("- **").append(l.asA()).append("** ").append(escape(s.asA())).append('\n');
        steps(md, l.given(), s.given(), l);
        steps(md, l.when(), s.when(), l);
        steps(md, l.then(), s.then(), l);
        md.append('\n');
    }

    private static void steps(StringBuilder md, String keyword, List<String> entries, Labels l) {
        for (int i = 0; i < entries.size(); i++) {
            md.append("- **").append(i == 0 ? keyword : l.and()).append("** ").append(escape(entries.get(i))).append('\n');
        }
    }

    private static void list(StringBuilder md, String label, List<String> items, boolean ordered) {
        if (items.isEmpty()) {
            return;
        }
        md.append("- **").append(label).append("** :\n");
        for (int i = 0; i < items.size(); i++) {
            md.append("  ").append(ordered ? (i + 1) + ". " : "- ").append(escape(items.get(i))).append('\n');
        }
    }

    private static void requirements(StringBuilder md, String title, List<Requirement> all, Requirement.Category category,
            Labels l) {
        md.append("## ").append(title).append("\n\n");
        List<Requirement> selected = all.stream().filter(r -> r.category() == category).toList();
        if (selected.isEmpty()) {
            md.append(l.none()).append("\n\n");
            return;
        }
        selected.forEach(r -> md.append("- **").append(r.code()).append("** : ").append(escape(r.statement())).append('\n'));
        md.append('\n');
    }

    private static String sourceLabel(SourceRef source, Labels l) {
        return switch (source.kind()) {
            case NEED_EXCERPT -> l.needExcerpt();
            case ANSWER -> l.answer();
            case ASSUMPTION -> l.assumption().toLowerCase(java.util.Locale.ROOT);
        };
    }

    /** Échappe les caractères Markdown ; les retours à la ligne deviennent des espaces. */
    public static String escape(String text) {
        if (text == null) {
            return "";
        }
        return SPECIAL.matcher(text.replaceAll("\\s*\\R\\s*", " ")).replaceAll("\\\\$1");
    }
}
