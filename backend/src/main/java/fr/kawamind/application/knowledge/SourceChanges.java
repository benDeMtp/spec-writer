package fr.kawamind.application.knowledge;

import fr.kawamind.domain.port.KnowledgeIndex.Fingerprint;
import fr.kawamind.domain.port.KnowledgeIndex.Key;
import fr.kawamind.domain.sync.SourceChange;
import java.util.Collection;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Éléments nouveaux, modifiés et retirés d'une source, par différence d'empreintes (017, FR-008). */
final class SourceChanges {

    private SourceChanges() {
    }

    static SourceChange between(Collection<Fingerprint> before, Collection<Fingerprint> after,
            Predicate<Fingerprint> member) {
        Map<Key, String> was = hashes(before, member);
        Map<Key, String> is = hashes(after, member);
        int added = 0;
        int modified = 0;
        for (Map.Entry<Key, String> e : is.entrySet()) {
            String previous = was.get(e.getKey());
            if (previous == null) {
                added++;
            } else if (!previous.equals(e.getValue())) {
                modified++;
            }
        }
        int removed = (int) was.keySet().stream().filter(k -> !is.containsKey(k)).count();
        return new SourceChange(added, modified, removed);
    }

    private static Map<Key, String> hashes(Collection<Fingerprint> fingerprints, Predicate<Fingerprint> member) {
        return fingerprints.stream().filter(member).collect(Collectors.toMap(Fingerprint::key, Fingerprint::hash,
                (a, b) -> a));
    }
}
