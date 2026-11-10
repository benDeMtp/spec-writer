package dev.specwriter.infrastructure.web;

import dev.specwriter.domain.error.ErrorCode;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.util.regex.Pattern;

/**
 * Taille des corps de requête (009, research.md R1) : la limite du serveur est portée à 21 Mo pour
 * l'envoi d'un document joint ; toutes les autres requêtes restent bornées à 256 Ko, comme avant. Un
 * corps sans longueur annoncée n'est accepté que sur les routes d'envoi.
 */
@ApplicationScoped
public class BodySizeFilter {

    static final long MAX_BODY = 256 * 1024;
    private static final Pattern UPLOAD =
            Pattern.compile("/api/attachment-drafts|/api/(sessions|conversations)/[^/]+/attachments");

    void register(@Observes Router router) {
        router.route().order(Integer.MIN_VALUE).handler(this::check);
    }

    void check(RoutingContext ctx) {
        HttpMethod method = ctx.request().method();
        boolean withBody = method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH;
        if (!withBody || isUpload(method, ctx.normalizedPath()) || allowed(ctx.request().getHeader(HttpHeaders.CONTENT_LENGTH),
                ctx.request().getHeader(HttpHeaders.TRANSFER_ENCODING))) {
            ctx.next();
            return;
        }
        ErrorCode code = ErrorCode.PAYLOAD_TOO_LARGE;
        ctx.response().setStatusCode(code.status()).putHeader(HttpHeaders.CONTENT_TYPE, Problem.MEDIA_TYPE)
                .end("{\"type\":\"urn:spec-writer:error:payload_too_large\",\"title\":\"" + code.name()
                        + "\",\"status\":" + code.status() + ",\"code\":\"" + code.name()
                        + "\",\"userMessage\":\"Le contenu envoyé est trop volumineux.\",\"action\":\""
                        + code.action().name() + "\"}");
    }

    static boolean isUpload(HttpMethod method, String path) {
        return isUploadPath(method.name(), path);
    }

    /**
     * Route d'envoi d'un document joint : {@code POST /api/attachment-drafts},
     * {@code /api/sessions/{id}/attachments} ou {@code /api/conversations/{id}/attachments}.
     */
    static boolean isUploadPath(String method, String path) {
        return "POST".equals(method) && path != null && UPLOAD.matcher(path).matches();
    }

    /** Longueur annoncée sous la limite ; sans longueur ni découpage, le corps est vide. */
    static boolean allowed(String contentLength, String transferEncoding) {
        if (contentLength == null) {
            return transferEncoding == null;
        }
        try {
            return Long.parseLong(contentLength.strip()) <= MAX_BODY;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
