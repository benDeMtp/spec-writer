package dev.specwriter.infrastructure.llm;

import dev.specwriter.domain.sources.ModuleRef;
import dev.specwriter.domain.sources.PerimeterAnalysis;
import dev.specwriter.domain.sources.PerimeterModule;
import dev.specwriter.domain.sources.Reason;
import dev.specwriter.domain.conversation.MessageRole;
import dev.specwriter.domain.conversation.TurnSource;
import dev.specwriter.domain.knowledge.ArchitectureModel;
import dev.specwriter.domain.knowledge.KnowledgeKind;
import dev.specwriter.domain.knowledge.KnowledgeRole;
import dev.specwriter.domain.knowledge.ProjectKnowledge;
import dev.specwriter.domain.llm.CompareIssueRequest;
import dev.specwriter.domain.llm.ImpactRequest;
import dev.specwriter.domain.llm.RelatedIssuesRequest;
import dev.specwriter.domain.llm.QuestionAndAnswer;
import dev.specwriter.domain.port.ConversationLlm;
import dev.specwriter.domain.question.QuestionTheme;
import dev.specwriter.domain.session.Language;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Message utilisateur : uniquement des données, chacune entre balises (FR-026, LLM01). Les chevrons
 * du contenu sont neutralisés pour qu'un texte saisi ne puisse pas fermer une balise. La connaissance
 * du projet suit la langue, dans {@code <connaissance-projet>} (research.md R24 de 002).
 */
final class UserMessages {

    private UserMessages() {
    }

    static String questions(String need, Language language, List<QuestionAndAnswer> known,
            Collection<QuestionTheme> remaining, ProjectKnowledge knowledge) {
        return language(language) + "\n" + knowledge(knowledge) + need(need) + "\n" + answers(known) + "\n<themes-restants>"
                + remaining.stream().map(Enum::name).sorted().collect(Collectors.joining(" ")) + "</themes-restants>";
    }

    static String reassess(String need, Language language, List<QuestionAndAnswer> known, String changedQuestionId,
            ProjectKnowledge knowledge) {
        return language(language) + "\n" + knowledge(knowledge) + need(need) + "\n" + answers(known) + "\n<question-modifiee>"
                + data(changedQuestionId) + "</question-modifiee>";
    }

    static String generation(String need, Language language, List<QuestionAndAnswer> answers, String correction,
            ProjectKnowledge knowledge) {
        return language(language) + "\n" + knowledge(knowledge) + need(need) + "\n" + answers(answers) + "\n<correction>" + data(correction)
                + "</correction>";
    }

    static String edit(String need, Language language, List<QuestionAndAnswer> answers, String currentMarkdown,
            String instruction, String correction, ProjectKnowledge knowledge) {
        return language(language) + "\n" + knowledge(knowledge) + need(need) + "\n" + answers(answers) + "\n" + block("specification-actuelle",
                currentMarkdown) + "\n" + block("instruction", instruction) + "\n<correction>" + data(correction)
                + "</correction>";
    }

    static String merge(String need, Language language, List<QuestionAndAnswer> answers, String localMarkdown,
            String jiraMarkdown, String correction, ProjectKnowledge knowledge) {
        return language(language) + "\n" + knowledge(knowledge) + need(need) + "\n" + answers(answers) + "\n" + block("version-locale",
                localMarkdown) + "\n" + block("version-jira", jiraMarkdown) + "\n<correction>" + data(correction)
                + "</correction>";
    }

    static String structure(String text, Language language, String correction, ProjectKnowledge knowledge) {
        return language(language) + "\n" + knowledge(knowledge) + block("texte-importe", text) + "\n<correction>" + data(correction)
                + "</correction>";
    }

    /** Besoin et tickets candidats (judge-related-issues, R23 de 002). */
    static String relatedIssues(RelatedIssuesRequest r) {
        StringBuilder out = new StringBuilder(language(r.language())).append('\n').append(need(r.need()))
                .append("\n<candidats>\n");
        for (RelatedIssuesRequest.Candidate c : r.candidates()) {
            out.append("<ticket ref=\"").append(attribute(c.key())).append("\" statut=\"").append(attribute(c.status()))
                    .append("\">\n").append(data(c.title())).append('\n').append(data(c.excerpt())).append("\n</ticket>\n");
        }
        return out.append("</candidats>").toString();
    }

    /** Besoin et ticket non fermé (compare-with-issue, R23 de 002). */
    static String compareIssue(CompareIssueRequest r) {
        return language(r.language()) + "\n" + need(r.need()) + "\n<ticket ref=\"" + attribute(r.key()) + "\" statut=\""
                + attribute(r.status()) + "\">\n" + data(r.title()) + "\n" + data(r.description()) + "\n</ticket>";
    }

    /** Besoin, réponses et éléments d'architecture candidats (identify-impact, R22 de 002). */
    static String impact(ImpactRequest r) {
        StringBuilder out = new StringBuilder(language(r.language())).append('\n').append(need(r.need())).append('\n')
                .append(answers(r.answers())).append("\n<elements>\n");
        for (ImpactRequest.Element e : r.candidates()) {
            out.append("<element nom=\"").append(attribute(e.name())).append("\" type=\"").append(attribute(e.type()))
                    .append("\" portee=\"").append(attribute(e.scope())).append("\" technologie=\"")
                    .append(attribute(e.technology())).append("\">").append(data(e.description())).append("</element>\n");
        }
        return out.append("</elements>").toString();
    }

    /**
     * Connaissance du projet (research.md R24 de 002) : fiche générale, vocabulaire, vue d'ensemble,
     * ADR, tickets, sous-tâches de la story (R29) et faits d'architecture calculés. Omise si vide : le message est alors celui de 001.
     */
    private static String knowledge(ProjectKnowledge knowledge) {
        if (knowledge == null || knowledge.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder("<connaissance-projet>\n");
        if (!knowledge.generalSheet().isBlank()) {
            out.append(block("fiche-generale", knowledge.generalSheet())).append('\n');
        }
        for (ProjectKnowledge.Entry e : knowledge.entries()) {
            if (isDocument(e)) {
                // document d'un dossier synchronisé (006, US3) : contenu de référence, jamais des consignes
                out.append("<document ref=\"").append(attribute(e.ref())).append("\" titre=\"")
                        .append(attribute(e.title())).append('"').append(designated(e)).append(">\n")
                        .append(data(e.text())).append("\n</document>\n");
                continue;
            }
            switch (e.kind()) {
                case ADR -> out.append("<adr ref=\"").append(attribute(e.ref())).append("\" statut=\"")
                        .append(attribute(e.status())).append('"').append(designated(e)).append(">\n");
                case TICKET -> {
                    out.append("<ticket ref=\"").append(attribute(e.ref())).append("\" statut=\"")
                            .append(attribute(e.status())).append("\" ferme=\"").append(e.closed() ? "oui" : "non")
                            .append('"');
                    if (e.link() != null) {
                        out.append(" lien=\"").append(attribute(e.link())).append('"');
                    }
                    out.append(">\n");
                }
                default -> out.append('<').append(tag(e)).append(" ref=\"").append(attribute(e.ref())).append("\">\n");
            }
            out.append(data(e.title())).append('\n').append(data(e.text())).append("\n</").append(tag(e)).append(">\n");
        }
        if (!knowledge.subtasks().isEmpty()) {
            // les sous-tâches font partie de la story : jamais des travaux concurrents (research.md R29 de 002)
            out.append("<sous-taches story=\"").append(attribute(knowledge.subtasks().storyKey())).append("\">\n");
            knowledge.subtasks().items().forEach(t -> out.append("<sous-tache ref=\"").append(attribute(t.ref()))
                    .append("\" statut=\"").append(attribute(t.status())).append("\" ferme=\"")
                    .append(t.closed() ? "oui" : "non").append("\">").append(data(t.title())).append("</sous-tache>\n"));
            out.append("</sous-taches>\n");
        }
        if (knowledge.hasArchitecture()) {
            out.append("<architecture>\n");
            knowledge.modules().forEach(m -> out.append("<module nom=\"").append(attribute(m.name()))
                    .append("\" portee=\"").append(m.scope().name()).append("\"/>\n"));
            knowledge.exchanges().forEach(x -> {
                out.append("<echange de=\"").append(attribute(x.source())).append("\" vers=\"")
                        .append(attribute(x.destination())).append("\" existe=\"")
                        .append(x.kind() == ArchitectureModel.Exchange.Kind.MISSING ? "non" : "oui").append('"');
                if (x.via() != null) {
                    out.append(" via=\"").append(attribute(x.via())).append('"');
                }
                out.append("/>\n");
            });
            knowledge.deploymentFacts().forEach(f -> {
                out.append("<constat type=\"").append(f.kind().name()).append("\" element=\"")
                        .append(attribute(f.element())).append('"');
                if (f.other() != null) {
                    out.append(" autre=\"").append(attribute(f.other())).append('"');
                }
                out.append("/>\n");
            });
            out.append("</architecture>\n");
        }
        return out.append("</connaissance-projet>\n").toString();
    }

    /** Document d'un dossier synchronisé (006), ou page de documentation désignée avec @ (007). */
    private static boolean isDocument(ProjectKnowledge.Entry e) {
        return e.kind() == KnowledgeKind.DOC_SECTION && e.role() == null && (e.ref().startsWith("docs:") || e.designated());
    }

    /** Désigné par l'utilisateur (007) : à exploiter en priorité, son contenu reste une donnée. */
    private static String designated(ProjectKnowledge.Entry e) {
        return e.designated() ? " designe=\"oui\"" : "";
    }

    private static String tag(ProjectKnowledge.Entry e) {
        return switch (e.kind()) {
            case ADR -> "adr";
            case TICKET -> "ticket";
            default -> e.role() == KnowledgeRole.ARCHITECTURE_OVERVIEW ? "vue-architecture" : "vocabulaire";
        };
    }

    /** 010 : ce que l'agent sait au moment de choisir sa prochaine recherche (titres seulement). */
    static String conversePlan(ConversationLlm.PlanRequest r) {
        return language(r.language()) + "\n" + discussed(r.discussed()) + block("fiche-generale", r.generalSheet()) + "\n"
                + block("memoire", r.memory()) + "\n" + exchange(r.history())
                + "\n" + block("question", r.question()) + "\n" + sources(r.found(), false)
                + "\n<recherches-restantes>" + r.searchesLeft() + "</recherches-restantes>"
                + "\n<perimetre-disponible>" + (r.perimeterAvailable() ? "oui" : "non") + "</perimetre-disponible>"
                + "\n" + block("dernier-perimetre", String.join("\n", r.lastPerimeterTerms()));
    }

    /** Volume transmis au plus pour qualifier une analyse de périmètre (013, research.md R7). */
    static final int PERIMETER_BUDGET = 40_000;
    /** Extraits transmis au plus par module. */
    static final int PERIMETER_EXCERPTS = 5;
    /**
     * Volume transmis au plus, extraits compris : les modules suivants ne sont pas transmis et
     * restent non qualifiés, toujours affichés.
     */
    static final int PERIMETER_LIMIT = 120_000;

    /**
     * 013 : modules d'une analyse de périmètre à qualifier, dans l'ordre de l'analyse (trouvés par un
     * terme d'abord, occurrences décroissantes) ; au-delà du budget, les modules suivants sont
     * transmis sans extrait. Les extraits sont des données échappées.
     */
    static String qualifyPerimeter(ConversationLlm.QualifyRequest r) {
        PerimeterAnalysis a = r.analysis();
        StringBuilder out = new StringBuilder(language(r.language())).append('\n').append(exchange(r.history()))
                .append('\n').append(block("question", r.question())).append('\n')
                .append(block("termes", String.join("\n", a.terms()))).append('\n')
                .append("<analyse etat=\"").append(a.state().name()).append("\"")
                .append(a.incompleteReason() == null ? "" : " raison=\"" + a.incompleteReason().name() + "\"")
                .append("/>\n<modules>\n");
        int used = out.length();
        int left = a.modules().size();
        for (PerimeterModule m : a.modules()) {
            StringBuilder module = new StringBuilder("<module id=\"").append(attribute(m.module().id().toString()))
                    .append("\" nom=\"").append(attribute(m.module().name())).append("\" sorte=\"")
                    .append(m.module().kind().name()).append("\" chemin=\"").append(attribute(m.module().path()))
                    .append("\">\n");
            for (Reason reason : m.reasons()) {
                module.append("<raison>").append(data(reason(reason))).append("</raison>\n");
            }
            StringBuilder excerpts = new StringBuilder();
            m.samples().stream().limit(PERIMETER_EXCERPTS).forEach(o -> excerpts.append("<extrait fichier=\"")
                    .append(attribute(o.path())).append("\" ligne=\"").append(o.line()).append("\">")
                    .append(data(o.excerpt())).append("</extrait>\n"));
            if (used + module.length() + excerpts.length() <= PERIMETER_BUDGET) {
                module.append(excerpts);
            }
            module.append("</module>\n");
            if (used + module.length() > PERIMETER_LIMIT) {
                out.append("<modules-non-transmis nombre=\"").append(left).append("\"/>\n");
                break;
            }
            used += module.length();
            left--;
            out.append(module);
        }
        return out.append("</modules>").toString();
    }

    private static String reason(Reason reason) {
        return switch (reason) {
            case Reason.Term t -> t.term() + " : " + t.count() + (t.count() > 1 ? " lignes" : " ligne");
            case Reason.Dependency d -> "dépend de " + String.join(" → ",
                    d.chain().stream().map(ModuleRef::name).toList());
            case Reason.Aggregator g -> "agrège " + g.of().name();
            case Reason.Architecture x -> "relié à " + x.related() + " (" + (x.technology() == null ? "" : x.technology())
                    + (x.description() == null ? "" : ", " + x.description()) + ")";
        };
    }

    /** 010 : données de la réponse ; chaque source porte le numéro qu'elle peut être citée. */
    static String converseAnswer(ConversationLlm.AnswerRequest r) {
        return language(r.language()) + "\n" + discussed(r.discussed()) + block("fiche-generale", r.generalSheet()) + "\n"
                + block("memoire", r.memory()) + "\n" + exchange(r.history())
                + "\n" + block("question", r.question()) + "\n" + sources(r.sources(), true) + "\n"
                + block("non-comparees", String.join("\n", r.notCompared())) + "\n<recherche-arretee>"
                + (r.searchStopped() ? "oui" : "non") + "</recherche-arretee>"
                + (r.perimeterUnavailable() ? "\n<perimetre-indisponible>oui</perimetre-indisponible>" : "");
    }

    /** 010 : échange et sources citées, pour rédiger un besoin. */
    static String needDraft(ConversationLlm.DraftRequest r) {
        StringBuilder cited = new StringBuilder("<sources-citees>\n");
        r.cited().forEach(c -> cited.append("<source sorte=\"").append(c.kind().name()).append("\" ref=\"")
                .append(attribute(c.ref())).append("\" titre=\"").append(attribute(c.title())).append("\"/>\n"));
        return language(r.language()) + "\n" + block("fiche-generale", r.generalSheet()) + "\n"
                + block("memoire", r.memory()) + "\n" + exchange(r.history())
                + "\n" + cited.append("</sources-citees>");
    }

    /**
     * 011 : nouvelle version d'après la discussion ; les messages de l'utilisateur portent la
     * référence qu'une source DISCUSSION peut citer.
     */
    static String revise(ConversationLlm.ReviseRequest r) {
        StringBuilder discussion = new StringBuilder("<discussion>\n").append(block("memoire", r.memory())).append('\n');
        for (ConversationLlm.DiscussionLine line : r.discussion()) {
            discussion.append("<message auteur=\"").append(line.role() == MessageRole.USER ? "utilisateur" : "agent")
                    .append('"');
            if (line.ref() != null) {
                discussion.append(" ref=\"").append(attribute(line.ref())).append('"');
            }
            discussion.append(">\n").append(data(line.text())).append("\n</message>\n");
        }
        StringBuilder cited = new StringBuilder("<sources-citees>\n");
        r.cited().forEach(c -> cited.append("<source sorte=\"").append(c.kind().name()).append("\" ref=\"")
                .append(attribute(c.ref())).append("\" titre=\"").append(attribute(c.title())).append("\"/>\n"));
        return language(r.language()) + "\n" + knowledge(r.knowledge()) + need(r.need()) + "\n" + answers(r.answers())
                + "\n" + block("specification-courante", r.currentMarkdown()) + "\n"
                + discussion.append("</discussion>") + "\n" + cited.append("</sources-citees>") + "\n<correction>"
                + data(r.correction()) + "</correction>";
    }

    /** 011 : relevé des changements que la discussion avance sur la version courante. */
    static String changes(ConversationLlm.ChangesRequest r) {
        StringBuilder discussion = new StringBuilder("<discussion>\n").append(block("memoire", r.memory())).append('\n');
        for (ConversationLlm.DiscussionLine line : r.discussion()) {
            discussion.append("<message auteur=\"").append(line.role() == MessageRole.USER ? "utilisateur" : "agent")
                    .append("\">\n").append(data(line.text())).append("\n</message>\n");
        }
        return language(r.language()) + "\n" + block("specification-courante", r.currentMarkdown()) + "\n"
                + discussion.append("</discussion>");
    }

    /** 010 : échanges anciens à résumer, après la mémoire précédente. */
    static String compact(ConversationLlm.CompactRequest r) {
        return language(r.language()) + "\n" + block("memoire-precedente", r.previous()) + "\n"
                + exchange(r.exchanges());
    }

    private static String exchange(List<ConversationLlm.Exchange> history) {
        StringBuilder out = new StringBuilder("<echange>\n");
        for (ConversationLlm.Exchange e : history) {
            out.append("<message auteur=\"").append(e.role() == MessageRole.USER ? "utilisateur" : "agent")
                    .append("\">\n").append(data(e.text())).append("\n</message>\n");
        }
        return out.append("</echange>").toString();
    }

    private static String sources(List<TurnSource> sources, boolean withText) {
        StringBuilder out = new StringBuilder("<sources>\n");
        for (TurnSource s : sources) {
            out.append("<source n=\"").append(s.n()).append("\" sorte=\"").append(s.kind().name())
                    .append("\" ref=\"").append(attribute(s.ref())).append("\" titre=\"").append(attribute(s.title()))
                    .append("\">");
            if (withText) {
                out.append("\n").append(data(s.text())).append("\n");
            }
            out.append("</source>\n");
        }
        return out.append("</sources>").toString();
    }

    /** Valeur d'attribut : chevrons et guillemets neutralisés. */
    /** 011 : spécification discutée, dont les sections sont en tête des sources ; rien hors discussion. */
    private static String discussed(ConversationLlm.Discussed d) {
        return d == null ? "" : "<specification-discutee nom=\"" + attribute(d.name()) + "\" version=\"" + d.version()
                + "\"/>\n";
    }

    private static String attribute(String text) {
        return data(text).replace("\"", "&quot;");
    }

    private static String block(String tag, String content) {
        return "<" + tag + ">\n" + data(content) + "\n</" + tag + ">";
    }

    private static String language(Language language) {
        return "<langue>" + language.code() + "</langue>";
    }

    private static String need(String need) {
        return "<besoin>\n" + data(need) + "\n</besoin>";
    }

    private static String answers(List<QuestionAndAnswer> answers) {
        StringBuilder out = new StringBuilder("<reponses>\n");
        for (QuestionAndAnswer qa : answers) {
            out.append("<reponse id=\"").append(qa.questionId()).append("\" theme=\"").append(qa.theme().name())
                    .append("\" hypothese=\"").append(qa.assumption()).append("\">\n<question>").append(data(qa.label()))
                    .append("</question>\n<valeur>").append(data(qa.answer())).append("</valeur>\n</reponse>\n");
        }
        return out.append("</reponses>").toString();
    }

    static String data(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
