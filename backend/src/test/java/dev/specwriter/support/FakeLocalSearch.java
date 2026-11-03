package dev.specwriter.support;

import dev.specwriter.domain.port.LocalSearch;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Recherche locale figée : renvoie les résultats préparés, quelle que soit la saisie. */
public class FakeLocalSearch implements LocalSearch {

    public final List<Hit> hits = new ArrayList<>();
    public final List<String> queries = new ArrayList<>();

    @Override
    public List<Hit> search(UUID projectId, String terms, int limit) {
        queries.add(terms);
        return hits.stream().limit(limit).toList();
    }
}
