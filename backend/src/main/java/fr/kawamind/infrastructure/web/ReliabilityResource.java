package fr.kawamind.infrastructure.web;

import fr.kawamind.application.context.ManageReliability;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.knowledge.Reliability;
import fr.kawamind.domain.knowledge.SourceOrigin;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** {@code /api/projects/{id}/reliability} : niveaux de fiabilité des sortes de source (016). */
@Path("/api/projects/{id}/reliability")
@Produces(MediaType.APPLICATION_JSON)
public class ReliabilityResource {

    public record LevelInput(String origin, String level) {
    }

    @Inject
    ManageReliability reliability;

    @GET
    public ManageReliability.Levels get(@PathParam("id") String id) {
        return reliability.get(ProjectResource.projectId(id));
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public ManageReliability.Levels set(@PathParam("id") String id, LevelInput input) {
        if (input == null) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED);
        }
        return reliability.set(ProjectResource.projectId(id), origin(input.origin()), level(input.level()));
    }

    private static SourceOrigin origin(String raw) {
        try {
            return raw == null ? null : SourceOrigin.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Sorte de source inconnue.");
        }
    }

    /** Niveau saisi ; vide ou absent : aucun (défaut). */
    static Reliability level(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Reliability.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "Choisissez un niveau : référence, interprétation ou à confirmer.");
        }
    }
}
