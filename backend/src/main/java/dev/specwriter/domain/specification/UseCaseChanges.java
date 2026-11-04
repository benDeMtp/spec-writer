package dev.specwriter.domain.specification;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Cas d'usage modifiés ou supprimés d'une version à la suivante (FR-047, research.md R29). La
 * comparaison se fait par code, sur le contenu normalisé (espaces et casse ignorés). Un contenu
 * qui réapparaît à l'identique sous un autre code est un renumérotage, pas un changement.
 */
public final class UseCaseChanges {

    /** Nature du changement d'un cas d'usage de la version précédente. */
    public enum Change {
        CHANGED,
        REMOVED
    }

    private UseCaseChanges() {
    }

    /** Changements, par code de la version précédente ; vide sans version précédente. */
    public static Map<String, Change> between(List<UseCase> previous, List<UseCase> current) {
        Map<String, String> now = new LinkedHashMap<>();
        current.forEach(uc -> now.put(uc.code(), content(uc)));
        Set<String> contents = new HashSet<>(now.values());
        Set<String> earlier = previous.stream().map(UseCaseChanges::content).collect(Collectors.toSet());
        Map<String, Change> changes = new LinkedHashMap<>();
        for (UseCase before : previous) {
            String content = content(before);
            String after = now.get(before.code());
            if (content.equals(after) || contents.contains(content)) {
                continue; // inchangé, ou renuméroté : le même cas d'usage sous un autre code
            }
            // le code est repris par un autre cas d'usage renuméroté : celui-ci a disparu
            boolean replaced = after != null && earlier.contains(after);
            changes.put(before.code(), after == null || replaced ? Change.REMOVED : Change.CHANGED);
        }
        return changes;
    }

    private static String content(UseCase uc) {
        return Stream.of(Stream.of(uc.title(), uc.actor(), uc.goal()), Stream.of("§"), uc.preconditions().stream(),
                Stream.of("§"), uc.nominalFlow().stream(), Stream.of("§"), uc.alternatives().stream())
                .flatMap(s -> s)
                .map(UseCaseChanges::normalize)
                .collect(Collectors.joining("\n"));
    }

    private static String normalize(String text) {
        return text == null ? "" : text.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
