package dev.specwriter.infrastructure.jira;

import dev.specwriter.domain.jira.CreatedIssue;
import dev.specwriter.domain.jira.IssueTrackerException;
import dev.specwriter.domain.jira.IssueTrackerException.Kind;
import dev.specwriter.domain.jira.JiraEndpoint;
import dev.specwriter.domain.jira.JiraIssue;
import dev.specwriter.domain.jira.JiraIssueSummary;
import dev.specwriter.domain.jira.IssueType;
import dev.specwriter.domain.jira.JiraSearchPage;
import dev.specwriter.domain.jira.LinkedJiraTicket;
import dev.specwriter.domain.jira.RequiredField;
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
import dev.specwriter.infrastructure.platform.ProxySettings;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
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

    @Inject
    ProxySettings proxy;

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
    public JiraSearchPage searchText(JiraEndpoint endpoint, String projectKey, String terms, int startAt, int maxResults) {
        String jql = JqlBuilder.textSearch(projectKey, terms);
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

    static final int META_PAGE = 100;
    /** Garde-fou : un projet n'a pas des milliers de types ou de champs. */
    static final int META_MAX_PAGES = 10;

    @Override
    public List<IssueType> issueTypes(JiraEndpoint endpoint, String projectKey) {
        List<IssueType> types = new ArrayList<>();
        for (Map<?, ?> v : pages(endpoint, (api, start) -> api.createMetaIssueTypes(projectKey, start, META_PAGE))) {
            if (!Boolean.TRUE.equals(v.get("subtask"))) {
                types.add(new IssueType(String.valueOf(v.get("id")), text(v.get("name"))));
            }
        }
        return types;
    }

    @Override
    public List<RequiredField> requiredFields(JiraEndpoint endpoint, String projectKey, String issueTypeId) {
        List<RequiredField> fields = new ArrayList<>();
        for (Map<?, ?> v : pages(endpoint, (api, start) -> api.createMetaFields(projectKey, issueTypeId, start, META_PAGE))) {
            if (Boolean.TRUE.equals(v.get("required")) && !Boolean.TRUE.equals(v.get("hasDefaultValue"))) {
                fields.add(field(v));
            }
        }
        return fields;
    }

    @Override
    public CreatedIssue createIssue(JiraEndpoint endpoint, String projectKey, String issueTypeId, String summary,
            String wikiDescription, Map<String, Object> extraFields) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("project", Map.of("key", projectKey));
        fields.put("issuetype", Map.of("id", issueTypeId));
        fields.put("summary", summary);
        fields.put("description", wikiDescription);
        fields.putAll(extraFields);
        Map<String, Object> created = call(endpoint, api -> {
            try {
                return api.createIssue(Map.of("fields", fields));
            } catch (WebApplicationException e) {
                if (e.getResponse() != null && e.getResponse().getStatus() == 400) {
                    throw new IssueTrackerException(Kind.REJECTED, "Jira a refusé la création", e, rejection(e));
                }
                throw e;
            }
        });
        return new CreatedIssue(String.valueOf(created.get("id")), String.valueOf(created.get("key")));
    }

    /** Causes d'un 400 de Jira : {@code errorMessages} et {@code errors} (champ : message). Jamais journalisées. */
    private static List<String> rejection(WebApplicationException e) {
        List<String> details = new ArrayList<>();
        try {
            Map<?, ?> body = e.getResponse().readEntity(Map.class);
            if (body.get("errorMessages") instanceof List<?> messages) {
                messages.forEach(m -> details.add(String.valueOf(m)));
            }
            if (body.get("errors") instanceof Map<?, ?> errors) {
                errors.forEach((field, message) -> details.add(field + " : " + message));
            }
        } catch (RuntimeException unreadable) {
            LOG.debug("Corps d'erreur Jira illisible", unreadable);
        }
        return details;
    }

    private static RequiredField field(Map<?, ?> v) {
        Map<?, ?> schema = map(v.get("schema"));
        String type = text(schema.get("type"));
        List<RequiredField.Option> options = new ArrayList<>();
        if (v.get("allowedValues") instanceof List<?> allowed) {
            for (Object o : allowed) {
                if (o instanceof Map<?, ?> m) {
                    Object label = m.get("name") != null ? m.get("name") : m.get("value");
                    options.add(new RequiredField.Option(String.valueOf(m.get("id")), String.valueOf(label)));
                }
            }
        }
        RequiredField.Kind kind;
        if (!options.isEmpty()) {
            kind = "array".equals(type) ? RequiredField.Kind.OPTIONS : RequiredField.Kind.OPTION;
        } else if ("string".equals(type)) {
            kind = RequiredField.Kind.TEXT;
        } else if ("number".equals(type)) {
            kind = RequiredField.Kind.NUMBER;
        } else if ("date".equals(type)) {
            kind = RequiredField.Kind.DATE;
        } else {
            kind = RequiredField.Kind.UNSUPPORTED;
        }
        return new RequiredField(String.valueOf(v.get("fieldId")), text(v.get("name")), kind, options);
    }

    /** Valeurs ({@code values}) de toutes les pages d'une ressource createmeta. */
    private List<Map<?, ?>> pages(JiraEndpoint endpoint, BiFunction<JiraApi, Integer, Map<String, Object>> page) {
        List<Map<?, ?>> values = new ArrayList<>();
        for (int i = 0, start = 0; i < META_MAX_PAGES; i++) {
            int from = start;
            Map<String, Object> body = call(endpoint, api -> page.apply(api, from));
            List<?> list = body.get("values") instanceof List<?> l ? l : List.of();
            list.forEach(o -> {
                if (o instanceof Map<?, ?> m) {
                    values.add(m);
                }
            });
            start += list.size();
            if (list.isEmpty() || start >= number(body.get("total"), start)) {
                break;
            }
        }
        return values;
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

    private JiraApi client(JiraEndpoint endpoint) {
        String token = endpoint.token();
        URI server = URI.create(endpoint.serverUrl());
        QuarkusRestClientBuilder builder = QuarkusRestClientBuilder.newBuilder()
                .baseUri(server)
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .register((ClientRequestFilter) ctx -> ctx.getHeaders().putSingle(HttpHeaders.AUTHORIZATION, "Bearer " + token));
        // le client de Quarkus ignore https.proxyHost : le proxy d'entreprise est appliqué ici (T139)
        proxy.forUri(server).ifPresent(p -> {
            builder.proxyAddress(p.host(), p.port());
            if (p.user() != null) {
                builder.proxyUser(p.user());
                builder.proxyPassword(p.password() == null ? "" : p.password());
            }
        });
        if (endpoint.caCertificatePath() != null) {
            builder.trustStore(JiraTlsConfigurer.trustStoreFromPem(Path.of(endpoint.caCertificatePath())), "");
        }
        return builder.build(JiraApi.class);
    }
}
