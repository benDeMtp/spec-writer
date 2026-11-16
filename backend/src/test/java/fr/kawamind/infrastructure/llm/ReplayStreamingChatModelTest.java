package fr.kawamind.infrastructure.llm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import fr.kawamind.domain.error.DomainException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReplayStreamingChatModelTest {

    static final class Recorder implements StreamingChatResponseHandler {
        final List<String> partials = new ArrayList<>();
        final List<String> thinking = new ArrayList<>();
        ChatResponse complete;
        Throwable error;
        int cancelAfter = Integer.MAX_VALUE;

        @Override
        public void onPartialResponse(PartialResponse partial, PartialResponseContext context) {
            partials.add(partial.text());
            if (partials.size() >= cancelAfter) {
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

    private static ChatRequest request(String user) {
        return ChatRequest.builder().messages(SystemMessage.from("[operation:converse-agent] x"), UserMessage.from(user))
                .build();
    }

    @Test
    void texteRejoueParMorceauxApresLaReflexion(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("01.json"), """
                {"operation":"converse-agent","userContains":["avoirs"],"reasoning":"Je réfléchis.",
                 "text":"Les avoirs passent par le bus d'événements [1], émis par billing."}""");
        Recorder handler = new Recorder();
        new ReplayStreamingChatModel(new ReplayChatModel(dir)).chat(request("avoirs ?"), handler);
        assertThat(handler.thinking).containsExactly("Je réfléchis.");
        assertThat(handler.partials).hasSizeGreaterThan(1);
        assertThat(String.join("", handler.partials))
                .isEqualTo("Les avoirs passent par le bus d'événements [1], émis par billing.");
        assertThat(handler.complete.aiMessage().text()).isEqualTo(String.join("", handler.partials));
    }

    @Test
    void appelsDOutilsRendusDUnBloc(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("01.json"), """
                {"operation":"converse-agent","userContains":[],"toolCalls":[{"name":"analyze_perimeter",
                 "arguments":{"terms":["CreditNote"]}}]}""");
        Recorder handler = new Recorder();
        new ReplayStreamingChatModel(new ReplayChatModel(dir)).chat(request("modules ?"), handler);
        assertThat(handler.partials).isEmpty();
        assertThat(handler.complete.finishReason()).isEqualTo(FinishReason.TOOL_EXECUTION);
        assertThat(handler.complete.aiMessage().toolExecutionRequests().get(0).arguments())
                .isEqualTo("{\"terms\":[\"CreditNote\"]}");
    }

    @Test
    void lArretCoupeLeTexte(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("01.json"), """
                {"operation":"converse-agent","userContains":[],"text":"un deux trois quatre cinq six sept huit neuf dix"}""");
        Recorder handler = new Recorder();
        handler.cancelAfter = 1;
        new ReplayStreamingChatModel(new ReplayChatModel(dir)).chat(request("x"), handler);
        assertThat(handler.partials).hasSize(1);
        assertThat(handler.complete.aiMessage().text()).isEqualTo(handler.partials.get(0));

        Recorder stopped = new Recorder();
        ScopedValue.where(CallCancellation.CURRENT, () -> true)
                .run(() -> new ReplayStreamingChatModel(new ReplayChatModel(dir)).chat(request("x"), stopped));
        assertThat(stopped.partials).isEmpty();
    }

    @Test
    void aucuneEntreeDonneUneErreur(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("01.json"), """
                {"operation":"converse-agent","userContains":["avoirs"],"text":"x"}""");
        Recorder handler = new Recorder();
        new ReplayStreamingChatModel(new ReplayChatModel(dir)).chat(request("autre"), handler);
        assertThat(handler.error).isInstanceOf(DomainException.class);
        assertThat(((DomainException) handler.error).userMessage()).contains("[avoirs]");
        assertThat(handler.complete).isNull();
    }
}
