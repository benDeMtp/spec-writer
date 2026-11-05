package dev.specwriter.domain.llm;

import dev.specwriter.domain.session.Language;
import java.util.List;

/** Besoin et tickets candidats à juger (judgeRelatedIssues, contracts/llm-port.md de 002). */
public record RelatedIssuesRequest(Language language, String need, List<Candidate> candidates) {

    public static final int MAX_CANDIDATES = 20;
    public static final int MAX_EXCERPT = 600;

    /** Ticket candidat : clé, titre, statut et extrait de 600 caractères au plus. */
    public record Candidate(String key, String title, String status, String excerpt) {
    }

    public RelatedIssuesRequest {
        candidates = List.copyOf(candidates);
    }
}
