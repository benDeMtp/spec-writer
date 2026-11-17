package fr.kawamind.infrastructure.web;

import fr.kawamind.application.agent.AgentChangeStatus;
import fr.kawamind.application.agent.GetFeature;
import fr.kawamind.application.agent.ListFeatures;
import fr.kawamind.application.agent.ListProjectsForAgent;
import fr.kawamind.application.agent.SearchKnowledgeForAgent;
import fr.kawamind.application.knowledge.KnowledgeQueries;
import fr.kawamind.application.task.AgentTasks;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.knowledge.KnowledgeKind;
import fr.kawamind.domain.session.ImplementationStatus;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * {@code /api/agent/**} : API de la passerelle MCP (contracts/mcp-tools.md), protégée par
 * {@link AgentSecretFilter}. Le nom annoncé par l'agent arrive dans {@code X-Agent-Name}.
 */
@Path("/api/agent")
@Produces(MediaType.APPLICATION_JSON)
public class AgentResource {

    static final String AGENT_NAME = "X-Agent-Name";
    /** Connexion de la passerelle : {@code <pid>-<début du processus en ms>} (015, research R4). */
    static final String AGENT_CONNECTION = "X-Agent-Connection";

    public record StatusRequest(String status) {
    }

    public record CompleteRequest(String report) {
    }

    @Inject
    ListProjectsForAgent listProjects;
    @Inject
    ListFeatures listFeatures;
    @Inject
    GetFeature getFeature;
    @Inject
    SearchKnowledgeForAgent searchKnowledge;
    @Inject
    AgentChangeStatus changeStatus;
    @Inject
    AgentTasks agentTasks;

    @GET
    @Path("/projects")
    public List<ListProjectsForAgent.ProjectForAgent> projects(@HeaderParam(AGENT_NAME) String agent) {
        return listProjects.run(agent);
    }

    @GET
    @Path("/projects/{id}/features")
    public List<ListFeatures.Feature> features(@HeaderParam(AGENT_NAME) String agent, @PathParam("id") String id) {
        return listFeatures.run(agent, Ids.parse(id));
    }

    @GET
    @Path("/features/{id}")
    public GetFeature.FeatureContent feature(@HeaderParam(AGENT_NAME) String agent,
            @HeaderParam(AGENT_CONNECTION) String connection, @PathParam("id") String id) {
        return getFeature.run(agent, connection, Ids.parse(id));
    }

    @GET
    @Path("/features/{id}/tasks/next")
    public AgentTasks.NextAnswer nextTask(@HeaderParam(AGENT_NAME) String agent,
            @HeaderParam(AGENT_CONNECTION) String connection, @PathParam("id") String id) {
        return agentTasks.next(agent, connection, Ids.parse(id));
    }

    @POST
    @Path("/features/{id}/tasks/{key}/start")
    public AgentTasks.TaskAnswer startTask(@HeaderParam(AGENT_NAME) String agent,
            @HeaderParam(AGENT_CONNECTION) String connection, @PathParam("id") String id, @PathParam("key") String key) {
        return agentTasks.start(agent, connection, Ids.parse(id), key);
    }

    @POST
    @Path("/features/{id}/tasks/{key}/complete")
    @Consumes(MediaType.APPLICATION_JSON)
    public AgentTasks.TaskAnswer completeTask(@HeaderParam(AGENT_NAME) String agent,
            @HeaderParam(AGENT_CONNECTION) String connection, @PathParam("id") String id, @PathParam("key") String key,
            CompleteRequest request) {
        return agentTasks.complete(agent, connection, Ids.parse(id), key, request == null ? null : request.report());
    }

    @GET
    @Path("/projects/{id}/knowledge")
    public List<KnowledgeQueries.Hit> knowledge(@PathParam("id") String id, @QueryParam("q") String q,
            @QueryParam("kinds") List<String> kinds) {
        return searchKnowledge.run(Ids.parse(id), q, kinds(kinds));
    }

    @PUT
    @Path("/features/{id}/status")
    @Consumes(MediaType.APPLICATION_JSON)
    public AgentChangeStatus.Changed status(@HeaderParam(AGENT_NAME) String agent, @PathParam("id") String id,
            StatusRequest request) {
        ImplementationStatus target;
        try {
            target = request == null || request.status() == null ? null : ImplementationStatus.valueOf(request.status());
        } catch (IllegalArgumentException e) {
            throw new DomainException(ErrorCode.TRANSITION_NOT_ALLOWED);
        }
        return changeStatus.run(agent, Ids.parse(id), target);
    }

    private static EnumSet<KnowledgeKind> kinds(List<String> raw) {
        List<KnowledgeKind> out = new ArrayList<>();
        for (String r : raw == null ? List.<String>of() : raw) {
            for (String part : r.split(",")) {
                if (!part.isBlank()) {
                    try {
                        out.add(KnowledgeKind.valueOf(part.strip()));
                    } catch (IllegalArgumentException e) {
                        throw new DomainException(ErrorCode.VALIDATION_FAILED, "Type de connaissance inconnu.");
                    }
                }
            }
        }
        return out.isEmpty() ? EnumSet.allOf(KnowledgeKind.class) : EnumSet.copyOf(out);
    }
}
