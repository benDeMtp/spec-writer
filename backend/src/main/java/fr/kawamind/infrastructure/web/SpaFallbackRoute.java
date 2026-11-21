package fr.kawamind.infrastructure.web;

import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Renvoie {@code index.html} pour les routes du front Angular (research.md R6), sans toucher à
 * l'API, aux endpoints techniques ni aux fichiers statiques. {@code index.html} n'est jamais gardé en
 * cache par le navigateur.
 */
@ApplicationScoped
public class SpaFallbackRoute {

    void register(@Observes Router router) {
        router.get().order(Integer.MIN_VALUE + 100).handler(ctx -> {
            String path = ctx.normalizedPath();
            if (isFrontRoute(path)) {
                noCache(ctx);
                ctx.reroute("/index.html");
            } else {
                if (isIndex(path)) {
                    noCache(ctx);
                }
                ctx.next();
            }
        });
    }

    /**
     * {@code index.html} nomme les fichiers du front, dont le nom change à chaque build : gardé en
     * cache (24 h, immuable, par défaut pour les fichiers statiques), il ferait charger l'ancien front
     * après une mise à jour, et ses fichiers n'existent plus. Les fichiers à empreinte restent en cache.
     */
    private static void noCache(io.vertx.ext.web.RoutingContext ctx) {
        ctx.addHeadersEndHandler(v -> ctx.response().headers().set("Cache-Control", "no-cache"));
    }

    static boolean isIndex(String path) {
        return path == null || path.equals("/") || path.equals("/index.html");
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
