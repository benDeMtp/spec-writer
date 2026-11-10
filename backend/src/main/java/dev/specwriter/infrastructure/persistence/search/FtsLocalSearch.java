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
    public List<Hit> search(UUID projectId, String terms, int limit) {
        String match = toMatch(terms);
        if (match.isEmpty()) {
            return List.of();
        }
        List<Object[]> rows = em.createNativeQuery("""
                        select f.session_id, s.name,
                               snippet(spec_search, -1, '', '', '…', 16), s.updated_at
                        from spec_search f join session s on s.id = f.session_id
                        where spec_search match ?1 and s.project_id = ?3
                        order by bm25(spec_search, 10.0, 3.0, 1.0)
                        limit ?2""")
                .setParameter(1, match).setParameter(2, limit).setParameter(3, projectId.toString()).getResultList();
        return rows.stream()
                .map(r -> new Hit(UUID.fromString((String) r[0]), (String) r[1], (String) r[2], Instant.parse((String) r[3])))
                .toList();
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public List<Hit> byDate(UUID projectId, String terms, int offset, int limit) {
        String match = toMatch(terms);
        if (match.isEmpty()) {
            return List.of();
        }
        List<Object[]> rows = em.createNativeQuery("""
                        select f.session_id, s.name,
                               snippet(spec_search, -1, '', '', '…', 16), s.updated_at
                        from spec_search f join session s on s.id = f.session_id
                        where spec_search match ?1 and s.project_id = ?2
                        order by s.updated_at desc, s.id
                        limit ?3 offset ?4""")
                .setParameter(1, match).setParameter(2, projectId.toString()).setParameter(3, limit)
                .setParameter(4, offset).getResultList();
        return rows.stream()
                .map(r -> new Hit(UUID.fromString((String) r[0]), (String) r[1], (String) r[2], Instant.parse((String) r[3])))
                .toList();
    }

    @Override
    @Transactional
    public int count(UUID projectId, String terms) {
        String match = toMatch(terms);
        if (match.isEmpty()) {
            return 0;
        }
        Number n = (Number) em.createNativeQuery("""
                        select count(*) from spec_search f join session s on s.id = f.session_id
                        where spec_search match ?1 and s.project_id = ?2""")
                .setParameter(1, match).setParameter(2, projectId.toString()).getSingleResult();
        return n.intValue();
    }

    @Override
    @Transactional
    public boolean matches(UUID projectId, String terms, UUID sessionId) {
        String match = toMatch(terms);
        if (match.isEmpty()) {
            return false;
        }
        Number n = (Number) em.createNativeQuery("""
                        select count(*) from spec_search f join session s on s.id = f.session_id
                        where spec_search match ?1 and s.project_id = ?2 and s.id = ?3""")
                .setParameter(1, match).setParameter(2, projectId.toString()).setParameter(3, sessionId.toString())
                .getSingleResult();
        return n.intValue() > 0;
    }

    /** Texte de version lu pour l'agent : l'extrait transmis est coupé ensuite selon le budget. */
    static final int SPEC_TEXT = 20_000;

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public List<SpecHit> byRelevance(UUID projectId, String terms, int limit) {
        String match = anyWord(terms);
        if (match.isEmpty()) {
            return List.of();
        }
        List<Object[]> rows = em.createNativeQuery("""
                        select s.id, s.name, v.number, substr(v.markdown, 1, ?4), s.implementation_status
                        from spec_search f join session s on s.id = f.session_id
                        join specification_version v on v.session_id = s.id
                             and v.number = (select max(x.number) from specification_version x where x.session_id = s.id)
                        where spec_search match ?1 and s.project_id = ?2
                        order by bm25(spec_search, 10.0, 3.0, 1.0)
                        limit ?3""")
                .setParameter(1, match).setParameter(2, projectId.toString()).setParameter(3, limit)
                .setParameter(4, SPEC_TEXT).getResultList();
        return rows.stream().map(r -> new SpecHit(UUID.fromString((String) r[0]), ((Number) r[2]).intValue(),
                (String) r[1], (String) r[3], "IMPLEMENTED".equals(r[4]))).toList();
    }

    /** {@code "mot1"* OR "mot2"*} : un mot d'au moins 3 lettres suffit (question en langage naturel). */
    static String anyWord(String terms) {
        if (terms == null) {
            return "";
        }
        return Arrays.stream(terms.split("[^\\p{L}\\p{N}]+"))
                .filter(t -> t.length() >= 3)
                .distinct()
                .limit(20)
                .map(t -> "\"" + t + "\"*")
                .collect(Collectors.joining(" OR "));
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
