package dev.specwriter.infrastructure.knowledge;

import dev.specwriter.domain.knowledge.ArchDeployment;
import dev.specwriter.domain.knowledge.ArchElement;
import dev.specwriter.domain.knowledge.ArchRelation;
import dev.specwriter.domain.knowledge.ArchScope;
import dev.specwriter.domain.knowledge.ArchitectureModel;
import dev.specwriter.domain.knowledge.KnowledgeItem;
import dev.specwriter.domain.knowledge.KnowledgeKind;
import dev.specwriter.domain.knowledge.KnowledgeRole;
import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.IdGenerator;
import dev.specwriter.domain.port.KnowledgeIndex;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.nio.ByteBuffer;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.hibernate.Session;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Index de connaissance dans SQLite (V10__knowledge_index.sql, research.md R18) : plein texte
 * FTS5, vecteurs {@code float32} en BLOB gardés en mémoire par projet et invalidés à l'écriture,
 * fusion des rangs (reciprocal rank fusion). Rien n'est journalisé du contenu.
 */
@ApplicationScoped
public class SqliteKnowledgeIndex implements KnowledgeIndex {

    /** Constante de la fusion des rangs. */
    static final int RRF_K = 60;
    /** Meilleurs résultats retenus de chaque recherche avant fusion. */
    static final int CANDIDATES = 50;
    private static final int MAX_TERMS = 32;

    private static final String COLUMNS = """
            id, project_id, kind, ref, chunk, title, body, status, status_category, superseded_by, issue_type,
            element_type, scope, transport, role, content_hash, source_updated_at, indexed_at""";

    @Inject
    EntityManager em;
    @Inject
    IdGenerator ids;
    @Inject
    ClockPort clock;

    /** Vecteurs d'un projet : identifiant de l'élément, sorte, modèle et valeurs. */
    private record Vector(String id, KnowledgeKind kind, String model, float[] values) {
    }

    private final Map<UUID, List<Vector>> vectors = new ConcurrentHashMap<>();

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<Key, String> hashes(UUID projectId, KnowledgeKind kind) {
        List<Object[]> rows = em.createNativeQuery(
                "select ref, chunk, content_hash from knowledge_item where project_id = ?1 and kind = ?2")
                .setParameter(1, projectId.toString()).setParameter(2, kind.name()).getResultList();
        Map<Key, String> hashes = new HashMap<>();
        rows.forEach(r -> hashes.put(new Key(kind, (String) r[0], ((Number) r[1]).intValue()), (String) r[2]));
        return hashes;
    }

    @Override
    @Transactional
    public Set<String> closedTickets(UUID projectId) {
        List<?> refs = em.createNativeQuery("""
                select ref from knowledge_item
                where project_id = ?1 and kind = 'TICKET' and status_category = ?2""")
                .setParameter(1, projectId.toString()).setParameter(2, KnowledgeItem.DONE).getResultList();
        return refs.stream().map(String.class::cast).collect(Collectors.toSet());
    }

    @Override
    @Transactional
    public void save(KnowledgeItem item, float[] vector, String embeddingModel) {
        em.createNativeQuery("insert into knowledge_item (" + COLUMNS + ", embedding, embedding_model) " + """
                values (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15, ?16, ?17, ?18, ?19, ?20)
                on conflict (project_id, kind, ref, chunk) do update set
                    title = excluded.title, body = excluded.body, status = excluded.status,
                    status_category = excluded.status_category, superseded_by = excluded.superseded_by,
                    issue_type = excluded.issue_type, element_type = excluded.element_type, scope = excluded.scope,
                    transport = excluded.transport, role = excluded.role,
                    source_updated_at = excluded.source_updated_at, indexed_at = excluded.indexed_at,
                    embedding = case when excluded.embedding is not null then excluded.embedding
                                     when knowledge_item.content_hash = excluded.content_hash
                                         then knowledge_item.embedding end,
                    embedding_model = case when excluded.embedding is not null then excluded.embedding_model
                                           when knowledge_item.content_hash = excluded.content_hash
                                               then knowledge_item.embedding_model end,
                    content_hash = excluded.content_hash""")
                .setParameter(1, ids.next().toString())
                .setParameter(2, item.projectId().toString())
                .setParameter(3, item.kind().name())
                .setParameter(4, item.ref())
                .setParameter(5, item.chunk())
                .setParameter(6, item.title())
                .setParameter(7, item.body())
                .setParameter(8, item.status())
                .setParameter(9, item.statusCategory())
                .setParameter(10, item.supersededBy())
                .setParameter(11, item.issueType())
                .setParameter(12, item.elementType())
                .setParameter(13, item.scope() == null ? null : item.scope().name())
                .setParameter(14, item.transport() ? 1 : 0)
                .setParameter(15, item.role() == null ? null : item.role().name())
                .setParameter(16, item.contentHash())
                .setParameter(17, text(item.sourceUpdatedAt()))
                .setParameter(18, text(item.indexedAt() == null ? clock.now() : item.indexedAt()))
                .setParameter(19, vector == null ? null : bytes(vector))
                .setParameter(20, vector == null ? null : embeddingModel)
                .executeUpdate();
        vectors.remove(item.projectId());
    }

    @Override
    @Transactional
    public int retain(UUID projectId, KnowledgeKind kind, Set<Key> keep) {
        Map<Key, String> existing = hashes(projectId, kind);
        int removed = 0;
        for (Key key : existing.keySet()) {
            if (!keep.contains(key)) {
                delete(projectId, key);
                removed++;
            }
        }
        return removed;
    }

    @Override
    @Transactional
    public void delete(UUID projectId, Key key) {
        em.createNativeQuery("delete from knowledge_item where project_id = ?1 and kind = ?2 and ref = ?3 and chunk = ?4")
                .setParameter(1, projectId.toString()).setParameter(2, key.kind().name())
                .setParameter(3, key.ref()).setParameter(4, key.chunk()).executeUpdate();
        vectors.remove(projectId);
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public Optional<KnowledgeItem> find(UUID projectId, Key key) {
        List<Object[]> rows = em.createNativeQuery("select " + COLUMNS
                + " from knowledge_item where project_id = ?1 and kind = ?2 and ref = ?3 and chunk = ?4")
                .setParameter(1, projectId.toString()).setParameter(2, key.kind().name())
                .setParameter(3, key.ref()).setParameter(4, key.chunk()).getResultList();
        return rows.stream().findFirst().map(SqliteKnowledgeIndex::item);
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public List<KnowledgeItem> withoutVector(UUID projectId, String embeddingModel, int limit) {
        List<Object[]> rows = em.createNativeQuery("select " + COLUMNS + " " + """
                from knowledge_item where project_id = ?1
                 and (embedding is null or embedding_model is null or embedding_model <> ?2)
                 order by kind, ref, chunk limit ?3""")
                .setParameter(1, projectId.toString()).setParameter(2, embeddingModel).setParameter(3, limit)
                .getResultList();
        return rows.stream().map(SqliteKnowledgeIndex::item).toList();
    }

    @Override
    @Transactional
    public void saveVector(UUID projectId, Key key, float[] vector, String embeddingModel) {
        em.createNativeQuery("""
                update knowledge_item set embedding = ?5, embedding_model = ?6
                where project_id = ?1 and kind = ?2 and ref = ?3 and chunk = ?4""")
                .setParameter(1, projectId.toString()).setParameter(2, key.kind().name())
                .setParameter(3, key.ref()).setParameter(4, key.chunk())
                .setParameter(5, bytes(vector)).setParameter(6, embeddingModel).executeUpdate();
        vectors.remove(projectId);
    }

    @Override
    @Transactional
    public List<Hit> search(UUID projectId, String terms, float[] queryVector, String embeddingModel,
            Collection<KnowledgeKind> kinds, int limit) {
        Set<KnowledgeKind> wanted = kinds == null || kinds.isEmpty() ? Set.of(KnowledgeKind.values()) : Set.copyOf(kinds);
        Map<String, Double> scores = new LinkedHashMap<>();
        List<String> byTerms = byTerms(projectId, terms, wanted);
        for (int rank = 0; rank < byTerms.size(); rank++) {
            scores.merge(byTerms.get(rank), 1.0 / (RRF_K + rank + 1), Double::sum);
        }
        if (queryVector != null && embeddingModel != null) {
            List<String> byMeaning = byMeaning(projectId, queryVector, embeddingModel, wanted);
            for (int rank = 0; rank < byMeaning.size(); rank++) {
                scores.merge(byMeaning.get(rank), 1.0 / (RRF_K + rank + 1), Double::sum);
            }
        }
        List<Map.Entry<String, Double>> best = scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed()).limit(limit).toList();
        Map<String, KnowledgeItem> items = items(best.stream().map(Map.Entry::getKey).toList());
        return best.stream().filter(e -> items.containsKey(e.getKey()))
                .map(e -> new Hit(items.get(e.getKey()), e.getValue())).toList();
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public List<KnowledgeItem> all(UUID projectId, KnowledgeKind kind) {
        List<Object[]> rows = em.createNativeQuery("select " + COLUMNS
                + " from knowledge_item where project_id = ?1 and kind = ?2 order by ref, chunk")
                .setParameter(1, projectId.toString()).setParameter(2, kind.name()).getResultList();
        return rows.stream().map(SqliteKnowledgeIndex::item).toList();
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public List<KnowledgeItem> withRole(UUID projectId) {
        List<Object[]> rows = em.createNativeQuery("select " + COLUMNS
                + " from knowledge_item where project_id = ?1 and role is not null order by role, ref, chunk")
                .setParameter(1, projectId.toString()).getResultList();
        return rows.stream().map(SqliteKnowledgeIndex::item).toList();
    }

    @Override
    @Transactional
    public void replaceArchitecture(UUID projectId, ArchitectureModel model) {
        String project = projectId.toString();
        em.createNativeQuery("delete from arch_relation where project_id = ?1").setParameter(1, project).executeUpdate();
        em.createNativeQuery("delete from arch_deployment where project_id = ?1").setParameter(1, project)
                .executeUpdate();
        int position = 0;
        for (ArchRelation r : model.relations()) {
            em.createNativeQuery("""
                    insert into arch_relation (project_id, position, source, destination, description, technology)
                    values (?1, ?2, ?3, ?4, ?5, ?6)""")
                    .setParameter(1, project).setParameter(2, position++).setParameter(3, r.source())
                    .setParameter(4, r.destination()).setParameter(5, bounded(r.description()))
                    .setParameter(6, bounded(r.technology())).executeUpdate();
        }
        position = 0;
        for (ArchDeployment d : model.deployments()) {
            em.createNativeQuery("""
                    insert into arch_deployment (project_id, position, element, environment, root_node, node_path,
                                                 instances, shared_infrastructure)
                    values (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)""")
                    .setParameter(1, project).setParameter(2, position++).setParameter(3, d.element())
                    .setParameter(4, d.environment()).setParameter(5, d.rootNode()).setParameter(6, d.nodePath())
                    .setParameter(7, d.instances()).setParameter(8, d.sharedInfrastructure() ? 1 : 0).executeUpdate();
        }
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public ArchitectureModel architecture(UUID projectId) {
        String project = projectId.toString();
        List<Object[]> elements = em.createNativeQuery("""
                select ref, element_type, scope, transport from knowledge_item
                where project_id = ?1 and kind = 'ARCH_ELEMENT' and chunk = 0 order by ref""")
                .setParameter(1, project).getResultList();
        List<Object[]> relations = em.createNativeQuery("""
                select source, destination, description, technology from arch_relation
                where project_id = ?1 order by position""").setParameter(1, project).getResultList();
        List<Object[]> deployments = em.createNativeQuery("""
                select element, environment, root_node, node_path, instances, shared_infrastructure
                from arch_deployment where project_id = ?1 order by position""").setParameter(1, project)
                .getResultList();
        return new ArchitectureModel(
                elements.stream().filter(r -> r[1] != null && r[2] != null)
                        .map(r -> new ArchElement((String) r[0], ArchElement.Type.valueOf((String) r[1]),
                                ArchScope.valueOf((String) r[2]), ((Number) r[3]).intValue() == 1, null, null))
                        .toList(),
                relations.stream().map(r -> new ArchRelation((String) r[0], (String) r[1], (String) r[2], (String) r[3]))
                        .toList(),
                deployments.stream().map(r -> new ArchDeployment((String) r[0], (String) r[1], (String) r[2],
                        (String) r[3], ((Number) r[4]).intValue(), ((Number) r[5]).intValue() == 1)).toList());
    }

    /** Nombre d'éléments, et non de morceaux : chaque élément découpé garde un morceau 0 (004, R1). */
    @Override
    @Transactional
    public long count(UUID projectId, KnowledgeKind kind) {
        return ((Number) em.createNativeQuery("select count(*) from knowledge_item where project_id = ?1 and kind = ?2 and chunk = 0")
                .setParameter(1, projectId.toString()).setParameter(2, kind.name()).getSingleResult()).longValue();
    }

    // ---------- Recherches ----------

    @SuppressWarnings("unchecked")
    private List<String> byTerms(UUID projectId, String terms, Set<KnowledgeKind> kinds) {
        String match = toMatch(terms);
        if (match.isEmpty()) {
            return List.of();
        }
        List<Object> rows = em.createNativeQuery("""
                        select k.id from knowledge_fts f join knowledge_item k on k.id = f.item_id
                        where knowledge_fts match ?1 and k.project_id = ?2
                        order by bm25(knowledge_fts, 0.0, 3.0, 1.0) limit ?3""")
                .setParameter(1, match).setParameter(2, projectId.toString()).setParameter(3, CANDIDATES * 4)
                .getResultList();
        if (kinds.size() == KnowledgeKind.values().length) {
            return rows.stream().map(String.class::cast).limit(CANDIDATES).toList();
        }
        Map<String, KnowledgeItem> items = items(rows.stream().map(String.class::cast).toList());
        return rows.stream().map(String.class::cast).filter(id -> items.containsKey(id)
                && kinds.contains(items.get(id).kind())).limit(CANDIDATES).toList();
    }

    private List<String> byMeaning(UUID projectId, float[] query, String model, Set<KnowledgeKind> kinds) {
        record Scored(String id, double score) {
        }
        return vectors(projectId).stream()
                .filter(v -> model.equals(v.model()) && kinds.contains(v.kind()) && v.values().length == query.length)
                .map(v -> new Scored(v.id(), dot(query, v.values())))
                .sorted(Comparator.comparingDouble(Scored::score).reversed()).limit(CANDIDATES)
                .map(Scored::id).toList();
    }

    /** Lu en JDBC : le pilote SQLite ne sait pas extraire un BLOB par {@code getBlob}, que Hibernate appelle. */
    private List<Vector> vectors(UUID projectId) {
        return vectors.computeIfAbsent(projectId, id -> em.unwrap(Session.class).doReturningWork(connection -> {
            List<Vector> loaded = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                    select id, kind, embedding_model, embedding from knowledge_item
                    where project_id = ? and embedding is not null""")) {
                statement.setString(1, id.toString());
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        loaded.add(new Vector(rows.getString(1), KnowledgeKind.valueOf(rows.getString(2)),
                                rows.getString(3), floats(rows.getBytes(4))));
                    }
                }
            }
            return List.copyOf(loaded);
        }));
    }

    @SuppressWarnings("unchecked")
    private Map<String, KnowledgeItem> items(List<String> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, KnowledgeItem> items = new HashMap<>();
        for (int from = 0; from < ids.size(); from += 200) {
            List<String> part = ids.subList(from, Math.min(ids.size(), from + 200));
            String placeholders = part.stream().map(p -> "?").collect(Collectors.joining(","));
            var query = em.createNativeQuery("select " + COLUMNS + " from knowledge_item where id in (" + placeholders + ")");
            for (int i = 0; i < part.size(); i++) {
                query.setParameter(i + 1, part.get(i));
            }
            ((List<Object[]>) query.getResultList()).forEach(r -> items.put((String) r[0], item(r)));
        }
        return items;
    }

    /**
     * {@code "mot1"* OR "mot2" …} : chaque mot est cité comme un littéral, un seul suffit, bm25
     * classe. Les mots de moins de 3 lettres sont ignorés ; préfixe à partir de 4 lettres.
     */
    static String toMatch(String terms) {
        if (terms == null) {
            return "";
        }
        return Arrays.stream(terms.split("[^\\p{L}\\p{N}]+"))
                .filter(t -> t.length() >= 3)
                .map(t -> t.toLowerCase(java.util.Locale.ROOT))
                .distinct()
                .limit(MAX_TERMS)
                .map(t -> "\"" + t + "\"" + (t.length() >= 4 ? "*" : ""))
                .collect(Collectors.joining(" OR "));
    }

    // ---------- Conversions ----------

    private static KnowledgeItem item(Object[] r) {
        return new KnowledgeItem(UUID.fromString((String) r[1]), KnowledgeKind.valueOf((String) r[2]), (String) r[3],
                ((Number) r[4]).intValue(), (String) r[5], (String) r[6], (String) r[7], (String) r[8], (String) r[9],
                (String) r[10], (String) r[11], r[12] == null ? null : ArchScope.valueOf((String) r[12]),
                ((Number) r[13]).intValue() == 1, r[14] == null ? null : KnowledgeRole.valueOf((String) r[14]),
                (String) r[15], instant(r[16]), instant(r[17]));
    }

    private static String text(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private static Instant instant(Object value) {
        return value == null ? null : Instant.parse((String) value);
    }

    private static String bounded(String value) {
        return value == null || value.length() <= 500 ? value : value.substring(0, 500);
    }

    static byte[] bytes(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        buffer.asFloatBuffer().put(vector);
        return buffer.array();
    }

    static float[] floats(byte[] bytes) {
        float[] values = new float[bytes.length / Float.BYTES];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(values);
        return values;
    }

    private static double dot(float[] a, float[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }
}
