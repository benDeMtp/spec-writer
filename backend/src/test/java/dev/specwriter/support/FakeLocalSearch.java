package dev.specwriter.support;

import dev.specwriter.domain.port.LocalSearch;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Recherche locale figée : renvoie les résultats préparés, quelle que soit la saisie. */
public class FakeLocalSearch implements LocalSearch {

    public final List<Hit> hits = new ArrayList<>();
    public final List<String> queries = new ArrayList<>();
    public final List<SpecHit> specs = new ArrayList<>();

    @Override
    public List<Hit> search(UUID projectId, String terms, int limit) {
        queries.add(terms);
        return hits.stream().limit(limit).toList();
    }

    @Override
    public List<Hit> byDate(UUID projectId, String terms, int offset, int limit) {
        queries.add(terms);
        return sorted().stream().skip(offset).limit(limit).toList();
    }

    @Override
    public int count(UUID projectId, String terms) {
        return hits.size();
    }

    @Override
    public boolean matches(UUID projectId, String terms, UUID sessionId) {
        return hits.stream().anyMatch(h -> h.sessionId().equals(sessionId));
    }

    @Override
    public List<SpecHit> byRelevance(UUID projectId, String terms, int limit) {
        queries.add(terms);
        return specs.stream().limit(limit).toList();
    }

    private List<Hit> sorted() {
        return hits.stream().sorted(Comparator.comparing(Hit::updatedAt).reversed()
                .thenComparing(h -> h.sessionId().toString())).toList();
    }
}
