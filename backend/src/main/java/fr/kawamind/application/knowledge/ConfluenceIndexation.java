package fr.kawamind.application.knowledge;

import fr.kawamind.application.settings.ConfluenceAccess;
import fr.kawamind.domain.knowledge.KnowledgeItem;
import fr.kawamind.domain.knowledge.KnowledgeKind;
import fr.kawamind.domain.port.ClockPort;
import fr.kawamind.domain.port.ConfluenceClient;
import fr.kawamind.domain.port.ConfluenceClient.ConfluenceException;
import fr.kawamind.domain.port.ConfluencePageStates;
import fr.kawamind.domain.port.ConfluencePageStates.PageState;
import fr.kawamind.domain.port.DocSourceRepository;
import fr.kawamind.domain.port.DocumentationReader;
import fr.kawamind.domain.port.DocumentationReader.Section;
import fr.kawamind.domain.port.KnowledgeIndex;
import fr.kawamind.domain.port.KnowledgeIndex.Key;
import fr.kawamind.domain.project.DocSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Lecture des racines Confluence d'un projet (016, R2, R3) : la racine et toute sa descendance par
 * une recherche {@code ancestor=}, puis le contenu des seules pages nouvelles ou dont la version
 * change (tout en indexation complète). Une page atteinte par deux racines est lue une fois et
 * indexée sous son adresse canonique. Une racine lue en entier remplace ses éléments ; injoignable,
 * ses pages restent. Aucun contenu n'est journalisé.
 */
public class ConfluenceIndexation {

    /** Pages demandées par appel de la descendance. */
    static final int PAGE = 100;
    /** Pages Confluence indexées au plus par projet, toutes racines (R10). */
    static final int MAX_PAGES = fr.kawamind.application.context.ManageDocSources.MAX_CONFLUENCE_PAGES;
    static final String NOT_CONFIGURED = "CONFLUENCE_NOT_CONFIGURED";

    /** Bilan de la lecture : éléments à garder par sorte, et lecture complète de toutes les racines. */
    public record Outcome(Set<Key> sections, Set<Key> adrs, boolean complete) {

        static final Outcome NONE = new Outcome(Set.of(), Set.of(), true);
    }

    private final ConfluenceAccess access;
    private final ConfluenceClient client;
    private final ConfluencePageStates states;
    private final DocSourceRepository docSources;
    private final KnowledgeIndex index;
    private final ClockPort clock;

    public ConfluenceIndexation(ConfluenceAccess access, ConfluenceClient client, ConfluencePageStates states,
            DocSourceRepository docSources, KnowledgeIndex index, ClockPort clock) {
        this.access = access;
        this.client = client;
        this.states = states;
        this.docSources = docSources;
        this.index = index;
        this.clock = clock;
    }

    /** Lecture d'une racine : ses pages dans l'ordre, ou l'échec qui l'a arrêtée. */
    private record Listing(List<ConfluenceClient.PageInfo> pages, ConfluenceException failure, boolean limited) {
    }

    public Outcome run(UUID projectId, List<DocSource> roots, SynchronizeKnowledge.Mode mode,
            IndexationProgress progress) {
        if (roots.isEmpty()) {
            return Outcome.NONE;
        }
        Optional<ConfluenceClient.Endpoint> endpoint = access.endpoint();
        if (endpoint.isEmpty()) {
            roots.forEach(r -> progress.error(new IndexationError(IndexationError.Kind.SOURCE, r.location(),
                    NOT_CONFIGURED, null, "Aucun serveur Confluence déclaré : les pages indexées sont gardées.")));
            return new Outcome(keysOf(projectId, roots), Set.of(), false);
        }
        ConfluenceClient.Endpoint server = endpoint.get();
        // pages des autres racines (hors de cette lecture), pour la limite du projet
        Set<String> others = new HashSet<>();
        Set<UUID> reading = roots.stream().map(DocSource::id).collect(java.util.stream.Collectors.toSet());
        states.roots(projectId).forEach((page, rs) -> {
            if (rs.stream().anyMatch(r -> !reading.contains(r))) {
                others.add(page);
            }
        });
        Map<String, Set<Key>> indexed = indexedByPage(projectId);
        Map<String, Set<Key>> read = new HashMap<>();
        Set<String> counted = new HashSet<>(others);
        Set<Key> keepSections = new HashSet<>();
        Set<Key> keepAdrs = new HashSet<>();
        Map<UUID, Set<Key>> keep = new LinkedHashMap<>();
        Map<UUID, Map<String, PageState>> next = new LinkedHashMap<>();
        Set<UUID> complete = new LinkedHashSet<>();
        Map<UUID, Integer> denied = new HashMap<>();
        boolean all = true;
        for (DocSource root : roots) {
            Listing listing = list(server, root, counted, progress);
            if (listing.failure() != null) {
                all = false;
                failed(projectId, root, listing.failure(), progress);
                indexedKeys(root, indexed).forEach(k -> (k.kind() == KnowledgeKind.ADR ? keepAdrs : keepSections).add(k));
                continue;
            }
            Map<String, PageState> known = states.of(root.id());
            Map<String, PageState> pages = new LinkedHashMap<>();
            Set<Key> rootKeys = new HashSet<>();
            boolean whole = !listing.limited();
            int done = 0;
            for (ConfluenceClient.PageInfo page : listing.pages()) {
                if (progress.cancelled()) {
                    throw new IndexationProgress.Cancelled();
                }
                String ref = ConfluenceClient.pageRef(server.serverUrl(), page.id());
                Set<Key> keys = read.get(page.id());
                if (keys == null) {
                    Set<Key> existing = indexed.getOrDefault(page.id(), Set.of());
                    PageState before = known.get(page.id());
                    boolean stale = mode == SynchronizeKnowledge.Mode.FULL || before == null
                            || before.version() != page.version() || existing.isEmpty();
                    if (stale) {
                        progress.advance(page.title(), done, listing.pages().size());
                        try {
                            keys = save(projectId, client.content(server, page.id(), ref), page.updatedAt());
                        } catch (ConfluenceException e) {
                            if (e.failure() == ConfluenceClient.Failure.FORBIDDEN
                                    || e.failure() == ConfluenceClient.Failure.UNAUTHORIZED) {
                                // page restreinte : ignorée et comptée, son indexation précédente reste
                                progress.error(new IndexationError(IndexationError.Kind.PAGE, ref, "ACCESS_DENIED",
                                        e.status(), null));
                                rootKeys.addAll(existing);
                                denied.merge(root.id(), 1, Integer::sum);
                                if (before != null) {
                                    pages.put(page.id(), before);
                                }
                                done++;
                                continue;
                            }
                            if (e.failure() == ConfluenceClient.Failure.NOT_FOUND) {
                                // supprimée entre la liste et la lecture : retirée
                                done++;
                                continue;
                            }
                            progress.error(new IndexationError(IndexationError.Kind.SOURCE, root.location(),
                                    e.failure().name(), e.status(), null));
                            whole = false;
                            rootKeys.addAll(indexedKeys(root, indexed));
                            break;
                        }
                    } else {
                        keys = existing;
                    }
                    read.put(page.id(), keys);
                }
                rootKeys.addAll(keys);
                pages.put(page.id(), new PageState(page.version(), page.updatedAt()));
                done++;
            }
            rootKeys.forEach(k -> (k.kind() == KnowledgeKind.ADR ? keepAdrs : keepSections).add(k));
            keep.put(root.id(), rootKeys);
            next.put(root.id(), whole ? pages : merged(known, pages));
            if (whole) {
                complete.add(root.id());
            } else {
                all = false;
            }
        }
        // d'abord rattacher chaque page à une racine qui la liste, puis retirer ce qui est sorti :
        // une page sortie d'une racine mais encore sous une autre n'est jamais supprimée
        keep.forEach((root, keys) -> index.attach(projectId, root, keys));
        Instant now = clock.now();
        for (DocSource root : roots) {
            if (!keep.containsKey(root.id())) {
                continue;
            }
            if (complete.contains(root.id())) {
                index.replaceSource(projectId, root.id(), keep.get(root.id()));
                docSources.find(root.id()).ifPresent(s -> docSources.save(s.read(now,
                        new DocSource.ReadSummary(next.get(root.id()).size(), denied.getOrDefault(root.id(), 0),
                                false))));
            }
            states.replace(root.id(), next.get(root.id()));
        }
        return new Outcome(keepSections, keepAdrs, all);
    }

    /** La racine puis sa descendance, dans la limite du projet ; un échec arrête la racine. */
    private Listing list(ConfluenceClient.Endpoint server, DocSource root, Set<String> counted,
            IndexationProgress progress) {
        List<ConfluenceClient.PageInfo> pages = new ArrayList<>();
        try {
            String id = root.pageId() != null ? root.pageId() : client.resolve(server, root.location());
            pages.add(client.page(server, id));
            int start = 0;
            while (true) {
                ConfluenceClient.Descendants d = client.descendants(server, id, start, PAGE);
                pages.addAll(d.pages());
                start += d.pages().size();
                if (d.pages().isEmpty() || start >= d.total()) {
                    break;
                }
            }
        } catch (ConfluenceException e) {
            return new Listing(List.of(), e, false);
        }
        List<ConfluenceClient.PageInfo> kept = new ArrayList<>();
        boolean limited = false;
        for (ConfluenceClient.PageInfo page : new LinkedHashMap<String, ConfluenceClient.PageInfo>(
                pages.stream().collect(java.util.stream.Collectors.toMap(ConfluenceClient.PageInfo::id,
                        Function.identity(), (a, b) -> a, LinkedHashMap::new))).values()) {
            if (!counted.contains(page.id()) && counted.size() >= MAX_PAGES) {
                limited = true;
                break;
            }
            counted.add(page.id());
            kept.add(page);
        }
        if (limited) {
            progress.error(new IndexationError(IndexationError.Kind.SOURCE, root.location(),
                    DocumentationReader.UnreadReason.LIMIT_REACHED.name(), null, null));
        }
        return new Listing(kept, null, limited);
    }

    /**
     * Racine en échec : disparue ({@code 404}), ses pages sont retirées ; injoignable ou jeton
     * refusé, elles restent, et l'erreur dit l'heure de la dernière lecture réussie.
     */
    private void failed(UUID projectId, DocSource root, ConfluenceException e, IndexationProgress progress) {
        boolean gone = e.failure() == ConfluenceClient.Failure.NOT_FOUND;
        String last = docSources.find(root.id()).map(DocSource::lastReadAt).map(Instant::toString).orElse(null);
        progress.error(new IndexationError(IndexationError.Kind.SOURCE, root.location(), e.failure().name(),
                e.status(), gone ? "Racine introuvable : ses pages sont retirées de l'index."
                        : last == null ? null : "Dernière lecture réussie : " + last));
        if (gone) {
            index.replaceSource(projectId, root.id(), Set.of());
            states.replace(root.id(), Map.of());
        }
    }

    /** Écrit les sections d'une page ; renvoie leurs clés. */
    private Set<Key> save(UUID projectId, DocumentationReader.Page page, Instant updatedAt) {
        Set<Key> keys = new LinkedHashSet<>();
        for (Section section : page.sections()) {
            String title = truncate(section.title(), KnowledgeItem.MAX_TITLE);
            String body = truncate(section.body(), KnowledgeItem.MAX_BODY);
            KnowledgeItem item = section.kind() == KnowledgeKind.ADR
                    ? KnowledgeItem.adr(projectId, section.ref(), section.chunk(), title, body, section.adr(), updatedAt,
                            null)
                    : KnowledgeItem.docSection(projectId, section.ref(), section.chunk(), title, body, section.role(),
                            updatedAt, null);
            index.save(item, null, null);
            keys.add(Key.of(item));
        }
        return keys;
    }

    /** Éléments indexés de chaque page Confluence, par identifiant de page. */
    private Map<String, Set<Key>> indexedByPage(UUID projectId) {
        Map<String, Set<Key>> out = new HashMap<>();
        for (KnowledgeKind kind : List.of(KnowledgeKind.DOC_SECTION, KnowledgeKind.ADR)) {
            index.hashes(projectId, kind).keySet().forEach(k -> ConfluenceClient.pageId(k.ref())
                    .ifPresent(id -> out.computeIfAbsent(id, x -> new HashSet<>()).add(k)));
        }
        return out;
    }

    private Set<Key> indexedKeys(DocSource root, Map<String, Set<Key>> indexed) {
        Set<Key> out = new HashSet<>();
        states.of(root.id()).keySet().forEach(id -> out.addAll(indexed.getOrDefault(id, Set.of())));
        return out;
    }

    private Set<Key> keysOf(UUID projectId, List<DocSource> roots) {
        Map<String, Set<Key>> indexed = indexedByPage(projectId);
        Set<Key> out = new HashSet<>();
        roots.forEach(r -> out.addAll(indexedKeys(r, indexed)));
        return out;
    }

    private static Map<String, PageState> merged(Map<String, PageState> known, Map<String, PageState> read) {
        Map<String, PageState> out = new LinkedHashMap<>(known);
        out.putAll(read);
        return out;
    }

    private static String truncate(String text, int max) {
        String t = text == null ? "" : text;
        return t.length() <= max ? t : t.substring(0, max);
    }
}
