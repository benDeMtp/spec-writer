package dev.specwriter.infrastructure.web;

import dev.specwriter.application.agent.ManageAgentAccess;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.infrastructure.platform.McpCommand;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/**
 * {@code /api/settings/agent-access} : activation de l'accès des agents, commande à déclarer,
 * régénération du secret et journal (FR-024, FR-028). Le secret n'est jamais renvoyé.
 */
@Path("/api/settings/agent-access")
@Produces(MediaType.APPLICATION_JSON)
public class AgentAccessResource {

    public record State(boolean enabled, String command, String mcpJson) {
    }

    public record EnableRequest(Boolean enabled) {
    }

    @Inject
    ManageAgentAccess access;

    @GET
    public State state() {
        McpCommand.Command command = McpCommand.current();
        return new State(access.enabled(), command.line(), command.mcpJson());
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public void enable(EnableRequest request) {
        if (request == null || request.enabled() == null) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Précisez si l'accès est activé.");
        }
        access.setEnabled(request.enabled());
    }

    @POST
    @Path("/secret")
    public void regenerate() {
        access.regenerate();
    }

    @GET
    @Path("/log")
    public ManageAgentAccess.LogPage log(@QueryParam("startAt") @DefaultValue("0") int startAt) {
        return access.log(startAt);
    }
}
