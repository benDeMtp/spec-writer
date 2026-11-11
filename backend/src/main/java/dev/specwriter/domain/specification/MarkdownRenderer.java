package dev.specwriter.domain.specification;

import dev.specwriter.domain.attachment.Attachment;
import dev.specwriter.domain.knowledge.ArchScope;
import dev.specwriter.domain.knowledge.ArchitectureModel.DeploymentFact;
import dev.specwriter.domain.knowledge.ArchitectureModel.Exchange;
import dev.specwriter.domain.knowledge.KnowledgeSelection;
import dev.specwriter.domain.scenario.AcceptanceScenario;
import dev.specwriter.domain.session.Language;
import dev.specwriter.domain.sources.Occurrence;
import dev.specwriter.domain.sources.PerimeterSnapshot;
import dev.specwriter.domain.sources.Qualification;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
            String source, String needExcerpt, String answer, String assumption, String projectContext, String asA,
            String given,
            String when, String then, String and, String none, String colon, String ticket, String page, String element, String architecture, String modules,
            String exchanges, String own, String common, String missing, String existing, String via,
            String deviations, String decision, String deviation, String efforts, String notDeployed,
            String multiple, String cross, String shared, String attachment) {
    }

    private static final Labels FR = new Labels("Résumé", "Détails", "Use cases", "Scénarios d'acceptation", "Hypothèses",
            "Exigences fonctionnelles", "Exigences techniques", "Acteur", "Objectif", "Préconditions",
            "Déroulement nominal", "Alternatives", "Source", "extrait du besoin", "réponse", "Hypothèse", "contexte du projet",
            "En tant que", "Étant donné", "Quand", "Alors", "Et", "Aucune.", " : ",
            "ticket", "page", "élément d'architecture", "Architecture", "Modules impactés", "Flux", "propre au projet",
            "commun", "flux manquant, à créer", "flux existant", "via", "Écarts aux ADR", "Décision", "Écart",
            "Efforts supplémentaires (déploiement)", "aucune instance en production, à déployer",
            "plusieurs instances en production", "traverse des infrastructures (ouverture réseau ou exposition à prévoir)",
            "dépend d'une infrastructure partagée", "document joint");
    private static final Labels EN = new Labels("Summary", "Details", "Use cases", "Acceptance scenarios", "Assumptions",
            "Functional requirements", "Technical requirements", "Actor", "Goal", "Preconditions", "Main flow",
            "Alternatives", "Source", "need excerpt", "answer", "Assumption", "project context", "As a", "Given", "When", "Then", "And",
            "None.", ": ", "ticket", "page", "architecture element", "Architecture",
            "Impacted modules", "Flows", "project-specific", "shared", "missing flow, to be created", "existing flow", "via",
            "ADR deviations", "Decision", "Deviation", "Additional effort (deployment)",
            "no production instance, to be deployed", "several production instances",
            "crosses infrastructures (network opening or exposure to plan)", "depends on a shared infrastructure",
            "attached document");

    /** Rubrique « Modules touchés » (013, US5). */
    private record PerimeterLabels(String title, String impacted, String toCheck, String notImpacted, String incomplete) {
    }

    private static final PerimeterLabels PERIMETER_FR = new PerimeterLabels("Modules touchés", "Impactés", "À vérifier",
            "Non impactés", "L'analyse du périmètre est incomplète : des modules peuvent manquer.");
    private static final PerimeterLabels PERIMETER_EN = new PerimeterLabels("Affected modules", "Impacted", "To check",
            "Not impacted", "The perimeter analysis is incomplete: some modules may be missing.");

    private MarkdownRenderer() {
    }

    /**
     * Ancre, dans l'écran de la spécification, de chaque rubrique de niveau 2, dans les deux langues
     * du rendu (011, sections de la spécification discutée).
     */
    static Map<String, String> anchors() {
        Map<String, String> out = new HashMap<>();
        for (Labels l : List.of(FR, EN)) {
            out.put(l.summary(), "summary");
            out.put(l.details(), "details");
            out.put(l.useCases(), "use-cases");
            out.put(l.scenarios(), "scenarios");
            out.put(l.assumptions(), "assumptions");
            out.put(l.functional(), "functional");
            out.put(l.technical(), "technical");
            out.put(l.architecture(), "architecture");
            out.put((l == FR ? PERIMETER_FR : PERIMETER_EN).title(), "perimeter");
            out.put(l.deviations(), "deviations");
            out.put(l.efforts(), "efforts");
        }
        return Map.copyOf(out);
    }

    public static String render(SpecificationContent spec, Language language) {
        return render(spec, language, KnowledgeSelection.EMPTY);
    }

    /**
     * Rendu avec les faits d'architecture calculés : les rubriques « Architecture » et « Efforts
     * supplémentaires (déploiement) » viennent de {@code knowledge}, jamais du LLM (FR-039 à FR-041).
     * Une rubrique vide est omise : sans connaissance, le rendu est celui de 001.
     */
    public static String render(SpecificationContent spec, Language language, KnowledgeSelection knowledge) {
        return render(spec, language, knowledge, null);
    }

    /**
     * Rendu avec le périmètre repris de la discussion (013, US5) : la rubrique « Modules touchés »
     * vient de l'instantané, jamais du LLM, et suit « Architecture ». Sans instantané, elle est omise.
     */
    public static String render(SpecificationContent spec, Language language, KnowledgeSelection knowledge,
            PerimeterSnapshot perimeter) {
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
                if (!uc.source().attachment() && uc.source().kind() != SourceRef.Kind.DISCUSSION
                        && uc.source().excerpt() != null && !uc.source().excerpt().isBlank()) {
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
        KnowledgeSelection k = knowledge == null ? KnowledgeSelection.EMPTY : knowledge;
        architecture(md, k, l);
        perimeter(md, perimeter, language);
        deviations(md, spec.adrDeviations(), l);
        efforts(md, k.deploymentFacts(), l);
        return md.toString().stripTrailing() + "\n";
    }

    private static void architecture(StringBuilder md, KnowledgeSelection k, Labels l) {
        if (k.modules().isEmpty() && k.exchanges().isEmpty()) {
            return;
        }
        md.append("## ").append(l.architecture()).append("\n\n");
        if (!k.modules().isEmpty()) {
            md.append("### ").append(l.modules()).append("\n\n");
            k.modules().forEach(m -> md.append("- ").append(escape(m.name())).append(" *(")
                    .append(m.scope() == ArchScope.PROJECT ? l.own() : l.common()).append(")*\n"));
            md.append('\n');
        }
        if (!k.exchanges().isEmpty()) {
            md.append("### ").append(l.exchanges()).append("\n\n");
            for (Exchange x : k.exchanges()) {
                md.append("- ").append(escape(x.source())).append(" → ").append(escape(x.destination()))
                        .append(l.colon());
                if (x.kind() == Exchange.Kind.MISSING) {
                    md.append("**").append(l.missing()).append("**");
                } else {
                    md.append(l.existing());
                    if (x.via() != null) {
                        md.append(", ").append(l.via()).append(' ').append(escape(x.via()));
                    }
                }
                md.append('\n');
            }
            md.append('\n');
        }
    }

    private static void perimeter(StringBuilder md, PerimeterSnapshot perimeter, Language language) {
        if (perimeter == null || perimeter.modules().isEmpty()) {
            return;
        }
        PerimeterLabels p = language == Language.EN ? PERIMETER_EN : PERIMETER_FR;
        String colon = language == Language.EN ? EN.colon() : FR.colon();
        md.append("## ").append(p.title()).append("\n\n");
        if (perimeter.incomplete()) {
            md.append(escape(p.incomplete())).append("\n\n");
        }
        perimeterGroup(md, p.impacted(), perimeter, e -> e.qualification() == Qualification.IMPACTED, language, colon);
        perimeterGroup(md, p.toCheck(), perimeter, PerimeterSnapshot.Entry::toCheck, language, colon);
        perimeterGroup(md, p.notImpacted(), perimeter, e -> e.qualification() == Qualification.NOT_IMPACTED, language,
                colon);
    }

    private static void perimeterGroup(StringBuilder md, String title, PerimeterSnapshot perimeter,
            java.util.function.Predicate<PerimeterSnapshot.Entry> filter, Language language, String colon) {
        List<PerimeterSnapshot.Entry> entries = perimeter.modules().stream().filter(filter).toList();
        if (entries.isEmpty()) {
            return;
        }
        md.append("### ").append(title).append("\n\n");
        for (PerimeterSnapshot.Entry e : entries) {
            String where = e.path().isEmpty() ? e.rootLabel() : e.rootLabel() + "/" + e.path();
            md.append("- **").append(escape(e.name())).append("** (").append(escape(where)).append(')');
            if (e.justification() != null && !e.justification().isBlank()) {
                md.append(colon).append(escape(e.justification(), ITEM + ITEM));
            }
            md.append('\n');
            e.reasons().forEach(r -> md.append(ITEM).append("- ").append(escape(PerimeterSnapshot.reason(r, language)))
                    .append('\n'));
            for (Occurrence o : e.occurrences()) {
                md.append(ITEM).append("- ").append(escape(o.path() + ":" + o.line())).append('\n');
            }
        }
        md.append('\n');
    }

    private static void deviations(StringBuilder md, List<SpecificationContent.AdrDeviation> deviations, Labels l) {
        if (deviations.isEmpty()) {
            return;
        }
        md.append("## ").append(l.deviations()).append("\n\n");
        for (SpecificationContent.AdrDeviation d : deviations) {
            md.append("- **").append(escape(d.adrRef())).append("**\n");
            if (d.decision() != null && !d.decision().isBlank()) {
                md.append("  - ").append(l.decision()).append(l.colon()).append(escape(d.decision(), "    "))
                        .append('\n');
            }
            md.append("  - ").append(l.deviation()).append(l.colon()).append(escape(d.deviation(), "    "))
                    .append('\n');
        }
        md.append('\n');
    }

    private static void efforts(StringBuilder md, List<DeploymentFact> facts, Labels l) {
        if (facts.isEmpty()) {
            return;
        }
        md.append("## ").append(l.efforts()).append("\n\n");
        for (DeploymentFact f : facts) {
            md.append("- ").append(escape(f.element()));
            if (f.other() != null) {
                md.append(" → ").append(escape(f.other()));
            }
            md.append(l.colon()).append(switch (f.kind()) {
                case NOT_DEPLOYED -> l.notDeployed();
                case MULTIPLE_INSTANCES -> l.multiple();
                case CROSS_INFRASTRUCTURE -> l.cross();
                case SHARED_INFRASTRUCTURE -> l.shared();
            }).append('\n');
        }
        md.append('\n');
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
            case PROJECT_CONTEXT -> l.projectContext();
            case JIRA_ISSUE -> l.ticket() + " " + escape(source.ref());
            case DOC_PAGE -> source.attachment()
                    ? l.attachment() + " " + escape(source.ref().substring(Attachment.REF_PREFIX.length()))
                    : l.page() + " " + escape(source.ref());
            case ARCH_ELEMENT -> l.element() + " " + escape(source.ref());
            // titre « Discussion du <date>, message n » donné par le code (011, research.md R4)
            case DISCUSSION -> escape(source.excerpt() == null || source.excerpt().isBlank()
                    ? source.ref() : source.excerpt());
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
