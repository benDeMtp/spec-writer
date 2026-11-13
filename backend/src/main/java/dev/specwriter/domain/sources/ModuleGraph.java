package dev.specwriter.domain.sources;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Dépendances Maven entre les modules recensés (013, US3, data-model.md) : un module dépend de ses
 * dépendances, de son parent et des BOM qu'il importe, quand ils sont dans l'arbre. Les parcours
 * suivent l'ordre des identifiants : le résultat est déterministe, et les cycles sont tolérés.
 */
public final class ModuleGraph {

    /** X → modules dont X dépend, triés. */
    private final Map<ModuleId, Set<ModuleId>> dependsOn;
    /** Y → modules qui dépendent de Y, triés. */
    private final Map<ModuleId, Set<ModuleId>> dependedBy;
    /** Agrégateur → modules de ses {@code <modules>}, dans leur ordre. */
    private final Map<ModuleId, List<ModuleId>> aggregates;
    private final List<ModuleId> duplicates;

    private ModuleGraph(Map<ModuleId, Set<ModuleId>> dependsOn, Map<ModuleId, Set<ModuleId>> dependedBy,
            Map<ModuleId, List<ModuleId>> aggregates, List<ModuleId> duplicates) {
        this.dependsOn = dependsOn;
        this.dependedBy = dependedBy;
        this.aggregates = aggregates;
        this.duplicates = duplicates;
    }

    public static ModuleGraph of(Map<ModuleId, ModuleDescriptor> descriptors) {
        Map<ModuleId, ModuleDescriptor> sorted = new TreeMap<>(descriptors);
        Map<String, ModuleId> byCoordinates = new HashMap<>();
        List<ModuleId> duplicates = new ArrayList<>();
        sorted.forEach((id, d) -> {
            if (d.coordinates() != null && byCoordinates.putIfAbsent(d.coordinates(), id) != null) {
                duplicates.add(id);
            }
        });
        Map<ModuleId, Set<ModuleId>> dependsOn = new TreeMap<>();
        Map<ModuleId, Set<ModuleId>> dependedBy = new TreeMap<>();
        Map<ModuleId, List<ModuleId>> aggregates = new TreeMap<>();
        sorted.forEach((id, d) -> {
            Set<String> targets = new HashSet<>(d.dependencies());
            targets.addAll(d.bomImports());
            if (d.parent() != null) {
                targets.add(d.parent());
            }
            for (String target : targets) {
                ModuleId to = byCoordinates.get(target);
                if (to != null && !to.equals(id)) {
                    dependsOn.computeIfAbsent(id, k -> new TreeSet<>()).add(to);
                    dependedBy.computeIfAbsent(to, k -> new TreeSet<>()).add(id);
                }
            }
            List<ModuleId> listed = new ArrayList<>();
            for (String relative : d.aggregated()) {
                ModuleId member = resolve(id, relative);
                if (member != null && sorted.containsKey(member) && !listed.contains(member)) {
                    listed.add(member);
                }
            }
            if (!listed.isEmpty()) {
                aggregates.put(id, listed);
            }
        });
        return new ModuleGraph(dependsOn, dependedBy, aggregates, List.copyOf(duplicates));
    }

    /** Modules écartés parce qu'un module plus tôt en ordre de chemin a les mêmes coordonnées. */
    public List<ModuleId> duplicates() {
        return duplicates;
    }

    /**
     * Modules qui dépendent, directement ou non, d'une graine : pour chacun, la chaîne la plus courte
     * depuis une graine jusqu'à lui. Une graine qui dépend d'une autre graine figure aussi.
     */
    public Map<ModuleId, List<ModuleId>> dependents(Set<ModuleId> seeds) {
        Map<ModuleId, List<ModuleId>> out = new LinkedHashMap<>();
        for (ModuleId candidate : reaching(seeds)) {
            List<ModuleId> chain = nearestSeed(candidate, seeds);
            if (chain != null) {
                out.put(candidate, chain);
            }
        }
        return out;
    }

    /** Agrégateurs d'un module trouvé, chacun avec le premier module trouvé de ses {@code <modules>}. */
    public Map<ModuleId, ModuleId> aggregators(Set<ModuleId> found) {
        Map<ModuleId, ModuleId> out = new LinkedHashMap<>();
        aggregates.forEach((aggregator, members) -> members.stream().filter(found::contains).findFirst()
                .ifPresent(member -> out.put(aggregator, member)));
        return out;
    }

    /** Modules depuis lesquels une graine est atteignable, triés. */
    private Set<ModuleId> reaching(Set<ModuleId> seeds) {
        Set<ModuleId> seen = new TreeSet<>();
        Deque<ModuleId> queue = new ArrayDeque<>(new TreeSet<>(seeds));
        while (!queue.isEmpty()) {
            for (ModuleId next : dependedBy.getOrDefault(queue.poll(), Set.of())) {
                if (seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        return seen;
    }

    /** Parcours en largeur depuis {@code start} jusqu'à la graine la plus proche ; chaîne graine → start. */
    private List<ModuleId> nearestSeed(ModuleId start, Set<ModuleId> seeds) {
        Map<ModuleId, ModuleId> previous = new HashMap<>();
        previous.put(start, start);
        Deque<ModuleId> queue = new ArrayDeque<>(List.of(start));
        while (!queue.isEmpty()) {
            ModuleId current = queue.poll();
            for (ModuleId next : dependsOn.getOrDefault(current, Set.of())) {
                if (previous.putIfAbsent(next, current) != null) {
                    continue;
                }
                if (seeds.contains(next)) {
                    List<ModuleId> chain = new ArrayList<>();
                    for (ModuleId step = next; !step.equals(start); step = previous.get(step)) {
                        chain.add(step);
                    }
                    chain.add(start);
                    return List.copyOf(chain);
                }
                queue.add(next);
            }
        }
        return null;
    }

    /** Chemin d'un module agrégé, relatif au dossier de l'agrégateur ; nul s'il sort de la racine. */
    static ModuleId resolve(ModuleId aggregator, String relative) {
        Deque<String> parts = new ArrayDeque<>();
        if (!aggregator.path().isEmpty()) {
            Collections.addAll(parts, aggregator.path().split("/"));
        }
        for (String part : relative.replace('\\', '/').split("/")) {
            if (part.isEmpty() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                if (parts.isEmpty()) {
                    return null;
                }
                parts.removeLast();
            } else {
                parts.addLast(part);
            }
        }
        return new ModuleId(aggregator.rootId(), String.join("/", parts));
    }
}
