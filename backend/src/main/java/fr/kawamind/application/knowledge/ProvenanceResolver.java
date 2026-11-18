package fr.kawamind.application.knowledge;

import fr.kawamind.domain.knowledge.KnowledgeItem;
import fr.kawamind.domain.knowledge.Provenance;
import fr.kawamind.domain.knowledge.Reliability;
import fr.kawamind.domain.knowledge.ReliabilitySettings;
import fr.kawamind.domain.knowledge.SourceOrigin;
import fr.kawamind.domain.port.ConfluencePageStates;
import fr.kawamind.domain.port.DocSourceRepository;
import fr.kawamind.domain.port.ReliabilityRepository;
import fr.kawamind.domain.project.DocSource;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Provenance d'un élément indexé (016, data-model.md) : sorte de source déduite de la sorte de
 * l'élément, du préfixe {@code docs:} et de sa source de rattachement ; date de mise à jour à la
 * source, jamais celle de l'indexation (FR-012) ; niveau de fiabilité d'après les réglages du
 * projet et le niveau propre des racines Confluence. Calculée à la lecture : un changement de niveau
 * vaut sans réindexation (FR-022).
 */
public class ProvenanceResolver {

    static final String DOCUMENTS_PREFIX = "docs:";
    private static final Pattern PAGE_ID = Pattern.compile("[?&]pageId=(\\d+)");

    private final DocSourceRepository docSources;
    private final ReliabilityRepository reliability;
    private final ConfluencePageStates pages;

    /** Sans sources ni réglages : origines déduites des éléments, niveaux par défaut (tests, évaluation). */
    public static ProvenanceResolver defaults() {
        return new ProvenanceResolver(null, null, null);
    }

    public ProvenanceResolver(DocSourceRepository docSources, ReliabilityRepository reliability,
            ConfluencePageStates pages) {
        this.docSources = docSources;
        this.reliability = reliability;
        this.pages = pages;
    }

    /** Lit une fois les sources, les niveaux et les racines Confluence du projet. */
    public Resolution forProject(UUID projectId) {
        Map<UUID, DocSource> sources = docSources == null ? Map.of() : docSources.list(projectId).stream()
                .collect(Collectors.toMap(DocSource::id, Function.identity()));
        ReliabilitySettings settings = reliability == null ? ReliabilitySettings.defaults(projectId)
                : reliability.find(projectId);
        boolean confluence = sources.values().stream().anyMatch(s -> s.kind() == DocSource.Kind.CONFLUENCE);
        Map<String, Set<UUID>> roots = confluence && pages != null ? pages.roots(projectId) : Map.of();
        return new Resolution(sources, settings, roots);
    }

    /** Provenances d'un projet, prêtes pour une recherche ou une sélection. */
    public static final class Resolution {

        private final Map<UUID, DocSource> sources;
        private final ReliabilitySettings settings;
        private final Map<String, Set<UUID>> roots;

        Resolution(Map<UUID, DocSource> sources, ReliabilitySettings settings, Map<String, Set<UUID>> roots) {
            this.sources = sources;
            this.settings = settings;
            this.roots = roots;
        }

        public ReliabilitySettings settings() {
            return settings;
        }

        public Provenance of(KnowledgeItem item) {
            SourceOrigin origin = origin(item);
            Reliability level = origin == SourceOrigin.CONFLUENCE ? confluenceLevel(item) : settings.level(origin);
            return new Provenance(origin, item.sourceUpdatedAt(), level);
        }

        /** Version d'une spécification du projet (recherche de l'agent). */
        public Provenance specification(Instant updatedAt) {
            return new Provenance(SourceOrigin.SPECIFICATIONS, updatedAt, settings.level(SourceOrigin.SPECIFICATIONS));
        }

        /** Sorte donnée (fiche générale, document joint, module) avec sa date. */
        public Provenance of(SourceOrigin origin, Instant updatedAt) {
            return new Provenance(origin, updatedAt, settings.level(origin));
        }

        SourceOrigin origin(KnowledgeItem item) {
            switch (item.kind()) {
                case TICKET:
                    return SourceOrigin.JIRA;
                case ARCH_ELEMENT:
                    return SourceOrigin.ARCHITECTURE;
                default:
                    break;
            }
            if (item.ref().startsWith(DOCUMENTS_PREFIX)) {
                return SourceOrigin.DOCUMENTS;
            }
            DocSource source = item.sourceId() == null ? null : sources.get(item.sourceId());
            if (source == null) {
                // page ou ADR d'un site lu avant le rattachement à sa source (V19)
                return SourceOrigin.ANTORA;
            }
            return switch (source.kind()) {
                case URL, FOLDER -> SourceOrigin.ANTORA;
                case DOCUMENTS -> SourceOrigin.DOCUMENTS;
                case CONFLUENCE -> SourceOrigin.CONFLUENCE;
            };
        }

        private Reliability confluenceLevel(KnowledgeItem item) {
            Matcher m = PAGE_ID.matcher(item.ref());
            Set<UUID> reaching = m.find() ? roots.get(m.group(1)) : null;
            List<UUID> ids = reaching == null || reaching.isEmpty()
                    ? item.sourceId() == null ? List.of() : List.of(item.sourceId())
                    : List.copyOf(reaching);
            return settings.confluence(ids.stream().map(sources::get).filter(java.util.Objects::nonNull)
                    .map(DocSource::reliability).toList());
        }
    }
}
