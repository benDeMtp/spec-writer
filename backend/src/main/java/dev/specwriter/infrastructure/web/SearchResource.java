package dev.specwriter.infrastructure.web;

import dev.specwriter.application.search.UnifiedSearch;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/** {@code /api/search} : recherche dans spec-writer et dans Jira (FR-039 à FR-045). */
@Path("/api/search")
@Produces(MediaType.APPLICATION_JSON)
public class SearchResource {

    @Inject
    UnifiedSearch search;

    @GET
    public UnifiedSearch.Result search(@QueryParam("q") String q, @QueryParam("page") @DefaultValue("0") int page) {
        return search.run(q, page);
    }
}
