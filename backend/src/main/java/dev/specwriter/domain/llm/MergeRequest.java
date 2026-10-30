package dev.specwriter.domain.llm;

import dev.specwriter.domain.session.Language;

/** Entrée de {@code mergeConflict} (FR-048). */
public record MergeRequest(String localMarkdown, String jiraMarkdown, Language language) {
}
