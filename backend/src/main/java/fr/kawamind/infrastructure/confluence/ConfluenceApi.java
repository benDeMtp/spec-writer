package fr.kawamind.infrastructure.confluence;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;

/** API REST de Confluence Server / Data Center, en lecture seule (016, contracts/confluence-client.md). */
@Path("/rest/api")
@Produces(MediaType.APPLICATION_JSON)
public interface ConfluenceApi extends AutoCloseable {

    @GET
    @Path("/user/current")
    Map<String, Object> currentUser();

    @GET
    @Path("/settings/systemInfo")
    Map<String, Object> systemInfo();

    @GET
    @Path("/content")
    Map<String, Object> byTitle(@QueryParam("spaceKey") String spaceKey, @QueryParam("title") String title,
            @QueryParam("type") String type, @QueryParam("expand") String expand);

    @GET
    @Path("/content/{id}")
    Map<String, Object> content(@PathParam("id") String id, @QueryParam("expand") String expand);

    @GET
    @Path("/content/search")
    Map<String, Object> search(@QueryParam("cql") String cql, @QueryParam("expand") String expand,
            @QueryParam("start") int start, @QueryParam("limit") int limit);

    @Override
    void close();
}
