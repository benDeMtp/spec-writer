package fr.kawamind.support;

import fr.kawamind.domain.port.ConfluencePageStates;
import fr.kawamind.domain.port.DocSourceRepository;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Pages Confluence lues, par racine, en mémoire (016). */
public class InMemoryConfluencePages implements ConfluencePageStates {

    public final Map<UUID, Map<String, PageState>> pages = new HashMap<>();
    private final DocSourceRepository sources;

    public InMemoryConfluencePages(DocSourceRepository sources) {
        this.sources = sources;
    }

    @Override
    public Map<String, PageState> of(UUID sourceId) {
        return new LinkedHashMap<>(pages.getOrDefault(sourceId, Map.of()));
    }

    @Override
    public void replace(UUID sourceId, Map<String, PageState> states) {
        pages.put(sourceId, new LinkedHashMap<>(states));
    }

    @Override
    public Map<String, Set<UUID>> roots(UUID projectId) {
        Map<String, Set<UUID>> out = new HashMap<>();
        pages.forEach((root, states) -> {
            boolean ofProject = sources.find(root).map(s -> s.projectId().equals(projectId)).orElse(false);
            if (ofProject) {
                states.keySet().forEach(page -> out.computeIfAbsent(page, k -> new LinkedHashSet<>()).add(root));
            }
        });
        return out;
    }
}
