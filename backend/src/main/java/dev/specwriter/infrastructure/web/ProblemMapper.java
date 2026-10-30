package dev.specwriter.infrastructure.web;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.NotSupportedException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Convertit toute erreur en {@link Problem}. Les messages d'origine et les traces ne sont jamais
 * renvoyés (FR-028) ; seul le type d'exception est journalisé (FR-025).
 */
public class ProblemMapper {

    private static final Logger LOG = Logger.getLogger(ProblemMapper.class);

    @ServerExceptionMapper
    public Response domain(DomainException e) {
        return respond(Problem.of(e.code(), e.userMessage()));
    }

    /** Erreurs du runtime Copilot (research.md R3) : sans leur message, qui peut citer la requête. */
    @ServerExceptionMapper
    public Response copilot(dev.specwriter.infrastructure.llm.CopilotSdkGateway.GatewayException e) {
        return respond(Problem.of(switch (e.kind()) {
            case NOT_CONNECTED -> ErrorCode.COPILOT_NOT_CONNECTED;
            case MODEL_UNAVAILABLE -> ErrorCode.MODEL_UNAVAILABLE;
            case QUOTA_EXHAUSTED -> ErrorCode.QUOTA_EXHAUSTED;
            case UNAVAILABLE -> ErrorCode.LLM_UNAVAILABLE;
        }));
    }

    @ServerExceptionMapper
    public Response notFound(NotFoundException e) {
        return respond(Problem.of(ErrorCode.NOT_FOUND));
    }

    @ServerExceptionMapper
    public Response unsupported(NotSupportedException e) {
        return respond(Problem.of(ErrorCode.UNSUPPORTED_MEDIA_TYPE));
    }

    @ServerExceptionMapper
    public Response web(WebApplicationException e) {
        int status = e.getResponse() == null ? 500 : e.getResponse().getStatus();
        if (status == 404) {
            return respond(Problem.of(ErrorCode.NOT_FOUND));
        }
        if (status == 403) {
            return respond(Problem.of(ErrorCode.FORBIDDEN));
        }
        if (status == 415) {
            return respond(Problem.of(ErrorCode.UNSUPPORTED_MEDIA_TYPE));
        }
        if (status >= 400 && status < 500) {
            return respond(Problem.of(ErrorCode.VALIDATION_FAILED));
        }
        return unexpected(e);
    }

    @ServerExceptionMapper(priority = jakarta.ws.rs.Priorities.USER + 1000)
    public Response unexpected(Throwable e) {
        LOG.errorf("Erreur inattendue (%s)", e.getClass().getName());
        return respond(Problem.of(ErrorCode.INTERNAL_ERROR));
    }

    static Response respond(Problem p) {
        return Response.status(p.status()).type(Problem.MEDIA_TYPE).entity(p).build();
    }
}
