package dev.specwriter.infrastructure.jira;

import dev.specwriter.domain.jira.IssueTrackerException;
import dev.specwriter.domain.jira.IssueTrackerException.Kind;
import dev.specwriter.domain.jira.JiraEndpoint;
import dev.specwriter.domain.port.IssueTracker;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ProcessingException;
import org.jboss.logging.Logger;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
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

    <T> T call(JiraEndpoint endpoint, Function<JiraApi, T> action) {
        try (JiraApi api = client(endpoint)) {
            return action.apply(api);
        } catch (WebApplicationException e) {
            throw fromStatus(e.getResponse() == null ? 0 : e.getResponse().getStatus(), e);
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
