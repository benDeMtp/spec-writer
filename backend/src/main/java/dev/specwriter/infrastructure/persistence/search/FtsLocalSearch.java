package dev.specwriter.infrastructure.persistence.search;

import dev.specwriter.domain.port.LocalSearch;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Recherche FTS5 (V6__fts5_search.sql). La saisie est découpée en mots, chacun cité comme un
 * littéral avec recherche par préfixe : aucun opérateur FTS5 ne peut y être injecté, et la requête
 * reste paramétrée.
 */
@ApplicationScoped
public class FtsLocalSearch implements LocalSearch {

    @Inject
    EntityManager em;

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public List<Hit> search(String terms, int limit) {
        String match = toMatch(terms);
        if (match.isEmpty()) {
            return List.of();
        }
        List<Object[]> rows = em.createNativeQuery("""
                        select f.session_id, s.name,
                               snippet(spec_search, -1, '', '', '…', 16), s.updated_at
                        from spec_search f join session s on s.id = f.session_id
                        where spec_search match ?1
                        order by bm25(spec_search, 10.0, 3.0, 1.0)
                        limit ?2""")
                .setParameter(1, match).setParameter(2, limit).getResultList();
        return rows.stream()
                .map(r -> new Hit(UUID.fromString((String) r[0]), (String) r[1], (String) r[2], Instant.parse((String) r[3])))
                .toList();
    }

    /** {@code "mot1"* "mot2"*} : tous les mots doivent figurer, en début de mot indexé. */
    static String toMatch(String terms) {
        if (terms == null) {
            return "";
        }
        return Arrays.stream(terms.split("[^\\p{L}\\p{N}]+"))
                .filter(t -> !t.isBlank())
                .limit(20)
                .map(t -> "\"" + t + "\"*")
                .collect(Collectors.joining(" "));
    }
}
