package dev.specwriter.domain.llm;

import dev.specwriter.domain.session.Language;

/** Besoin et ticket non fermé à comparer (compareWithIssue, contracts/llm-port.md de 002). */
public record CompareIssueRequest(Language language, String need, String key, String title, String description,
        String status) {
}
