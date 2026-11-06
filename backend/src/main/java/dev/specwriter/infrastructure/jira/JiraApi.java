package dev.specwriter.infrastructure.jira;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;

/** API REST v2 de Jira Server / Data Center (research.md R10). Complétée par US6 et US5. */
@Path("/rest/api/2")
@Produces(MediaType.APPLICATION_JSON)
public interface JiraApi extends AutoCloseable {

    @GET
    @Path("/serverInfo")
    Map<String, Object> serverInfo();

    @GET
    @Path("/myself")
    Map<String, Object> myself();

    @GET
    @Path("/project/{key}")
    Map<String, Object> project(@PathParam("key") String key);

    @GET
    @Path("/mypermissions")
    Map<String, Object> myPermissions(@QueryParam("projectKey") String projectKey,
            @QueryParam("permissions") String permissions);

    @GET
    @Path("/search")
    Map<String, Object> search(@QueryParam("jql") String jql, @QueryParam("startAt") int startAt,
            @QueryParam("maxResults") int maxResults, @QueryParam("fields") String fields);

    /** Recherche dont les valeurs inconnues (clé supprimée) ne sont que des avertissements. */
    @GET
    @Path("/search")
    Map<String, Object> searchLenient(@QueryParam("jql") String jql, @QueryParam("startAt") int startAt,
            @QueryParam("maxResults") int maxResults, @QueryParam("fields") String fields,
            @QueryParam("validateQuery") String validateQuery);

    @GET
    @Path("/issue/{key}")
    Map<String, Object> issue(@PathParam("key") String key, @QueryParam("fields") String fields);

    @PUT
    @Path("/issue/{key}")
    @Consumes(MediaType.APPLICATION_JSON)
    void updateIssue(@PathParam("key") String key, Map<String, Object> body);

    @GET
    @Path("/issue/createmeta/{project}/issuetypes")
    Map<String, Object> createMetaIssueTypes(@PathParam("project") String project, @QueryParam("startAt") int startAt,
            @QueryParam("maxResults") int maxResults);

    @GET
    @Path("/issue/createmeta/{project}/issuetypes/{issueTypeId}")
    Map<String, Object> createMetaFields(@PathParam("project") String project, @PathParam("issueTypeId") String issueTypeId,
            @QueryParam("startAt") int startAt, @QueryParam("maxResults") int maxResults);

    @GET
    @Path("/issueLinkType")
    Map<String, Object> issueLinkTypes();

    @POST
    @Path("/issueLink")
    @Consumes(MediaType.APPLICATION_JSON)
    void createIssueLink(Map<String, Object> body);

    @POST
    @Path("/issue")
    @Consumes(MediaType.APPLICATION_JSON)
    Map<String, Object> createIssue(Map<String, Object> body);

    @Override
    void close();
}
