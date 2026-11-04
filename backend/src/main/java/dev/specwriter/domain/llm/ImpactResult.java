package dev.specwriter.domain.llm;

import java.util.List;

/** Modules impactés et échanges que le besoin suppose ; leur existence est vérifiée par le code. */
public record ImpactResult(List<String> modules, List<Exchange> exchanges) {

    public static final int MAX_ITEMS = 8;

    public record Exchange(String from, String to, String reason) {
    }

    public ImpactResult {
        modules = modules == null ? List.of() : modules.stream().limit(MAX_ITEMS).toList();
        exchanges = exchanges == null ? List.of() : exchanges.stream().limit(MAX_ITEMS).toList();
    }
}
