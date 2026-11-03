package dev.specwriter.domain.llm;

import dev.specwriter.domain.session.Language;
import java.util.List;

/** Entrée de {@code mergeConflict} (FR-048) : la version locale et la version Jira, en Markdown. */
public record MergeRequest(String need, Language language, List<QuestionAndAnswer> answers, String localMarkdown,
        String jiraMarkdown, String correction, String projectContext) {

    public MergeRequest {
        answers = List.copyOf(answers);
        projectContext = projectContext == null ? "" : projectContext;
    }

    public MergeRequest(String need, Language language, List<QuestionAndAnswer> answers, String localMarkdown,
            String jiraMarkdown, String correction) {
        this(need, language, answers, localMarkdown, jiraMarkdown, correction, "");
    }

    public MergeRequest withCorrection(String newCorrection) {
        return new MergeRequest(need, language, answers, localMarkdown, jiraMarkdown, newCorrection, projectContext);
    }
}
