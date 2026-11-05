package dev.specwriter.domain.llm;

import dev.specwriter.domain.knowledge.ProjectKnowledge;
import dev.specwriter.domain.session.Language;
import java.util.List;

/** Entrée de {@code mergeConflict} (FR-048) : la version locale et la version Jira, en Markdown. */
public record MergeRequest(String need, Language language, List<QuestionAndAnswer> answers, String localMarkdown,
        String jiraMarkdown, String correction, ProjectKnowledge knowledge) {

    public MergeRequest {
        answers = List.copyOf(answers);
        knowledge = knowledge == null ? ProjectKnowledge.EMPTY : knowledge;
    }

    public MergeRequest(String need, Language language, List<QuestionAndAnswer> answers, String localMarkdown,
            String jiraMarkdown, String correction) {
        this(need, language, answers, localMarkdown, jiraMarkdown, correction, ProjectKnowledge.EMPTY);
    }

    public MergeRequest withCorrection(String newCorrection) {
        return new MergeRequest(need, language, answers, localMarkdown, jiraMarkdown, newCorrection, knowledge);
    }
}
