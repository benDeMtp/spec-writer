package dev.specwriter.infrastructure.docs;

import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.DocumentationReader;
import dev.specwriter.domain.project.DocSource;
import dev.specwriter.infrastructure.platform.ProxySettings;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.jboss.logging.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

/**
 * Lecture d'un site Antora (research.md R3 de 002) : le sitemap à la racine du lien (ou l'index de
 * sitemaps de plusieurs composants), à défaut les liens du même hôte et sous le même chemin, ou les
 * fichiers {@code *.html} d'un dossier. Seul le contenu de {@code article.doc} est gardé. Aucune
 * redirection vers un autre hôte n'est suivie ; le contenu lu n'est jamais journalisé.
 */
public class AntoraReader implements DocumentationReader {

    private static final Logger LOG = Logger.getLogger(AntoraReader.class);
    private static final int MAX_REDIRECTS = 5;
    private static final int MAX_SITEMAPS = 50;

    /** Bornes de lecture (FR-013e). */
    public record Limits(int maxPages, Duration maxDuration, int maxPageBytes, Duration pageTimeout, int parallelism) {

        public static final Limits DEFAULT = new Limits(1_000, Duration.ofMinutes(10), 2 * 1024 * 1024,
                Duration.ofSeconds(15), 4);
    }

    /** Réponse d'une page : son contenu, ou le motif pour lequel elle n'a pas été lue. */
    private record Fetched(URI uri, String body, UnreadReason failure, Integer status) {

        Fetched(URI uri, String body, UnreadReason failure) {
            this(uri, body, failure, null);
        }
    }

    private static final class TooLarge extends IOException {
        private static final long serialVersionUID = 1L;
    }

    private final ClockPort clock;
    private final Limits limits;
    private final HttpClient http;

    public AntoraReader(ProxySettings proxy, ClockPort clock, Limits limits) {
        this.clock = clock;
        this.limits = limits;
        HttpClient.Builder builder = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5))
                .proxy(new SettingsProxySelector(proxy));
        authenticator(proxy).ifPresent(builder::authenticator);
        this.http = builder.build();
    }

    @Override
    public Availability count(DocSource source) {
        if (source.kind() == DocSource.Kind.FOLDER) {
            FolderScan scan = scan(Path.of(source.location()));
            return scan.html().isEmpty() ? new Availability(0, scan.problem()) : Availability.of(scan.html().size());
        }
        return Availability.of(sitemapPages(root(source)).map(List::size).orElse(0));
    }

    @Override
    public Result read(DocSource source, Listener listener) {
        Instant deadline = clock.now().plus(limits.maxDuration());
        Result result = source.kind() == DocSource.Kind.FOLDER ? readFolder(Path.of(source.location()), deadline, listener)
                : readSite(root(source), deadline, listener);
        result.unread().stream().filter(u -> u.reason() != UnreadReason.NOT_ANTORA).forEach(u -> LOG.warnf(
                "Documentation : %s non lue (%s%s)", u.location(), u.reason(),
                u.httpStatus() == null ? "" : ", HTTP " + u.httpStatus()));
        return result;
    }

    private static Unread unread(String location, Fetched f) {
        return new Unread(location, f.failure(), f.status());
    }

    // ---------- Site distant ou local ----------

    private Result readSite(URI root, Instant deadline, Listener listener) {
        Optional<List<URI>> fromSitemap = sitemapPages(root);
        if (fromSitemap.isPresent()) {
            return readPages(fromSitemap.get(), deadline, listener);
        }
        return crawl(root, deadline, listener);
    }

    private Result readPages(List<URI> uris, Instant deadline, Listener listener) {
        List<Page> pages = Collections.synchronizedList(new ArrayList<>());
        List<Unread> unread = Collections.synchronizedList(new ArrayList<>());
        List<URI> selected = uris.size() > limits.maxPages() ? uris.subList(0, limits.maxPages()) : uris;
        boolean[] stopped = {uris.size() > limits.maxPages()};
        Semaphore slots = new Semaphore(limits.parallelism());
        AtomicInteger done = new AtomicInteger();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> running = new ArrayList<>();
            for (URI uri : selected) {
                slots.acquireUninterruptibly();
                if (listener.cancelled()) {
                    slots.release();
                    break;
                }
                if (clock.now().isAfter(deadline)) {
                    slots.release();
                    stopped[0] = true;
                    break;
                }
                running.add(executor.submit(() -> {
                    try {
                        listener.reading(uri.toString(), done.getAndIncrement(), selected.size());
                        Fetched f = fetch(uri);
                        if (f.failure() != null) {
                            unread.add(unread(uri.toString(), f));
                        } else {
                            page(uri.toString(), f.body(), f.uri().toString()).ifPresentOrElse(pages::add,
                                    () -> unread.add(new Unread(uri.toString(), UnreadReason.NOT_ANTORA)));
                        }
                    } finally {
                        slots.release();
                    }
                }));
            }
            running.forEach(AntoraReader::await);
        }
        return result(order(pages, selected), unread, stopped[0], selected.isEmpty() ? null : selected.getFirst());
    }

    /** Sans sitemap : les liens du même hôte et sous le même chemin, depuis la page d'accueil. */
    private Result crawl(URI root, Instant deadline, Listener listener) {
        List<Page> pages = new ArrayList<>();
        List<Unread> unread = new ArrayList<>();
        Set<URI> seen = new LinkedHashSet<>();
        List<URI> queue = new ArrayList<>(List.of(root));
        boolean stopped = false;
        String prefix = root.getPath() == null || root.getPath().isEmpty() ? "/" : root.getPath();
        while (!queue.isEmpty()) {
            URI uri = queue.removeFirst();
            if (!seen.add(withoutFragment(uri))) {
                continue;
            }
            if (listener.cancelled()) {
                break;
            }
            if (pages.size() + unread.size() >= limits.maxPages() || clock.now().isAfter(deadline)) {
                stopped = true;
                break;
            }
            listener.reading(uri.toString(), pages.size() + unread.size(), 0);
            Fetched f = fetch(uri);
            if (f.failure() != null) {
                if (!uri.equals(root) || pages.isEmpty()) {
                    unread.add(unread(uri.toString(), f));
                }
                continue;
            }
            Document doc = Jsoup.parse(f.body(), f.uri().toString());
            for (Element a : doc.select("a[href]")) {
                URI link = parse(a.absUrl("href"));
                if (link != null && sameHost(root, link) && link.getPath() != null && link.getPath().startsWith(prefix)
                        && link.getPath().endsWith(".html") && !seen.contains(withoutFragment(link))) {
                    queue.add(withoutFragment(link));
                }
            }
            boolean redirectedRoot = uri.equals(root) && !f.uri().equals(root);
            if (redirectedRoot) {
                seen.add(withoutFragment(f.uri()));
            }
            Optional<Page> page = page(redirectedRoot ? f.uri().toString() : uri.toString(), f.body(), f.uri().toString());
            if (page.isPresent()) {
                pages.add(page.get());
            } else if (!uri.equals(root)) {
                unread.add(new Unread(uri.toString(), UnreadReason.NOT_ANTORA));
            }
        }
        return result(pages, unread, stopped, root);
    }

    private Optional<List<URI>> sitemapPages(URI root) {
        Fetched index = fetch(root.resolve("sitemap.xml"));
        if (index.failure() != null) {
            return Optional.empty();
        }
        Document xml = Jsoup.parse(index.body(), index.uri().toString(), Parser.xmlParser());
        List<URI> pages = new ArrayList<>();
        if (!xml.select("sitemapindex").isEmpty()) {
            for (Element loc : xml.select("sitemap > loc").stream().limit(MAX_SITEMAPS).toList()) {
                URI sub = parse(loc.text().strip());
                if (sub == null || !sameHost(root, sub)) {
                    continue;
                }
                Fetched child = fetch(sub);
                if (child.failure() == null) {
                    pages.addAll(locs(Jsoup.parse(child.body(), sub.toString(), Parser.xmlParser()), root));
                }
            }
        } else {
            pages.addAll(locs(xml, root));
        }
        return Optional.of(pages);
    }

    private static List<URI> locs(Document urlset, URI root) {
        return urlset.select("url > loc").stream().map(l -> parse(l.text().strip())).filter(Objects::nonNull)
                .filter(u -> sameHost(root, u)).distinct().toList();
    }

    /** GET avec redirections suivies sur le même hôte seulement, et corps borné. */
    private Fetched fetch(URI start) {
        URI uri = start;
        try {
            for (int i = 0; i <= MAX_REDIRECTS; i++) {
                HttpRequest request = HttpRequest.newBuilder(uri).timeout(limits.pageTimeout()).GET().build();
                HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
                int status = response.statusCode();
                if (status >= 300 && status < 400) {
                    response.body().close();
                    URI current = uri;
                    URI next = response.headers().firstValue("Location").map(current::resolve).orElse(null);
                    if (next == null || !sameHost(start, next)) {
                        return new Fetched(uri, null, UnreadReason.REDIRECT_OTHER_HOST, status);
                    }
                    uri = next;
                    continue;
                }
                if (status != 200) {
                    response.body().close();
                    return new Fetched(uri, null, UnreadReason.HTTP_ERROR, status);
                }
                return new Fetched(uri, bounded(response.body()), null);
            }
            return new Fetched(uri, null, UnreadReason.HTTP_ERROR);
        } catch (TooLarge e) {
            return new Fetched(uri, null, UnreadReason.TOO_LARGE);
        } catch (IOException e) {
            LOG.debugf("Documentation injoignable (%s)", e.getClass().getSimpleName());
            return new Fetched(uri, null, UnreadReason.UNREACHABLE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Fetched(uri, null, UnreadReason.UNREACHABLE);
        }
    }

    private String bounded(InputStream in) throws IOException {
        try (in) {
            byte[] bytes = in.readNBytes(limits.maxPageBytes() + 1);
            if (bytes.length > limits.maxPageBytes()) {
                throw new TooLarge();
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    // ---------- Dossier local ----------

    private Result readFolder(Path folder, Instant deadline, Listener listener) {
        List<Page> pages = new ArrayList<>();
        List<Unread> unread = new ArrayList<>();
        boolean stopped = false;
        FolderScan scan = scan(folder);
        if (scan.html().isEmpty() && scan.problem() != UnreadReason.NO_HTML && scan.problem() != UnreadReason.ADOC_SOURCES) {
            return new Result(List.of(), List.of(new Unread(folder.toString(), scan.problem())));
        }
        unread.addAll(scan.unreadable());
        List<Path> files = scan.html();
        for (Path file : files) {
            String ref = folder.relativize(file).toString().replace('\\', '/');
            if (listener.cancelled()) {
                break;
            }
            if (pages.size() + unread.size() >= limits.maxPages() || clock.now().isAfter(deadline)) {
                stopped = true;
                break;
            }
            listener.reading(ref, pages.size() + unread.size(), Math.min(files.size(), limits.maxPages()));
            try {
                if (Files.size(file) > limits.maxPageBytes()) {
                    unread.add(new Unread(ref, UnreadReason.TOO_LARGE));
                    continue;
                }
                String body = Files.readString(file, StandardCharsets.UTF_8);
                page(ref, body, file.toUri().toString()).ifPresentOrElse(pages::add,
                        () -> unread.add(new Unread(ref, UnreadReason.NOT_ANTORA)));
            } catch (AccessDeniedException e) {
                unread.add(new Unread(ref, UnreadReason.ACCESS_DENIED));
            } catch (IOException e) {
                unread.add(new Unread(ref, UnreadReason.UNREADABLE));
            }
        }
        return result(pages, unread, stopped, null);
    }

    /**
     * Pages HTML du dossier, sous-dossiers illisibles (signalés sans arrêter le parcours) et, sans
     * aucune page, le motif : dossier absent, fichier, illisible, sources AsciiDoc ou vide.
     */
    record FolderScan(List<Path> html, List<Unread> unreadable, UnreadReason problem) {
    }

    static FolderScan scan(Path folder) {
        if (!Files.exists(folder)) {
            return new FolderScan(List.of(), List.of(), UnreadReason.FOLDER_NOT_FOUND);
        }
        if (!Files.isDirectory(folder)) {
            return new FolderScan(List.of(), List.of(), UnreadReason.NOT_A_FOLDER);
        }
        List<Path> html = new ArrayList<>();
        List<Unread> unreadable = new ArrayList<>();
        boolean[] adoc = {false};
        try {
            Files.walkFileTree(folder, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    Path fileName = file.getFileName();
                    String name = fileName == null ? "" : fileName.toString().toLowerCase(Locale.ROOT);
                    if (attrs.isRegularFile() && name.endsWith(".html")) {
                        html.add(file);
                    } else if (name.endsWith(".adoc")) {
                        adoc[0] = true;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException e) {
                    unreadable.add(new Unread(folder.relativize(file).toString().replace('\\', '/'),
                            e instanceof AccessDeniedException ? UnreadReason.ACCESS_DENIED : UnreadReason.UNREADABLE));
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            return new FolderScan(List.of(), List.of(), e instanceof AccessDeniedException ? UnreadReason.ACCESS_DENIED
                    : UnreadReason.UNREADABLE);
        }
        html.sort(null);
        UnreadReason problem = null;
        if (html.isEmpty()) {
            boolean rootDenied = unreadable.stream().anyMatch(u -> u.location().isEmpty());
            problem = rootDenied ? UnreadReason.ACCESS_DENIED : adoc[0] ? UnreadReason.ADOC_SOURCES : UnreadReason.NO_HTML;
        }
        return new FolderScan(List.copyOf(html), List.copyOf(unreadable), problem);
    }

    // ---------- Contenu ----------

    /** Le contenu de {@code article.doc}, en texte structuré ; vide si la page n'est pas une page Antora. */
    static Optional<Page> page(String ref, String html, String baseUri) {
        Document doc = Jsoup.parse(html, baseUri);
        Element article = doc.selectFirst("article.doc");
        if (article == null) {
            return Optional.empty();
        }
        Element h1 = article.selectFirst("h1");
        String title = h1 != null ? h1.text() : doc.title();
        return Optional.of(new Page(ref, title, StructuredText.of(article)));
    }

    private static Result result(List<Page> pages, List<Unread> unread, boolean stopped, URI origin) {
        List<Unread> all = new ArrayList<>(unread);
        if (stopped) {
            all.add(new Unread(origin == null ? "" : origin.toString(), UnreadReason.LIMIT_REACHED));
        }
        return new Result(List.copyOf(pages), List.copyOf(all));
    }

    /** Les pages dans l'ordre du sitemap, malgré la lecture en parallèle. */
    private static List<Page> order(List<Page> pages, List<URI> uris) {
        List<String> refs = uris.stream().map(URI::toString).toList();
        return pages.stream().sorted(java.util.Comparator.comparingInt(p -> refs.indexOf(p.ref()))).toList();
    }

    private static URI root(DocSource source) {
        URI uri = URI.create(source.location());
        String path = uri.getPath() == null || uri.getPath().isEmpty() ? "/" : uri.getPath();
        return uri.resolve(path.endsWith("/") ? path : path.substring(0, path.lastIndexOf('/') + 1));
    }

    private static boolean sameHost(URI a, URI b) {
        return a.getHost() != null && a.getHost().equalsIgnoreCase(b.getHost()) && a.getPort() == b.getPort()
                && Objects.equals(a.getScheme(), b.getScheme());
    }

    private static URI withoutFragment(URI uri) {
        try {
            return new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), uri.getQuery(), null);
        } catch (java.net.URISyntaxException e) {
            return uri;
        }
    }

    private static URI parse(String raw) {
        try {
            return raw == null || raw.isBlank() ? null : URI.create(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static void await(Future<?> f) {
        try {
            f.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (java.util.concurrent.ExecutionException e) {
            LOG.debugf("Lecture d'une page interrompue (%s)", e.getCause().getClass().getSimpleName());
        }
    }

    // ---------- Proxy d'entreprise (T139 de 001) ----------

    /** Proxy de {@link ProxySettings}, et jamais celui des propriétés système du JDK. */
    private static final class SettingsProxySelector extends ProxySelector {
        private final ProxySettings settings;

        SettingsProxySelector(ProxySettings settings) {
            this.settings = settings;
        }

        @Override
        public List<Proxy> select(URI uri) {
            return settings.forUri(uri)
                    .map(p -> List.of(new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(p.host(), p.port()))))
                    .orElse(List.of(Proxy.NO_PROXY));
        }

        @Override
        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            // rien à mémoriser : la page est signalée comme injoignable
        }
    }

    private static Optional<Authenticator> authenticator(ProxySettings settings) {
        return Stream.of(URI.create("https://proxy.invalid"), URI.create("http://proxy.invalid"))
                .map(settings::forUri).flatMap(Optional::stream).filter(p -> p.user() != null).findFirst()
                .map(p -> new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return getRequestorType() == RequestorType.PROXY
                                ? new PasswordAuthentication(p.user(), (p.password() == null ? "" : p.password()).toCharArray())
                                : null;
                    }
                });
    }
}
