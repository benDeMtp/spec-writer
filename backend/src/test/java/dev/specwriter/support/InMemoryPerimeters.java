package dev.specwriter.support;

import dev.specwriter.domain.port.PerimeterRepository;
import dev.specwriter.domain.sources.PerimeterAnalysis;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Analyses de périmètre en mémoire ; {@code conversationOf} rattache un message à sa conversation. */
public class InMemoryPerimeters implements PerimeterRepository {
    private final Map<UUID, List<PerimeterAnalysis>> byMessage = new LinkedHashMap<>();
    public final Map<UUID, UUID> conversationOf = new LinkedHashMap<>();

    @Override
    public void save(UUID messageId, PerimeterAnalysis analysis) {
        List<PerimeterAnalysis> list = byMessage.computeIfAbsent(messageId, k -> new ArrayList<>());
        list.removeIf(a -> a.ordinal() == analysis.ordinal());
        list.add(analysis);
        list.sort(Comparator.comparingInt(PerimeterAnalysis::ordinal));
    }

    @Override
    public List<PerimeterAnalysis> list(UUID messageId) {
        return List.copyOf(byMessage.getOrDefault(messageId, List.of()));
    }

    @Override
    public Optional<PerimeterAnalysis> last(UUID conversationId) {
        PerimeterAnalysis found = null;
        for (Map.Entry<UUID, List<PerimeterAnalysis>> e : byMessage.entrySet()) {
            if (conversationId.equals(conversationOf.get(e.getKey()))) {
                for (PerimeterAnalysis a : e.getValue()) {
                    if (a.unavailable() == null) {
                        found = a;
                    }
                }
            }
        }
        return Optional.ofNullable(found);
    }

    @Override
    public void clear(UUID messageId) {
        byMessage.remove(messageId);
    }
}
