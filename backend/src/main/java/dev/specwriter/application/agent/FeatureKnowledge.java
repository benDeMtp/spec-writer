package dev.specwriter.application.agent;

import dev.specwriter.domain.knowledge.ArchRelation;
import dev.specwriter.domain.knowledge.ArchScope;
import dev.specwriter.domain.knowledge.ArchitectureModel;
import dev.specwriter.domain.knowledge.KnowledgeItem;
import dev.specwriter.domain.knowledge.KnowledgeKind;
import dev.specwriter.domain.knowledge.KnowledgeSelection;
import dev.specwriter.domain.port.KnowledgeIndex;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.project.ProjectContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Connaissance remise avec une feature (research.md R26) : la fiche générale du projet, les ADR et
 * les modules de la sélection enregistrée avec la version, lus dans l'index local. Aucun appel à
 * Jira ni à Copilot.
 */
public class FeatureKnowledge {

    static final int EXCERPT = 600;

    public record Knowledge(String generalContext, List<Adr> adrs, List<Module> modules) {
    }

    public record Adr(String ref, String title, String status, String url, String excerpt) {
    }

    public record Module(String name, String type, ArchScope scope, List<Relation> relations) {
    }

    /** {@code via} : intermédiaire de transport d'un échange indirect. */
    public record Relation(String to, String description, String technology, String via) {
    }

    private final ProjectRepository projects;
    private final KnowledgeIndex index;

    public FeatureKnowledge(ProjectRepository projects, KnowledgeIndex index) {
        this.projects = projects;
        this.index = index;
    }

    public Knowledge of(UUID projectId, KnowledgeSelection selection) {
        String general = projects.find(projectId).map(Project::context).map(ProjectContext::markdown).orElse("");
        return new Knowledge(general, adrs(projectId, selection), modules(projectId, selection));
    }

    private List<Adr> adrs(UUID projectId, KnowledgeSelection selection) {
        Map<String, Adr> out = new LinkedHashMap<>();
        for (KnowledgeSelection.Selected s : selection.items()) {
            if (s.kind() != KnowledgeKind.ADR || out.containsKey(s.ref())) {
                continue;
            }
            Optional<KnowledgeItem> item = index.find(projectId, new KnowledgeIndex.Key(s.kind(), s.ref(), s.chunk()));
            item.ifPresent(i -> out.put(s.ref(), new Adr(i.ref(), i.title(), i.status(), web(i.ref()) ? i.ref() : null,
                    excerpt(i.body()))));
        }
        return List.copyOf(out.values());
    }

    private List<Module> modules(UUID projectId, KnowledgeSelection selection) {
        if (selection.modules().isEmpty()) {
            return List.of();
        }
        ArchitectureModel model = index.architecture(projectId);
        List<Module> out = new ArrayList<>();
        for (KnowledgeSelection.ImpactedModule m : selection.modules()) {
            List<Relation> relations = new ArrayList<>();
            for (ArchRelation r : model.relations()) {
                if (r.source().equals(m.name())) {
                    relations.add(new Relation(r.destination(), r.description(), r.technology(), null));
                }
            }
            for (ArchitectureModel.Exchange e : selection.exchanges()) {
                if (e.source().equals(m.name()) && e.kind() != ArchitectureModel.Exchange.Kind.DIRECT) {
                    relations.add(new Relation(e.destination(),
                            e.kind() == ArchitectureModel.Exchange.Kind.MISSING ? "flux manquant, à créer" : "flux indirect",
                            null, e.via()));
                }
            }
            String type = model.element(m.name()).map(el -> el.type().name()).orElse(null);
            out.add(new Module(m.name(), type, m.scope(), relations));
        }
        return out;
    }

    private static String excerpt(String body) {
        return body.length() <= EXCERPT ? body : body.substring(0, EXCERPT - 1) + "…";
    }

    private static boolean web(String ref) {
        String lower = ref.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }
}
