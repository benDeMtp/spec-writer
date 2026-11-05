package dev.specwriter.infrastructure.web;

import dev.specwriter.application.search.UnifiedSearch;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/** {@code /api/search} : recherche dans spec-writer et dans Jira (FR-039 à FR-045), par pages de 25
 * suivies d'un curseur opaque (FR-021a de 002). */
@Path("/api/search")
@Produces(MediaType.APPLICATION_JSON)
public class SearchResource {

    @Inject
    UnifiedSearch search;

    @GET
    public UnifiedSearch.Page search(@QueryParam("q") String q, @QueryParam("cursor") String cursor) {
        return search.run(q, cursor);
    }
}
