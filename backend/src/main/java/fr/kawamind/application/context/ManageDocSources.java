package fr.kawamind.application.context;

import fr.kawamind.application.settings.ConfluenceAccess;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.knowledge.KnowledgeKind;
import fr.kawamind.domain.knowledge.Reliability;
import fr.kawamind.domain.port.ConfluenceClient;
import fr.kawamind.domain.port.ConfluenceClient.ConfluenceException;
import fr.kawamind.domain.port.ConfluencePageStates;
import fr.kawamind.domain.port.DocSourceRepository;
import fr.kawamind.domain.port.DocumentFolder;
import fr.kawamind.domain.port.IdGenerator;
import fr.kawamind.domain.port.KnowledgeIndex;
import fr.kawamind.domain.port.ProjectRepository;
import fr.kawamind.domain.project.DocSource;
import fr.kawamind.domain.project.ExclusionPatterns;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Sources de documentation d'un projet, 10 au plus (FR-013 de 002) : sites Antora, et dossiers de
 * documents avec leurs motifs d'exclusion et leur aperçu (006), racines Confluence avec leur aperçu
 * et leur niveau de fiabilité propre (016).
 */
public class ManageDocSources {

    /** Exemples montrés par l'aperçu, pour chaque catégorie (006, FR-010). */
    static final int EXAMPLES = 10;

    /** Effet des motifs sur un dossier, sans rien lire : noms et attributs seulement. */
    public record Preview(int retained, int excluded, int ignored, List<String> retainedExamples,
            List<String> excludedExamples, List<String> ignoredExamples, boolean limitReached) {
    }

    /** Pages Confluence indexées au plus par projet, toutes racines confondues (016, R10). */
    public static final int MAX_CONFLUENCE_PAGES = 2_000;

    /**
     * Aperçu d'une racine Confluence, sans rien indexer (FR-004) : titre, identifiant et nombre de
     * pages (racine comprise), ou le motif du refus.
     */
    public record ConfluencePreview(int pages, String title, String pageId, String problem) {

        static ConfluencePreview refused(String problem) {
            return new ConfluencePreview(0, null, null, problem);
        }
    }

    private final ProjectRepository projects;
    private final DocSourceRepository docSources;
    private final IdGenerator ids;
    private final KnowledgeIndex index;
    private final DocumentFolder folder;
    private final ConfluenceAccess confluence;
    private final ConfluenceClient client;
    private final ConfluencePageStates pages;

    public ManageDocSources(ProjectRepository projects, DocSourceRepository docSources, IdGenerator ids,
            KnowledgeIndex index, DocumentFolder folder, ConfluenceAccess confluence, ConfluenceClient client,
            ConfluencePageStates pages) {
        this.projects = projects;
        this.docSources = docSources;
        this.ids = ids;
        this.index = index;
        this.folder = folder;
        this.confluence = confluence;
        this.client = client;
        this.pages = pages;
    }

    public ManageDocSources(ProjectRepository projects, DocSourceRepository docSources, IdGenerator ids,
            KnowledgeIndex index, DocumentFolder folder) {
        this(projects, docSources, ids, index, folder, null, null, null);
    }

    public List<DocSource> list(UUID projectId) {
        requireProject(projectId);
        return docSources.list(projectId);
    }

    public DocSource add(UUID projectId, DocSource.Kind kind, String location) {
        return add(projectId, kind, location, List.of());
    }

    public DocSource add(UUID projectId, DocSource.Kind kind, String location, List<String> exclusions) {
        if (kind == DocSource.Kind.CONFLUENCE) {
            return addConfluence(projectId, location, null);
        }
        requireProject(projectId);
        DocSource.requireRoomFor(docSources.list(projectId).size());
        DocSource source = DocSource.create(ids.next(), projectId, kind, location, ExclusionPatterns.of(exclusions));
        docSources.save(source);
        return source;
    }

    /** Nouveaux motifs d'un dossier de documents, appliqués à sa prochaine indexation. */
    public DocSource setExclusions(UUID projectId, UUID sourceId, List<String> exclusions) {
        DocSource changed = owned(projectId, sourceId).withExclusions(ExclusionPatterns.of(exclusions));
        docSources.save(changed);
        return changed;
    }

    /** Aperçu des motifs sur un dossier, déclaré ou non, sans ouvrir aucun fichier. */
    public Preview preview(UUID projectId, String location, List<String> exclusions) {
        requireProject(projectId);
        DocSource candidate = DocSource.create(ids.next(), projectId, DocSource.Kind.DOCUMENTS, location,
                ExclusionPatterns.of(exclusions));
        DocumentFolder.Scan scan = folder.scan(candidate);
        if (scan.problem() != null) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Ce dossier ne peut pas être lu : " + location);
        }
        return new Preview(scan.retained().size(), scan.excluded().size(), scan.ignored().size(),
                scan.retained().stream().map(DocumentFolder.FileEntry::path).limit(EXAMPLES).toList(),
                scan.excluded().stream().limit(EXAMPLES).toList(), scan.ignored().stream().limit(EXAMPLES).toList(),
                scan.limitReached());
    }

    /**
     * Ajoute une racine Confluence après son aperçu (FR-004) : refusée si l'aperçu l'est, avec la
     * raison ; garde l'identifiant et le titre de la page.
     */
    public DocSource addConfluence(UUID projectId, String location, Reliability reliability) {
        requireProject(projectId);
        List<DocSource> existing = docSources.list(projectId);
        DocSource.requireRoomFor(existing.size());
        ConfluencePreview preview = previewConfluence(projectId, location);
        if (preview.problem() != null) {
            if ("CONFLUENCE_NOT_CONFIGURED".equals(preview.problem())) {
                throw new DomainException(ErrorCode.CONFLUENCE_NOT_CONFIGURED);
            }
            throw new DomainException(ErrorCode.VALIDATION_FAILED, refusal(preview));
        }
        if (existing.stream().anyMatch(s -> preview.pageId().equals(s.pageId()))) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "Cette page Confluence est déjà une racine du projet : « " + preview.title() + " ».");
        }
        DocSource source = DocSource.confluence(ids.next(), projectId, location, preview.pageId(), preview.title(),
                reliability);
        docSources.save(source);
        return source;
    }

    /** Aperçu d'une racine : page résolue, titre et nombre de pages, sans rien indexer. */
    public ConfluencePreview previewConfluence(UUID projectId, String location) {
        requireProject(projectId);
        Optional<ConfluenceClient.Endpoint> endpoint = confluence == null ? Optional.empty() : confluence.endpoint();
        if (endpoint.isEmpty()) {
            return ConfluencePreview.refused("CONFLUENCE_NOT_CONFIGURED");
        }
        // l'adresse est validée comme un lien http ou https complet avant tout appel
        DocSource.create(ids.next(), projectId, DocSource.Kind.URL, location);
        try {
            String id = client.resolve(endpoint.get(), location.strip());
            ConfluenceClient.PageInfo root = client.page(endpoint.get(), id);
            int count = 1 + client.descendants(endpoint.get(), id, 0, 1).total();
            if (count + otherPages(projectId, id) > MAX_CONFLUENCE_PAGES) {
                return new ConfluencePreview(count, root.title(), id, "TOO_LARGE");
            }
            return new ConfluencePreview(count, root.title(), id, null);
        } catch (ConfluenceException e) {
            return ConfluencePreview.refused(switch (e.failure()) {
                case OTHER_HOST -> "OTHER_HOST";
                case NOT_A_PAGE, NOT_FOUND, NOT_CONFLUENCE, REJECTED -> "NOT_A_PAGE";
                case UNAUTHORIZED, FORBIDDEN -> "ACCESS_DENIED";
                case UNREACHABLE, RATE_LIMITED, TLS_ERROR -> "UNREACHABLE";
            });
        }
    }

    /** Pages déjà lues des autres racines du projet, chacune comptée une fois. */
    private int otherPages(UUID projectId, String rootPageId) {
        if (pages == null) {
            return 0;
        }
        return (int) pages.roots(projectId).keySet().stream().filter(id -> !id.equals(rootPageId)).count();
    }

    static String refusal(ConfluencePreview preview) {
        return switch (preview.problem()) {
            case "OTHER_HOST" -> "Cette page n'est pas sur le serveur Confluence déclaré dans Configuration.";
            case "NOT_A_PAGE" -> "Cette adresse ne désigne pas une page Confluence lisible. Copiez l'adresse de la"
                    + " page depuis votre navigateur.";
            case "ACCESS_DENIED" -> "Votre jeton ne donne pas accès à cette page.";
            case "TOO_LARGE" -> "Cette racine compte " + preview.pages() + " pages : un projet en indexe "
                    + MAX_CONFLUENCE_PAGES + " au plus. Choisissez une page plus basse dans l'arbre.";
            default -> "Confluence ne répond pas. Vérifiez votre connexion, puis réessayez.";
        };
    }

    /** Niveau propre d'une racine Confluence (FR-016a) ; {@code null} : celui de Confluence. */
    public DocSource setReliability(UUID projectId, UUID sourceId, Reliability level) {
        DocSource changed = owned(projectId, sourceId).withReliability(level);
        docSources.save(changed);
        return changed;
    }

    /** Supprime la source ; ses éléments quittent l'index, quel que soit son type (006, 010). */
    public void delete(UUID projectId, UUID sourceId) {
        DocSource source = owned(projectId, sourceId);
        if (source.kind() == DocSource.Kind.CONFLUENCE) {
            keepSharedPages(projectId, source.id());
        }
        docSources.delete(source.id());
        if (source.kind() == DocSource.Kind.DOCUMENTS) {
            index.retainUnder(projectId, DocumentFolder.prefix(source), Set.of());
        } else {
            index.replaceSource(projectId, source.id(), Set.of());
        }
    }

    /**
     * Les pages qu'une autre racine atteint encore lui sont rattachées avant la suppression : elles
     * restent dans l'index (016, R3).
     */
    private void keepSharedPages(UUID projectId, UUID removed) {
        if (pages == null) {
            return;
        }
        Map<String, Set<UUID>> roots = pages.roots(projectId);
        Map<UUID, Set<KnowledgeIndex.Key>> byRoot = new HashMap<>();
        for (KnowledgeKind kind : List.of(KnowledgeKind.DOC_SECTION, KnowledgeKind.ADR)) {
            for (KnowledgeIndex.Key key : index.hashes(projectId, kind).keySet()) {
                ConfluenceClient.pageId(key.ref()).map(roots::get).flatMap(rs -> rs.stream()
                        .filter(r -> !r.equals(removed)).findFirst())
                        .ifPresent(r -> byRoot.computeIfAbsent(r, x -> new HashSet<>()).add(key));
            }
        }
        byRoot.forEach((root, keys) -> index.attach(projectId, root, keys));
    }

    private DocSource owned(UUID projectId, UUID sourceId) {
        requireProject(projectId);
        return docSources.find(sourceId).filter(s -> s.projectId().equals(projectId))
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    }

    private void requireProject(UUID projectId) {
        if (projects.find(projectId).isEmpty()) {
            throw new DomainException(ErrorCode.PROJECT_NOT_FOUND);
        }
    }
}
