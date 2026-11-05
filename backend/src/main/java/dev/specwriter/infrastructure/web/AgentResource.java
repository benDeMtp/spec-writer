package dev.specwriter.infrastructure.web;

import dev.specwriter.application.agent.AgentChangeStatus;
import dev.specwriter.application.agent.GetFeature;
import dev.specwriter.application.agent.ListFeatures;
import dev.specwriter.application.agent.ListProjectsForAgent;
import dev.specwriter.application.agent.SearchKnowledgeForAgent;
import dev.specwriter.application.knowledge.KnowledgeQueries;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.knowledge.KnowledgeKind;
import dev.specwriter.domain.session.ImplementationStatus;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
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

    public record StatusRequest(String status) {
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
    public GetFeature.FeatureContent feature(@HeaderParam(AGENT_NAME) String agent, @PathParam("id") String id) {
        return getFeature.run(agent, Ids.parse(id));
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
