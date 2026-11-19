package fr.kawamind.support;

import fr.kawamind.domain.knowledge.KnowledgeKind;
import fr.kawamind.domain.port.ConfluenceClient;
import fr.kawamind.domain.port.DocumentationReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Confluence simulé (016) : un arbre de pages (parent, titre, version, date, texte), des pages
 * restreintes, une panne. Chaque appel est noté, pour compter les lectures de contenu.
 */
public class FakeConfluenceClient implements ConfluenceClient {

    public static final String SERVER = "https://wiki.example.com/confluence";

    public record Node(String id, String parent, String title, int version, Instant updatedAt, String text) {
    }

    public final Map<String, Node> nodes = new LinkedHashMap<>();
    public final List<String> forbidden = new ArrayList<>();
    public final List<String> calls = new ArrayList<>();
    public Failure down;
    /** Pages rendues au plus par appel de la descendance (le vrai serveur en rend 100). */
    public int pageSize = 5;

    public FakeConfluenceClient page(String id, String parent, String title, int version, Instant at, String text) {
        nodes.put(id, new Node(id, parent, title, version, at, text));
        return this;
    }

    private void check() {
        if (down != null) {
            throw new ConfluenceException(down);
        }
    }

    @Override
    public String currentUser(Endpoint endpoint) {
        calls.add("user");
        check();
        return "Ada";
    }

    @Override
    public Optional<String> serverVersion(Endpoint endpoint) {
        return Optional.of("8.5.4");
    }

    @Override
    public String resolve(Endpoint endpoint, String address) {
        calls.add("resolve");
        check();
        if (!address.startsWith(SERVER)) {
            throw new ConfluenceException(Failure.OTHER_HOST);
        }
        return ConfluenceClient.pageId(address).orElseThrow(() -> new ConfluenceException(Failure.NOT_A_PAGE));
    }

    @Override
    public PageInfo page(Endpoint endpoint, String id) {
        calls.add("page " + id);
        check();
        Node n = nodes.get(id);
        if (n == null) {
            throw new ConfluenceException(Failure.NOT_FOUND, 404, null);
        }
        return new PageInfo(n.id(), n.title(), n.version(), n.updatedAt());
    }

    @Override
    public Descendants descendants(Endpoint endpoint, String id, int start, int limit) {
        calls.add("descendants " + id + " " + start);
        check();
        List<PageInfo> all = new ArrayList<>();
        // enfants de chaque page, calculés une fois par appel : une descendance de 2 000 pages reste linéaire
        Map<String, List<Node>> children = new java.util.HashMap<>();
        nodes.values().stream().filter(n -> n.parent() != null)
                .forEach(n -> children.computeIfAbsent(n.parent(), k -> new ArrayList<>()).add(n));
        collect(id, children, all);
        List<PageInfo> slice = all.subList(Math.min(start, all.size()), Math.min(all.size(), start + Math.min(limit, pageSize)));
        return new Descendants(slice, all.size());
    }

    private static void collect(String parent, Map<String, List<Node>> children, List<PageInfo> out) {
        for (Node n : children.getOrDefault(parent, List.of())) {
            out.add(new PageInfo(n.id(), n.title(), n.version(), n.updatedAt()));
            collect(n.id(), children, out);
        }
    }

    @Override
    public DocumentationReader.Page content(Endpoint endpoint, String id, String ref) {
        calls.add("content " + id);
        check();
        if (forbidden.contains(id)) {
            throw new ConfluenceException(Failure.FORBIDDEN, 403, null);
        }
        Node n = nodes.get(id);
        if (n == null) {
            throw new ConfluenceException(Failure.NOT_FOUND, 404, null);
        }
        return new DocumentationReader.Page(ref, n.title(), n.text(), List.of(new DocumentationReader.Section(ref + "#0",
                0, n.title(), n.text(), KnowledgeKind.DOC_SECTION, null, null)), n.updatedAt());
    }

    public long contentReads() {
        return calls.stream().filter(c -> c.startsWith("content ")).count();
    }
}
