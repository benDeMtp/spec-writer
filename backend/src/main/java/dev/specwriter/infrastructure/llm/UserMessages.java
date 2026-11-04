package dev.specwriter.infrastructure.llm;

import dev.specwriter.domain.knowledge.ArchitectureModel;
import dev.specwriter.domain.knowledge.KnowledgeRole;
import dev.specwriter.domain.knowledge.ProjectKnowledge;
import dev.specwriter.domain.llm.CompareIssueRequest;
import dev.specwriter.domain.llm.ImpactRequest;
import dev.specwriter.domain.llm.RelatedIssuesRequest;
import dev.specwriter.domain.llm.QuestionAndAnswer;
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
            switch (e.kind()) {
                case ADR -> out.append("<adr ref=\"").append(attribute(e.ref())).append("\" statut=\"")
                        .append(attribute(e.status())).append("\">\n");
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

    private static String tag(ProjectKnowledge.Entry e) {
        return switch (e.kind()) {
            case ADR -> "adr";
            case TICKET -> "ticket";
            default -> e.role() == KnowledgeRole.ARCHITECTURE_OVERVIEW ? "vue-architecture" : "vocabulaire";
        };
    }

    /** Valeur d'attribut : chevrons et guillemets neutralisés. */
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
