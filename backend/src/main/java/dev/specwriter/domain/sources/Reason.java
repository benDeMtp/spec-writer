package dev.specwriter.domain.sources;

import java.util.List;

/** Raison de la présence d'un module dans le périmètre (013, data-model.md). */
public sealed interface Reason {

    /** Le module contient {@code count} occurrences du terme. */
    record Term(String term, int count) implements Reason {
    }

    /** Le module dépend d'un module trouvé : chaîne de la graine au module. */
    record Dependency(List<ModuleRef> chain) implements Reason {
        public Dependency {
            chain = List.copyOf(chain);
        }
    }

    /** Le module agrège ({@code <modules>}) un module trouvé. */
    record Aggregator(ModuleRef of) implements Reason {
    }

    /** Le modèle d'architecture relie l'élément du module à celui d'un module trouvé. */
    record Architecture(String element, String related, String description, String technology) implements Reason {
    }
}
