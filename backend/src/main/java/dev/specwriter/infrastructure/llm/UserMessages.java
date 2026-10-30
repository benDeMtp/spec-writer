package dev.specwriter.infrastructure.llm;

import dev.specwriter.domain.llm.QuestionAndAnswer;
import dev.specwriter.domain.question.QuestionTheme;
import dev.specwriter.domain.session.Language;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Message utilisateur : uniquement des données, chacune entre balises (FR-026, LLM01). Les chevrons
 * du contenu sont neutralisés pour qu'un texte saisi ne puisse pas fermer une balise.
 */
final class UserMessages {

    private UserMessages() {
    }

    static String questions(String need, Language language, List<QuestionAndAnswer> known,
            Collection<QuestionTheme> remaining) {
        return language(language) + "\n" + need(need) + "\n" + answers(known) + "\n<themes-restants>"
                + remaining.stream().map(Enum::name).sorted().collect(Collectors.joining(" ")) + "</themes-restants>";
    }

    static String generation(String need, Language language, List<QuestionAndAnswer> answers, String correction) {
        return language(language) + "\n" + need(need) + "\n" + answers(answers) + "\n<correction>" + data(correction)
                + "</correction>";
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
