package dev.specwriter.infrastructure.web;

import dev.specwriter.domain.error.ErrorCode;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;

/** {@code POST /api/session-auth} : échange le jeton de lancement contre le cookie local (R14). */
@Path("/api/session-auth")
public class SessionAuthResource {

    public record LaunchTokenRequest(String launchToken) {
    }

    @Inject
    LaunchTokenService tokens;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response exchange(LaunchTokenRequest request) {
        String token = request == null ? null : request.launchToken();
        return tokens.exchange(token)
                .map(session -> Response.noContent().cookie(new NewCookie.Builder(LocalSecurityFilter.COOKIE)
                                .value(session)
                                .path("/")
                                .httpOnly(true)
                                .sameSite(NewCookie.SameSite.STRICT)
                                .build())
                        .build())
                .orElseGet(() -> ProblemMapper.respond(Problem.of(ErrorCode.FORBIDDEN)));
    }
}
