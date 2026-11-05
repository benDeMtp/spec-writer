package dev.specwriter.support;

import dev.specwriter.domain.knowledge.ArchElement;
import dev.specwriter.domain.knowledge.ArchitectureModel;
import dev.specwriter.domain.knowledge.KnowledgeItem;
import dev.specwriter.domain.knowledge.KnowledgeKind;
import dev.specwriter.domain.port.KnowledgeIndex;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Index en mémoire : recherche par mots contenus, sans vecteurs comparés. */
public class InMemoryKnowledgeIndex implements KnowledgeIndex {

    public record Entry(KnowledgeItem item, float[] vector, String model) {
    }

    public final Map<UUID, Map<Key, Entry>> items = new HashMap<>();
    public final Map<UUID, ArchitectureModel> architectures = new HashMap<>();
    /** Écritures, dans l'ordre : {@code save ref} ou {@code vector ref}. */
    public final List<String> writes = new ArrayList<>();

    private Map<Key, Entry> of(UUID projectId) {
        return items.computeIfAbsent(projectId, id -> new LinkedHashMap<>());
    }

    @Override
    public Map<Key, String> hashes(UUID projectId, KnowledgeKind kind) {
        Map<Key, String> out = new HashMap<>();
        of(projectId).forEach((k, e) -> {
            if (k.kind() == kind) {
                out.put(k, e.item().contentHash());
            }
        });
        return out;
    }

    @Override
    public void save(KnowledgeItem item, float[] vector, String embeddingModel) {
        Key key = Key.of(item);
        Entry previous = of(item.projectId()).get(key);
        boolean same = previous != null && previous.item().contentHash().equals(item.contentHash());
        float[] kept = vector != null ? vector : same ? previous.vector() : null;
        String model = vector != null ? embeddingModel : same ? previous.model() : null;
        of(item.projectId()).put(key, new Entry(item, kept, model));
        writes.add("save " + item.ref());
    }

    @Override
    public int retain(UUID projectId, KnowledgeKind kind, Set<Key> keep) {
        List<Key> gone = of(projectId).keySet().stream().filter(k -> k.kind() == kind && !keep.contains(k)).toList();
        gone.forEach(of(projectId)::remove);
        return gone.size();
    }

    @Override
    public void delete(UUID projectId, Key key) {
        of(projectId).remove(key);
    }

    @Override
    public Optional<KnowledgeItem> find(UUID projectId, Key key) {
        return Optional.ofNullable(of(projectId).get(key)).map(Entry::item);
    }

    @Override
    public List<KnowledgeItem> withoutVector(UUID projectId, String embeddingModel, int limit) {
        return of(projectId).values().stream().filter(e -> e.vector() == null || !embeddingModel.equals(e.model()))
                .map(Entry::item).limit(limit).toList();
    }

    @Override
    public void saveVector(UUID projectId, Key key, float[] vector, String embeddingModel) {
        Entry e = of(projectId).get(key);
        of(projectId).put(key, new Entry(e.item(), vector, embeddingModel));
        writes.add("vector " + key.ref());
    }

    @Override
    public List<Hit> search(UUID projectId, String terms, float[] queryVector, String embeddingModel,
            Collection<KnowledgeKind> kinds, int limit) {
        String[] words = terms == null ? new String[0] : terms.toLowerCase(Locale.ROOT).split("\\W+");
        return of(projectId).values().stream().map(Entry::item)
                .filter(i -> kinds == null || kinds.isEmpty() || kinds.contains(i.kind()))
                .map(i -> new Hit(i, score(i, words))).filter(h -> h.score() > 0)
                .sorted((a, b) -> Double.compare(b.score(), a.score())).limit(limit).toList();
    }

    private static double score(KnowledgeItem item, String[] words) {
        String text = (item.title() + " " + item.body()).toLowerCase(Locale.ROOT);
        double score = 0;
        for (String w : words) {
            if (w.length() >= 3 && text.contains(w)) {
                score++;
            }
        }
        return score;
    }

    @Override
    public List<KnowledgeItem> all(UUID projectId, KnowledgeKind kind) {
        return of(projectId).values().stream().map(Entry::item).filter(i -> i.kind() == kind).toList();
    }

    @Override
    public List<KnowledgeItem> withRole(UUID projectId) {
        return of(projectId).values().stream().map(Entry::item).filter(i -> i.role() != null).toList();
    }

    @Override
    public void replaceArchitecture(UUID projectId, ArchitectureModel model) {
        architectures.put(projectId, model);
    }

    @Override
    public ArchitectureModel architecture(UUID projectId) {
        ArchitectureModel stored = architectures.getOrDefault(projectId, ArchitectureModel.EMPTY);
        List<ArchElement> elements = of(projectId).values().stream().map(Entry::item)
                .filter(i -> i.kind() == KnowledgeKind.ARCH_ELEMENT)
                .map(i -> new ArchElement(i.ref(), ArchElement.Type.valueOf(i.elementType()), i.scope(), i.transport(),
                        null, null))
                .toList();
        return new ArchitectureModel(elements, stored.relations(), stored.deployments());
    }

    @Override
    public long count(UUID projectId, KnowledgeKind kind) {
        return of(projectId).keySet().stream().filter(k -> k.kind() == kind).count();
    }
}
