package dev.specwriter.application.sources;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.ConversationLlm;
import dev.specwriter.domain.port.KnowledgeIndex;
import dev.specwriter.domain.port.SourceRootRepository;
import dev.specwriter.domain.port.SourceTree;
import dev.specwriter.domain.session.Language;
import dev.specwriter.domain.sources.Module;
import dev.specwriter.domain.sources.ModuleGraph;
import dev.specwriter.domain.sources.ModuleId;
import dev.specwriter.domain.sources.ModuleRef;
import dev.specwriter.domain.sources.PerimeterAnalysis;
import dev.specwriter.domain.sources.PerimeterModule;
import dev.specwriter.domain.sources.PerimeterTerms;
import dev.specwriter.domain.sources.Reason;
import dev.specwriter.domain.sources.SourceRoot;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.jboss.logging.Logger;

/**
 * Analyse de périmètre (013, US2, research.md R6 et R7) : l'outil recense, dans toutes les racines
 * du projet et sans plafond de pertinence, les modules qui contiennent les termes proposés par
 * l'agent ; l'agent qualifie ensuite chaque module, sans pouvoir en retirer un. Seuls des
 * compteurs sont journalisés : ni terme, ni chemin, ni extrait.
 */
public class AnalyzePerimeter {

    private static final Logger LOG = Logger.getLogger(AnalyzePerimeter.class);

    /** Analyse qualifiée, ou laissée sans qualification si l'agent n'a pas pu répondre. */
    public record Qualified(PerimeterAnalysis analysis, boolean failed) {
    }

    private final SourceRootRepository roots;
    private final SourceTree tree;
    private final ConversationLlm llm;
    private final ClockPort clock;
    private final SourceTree.Limits limits;
    /** Modèle d'architecture du projet (US4) ; nul : pas de propagation par l'architecture. */
    private final KnowledgeIndex index;

    public AnalyzePerimeter(SourceRootRepository roots, SourceTree tree, ConversationLlm llm, ClockPort clock) {
        this(roots, tree, llm, clock, SourceTree.Limits.DEFAULT, null);
    }

    public AnalyzePerimeter(SourceRootRepository roots, SourceTree tree, ConversationLlm llm, ClockPort clock,
            SourceTree.Limits limits, KnowledgeIndex index) {
        this.roots = roots;
        this.tree = tree;
        this.llm = llm;
        this.clock = clock;
        this.limits = limits;
        this.index = index;
    }

    /** Vrai si le projet a au moins une racine de sources. */
    public boolean available(UUID projectId) {
        return !roots.list(projectId).isEmpty();
    }

    /**
     * Recensement : modules trouvés par les termes, triés, marqués nouveaux par rapport à
     * {@code previous}. Sans racine, l'analyse est indisponible ; sans terme valide, refusée.
     */
    public PerimeterAnalysis analyze(UUID projectId, List<String> rawTerms, PerimeterAnalysis previous, int ordinal,
            BooleanSupplier cancelled) {
        List<SourceRoot> declared = roots.list(projectId);
        if (declared.isEmpty()) {
            return PerimeterAnalysis.noRoots(ordinal, rawTerms == null ? List.of() : rawTerms.stream()
                    .filter(t -> t != null && !t.isBlank()).map(String::strip).limit(PerimeterTerms.MAX_TERMS).toList());
        }
        PerimeterTerms terms = PerimeterTerms.of(rawTerms);
        Instant start = clock.now();
        SourceTree.Scan scan = tree.search(declared, terms, limits, cancelled);
        ArchitecturePerimeter.Result linked = ArchitecturePerimeter.apply(
                index == null ? null : index.architecture(projectId), scan.modules(), propagate(scan));
        long duration = Math.max(0, Duration.between(start, clock.now()).toMillis());
        PerimeterAnalysis analysis = PerimeterAnalysis.complete(ordinal, terms.values(), terms.rejected(), scan.roots(),
                scan.filesScanned(), scan.filesSkipped(), scan.skippedExamples(), duration, linked.modules(),
                linked.architectureOnly());
        if (scan.incomplete() != null) {
            analysis = analysis.incomplete(scan.incomplete());
        }
        analysis = analysis.sorted().markNew(previous);
        LOG.infof("Périmètre : %d terme(s), %d racine(s), %d fichier(s), %d module(s), %s, %d ms", terms.values().size(),
                declared.size(), scan.filesScanned(), analysis.modules().size(), analysis.state(), duration);
        return analysis;
    }

    /**
     * Modules trouvés par un terme, puis leurs dépendants et leurs agrégateurs (US3) : un module
     * atteint de plusieurs façons n'apparaît qu'une fois, avec toutes ses raisons (FR-020).
     */
    private static List<PerimeterModule> propagate(SourceTree.Scan scan) {
        Map<ModuleId, PerimeterModule> byId = new LinkedHashMap<>();
        for (SourceTree.ModuleHit hit : scan.hits()) {
            List<Reason> reasons = new ArrayList<>();
            hit.countByTerm().forEach((term, n) -> reasons.add(new Reason.Term(term, n)));
            byId.put(hit.module().id(), PerimeterModule.found(hit.module(), reasons, hit.total(), hit.samples()));
        }
        if (scan.descriptors().isEmpty() || byId.isEmpty()) {
            return new ArrayList<>(byId.values());
        }
        Map<ModuleId, Module> known = new HashMap<>();
        scan.modules().forEach(m -> known.put(m.id(), m));
        Set<ModuleId> seeds = Set.copyOf(byId.keySet());
        ModuleGraph graph = ModuleGraph.of(scan.descriptors());
        graph.dependents(seeds).forEach((id, chain) -> add(byId, known, id,
                new Reason.Dependency(chain.stream().map(c -> ref(known, c)).toList())));
        graph.aggregators(seeds).forEach((id, member) -> add(byId, known, id, new Reason.Aggregator(ref(known, member))));
        return new ArrayList<>(byId.values());
    }

    private static void add(Map<ModuleId, PerimeterModule> byId, Map<ModuleId, Module> known, ModuleId id, Reason reason) {
        PerimeterModule existing = byId.get(id);
        if (existing != null) {
            byId.put(id, existing.withReasons(List.of(reason)));
        } else if (known.containsKey(id)) {
            byId.put(id, PerimeterModule.found(known.get(id), List.of(reason), 0, List.of()));
        }
    }

    private static ModuleRef ref(Map<ModuleId, Module> known, ModuleId id) {
        Module m = known.get(id);
        return new ModuleRef(id, m == null ? id.path() : m.name());
    }

    /** Qualification par l'agent ; en cas d'échec, l'analyse reste affichée, non qualifiée. */
    public Qualified qualify(PerimeterAnalysis analysis, Language language, List<ConversationLlm.Exchange> history,
            String question) {
        if (analysis.modules().isEmpty()) {
            return new Qualified(analysis, false);
        }
        try {
            return new Qualified(analysis.withQualifications(llm.qualify(new ConversationLlm.QualifyRequest(language,
                    history, question, analysis))), false);
        } catch (DomainException e) {
            LOG.warnf("Périmètre : qualification impossible (%s)", e.code());
            return new Qualified(analysis, true);
        }
    }
}
