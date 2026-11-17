package fr.kawamind.infrastructure.confluence;

import fr.kawamind.domain.port.ConfluenceClient;
import fr.kawamind.domain.port.DocumentationReader;
import fr.kawamind.infrastructure.docs.HtmlPages;
import fr.kawamind.infrastructure.platform.HttpTlsConfigurer;
import fr.kawamind.infrastructure.platform.ProxySettings;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import javax.net.ssl.SSLException;
import org.jboss.logging.Logger;

/**
 * Client Confluence Server / Data Center (016, research.md R2, R4) : jeton personnel en
 * {@code Authorization: Bearer}, proxy d'entreprise, autorité de certification interne, 8 requêtes
 * par seconde au plus, attente selon {@code Retry-After} sur un {@code 429} ou un {@code 503}.
 * Lecture seule. Aucun corps de réponse, aucun jeton n'est journalisé ni repris dans les erreurs.
 */
@ApplicationScoped
public class ConfluenceRestClient implements ConfluenceClient {

    private static final Logger LOG = Logger.getLogger(ConfluenceRestClient.class);
    static final int MAX_ATTEMPTS = 3;
    static final Duration MAX_DELAY = Duration.ofSeconds(30);

    private final ConfluenceThrottle throttle = new ConfluenceThrottle();

    @Inject
    ProxySettings proxy;

    @Override
    public String currentUser(Endpoint endpoint) {
        Map<String, Object> user = call(endpoint, ConfluenceApi::currentUser);
        Object name = user.get("displayName") != null ? user.get("displayName") : user.get("username");
        if (name == null) {
            throw new ConfluenceException(Failure.NOT_CONFLUENCE);
        }
        return String.valueOf(name);
    }

    @Override
    public Optional<String> serverVersion(Endpoint endpoint) {
        try {
            Object version = call(endpoint, ConfluenceApi::systemInfo).get("version");
            return Optional.ofNullable(version).map(String::valueOf);
        } catch (ConfluenceException e) {
            // facultatif : refusé aux comptes sans droits d'administration
            return Optional.empty();
        }
    }

    @Override
    public String resolve(Endpoint endpoint, String address) {
        ConfluencePageAddress parsed = ConfluencePageAddress.parse(endpoint.serverUrl(), address);
        if (!parsed.byTitle()) {
            return parsed.pageId();
        }
        Map<String, Object> found = call(endpoint, api -> api.byTitle(parsed.spaceKey(), parsed.title(), "page", null));
        List<?> results = found.get("results") instanceof List<?> l ? l : List.of();
        if (results.isEmpty() || !(results.getFirst() instanceof Map<?, ?> first) || first.get("id") == null) {
            throw new ConfluenceException(Failure.NOT_A_PAGE);
        }
        return String.valueOf(first.get("id"));
    }

    @Override
    public PageInfo page(Endpoint endpoint, String id) {
        Map<String, Object> page = call(endpoint, api -> api.content(id, "version,space"));
        if (page.get("type") != null && !"page".equals(page.get("type"))) {
            throw new ConfluenceException(Failure.NOT_A_PAGE);
        }
        return info(page);
    }

    @Override
    public Descendants descendants(Endpoint endpoint, String id, int start, int limit) {
        String cql = "ancestor=" + Long.parseLong(id) + " and type=page";
        Map<String, Object> found = call(endpoint, api -> api.search(cql, "version", start, limit));
        List<PageInfo> pages = new ArrayList<>();
        if (found.get("results") instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    pages.add(info(m));
                }
            }
        }
        int total = found.get("totalSize") instanceof Number n ? n.intValue()
                : found.get("size") instanceof Number s ? start + s.intValue() : start + pages.size();
        return new Descendants(pages, total);
    }

    @Override
    public DocumentationReader.Page content(Endpoint endpoint, String id, String ref) {
        Map<String, Object> page = call(endpoint, api -> api.content(id, "body.view,version"));
        PageInfo info = info(page);
        Object html = page.get("body") instanceof Map<?, ?> body && body.get("view") instanceof Map<?, ?> view
                ? view.get("value") : null;
        return HtmlPages.of(ref, info.title(), html == null ? "" : String.valueOf(html), info.updatedAt());
    }

    private static PageInfo info(Map<?, ?> page) {
        Object id = page.get("id");
        if (id == null) {
            throw new ConfluenceException(Failure.NOT_CONFLUENCE);
        }
        Map<?, ?> version = page.get("version") instanceof Map<?, ?> v ? v : Map.of();
        int number = version.get("number") instanceof Number n ? Math.max(1, n.intValue()) : 1;
        String title = page.get("title") == null ? String.valueOf(id) : String.valueOf(page.get("title"));
        return new PageInfo(String.valueOf(id), title, number, date(version.get("when")));
    }

    static Instant date(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(String.valueOf(value)).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    <T> T call(Endpoint endpoint, Function<ConfluenceApi, T> action) {
        for (int attempt = 1;; attempt++) {
            try {
                return once(endpoint, action);
            } catch (WebApplicationException e) {
                int status = e.getResponse() == null ? 0 : e.getResponse().getStatus();
                if (status != 429 && status != 503 || attempt >= MAX_ATTEMPTS) {
                    throw fromStatus(status, e);
                }
                Duration delay = retryAfter(e.getResponse().getHeaderString("Retry-After"));
                LOG.infof("Confluence limite le débit : nouvelle tentative dans %d s (%d/%d)", delay.toSeconds(),
                        attempt + 1, MAX_ATTEMPTS);
                try {
                    Thread.sleep(delay.toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw fromStatus(status, e);
                }
            }
        }
    }

    private <T> T once(Endpoint endpoint, Function<ConfluenceApi, T> action) {
        if (!throttle.acquire()) {
            throw new ConfluenceException(Failure.UNREACHABLE);
        }
        try (ConfluenceApi api = client(endpoint)) {
            return action.apply(api);
        } catch (ProcessingException e) {
            boolean tls = causedBy(e, SSLException.class);
            LOG.warnf("Confluence injoignable (%s)", causes(e));
            throw new ConfluenceException(tls ? Failure.TLS_ERROR : Failure.UNREACHABLE, null, e);
        } catch (IllegalArgumentException | IllegalStateException e) {
            LOG.warnf("Configuration Confluence invalide (%s)", causes(e));
            throw new ConfluenceException(Failure.TLS_ERROR, null, e);
        }
    }

    /** Délai d'un {@code Retry-After} en secondes, plafonné à 30 s ; 1 s s'il est absent ou illisible. */
    static Duration retryAfter(String header) {
        try {
            long seconds = Long.parseLong(header == null ? "" : header.strip());
            return Duration.ofSeconds(Math.max(0, Math.min(seconds, MAX_DELAY.toSeconds())));
        } catch (NumberFormatException e) {
            return Duration.ofSeconds(1);
        }
    }

    static ConfluenceException fromStatus(int status, Throwable cause) {
        Failure failure = switch (status) {
            case 401 -> Failure.UNAUTHORIZED;
            case 403 -> Failure.FORBIDDEN;
            case 404 -> Failure.NOT_FOUND;
            case 429 -> Failure.RATE_LIMITED;
            default -> status >= 500 || status == 0 ? Failure.UNREACHABLE : Failure.REJECTED;
        };
        return new ConfluenceException(failure, status == 0 ? null : status, cause);
    }

    private static boolean causedBy(Throwable e, Class<? extends Throwable> type) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
    }

    /** Chaîne des types d'exception, sans leurs messages (ils peuvent citer l'adresse ou un en-tête). */
    static String causes(Throwable e) {
        StringBuilder out = new StringBuilder();
        for (Throwable t = e; t != null && out.length() < 300; t = t.getCause()) {
            if (!out.isEmpty()) {
                out.append(" <- ");
            }
            out.append(t.getClass().getSimpleName());
        }
        return out.toString();
    }

    private ConfluenceApi client(Endpoint endpoint) {
        String token = endpoint.token();
        URI server = URI.create(endpoint.serverUrl());
        QuarkusRestClientBuilder builder = QuarkusRestClientBuilder.newBuilder()
                .baseUri(server)
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .register((ClientRequestFilter) ctx -> ctx.getHeaders().putSingle(HttpHeaders.AUTHORIZATION,
                        "Bearer " + token));
        // le client de Quarkus ignore https.proxyHost : le proxy d'entreprise est appliqué ici (T139)
        proxy.forUri(server).ifPresent(p -> {
            builder.proxyAddress(p.host(), p.port());
            if (p.user() != null) {
                builder.proxyUser(p.user());
                builder.proxyPassword(p.password() == null ? "" : p.password());
            }
        });
        if (endpoint.caCertificatePath() != null) {
            builder.trustStore(HttpTlsConfigurer.trustStoreFromPem(Path.of(endpoint.caCertificatePath()), "confluence"),
                    "");
        }
        return builder.build(ConfluenceApi.class);
    }
}
