package dev.specwriter.domain.specification;

import dev.specwriter.domain.scenario.AcceptanceScenario;
import dev.specwriter.domain.session.Language;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Rendu Markdown déterministe d'une version (export FR-019, indexation, Jira). Le texte vient du
 * besoin, des réponses ou du LLM : il est échappé (edge case « caractères spéciaux »), en gardant
 * ses retours à la ligne (FR-012c). Le résumé et les détails sont du Markdown (FR-012b) : seuls
 * leurs titres de niveau 1 et 2 sont rétrogradés, pour ne pas casser la structure du document.
 */
public final class MarkdownRenderer {

    private static final Pattern SPECIAL = Pattern.compile("([\\\\`*_{}\\[\\]<>#|!~])");
    private static final Pattern LINE_BREAK = Pattern.compile("[ \\t]*\\R\\s*");
    /** Début de ligne qui ouvrirait une liste ou un titre souligné après un saut de ligne. */
    private static final Pattern BLOCK_START = Pattern.compile("^(\\d+)([.)])|^([-+=])");
    private static final Pattern TOP_HEADING = Pattern.compile("^ {0,3}#{1,2}(?=\\s|$)");
    private static final Pattern FENCE = Pattern.compile("^ {0,3}(```|~~~)");
    private static final Pattern SETEXT_EQUALS = Pattern.compile("^ {0,3}=+\\s*$");
    private static final Pattern SETEXT_DASHES = Pattern.compile("^ {0,3}-+\\s*$");
    private static final String ITEM = "  ";

    private record Labels(String summary, String details, String useCases, String scenarios, String assumptions, String functional,
            String technical, String actor, String goal, String preconditions, String flow, String alternatives,
            String source, String needExcerpt, String answer, String assumption, String asA, String given,
            String when, String then, String and, String none, String colon) {
    }

    private static final Labels FR = new Labels("Résumé", "Détails", "Use cases", "Scénarios d'acceptation", "Hypothèses",
            "Exigences fonctionnelles", "Exigences techniques", "Acteur", "Objectif", "Préconditions",
            "Déroulement nominal", "Alternatives", "Source", "extrait du besoin", "réponse", "Hypothèse",
            "En tant que", "Étant donné", "Quand", "Alors", "Et", "Aucune.", " : ");
    private static final Labels EN = new Labels("Summary", "Details", "Use cases", "Acceptance scenarios", "Assumptions",
            "Functional requirements", "Technical requirements", "Actor", "Goal", "Preconditions", "Main flow",
            "Alternatives", "Source", "need excerpt", "answer", "Assumption", "As a", "Given", "When", "Then", "And",
            "None.", ": ");

    private MarkdownRenderer() {
    }

    public static String render(SpecificationContent spec, Language language) {
        Labels l = language == Language.EN ? EN : FR;
        StringBuilder md = new StringBuilder();
        md.append("# ").append(inline(spec.title())).append("\n\n");
        md.append("## ").append(l.summary()).append("\n\n").append(rich(spec.summary())).append("\n\n");
        if (!spec.details().isBlank()) {
            md.append("## ").append(l.details()).append("\n\n").append(rich(spec.details())).append("\n\n");
        }

        md.append("## ").append(l.useCases()).append("\n\n");
        for (UseCase uc : spec.useCases()) {
            md.append("### ").append(uc.code()).append(l.colon()).append(inline(uc.title())).append("\n\n");
            md.append("- **").append(l.actor()).append("**" + l.colon()).append(escape(uc.actor())).append('\n');
            md.append("- **").append(l.goal()).append("**" + l.colon()).append(escape(uc.goal())).append('\n');
            list(md, l.preconditions(), uc.preconditions(), false, l);
            list(md, l.flow(), uc.nominalFlow(), true, l);
            list(md, l.alternatives(), uc.alternatives(), false, l);
            if (uc.source() != null) {
                md.append("- **").append(l.source()).append("**" + l.colon()).append(sourceLabel(uc.source(), l));
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
            md.append("### ").append(uc.code()).append(l.colon()).append(inline(uc.title())).append("\n\n");
            for (AcceptanceScenario s : scenarios) {
                scenario(md, s, l);
            }
        }

        md.append("## ").append(l.assumptions()).append("\n\n");
        if (spec.assumptions().isEmpty()) {
            md.append(l.none()).append("\n\n");
        } else {
            spec.assumptions().forEach(a -> md.append("- **").append(a.code()).append("**" + l.colon())
                    .append(escape(a.statement())).append('\n'));
            md.append('\n');
        }
        requirements(md, l.functional(), spec.requirements(), Requirement.Category.FUNCTIONAL, l);
        requirements(md, l.technical(), spec.requirements(), Requirement.Category.TECHNICAL, l);
        return md.toString().stripTrailing() + "\n";
    }

    /** Rendu d'un scénario, conforme à l'exemple de contracts/scenario-format.md. */
    static void scenario(StringBuilder md, AcceptanceScenario s, Labels l) {
        md.append("#### ").append(s.code()).append(l.colon()).append(inline(s.title()));
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

    private static void list(StringBuilder md, String label, List<String> items, boolean ordered, Labels l) {
        if (items.isEmpty()) {
            return;
        }
        md.append("- **").append(label).append("**").append(l.colon().stripTrailing()).append('\n');
        for (int i = 0; i < items.size(); i++) {
            String marker = ordered ? (i + 1) + ". " : "- ";
            md.append("  ").append(marker).append(escape(items.get(i), " ".repeat(2 + marker.length()))).append('\n');
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
        selected.forEach(r -> md.append("- **").append(r.code()).append("**" + l.colon()).append(escape(r.statement())).append('\n'));
        md.append('\n');
    }

    private static String sourceLabel(SourceRef source, Labels l) {
        return switch (source.kind()) {
            case NEED_EXCERPT -> l.needExcerpt();
            case ANSWER -> l.answer();
            case ASSUMPTION -> l.assumption().toLowerCase(java.util.Locale.ROOT);
        };
    }

    /** Échappe les caractères Markdown d'un élément de liste ; un retour à la ligne devient un saut forcé. */
    public static String escape(String text) {
        return escape(text, ITEM);
    }

    /**
     * Échappe les caractères Markdown ; chaque retour à la ligne devient un saut de ligne forcé
     * ({@code \} en fin de ligne), suivi de {@code indent} pour rester dans l'élément de liste.
     */
    static String escape(String text, String indent) {
        if (text == null) {
            return "";
        }
        String[] lines = LINE_BREAK.split(text.strip(), -1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = SPECIAL.matcher(lines[i]).replaceAll("\\\\$1");
            if (i > 0) {
                out.append("\\\n").append(indent);
                line = BLOCK_START.matcher(line).replaceFirst(m -> m.group(1) != null
                        ? m.group(1) + "\\\\" + m.group(2) : "\\\\" + m.group(3));
            }
            out.append(line);
        }
        return out.toString();
    }

    /** Titre : une seule ligne. */
    private static String inline(String text) {
        return text == null ? "" : SPECIAL.matcher(text.strip().replaceAll("\\s*\\R\\s*", " ")).replaceAll("\\\\$1");
    }

    /**
     * Texte riche (FR-012b) : Markdown conservé, hors blocs de code, titres {@code #} et {@code ##}
     * rétrogradés en {@code ####}, soulignés de titre neutralisés (un {@code ---} devient un séparateur).
     */
    private static String rich(String markdown) {
        if (markdown == null) {
            return "";
        }
        String[] lines = markdown.strip().split("\\R", -1);
        StringBuilder out = new StringBuilder();
        boolean code = false;
        for (String line : lines) {
            if (FENCE.matcher(line).find()) {
                code = !code;
            } else if (!code) {
                if (TOP_HEADING.matcher(line).find()) {
                    line = TOP_HEADING.matcher(line).replaceFirst("####");
                } else if (SETEXT_EQUALS.matcher(line).matches()) {
                    line = "\\" + line.strip();
                } else if (SETEXT_DASHES.matcher(line).matches() && line.strip().length() < 3) {
                    line = "\\" + line.strip();
                } else if (SETEXT_DASHES.matcher(line).matches() && !out.isEmpty() && !out.toString().endsWith("\n\n")) {
                    out.append('\n');
                }
            }
            out.append(line).append('\n');
        }
        return out.toString().stripTrailing();
    }
}
