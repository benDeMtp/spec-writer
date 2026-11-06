package dev.specwriter.infrastructure.web;

import dev.specwriter.application.knowledge.ReadDocFile;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;

/**
 * Pages d'un dossier de documentation du projet, ouvertes depuis la page des ADR (feature 004,
 * research.md R3). Aucun script de la documentation ne s'exécute : {@code sandbox} sans
 * {@code allow-scripts}, doublé de {@code script-src 'none'}. Refus : 404 sans détail.
 */
@Path("/api/projects/{id}/doc-files")
public class DocFileResource {

    static final String CSP =
            "sandbox allow-same-origin; script-src 'none'; object-src 'none'; default-src 'self' 'unsafe-inline'";

    @Inject
    ReadDocFile read;

    @GET
    @Path("/{path: .+}")
    public Response file(@PathParam("id") String id, @PathParam("path") String path) {
        return read.run(ProjectResource.projectId(id), path)
                .map(f -> Response.ok(f.content(), f.mediaType()).header("Content-Security-Policy", CSP)
                        .header("X-Content-Type-Options", "nosniff").header("Referrer-Policy", "no-referrer")
                        .header("Cache-Control", "no-store").build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
    }
}
