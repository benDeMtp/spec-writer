package dev.specwriter.infrastructure.web;

import dev.specwriter.application.conversation.Converse;
import dev.specwriter.application.conversation.DraftNeed;
import dev.specwriter.application.conversation.ManageConversations;
import dev.specwriter.application.conversation.StartFromConversation;
import dev.specwriter.application.conversation.TurnEvents;
import dev.specwriter.application.session.SessionSnapshot;
import dev.specwriter.domain.conversation.Conversation;
import dev.specwriter.domain.conversation.EstablishedPoint;
import dev.specwriter.domain.conversation.NeedDraft;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.question.QuestionTheme;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.jboss.logging.Logger;

/**
 * Une conversation (010, contracts/api.md) : détail, renommage, suppression, messages ; le tour de
 * l'agent se suit en flux SSE, qui rejoue d'abord ce qui est déjà produit (research.md R2).
 */
@Path("/api/conversations/{cid}")
@Produces(MediaType.APPLICATION_JSON)
public class ConversationResource {

    private static final Logger LOG = Logger.getLogger(ConversationResource.class);

    public record MessageInput(String text) {
    }

    public record RenameInput(String name) {
    }

    public record PointInput(String theme, String question, String answer, String basis, String sourceRef) {
    }

    public record DraftInput(String need, List<PointInput> points) {
    }

    @Inject
    DraftNeed draftNeed;

    @Inject
    StartFromConversation startFromConversation;

    @Inject
    Converse converse;

    @Inject
    ManageConversations manage;

    @Inject
    TurnEvents events;

    @GET
    public ManageConversations.Detail detail(@PathParam("cid") String cid) {
        return manage.detail(id(cid));
    }

    @PATCH
    @Consumes(MediaType.APPLICATION_JSON)
    public Conversation rename(@PathParam("cid") String cid, RenameInput input) {
        return manage.rename(id(cid), input == null ? null : input.name());
    }

    @DELETE
    public Response delete(@PathParam("cid") String cid) {
        manage.delete(id(cid));
        return Response.noContent().build();
    }

    @POST
    @Path("/messages")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response send(@PathParam("cid") String cid, MessageInput input) {
        Converse.Started started = converse.send(id(cid), input == null ? null : input.text());
        return Response.accepted(started).build();
    }

    @POST
    @Path("/messages/{mid}/retry")
    public Response retry(@PathParam("cid") String cid, @PathParam("mid") String mid) {
        return Response.accepted(converse.retry(id(cid), Ids.parse(mid))).build();
    }

    @DELETE
    @Path("/turns/{turnId}")
    public Response stop(@PathParam("cid") String cid, @PathParam("turnId") String turnId) {
        converse.stop(id(cid), Ids.parse(turnId));
        return Response.noContent().build();
    }

    /** Besoin et points établis proposés par l'agent ; rien n'est enregistré. */
    @POST
    @Path("/need-draft")
    public NeedDraft needDraft(@PathParam("cid") String cid) {
        return draftNeed.draft(id(cid));
    }

    /** Démarre la spécification avec le brouillon relu par l'utilisateur. */
    @POST
    @Path("/specifications")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response specify(@PathParam("cid") String cid, DraftInput input) {
        SessionSnapshot started = startFromConversation.start(id(cid), draft(input));
        return Response.status(Response.Status.CREATED).entity(SessionViews.detail(started, 0, null)).build();
    }

    static NeedDraft draft(DraftInput input) {
        if (input == null) {
            throw new DomainException(ErrorCode.NEED_TOO_SHORT);
        }
        List<PointInput> points = input.points() == null ? List.of() : input.points();
        if (points.size() > NeedDraft.MAX_POINTS) {
            throw new DomainException(ErrorCode.NEED_DRAFT_INVALID, "10 points établis au plus.");
        }
        return new NeedDraft(input.need(), points.stream().map(ConversationResource::point).toList());
    }

    private static EstablishedPoint point(PointInput p) {
        if (p == null || p.theme() == null) {
            throw new DomainException(ErrorCode.NEED_DRAFT_INVALID, "Un point établi a un thème ou une origine inconnus.");
        }
        try {
            return new EstablishedPoint(QuestionTheme.valueOf(p.theme()), p.question(), p.answer(),
                    p.basis() == null ? EstablishedPoint.Basis.USER : EstablishedPoint.Basis.valueOf(p.basis()),
                    p.sourceRef());
        } catch (IllegalArgumentException e) {
            throw new DomainException(ErrorCode.NEED_DRAFT_INVALID, "Un point établi a un thème ou une origine inconnus.");
        }
    }

    /** Flux du tour : {@code step}, {@code delta}, {@code sources}, puis {@code done} ou {@code error}. */
    @GET
    @Path("/turns/{turnId}/events")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void events(@PathParam("cid") String cid, @PathParam("turnId") String turnId, @Context SseEventSink sink,
            @Context Sse sse) {
        manage.detail(id(cid));
        UUID turn = Ids.parse(turnId);
        AtomicReference<TurnEvents.Subscription> subscription = new AtomicReference<>();
        var found = events.subscribe(turn, event -> {
            if (sink.isClosed()) {
                close(subscription.get());
                return;
            }
            sink.send(sse.newEventBuilder().name(name(event)).mediaType(MediaType.APPLICATION_JSON_TYPE)
                    .data(Object.class, event).build());
            if (event.last()) {
                closeQuietly(sink);
                close(subscription.get());
            }
        });
        if (found.isEmpty()) {
            // tour fini depuis longtemps ou inconnu : l'écran relit la conversation
            sink.send(sse.newEventBuilder().name("gone").data("{}").build());
            closeQuietly(sink);
            return;
        }
        subscription.set(found.get());
        if (found.get().finished() && !sink.isClosed()) {
            closeQuietly(sink);
        }
    }

    private static void closeQuietly(SseEventSink sink) {
        try {
            sink.close();
        } catch (java.io.IOException e) {
            LOG.debugf("Flux du tour : l'écran s'est déjà déconnecté (%s)", e.getClass().getSimpleName());
        }
    }

    private static void close(TurnEvents.Subscription subscription) {
        if (subscription != null) {
            subscription.close();
        }
    }

    static String name(TurnEvents.Event event) {
        return switch (event) {
            case TurnEvents.Event.Step s -> "step";
            case TurnEvents.Event.Delta d -> "delta";
            case TurnEvents.Event.Sources s -> "sources";
            case TurnEvents.Event.Done d -> "done";
            case TurnEvents.Event.Error e -> "error";
        };
    }

    private static UUID id(String raw) {
        try {
            return Ids.parse(raw);
        } catch (DomainException e) {
            throw new DomainException(ErrorCode.CONVERSATION_NOT_FOUND);
        }
    }
}
