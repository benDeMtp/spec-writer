package fr.kawamind.infrastructure.llm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.infrastructure.llm.CopilotSdkGateway.CapturedCall;
import fr.kawamind.infrastructure.llm.CopilotSdkGateway.GatewayException;
import fr.kawamind.infrastructure.llm.CopilotSdkGateway.Reply;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class CopilotSdkStreamingChatModelTest {

    /** Double du SDK en flux : rend des morceaux et de la réflexion, s'arrête si l'arrêt est demandé. */
    static final class FakeGateway implements CopilotSdkGateway {
        final List<Call> calls = new ArrayList<>();
        Function<Call, Reply> behaviour;

        @Override
        public Reply call(Call call) {
            calls.add(call);
            return behaviour.apply(call);
        }
    }

    /** Ce que le framework reçoit. */
    static final class Recorder implements StreamingChatResponseHandler {
        final List<String> partials = new ArrayList<>();
        final List<String> thinking = new ArrayList<>();
        ChatResponse complete;
        Throwable error;
        boolean cancelAfterFirst;

        @Override
        public void onPartialResponse(PartialResponse partial, PartialResponseContext context) {
            partials.add(partial.text());
            if (cancelAfterFirst) {
                context.streamingHandle().cancel();
            }
        }

        @Override
        public void onPartialThinking(PartialThinking partialThinking) {
            thinking.add(partialThinking.text());
        }

        @Override
        public void onCompleteResponse(ChatResponse response) {
            complete = response;
        }

        @Override
        public void onError(Throwable e) {
            error = e;
        }
    }

    private final FakeGateway gateway = new FakeGateway();
    private final CopilotSdkStreamingChatModel model = new CopilotSdkStreamingChatModel(gateway,
            () -> Optional.of("claude-sonnet-5.5"));
    private final Recorder handler = new Recorder();

    private static ChatRequest request() {
        return ChatRequest.builder().messages(SystemMessage.from("[operation:converse-agent] Consignes"),
                UserMessage.from("<question>\nAvoirs ?\n</question>")).build();
    }

    @Test
    void morceauxEtReflexionTransmisAuFilDeLEau() {
        gateway.behaviour = call -> {
            call.reasoning().accept("Je cherche les avoirs.");
            call.deltas().accept("Les avoirs ");
            call.deltas().accept("passent par le bus [1].");
            return new Reply("Les avoirs passent par le bus [1].", List.of());
        };
        model.chat(request(), handler);
        assertThat(handler.partials).containsExactly("Les avoirs ", "passent par le bus [1].");
        assertThat(handler.thinking).containsExactly("Je cherche les avoirs.");
        assertThat(handler.complete.aiMessage().text()).isEqualTo("Les avoirs passent par le bus [1].");
        assertThat(handler.complete.finishReason()).isEqualTo(FinishReason.STOP);
        assertThat(gateway.calls.get(0).model()).isEqualTo("claude-sonnet-5.5");
        assertThat(gateway.calls.get(0).system()).isEqualTo("[operation:converse-agent] Consignes");
    }

    @Test
    void appelsDOutilsRendusALaFin() {
        gateway.behaviour = call -> new Reply("", List.of(new CapturedCall("toolu_1", "search_knowledge",
                "{\"query\":\"avoirs\"}")));
        model.chat(ChatRequest.builder().messages(request().messages()).toolSpecifications(ToolSpecification.builder()
                .name("search_knowledge").parameters(JsonObjectSchema.builder().addStringProperty("query").build())
                .build()).build(), handler);
        assertThat(gateway.calls.get(0).tools()).extracting(CopilotSdkGateway.ToolSpec::name)
                .containsExactly("search_knowledge");
        assertThat(handler.complete.finishReason()).isEqualTo(FinishReason.TOOL_EXECUTION);
        assertThat(handler.complete.aiMessage().toolExecutionRequests().get(0).arguments())
                .isEqualTo("{\"query\":\"avoirs\"}");
    }

    @Test
    void lArretDuFluxAbandonneLaRequete() {
        AtomicBoolean sawCancel = new AtomicBoolean();
        gateway.behaviour = call -> {
            call.deltas().accept("Les avoirs ");
            sawCancel.set(call.cancelled().getAsBoolean());
            return new Reply("Les avoirs ", List.of());
        };
        handler.cancelAfterFirst = true;
        model.chat(request(), handler);
        assertThat(sawCancel).isTrue();
        assertThat(handler.complete.aiMessage().text()).isEqualTo("Les avoirs ");
    }

    @Test
    void lArretDuTourEstLuDansLaPorteeDeLAppel() {
        AtomicBoolean sawCancel = new AtomicBoolean();
        gateway.behaviour = call -> {
            sawCancel.set(call.cancelled().getAsBoolean());
            return new Reply("", List.of());
        };
        ScopedValue.where(CallCancellation.CURRENT, () -> true).run(() -> model.chat(request(), handler));
        assertThat(sawCancel).isTrue();
    }

    @Test
    void erreursDuSdkTraduitesEnErreursMetier() {
        GatewayException failure = new GatewayException(GatewayException.Kind.QUOTA_EXHAUSTED, "x", null);
        gateway.behaviour = call -> {
            throw failure;
        };
        model.chat(request(), handler);
        assertThat(handler.complete).isNull();
        assertThat(handler.error).isInstanceOf(DomainException.class).hasCause(failure);
        assertThat(((DomainException) handler.error).code()).isEqualTo(ErrorCode.QUOTA_EXHAUSTED);
    }
}
