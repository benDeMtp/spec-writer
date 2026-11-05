package dev.specwriter.domain.knowledge;

import dev.specwriter.domain.knowledge.ArchitectureModel.DeploymentFact;
import dev.specwriter.domain.knowledge.ArchitectureModel.Exchange;
import java.util.List;

/**
 * Connaissance transmise à l'IA pour une session, enregistrée avec la version (data-model.md,
 * KnowledgeSelection ; research.md R24) : empreinte de la fiche générale, éléments retenus et faits
 * d'architecture calculés.
 */
public record KnowledgeSelection(String generalSheetHash, List<Selected> items, List<ImpactedModule> modules,
        List<Exchange> exchanges, List<DeploymentFact> deploymentFacts) {

    public static final KnowledgeSelection EMPTY = new KnowledgeSelection(null, List.of(), List.of(), List.of(),
            List.of());

    public record Selected(KnowledgeKind kind, String ref, int chunk, String contentHash) {

        public static Selected of(KnowledgeItem item) {
            return new Selected(item.kind(), item.ref(), item.chunk(), item.contentHash());
        }
    }

    public record ImpactedModule(String name, ArchScope scope) {
    }

    public KnowledgeSelection {
        items = items == null ? List.of() : List.copyOf(items);
        modules = modules == null ? List.of() : List.copyOf(modules);
        exchanges = exchanges == null ? List.of() : List.copyOf(exchanges);
        deploymentFacts = deploymentFacts == null ? List.of() : List.copyOf(deploymentFacts);
    }
}
