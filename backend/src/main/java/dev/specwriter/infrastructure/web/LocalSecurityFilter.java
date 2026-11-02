package dev.specwriter.infrastructure.web;

import dev.specwriter.domain.error.ErrorCode;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.Provider;
import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * Protège l'API locale contre les autres sites ouverts dans le navigateur (research.md R14) :
 * header Host local (DNS rebinding), Origin de l'application, cookie de session locale,
 * {@code Content-Type: application/json} pour les écritures.
 */
@Provider
@PreMatching
public class LocalSecurityFilter implements ContainerRequestFilter {

    public static final String COOKIE = "sw_launch";
    static final String SESSION_AUTH_PATH = "api/session-auth";

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    @Inject
    LaunchTokenService tokens;

    @Override
    public void filter(ContainerRequestContext ctx) {
        String path = ctx.getUriInfo().getPath();
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        if (!normalized.startsWith("api/") && !normalized.equals("api")) {
            return;
        }
        int port = ctx.getUriInfo().getBaseUri().getPort();
        if (!isLocalHost(ctx.getHeaderString(HttpHeaders.HOST), port)) {
            deny(ctx, ErrorCode.FORBIDDEN);
            return;
        }
        String method = ctx.getMethod().toUpperCase(Locale.ROOT);
        String origin = ctx.getHeaderString("Origin");
        boolean unsafe = !SAFE_METHODS.contains(method);
        if (origin != null ? !isLocalOrigin(origin, port) : unsafe) {
            deny(ctx, ErrorCode.FORBIDDEN);
            return;
        }
        if (unsafe && ctx.hasEntity() && !isJson(ctx.getMediaType())) {
            deny(ctx, ErrorCode.UNSUPPORTED_MEDIA_TYPE);
            return;
        }
        if (normalized.equals(SESSION_AUTH_PATH)) {
            return;
        }
        Cookie cookie = ctx.getCookies().get(COOKIE);
        if (cookie == null || !tokens.isValidSession(cookie.getValue())) {
            deny(ctx, ErrorCode.FORBIDDEN);
        }
    }

    private static boolean isJson(MediaType type) {
        return type != null && type.isCompatible(MediaType.APPLICATION_JSON_TYPE);
    }

    static boolean isLocalHost(String host, int port) {
        if (host == null) {
            return false;
        }
        String h = host.toLowerCase(Locale.ROOT);
        return h.equals("127.0.0.1:" + port) || h.equals("localhost:" + port);
    }

    static boolean isLocalOrigin(String origin, int port) {
        try {
            URI uri = URI.create(origin);
            return "http".equals(uri.getScheme())
                    && uri.getPort() == port
                    && ("127.0.0.1".equals(uri.getHost()) || "localhost".equals(uri.getHost()))
                    && (uri.getPath() == null || uri.getPath().isEmpty());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static void deny(ContainerRequestContext ctx, ErrorCode code) {
        ctx.abortWith(ProblemMapper.respond(Problem.of(code)));
    }
}
