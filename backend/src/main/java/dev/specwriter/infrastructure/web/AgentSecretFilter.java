package dev.specwriter.infrastructure.web;

import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.infrastructure.platform.AgentEndpointFile;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.ext.Provider;

/**
 * API des agents, {@code /api/agent/**} (research.md R2) : hôte local, aucun en-tête {@code Origin}
 * (un navigateur en envoie toujours un), et le secret du fichier des agents en {@code Bearer},
 * comparé à temps constant. Le cookie de l'interface n'y donne pas accès, et le secret ne donne
 * accès à rien d'autre.
 */
@Provider
@PreMatching
@Priority(Priorities.AUTHENTICATION)
public class AgentSecretFilter implements ContainerRequestFilter {

    static final String PREFIX = "api/agent/";
    private static final String BEARER = "Bearer ";

    /** Résolu à la requête : le filtre est créé avant que le dossier de données soit connu. */
    @Inject
    Instance<AgentEndpointFile> endpoint;

    static boolean isAgentPath(String normalized) {
        return normalized.startsWith(PREFIX) || normalized.equals("api/agent");
    }

    @Override
    public void filter(ContainerRequestContext ctx) {
        String path = ctx.getUriInfo().getPath();
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        if (!isAgentPath(normalized)) {
            return;
        }
        int port = ctx.getUriInfo().getBaseUri().getPort();
        if (!LocalSecurityFilter.isLocalHost(ctx.getHeaderString(HttpHeaders.HOST), port)
                || ctx.getHeaderString("Origin") != null) {
            ctx.abortWith(ProblemMapper.respond(Problem.of(ErrorCode.FORBIDDEN)));
            return;
        }
        String authorization = ctx.getHeaderString(HttpHeaders.AUTHORIZATION);
        String presented = authorization != null && authorization.startsWith(BEARER)
                ? authorization.substring(BEARER.length()) : null;
        if (!endpoint.get().accepts(presented)) {
            ctx.abortWith(ProblemMapper.respond(Problem.of(ErrorCode.AGENT_ACCESS_DENIED)));
        }
    }
}
