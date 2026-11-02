package dev.specwriter.infrastructure.web;

import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Renvoie {@code index.html} pour les routes du front Angular (research.md R6), sans toucher à
 * l'API, aux endpoints techniques ni aux fichiers statiques.
 */
@ApplicationScoped
public class SpaFallbackRoute {

    void register(@Observes Router router) {
        router.get().order(Integer.MIN_VALUE + 100).handler(ctx -> {
            if (isFrontRoute(ctx.normalizedPath())) {
                ctx.reroute("/index.html");
            } else {
                ctx.next();
            }
        });
    }

    static boolean isFrontRoute(String path) {
        if (path == null || path.equals("/") || path.equals("/index.html")) {
            return false;
        }
        if (path.startsWith("/api/") || path.equals("/api") || path.startsWith("/q/") || path.startsWith("/e2e/")) {
            return false;
        }
        String last = path.substring(path.lastIndexOf('/') + 1);
        return !last.contains(".");
    }
}
