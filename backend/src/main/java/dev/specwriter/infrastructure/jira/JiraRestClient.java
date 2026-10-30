package dev.specwriter.infrastructure.jira;

import dev.specwriter.domain.jira.IssueTrackerException;
import dev.specwriter.domain.jira.IssueTrackerException.Kind;
import dev.specwriter.domain.jira.JiraEndpoint;
import dev.specwriter.domain.jira.JiraIssue;
import dev.specwriter.domain.jira.JiraIssueSummary;
import dev.specwriter.domain.jira.JiraSearchPage;
import dev.specwriter.domain.jira.LinkedJiraTicket;
import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.IssueTracker;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import org.jboss.logging.Logger;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Client Jira Server / Data Center (research.md R10) : jeton d'accès personnel en
 * {@code Authorization: Bearer}, autorité de certification interne facultative. Aucun corps de
 * réponse n'est journalisé ni repris dans les erreurs.
 */
@ApplicationScoped
public class JiraRestClient implements IssueTracker {

    private static final Logger LOG = Logger.getLogger(JiraRestClient.class);
    private static final DateTimeFormatter JIRA_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    static final String SEARCH_FIELDS = "summary,status,issuetype,updated";
    static final String ISSUE_FIELDS = "summary,description,updated";

    @Inject
    ClockPort clock;

    @Override
    public String serverVersion(JiraEndpoint endpoint) {
        return call(endpoint, api -> String.valueOf(api.serverInfo().get("version")));
    }

    @Override
    public String currentUser(JiraEndpoint endpoint) {
        return call(endpoint, api -> String.valueOf(api.myself().get("name")));
    }

    @Override
    public void project(JiraEndpoint endpoint, String projectKey) {
        call(endpoint, api -> api.project(projectKey));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Boolean> permissions(JiraEndpoint endpoint, String projectKey, Set<String> permissions) {
        Map<String, Object> body = call(endpoint,
                api -> api.myPermissions(projectKey, String.join(",", new TreeSet<>(permissions))));
        Map<String, Boolean> out = new HashMap<>();
        Object perms = body.get("permissions");
        if (perms instanceof Map<?, ?> map) {
            for (String p : permissions) {
                Object entry = map.get(p);
                out.put(p, entry instanceof Map<?, ?> e && Boolean.TRUE.equals(((Map<String, Object>) e).get("havePermission")));
            }
        }
        return out;
    }

    @Override
    public JiraSearchPage search(JiraEndpoint endpoint, String jql, int startAt, int maxResults) {
        Map<String, Object> body = call(endpoint, api -> api.search(jql, startAt, maxResults, SEARCH_FIELDS));
        List<JiraIssueSummary> issues = new ArrayList<>();
        if (body.get("issues") instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> issue) {
                    Map<?, ?> fields = map(issue.get("fields"));
                    issues.add(new JiraIssueSummary(String.valueOf(issue.get("key")), text(fields.get("summary")),
                            name(fields.get("status")), name(fields.get("issuetype")), date(fields.get("updated"))));
                }
            }
        }
        return new JiraSearchPage(issues, number(body.get("startAt"), startAt), number(body.get("total"), issues.size()));
    }

    @Override
    public JiraIssue getIssue(JiraEndpoint endpoint, String issueKey) {
        requireKey(issueKey);
        Map<String, Object> body = call(endpoint, api -> api.issue(issueKey, ISSUE_FIELDS));
        Map<?, ?> fields = map(body.get("fields"));
        String description = text(fields.get("description"));
        return new JiraIssue(String.valueOf(body.get("key")), text(fields.get("summary")),
                description == null ? "" : description, date(fields.get("updated")));
    }

    @Override
    public void updateDescription(JiraEndpoint endpoint, String issueKey, String wikiDescription) {
        requireKey(issueKey);
        call(endpoint, api -> {
            api.updateIssue(issueKey, Map.of("fields", Map.of("description", wikiDescription)));
            return null;
        });
    }

    private static void requireKey(String issueKey) {
        if (!LinkedJiraTicket.isKey(issueKey)) {
            throw new IllegalArgumentException("Clé de ticket invalide");
        }
    }

    private static Map<?, ?> map(Object o) {
        return o instanceof Map<?, ?> m ? m : Map.of();
    }

    private static String text(Object o) {
        return o == null ? null : o.toString();
    }

    private static String name(Object o) {
        return o instanceof Map<?, ?> m ? text(m.get("name")) : null;
    }

    private static int number(Object o, int fallback) {
        return o instanceof Number n ? n.intValue() : fallback;
    }

    static Instant date(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(o.toString(), JIRA_DATE).toInstant();
        } catch (DateTimeParseException e) {
            return OffsetDateTime.parse(o.toString()).toInstant();
        }
    }

    <T> T call(JiraEndpoint endpoint, Function<JiraApi, T> action) {
        for (int attempt = 1;; attempt++) {
            try {
                return once(endpoint, action);
            } catch (WebApplicationException e) {
                int status = e.getResponse() == null ? 0 : e.getResponse().getStatus();
                if (status != 429 || attempt >= RateLimitHandler.MAX_ATTEMPTS) {
                    throw fromStatus(status, e);
                }
                Duration delay = RateLimitHandler.delay(e.getResponse().getStringHeaders(), clock.now());
                LOG.infof("Jira limite le débit : nouvelle tentative dans %d s (%d/%d)", delay.toSeconds(), attempt + 1,
                        RateLimitHandler.MAX_ATTEMPTS);
                try {
                    Thread.sleep(delay.toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw fromStatus(status, e);
                }
            }
        }
    }

    private <T> T once(JiraEndpoint endpoint, Function<JiraApi, T> action) {
        try (JiraApi api = client(endpoint)) {
            return action.apply(api);
        } catch (ProcessingException e) {
            LOG.warnf("Jira injoignable (%s)", causes(e));
            LOG.debug("Détail de l'erreur Jira", e);
            throw new IssueTrackerException(Kind.UNREACHABLE, "Jira injoignable", e);
        } catch (IllegalArgumentException | IllegalStateException e) {
            LOG.warnf("Configuration Jira invalide (%s)", causes(e));
            throw new IssueTrackerException(Kind.UNREACHABLE, "Configuration Jira invalide", e);
        }
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

    static IssueTrackerException fromStatus(int status, Throwable cause) {
        Kind kind = switch (status) {
            case 401 -> Kind.UNAUTHORIZED;
            case 403 -> Kind.FORBIDDEN;
            case 404 -> Kind.NOT_FOUND;
            case 429 -> Kind.RATE_LIMITED;
            default -> status >= 500 || status == 0 ? Kind.UNREACHABLE : Kind.REJECTED;
        };
        return new IssueTrackerException(kind, "Jira a répondu " + status, cause);
    }

    private static JiraApi client(JiraEndpoint endpoint) {
        String token = endpoint.token();
        QuarkusRestClientBuilder builder = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(endpoint.serverUrl()))
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .register((ClientRequestFilter) ctx -> ctx.getHeaders().putSingle(HttpHeaders.AUTHORIZATION, "Bearer " + token));
        if (endpoint.caCertificatePath() != null) {
            builder.trustStore(JiraTlsConfigurer.trustStoreFromPem(Path.of(endpoint.caCertificatePath())), "");
        }
        return builder.build(JiraApi.class);
    }
}
