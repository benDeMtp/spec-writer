package dev.specwriter.infrastructure.web;

import dev.specwriter.application.consent.UpdateConsent;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.MediaType;
import java.util.Arrays;

/** {@code PUT /api/consents/{service}} : donne ou retire le consentement (FR-024). */
@Path("/api/consents")
public class ConsentResource {

    public record ConsentRequest(Boolean granted) {
    }

    @Inject
    UpdateConsent updateConsent;

    @PUT
    @Path("/{service}")
    @Consumes(MediaType.APPLICATION_JSON)
    public void update(@PathParam("service") String service, ConsentRequest request) {
        ConsentService target = Arrays.stream(ConsentService.values()).filter(s -> s.name().equals(service))
                .findFirst().orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        if (request == null || request.granted() == null) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Indiquez si vous acceptez ou refusez.");
        }
        updateConsent.run(target, request.granted());
    }
}
