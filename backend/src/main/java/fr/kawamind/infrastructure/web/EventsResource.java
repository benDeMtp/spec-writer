package fr.kawamind.infrastructure.web;

import fr.kawamind.domain.sync.SourceUpdated;
import fr.kawamind.infrastructure.events.SourceUpdates;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jboss.resteasy.reactive.RestStreamElementType;

/**
 * {@code GET /api/events} (017, contracts/events.md) : flux SSE des sources mises à jour, ouvert par
 * chaque onglet. Les événements manqués depuis {@code Last-Event-ID} sont renvoyés d'abord.
 */
@Path("/api/events")
public class EventsResource {

    static final String SOURCE_UPDATED = "source-updated";
    static final Duration KEEP_ALIVE = Duration.ofSeconds(25);

    @Inject
    SourceUpdates updates;

    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Multi<OutboundSseEvent> events(@HeaderParam("Last-Event-ID") String lastEventId, @Context Sse sse) {
        Multi<OutboundSseEvent> missed = Multi.createFrom().iterable(updates.after(lastEventId))
                .map(e -> event(sse, e));
        Multi<OutboundSseEvent> live = updates.stream().map(e -> event(sse, e));
        Multi<OutboundSseEvent> keepAlive = Multi.createFrom().ticks().every(KEEP_ALIVE)
                .map(t -> sse.newEventBuilder().comment("ping").build());
        return Multi.createBy().concatenating().streams(missed,
                Multi.createBy().merging().streams(live, keepAlive));
    }

    private OutboundSseEvent event(Sse sse, SourceUpdated e) {
        return sse.newEventBuilder().id(e.id().toString()).name(SOURCE_UPDATED)
                .mediaType(MediaType.APPLICATION_JSON_TYPE).data(Map.class, payload(e)).build();
    }

    private static Map<String, Object> payload(SourceUpdated e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", e.id());
        body.put("projectId", e.projectId());
        body.put("projectName", e.projectName());
        body.put("sourceKey", e.sourceKey());
        body.put("sourceKind", e.sourceKind());
        body.put("sourceLabel", e.sourceLabel());
        body.put("added", e.change().added());
        body.put("modified", e.change().modified());
        body.put("removed", e.change().removed());
        body.put("at", e.at().toString());
        return body;
    }
}
