package dev.specwriter.infrastructure.llm;

import dev.specwriter.domain.llm.BuildContextRequest;
import dev.specwriter.domain.llm.ProjectContextInput;
import dev.specwriter.domain.llm.QuestionAndAnswer;
import dev.specwriter.domain.llm.SourceFacts;
import dev.specwriter.domain.llm.SummarizeSourcesRequest;
import dev.specwriter.domain.question.QuestionTheme;
import dev.specwriter.domain.session.Language;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Message utilisateur : uniquement des données, chacune entre balises (FR-026, LLM01). Les chevrons
 * du contenu sont neutralisés pour qu'un texte saisi ne puisse pas fermer une balise. Le contexte du
 * projet suit la langue : {@code <contexte-fonctionnel>} puis {@code <contexte-technique>} (research.md R5
 * et R16 de 002).
 */
final class UserMessages {

    private UserMessages() {
    }

    static String questions(String need, Language language, List<QuestionAndAnswer> known,
            Collection<QuestionTheme> remaining, ProjectContextInput projectContext) {
        return language(language) + "\n" + context(projectContext) + need(need) + "\n" + answers(known) + "\n<themes-restants>"
                + remaining.stream().map(Enum::name).sorted().collect(Collectors.joining(" ")) + "</themes-restants>";
    }

    static String reassess(String need, Language language, List<QuestionAndAnswer> known, String changedQuestionId,
            ProjectContextInput projectContext) {
        return language(language) + "\n" + context(projectContext) + need(need) + "\n" + answers(known) + "\n<question-modifiee>"
                + data(changedQuestionId) + "</question-modifiee>";
    }

    static String generation(String need, Language language, List<QuestionAndAnswer> answers, String correction,
            ProjectContextInput projectContext) {
        return language(language) + "\n" + context(projectContext) + need(need) + "\n" + answers(answers) + "\n<correction>" + data(correction)
                + "</correction>";
    }

    static String edit(String need, Language language, List<QuestionAndAnswer> answers, String currentMarkdown,
            String instruction, String correction, ProjectContextInput projectContext) {
        return language(language) + "\n" + context(projectContext) + need(need) + "\n" + answers(answers) + "\n" + block("specification-actuelle",
                currentMarkdown) + "\n" + block("instruction", instruction) + "\n<correction>" + data(correction)
                + "</correction>";
    }

    static String merge(String need, Language language, List<QuestionAndAnswer> answers, String localMarkdown,
            String jiraMarkdown, String correction, ProjectContextInput projectContext) {
        return language(language) + "\n" + context(projectContext) + need(need) + "\n" + answers(answers) + "\n" + block("version-locale",
                localMarkdown) + "\n" + block("version-jira", jiraMarkdown) + "\n<correction>" + data(correction)
                + "</correction>";
    }

    static String structure(String text, Language language, String correction, ProjectContextInput projectContext) {
        return language(language) + "\n" + context(projectContext) + block("texte-importe", text) + "\n<correction>" + data(correction)
                + "</correction>";
    }

    /** Lot de pages et de tickets à résumer (summarize-project-sources). */
    static String sources(Language language, List<SummarizeSourcesRequest.Source> sources, String correction) {
        StringBuilder out = new StringBuilder(language(language)).append("\n<sources>\n");
        for (SummarizeSourcesRequest.Source s : sources) {
            out.append("<source ref=\"").append(attribute(s.ref())).append("\" titre=\"").append(attribute(s.title()))
                    .append("\">\n").append(data(s.text())).append("\n</source>\n");
        }
        return out.append("</sources>\n<correction>").append(data(correction)).append("</correction>").toString();
    }

    /** Faits extraits, fiches actuelles, réponses et questions passées (build-project-context, R16 de 002). */
    static String buildContext(BuildContextRequest r) {
        StringBuilder out = new StringBuilder(language(r.language())).append("\n<faits>\n");
        for (SourceFacts.Fact f : r.facts()) {
            String category = f.category() == null ? "" : f.category().name();
            String sheet = f.category() == null ? "" : f.category().sheet().name();
            out.append("<fait fiche=\"").append(sheet).append("\" categorie=\"").append(category).append("\" sources=\"")
                    .append(attribute(String.join(" ", f.refs()))).append("\">").append(data(f.statement())).append("</fait>\n");
        }
        out.append("</faits>\n").append(block("fiche-fonctionnelle-actuelle", r.currentFunctional())).append('\n')
                .append(block("fiche-technique-actuelle", r.currentTechnical())).append("\n<reponses>\n");
        for (BuildContextRequest.Answer a : r.answers()) {
            out.append("<reponse ref=\"").append(attribute(a.ref())).append("\" fiche=\"").append(a.sheet().name())
                    .append("\" question=\"").append(attribute(a.question())).append("\">").append(data(a.answer()))
                    .append("</reponse>\n");
        }
        out.append("</reponses>\n<questions-passees>\n");
        for (BuildContextRequest.Skipped q : r.skipped()) {
            out.append("<question fiche=\"").append(q.sheet().name()).append("\">").append(data(q.question()))
                    .append("</question>\n");
        }
        return out.append("</questions-passees>\n<correction>").append(data(r.correction())).append("</correction>")
                .toString();
    }

    /** Fiches fonctionnelle et technique ; une fiche vide est omise, et sans fiche le message est celui de 001. */
    private static String context(ProjectContextInput projectContext) {
        if (projectContext == null || projectContext.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        if (!projectContext.functional().isBlank()) {
            out.append(block("contexte-fonctionnel", projectContext.functional())).append('\n');
        }
        if (!projectContext.technical().isBlank()) {
            out.append(block("contexte-technique", projectContext.technical())).append('\n');
        }
        return out.toString();
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
