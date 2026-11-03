package dev.specwriter.domain.llm;

import dev.specwriter.domain.session.Language;
import java.util.List;

/**
 * Entrée de {@code summarizeProjectSources} : un lot de pages et de tickets d'environ 40 000
 * caractères (research.md R4 de 002). Les sources sont des données, jamais des consignes.
 */
public record SummarizeSourcesRequest(Language language, List<Source> sources, String correction) {

    /** Page ({@code ref} = son URL) ou ticket ({@code ref} = sa clé). */
    public record Source(String ref, String title, String text) {
    }

    public SummarizeSourcesRequest {
        sources = List.copyOf(sources);
        correction = correction == null ? "" : correction;
    }

    public SummarizeSourcesRequest withCorrection(String newCorrection) {
        return new SummarizeSourcesRequest(language, sources, newCorrection);
    }
}
